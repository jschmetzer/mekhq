# Planetary Militia Reinforcements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add transient planetary-militia forces (combat vehicles + occasional infantry, mixed green/regular) to the defending static OpFor in attacker contracts, seeded as a starting pool and grown by a morale-driven reinforcement mechanic, without letting militia gate the contract win.

**Architecture:** A `militia` flag on `StratConOpForFormation` tags militia formations; `StratConOpForRoster` excludes militia from the win condition (`livingLineUnits`); `StratConOpForRosterBuilder` gains a parameterized unit generator (vehicles/infantry) plus a starting-pool seeder and a militia reinforcement adder; a `MilitiaReinforcementService` + `ContractTypeMilitiaReinforcementProfile` drive monthly growth; a `useStaticOpForMilitia` campaign option gates it. StratCon-only, `isAttacker()`-only.

**Tech Stack:** Java 21, JUnit 5, Mockito, Gradle. Build from `/Users/jasonschmetzer/projects/mekhq-static-opfor`. Pre-warm shared included builds (`./gradlew :MekHQ:compileTestJava`) once before any parallel worktree runs (sibling `megamek`/`mm-data`/`megameklab` builds are shared and must not be written concurrently).

Spec: [2026-06-19-planetary-militia-design.md](../specs/2026-06-19-planetary-militia-design.md)

## Execution staging (dependencies)

- **Stage 1 — parallel** (independent leaves): Task 1 (militia flag), Task 2 (militia profile), Task 3 (config option). Disjoint files. Run in parallel worktrees; merge.
- **Stage 2 — parallel** (depend on Stage 1, disjoint files): Task 4 (roster victory-exclusion + counter), Task 5 (builder generation + seed + reinforce adder), Task 6 (OOB panel label). Run in parallel worktrees; merge.
- **Stage 3 — sequential** (coupled): Task 7 (MilitiaReinforcementService — needs Tasks 4+5+2), then Task 8 (new-day hook — needs Task 7).
- **Stage 4:** Task 9 (docs) + Task 10 (full verification).

All gradle test commands target narrow classes to stay fast under shared-build constraints; retry once on a lock error.

---

## Task 1: Militia flag on `StratConOpForFormation`  *(Stage 1)*

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForFormation.java` (fields ~96-97; add accessors)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForFormationTest.java` (create if absent; otherwise add a test) and `StratConCampaignStateJaxbTest` for round-trip

- [ ] **Step 1: Write the failing test** (in `StratConCampaignStateJaxbTest` or a new `StratConOpForFormationTest`)

```java
@Test
void militiaFlag_defaultsFalse_andRoundTrips() {
    StratConOpForFormation f = new StratConOpForFormation();
    assertFalse(f.isMilitia(), "militia must default to false");
    f.setMilitia(true);
    assertTrue(f.isMilitia());
}
```

- [ ] **Step 2: Run it — fails to compile** (`isMilitia`/`setMilitia` missing)

Run: `./gradlew :MekHQ:compileTestJava` → expect compile error.

- [ ] **Step 3: Add the field + accessors**

After the `lastDeployedScenarioId` field (line ~97) in `StratConOpForFormation.java`:

```java
    /** True when this formation is planetary militia (excluded from the contract-win condition). */
    @XmlElement
    private boolean militia = false;
```

Add accessors in the helper-methods region:

```java
    public boolean isMilitia() {
        return militia;
    }

    public void setMilitia(final boolean militia) {
        this.militia = militia;
    }
```

- [ ] **Step 4: Run the test** → PASS.
Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForFormationTest" --tests "mekhq.campaign.stratCon.opfor.StratConCampaignStateJaxbTest"`

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForFormation.java MekHQ/unittests/mekhq/campaign/stratCon/opfor/*.java
git commit -m "feat(opfor): add militia flag to StratConOpForFormation

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `ContractTypeMilitiaReinforcementProfile` + `MilitiaProfile`  *(Stage 1)*

**Files:**
- Create: `MekHQ/src/mekhq/campaign/stratCon/opfor/ContractTypeMilitiaReinforcementProfile.java`
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/ContractTypeMilitiaReinforcementProfileTest.java`

Model this file on the existing `ContractTypeReinforcementProfile.java` (same package — read it for structure, copyright header, and the `getProfile(AtBContractType)` switch pattern), with these concrete differences:

- Define a nested record:
```java
public record MilitiaProfile(
        AtBMoraleLevel triggerThreshold,
        double probability,
        int minFormations,
        int maxFormations,
        int eventCap,
        int minStarting,
        int maxStarting) {
    public boolean isReinforcementAllowed() {
        return (probability > 0.0) && (eventCap > 0) && (maxFormations > 0);
    }
    public boolean hasStartingPool() {
        return maxStarting > 0;
    }
}
```
- Constants and the `getProfile` mapping (return `NEVER` for anything not listed):
```java
private static final MilitiaProfile ASSAULT =
        new MilitiaProfile(AtBMoraleLevel.ADVANCING, 0.55, 1, 2, 5, 2, 4);
private static final MilitiaProfile RAID =
        new MilitiaProfile(AtBMoraleLevel.ADVANCING, 0.40, 1, 1, 3, 1, 2);
private static final MilitiaProfile IRREGULAR =
        new MilitiaProfile(AtBMoraleLevel.ADVANCING, 0.30, 1, 1, 2, 0, 1);
private static final MilitiaProfile NEVER =
        new MilitiaProfile(AtBMoraleLevel.STALEMATE, 0.0, 0, 0, 0, 0, 0);
```
  - ASSAULT → `PLANETARY_ASSAULT`
  - RAID → `OBJECTIVE_RAID, DIVERSIONARY_RAID, RECON_RAID, EXTRACTION_RAID, OBSERVATION_RAID`
  - IRREGULAR → `PIRATE_HUNTING, GUERRILLA_WARFARE, MOLE_HUNTING`
  - everything else → `NEVER`

- [ ] **Step 1: Write failing tests**

```java
@Test
void getProfile_planetaryAssault_isAssault() {
    var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.PLANETARY_ASSAULT);
    assertEquals(AtBMoraleLevel.ADVANCING, p.triggerThreshold());
    assertEquals(5, p.eventCap());
    assertEquals(4, p.maxStarting());
    assertTrue(p.isReinforcementAllowed());
    assertTrue(p.hasStartingPool());
}

@Test
void getProfile_cadreDuty_isNever() {
    var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.CADRE_DUTY);
    assertFalse(p.isReinforcementAllowed());
    assertFalse(p.hasStartingPool());
}

@Test
void getProfile_objectiveRaid_isRaid() {
    var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.OBJECTIVE_RAID);
    assertEquals(3, p.eventCap());
    assertEquals(2, p.maxStarting());
}
```

- [ ] **Step 2: Run → fail (class missing).** `./gradlew :MekHQ:compileTestJava`
- [ ] **Step 3: Create the class** per the structure above (mirror `ContractTypeReinforcementProfile.java` formatting/header exactly).
- [ ] **Step 4: Run → PASS.** `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.ContractTypeMilitiaReinforcementProfileTest"`
- [ ] **Step 5: Commit**
```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/ContractTypeMilitiaReinforcementProfile.java MekHQ/unittests/mekhq/campaign/stratCon/opfor/ContractTypeMilitiaReinforcementProfileTest.java
git commit -m "feat(opfor): add militia reinforcement profile table

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `useStaticOpForMilitia` campaign option  *(Stage 1)*

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/campaignOptions/CampaignOptions.java` (field near the other static-OpFor options ~724; default in constructor ~1416; getter/setter near ~5853)
- Modify: `MekHQ/src/mekhq/campaign/campaignOptions/CampaignOptionsMarshaller.java` (near the static-OpFor writes ~1276)
- Modify: `MekHQ/src/mekhq/campaign/campaignOptions/CampaignOptionsUnmarshaller.java` (near the static-OpFor cases ~981)
- Modify: `MekHQ/src/mekhq/gui/campaignOptions/contents/RulesetsTab.java` (save ~1117, load ~1189, plus a checkbox component near the other static-OpFor controls)
- Test: `CampaignOptions` JAXB/round-trip test if one exists for these options; otherwise add a focused getter/default test.

Mirror exactly how `useStaticOpForRoster` is declared/serialized/loaded/bound (search those four files for `useStaticOpForRoster` and copy the pattern), with the new key `useStaticOpForMilitia`, default `true`.

- [ ] **Step 1: Write failing test** (default + setter)
```java
@Test
void useStaticOpForMilitia_defaultsTrue() {
    CampaignOptions o = new CampaignOptions();
    assertTrue(o.isUseStaticOpForMilitia());
    o.setUseStaticOpForMilitia(false);
    assertFalse(o.isUseStaticOpForMilitia());
}
```
- [ ] **Step 2: Run → fails (missing methods).** `./gradlew :MekHQ:compileTestJava`
- [ ] **Step 3: Implement** the field, constructor default `true`, getter `isUseStaticOpForMilitia()`, setter, marshaller write, unmarshaller case `parseBoolean(nodeContents)`, and the RulesetsTab checkbox + save/load wiring — each mirroring the corresponding `useStaticOpForRoster` line.
- [ ] **Step 4: Run → PASS.** Run the focused option test.
- [ ] **Step 5: Commit**
```bash
git add MekHQ/src/mekhq/campaign/campaignOptions/CampaignOptions.java MekHQ/src/mekhq/campaign/campaignOptions/CampaignOptionsMarshaller.java MekHQ/src/mekhq/campaign/campaignOptions/CampaignOptionsUnmarshaller.java MekHQ/src/mekhq/gui/campaignOptions/contents/RulesetsTab.java MekHQ/unittests/mekhq/campaign/**/CampaignOptions*Test.java
git commit -m "feat(opfor): add useStaticOpForMilitia campaign option (default on)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Roster victory-exclusion + militia cap counter  *(Stage 2, needs Task 1)*

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRoster.java` (fields ~106; living helpers ~216-303; `checkEliminationStatus` 363-380)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/CheckEliminationStatusTest.java`

- [ ] **Step 1: Write failing tests** in `CheckEliminationStatusTest` (read the file for its roster-builder helpers; militia formations are created via `formation.setMilitia(true)`):

```java
@Test
void checkEliminationStatus_onlyMilitiaRemain_contractWon() {
    // roster: 1 line formation (all units DESTROYED) + 1 militia formation (units READY)
    // expect CONTRACT_WON because no LINE units remain
    // ... build per the test file's existing helpers, mark militia formation setMilitia(true),
    //     set all line units' status to DESTROYED ...
    EliminationResult r = roster.checkEliminationStatus(campaign, contract, null);
    assertEquals(EliminationResult.CONTRACT_WON, r);
}

@Test
void checkEliminationStatus_lineUnitsAlive_stillActive() {
    // line units READY -> STILL_ACTIVE regardless of militia
    EliminationResult r = roster.checkEliminationStatus(campaign, contract, null);
    assertEquals(EliminationResult.STILL_ACTIVE, r);
}
```

- [ ] **Step 2: Run → fail** (`checkEliminationStatus` still counts militia, so "onlyMilitiaRemain" returns STILL_ACTIVE).
Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.CheckEliminationStatusTest"`

- [ ] **Step 3: Implement the exclusion + counter** in `StratConOpForRoster.java`.

Add the militia cap field after `reinforcementEventsFired` (~line 106):
```java
    @XmlElement
    private int militiaReinforcementEventsFired = 0;
```
Accessors near the existing reinforcement-counter accessors (~291-303):
```java
    public int getMilitiaReinforcementEventsFired() {
        return militiaReinforcementEventsFired;
    }

    public void setMilitiaReinforcementEventsFired(final int value) {
        this.militiaReinforcementEventsFired = value;
    }

    public void incrementMilitiaReinforcementEventsFired() {
        this.militiaReinforcementEventsFired++;
    }
```
Add a militia lookup + line-living helpers near `livingUnits()` (~220):
```java
    /** True when the unit's owning formation is flagged militia. */
    private boolean isMilitiaUnit(final StratConOpForUnit unit) {
        if (unit == null) {
            return false;
        }
        for (StratConOpForFormation formation : formations) {
            if (formation.getId() != null
                    && formation.getId().equals(unit.getFormationId())) {
                return formation.isMilitia();
            }
        }
        return false;
    }

    /** Living units belonging to non-militia (line) formations. */
    public List<StratConOpForUnit> livingLineUnits() {
        return unitList.stream()
                .filter(u -> !u.getStatus().isTerminal())
                .filter(u -> !isMilitiaUnit(u))
                .collect(Collectors.toList());
    }

    /** Living non-militia units assigned to the given track. */
    public List<StratConOpForUnit> livingLineUnitsForTrack(final String trackName) {
        return livingUnitsForTrack(trackName).stream()
                .filter(u -> !isMilitiaUnit(u))
                .toList();
    }
```
Change `checkEliminationStatus` (363-380) to use the line helpers:
```java
        if (livingLineUnits().isEmpty()) {
            return EliminationResult.CONTRACT_WON;
        }
        if (justResolvedScenario == null) {
            return EliminationResult.STILL_ACTIVE;
        }
        StratConTrackState track = justResolvedScenario.getTrackForScenario(
                campaign, contract.getStratconCampaignState());
        if ((track != null) && livingLineUnitsForTrack(track.getDisplayableName()).isEmpty()) {
            return EliminationResult.TRACK_PACIFIED;
        }
        return EliminationResult.STILL_ACTIVE;
```
(`StratConOpForUnit.getFormationId()` already exists — verify; it is referenced in fold logic.)

- [ ] **Step 4: Run → PASS.** Re-run `CheckEliminationStatusTest` plus the existing roster test to confirm no regression:
`./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.CheckEliminationStatusTest" --tests "mekhq.campaign.stratCon.opfor.StratConOpForRosterTest"`

- [ ] **Step 5: Commit**
```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRoster.java MekHQ/unittests/mekhq/campaign/stratCon/opfor/CheckEliminationStatusTest.java
git commit -m "feat(opfor): exclude militia from the contract-win condition + militia cap counter

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Builder — parameterized generator, starting-pool seeder, militia reinforcement adder  *(Stage 2, needs Tasks 1+2)*

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilder.java` (generateUnit 604-653; build path; reinforcement adder ~493-537)
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/FormationNamer.java` (militia naming)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilderTest.java`

- [ ] **Step 1: Write failing tests**

```java
@Test
void generateUnitOfType_tank_producesTankParams() {
    // Use a Mockito-spied/captured IUnitGenerator to assert the params unitType == TANK
    // and that MIXED ground movement modes were added. (Mirror the existing generate-path tests
    // in this file; capture UnitGeneratorParameters via ArgumentCaptor.)
}

@Test
void seedMilitiaPool_attackerContract_seedsWithinRange_andFlagsMilitia() {
    // contract.isAttacker() == true, PLANETARY_ASSAULT (start 2..4)
    // after seedMilitiaPool, roster has 2..4 formations all isMilitia()==true
}

@Test
void seedMilitiaPool_defenderContract_seedsNothing() {
    // contract.isAttacker() == false -> no militia formations added
}
```

- [ ] **Step 2: Run → fail** (methods missing). `./gradlew :MekHQ:compileTestJava`

- [ ] **Step 3: Implement.**

(a) Parameterize the generator. Replace the hard-coded `params.setUnitType(MEK)` body of `generateUnit` (604-653) by extracting a private overload that takes the type + movement modes + filter, and keep the existing signature delegating with `MEK`:

```java
    private static StratConOpForUnit generateUnit(final Campaign campaign,
            final AtBContract contract, final Faction enemyFaction,
            final SkillLevel skill, final int quality, final int weightClass) {
        return generateUnit(campaign, contract, enemyFaction, skill, quality, weightClass,
                MEK, null, null);
    }

    private static StratConOpForUnit generateUnit(final Campaign campaign,
            final AtBContract contract, final Faction enemyFaction,
            final SkillLevel skill, final int quality, final int weightClass,
            final int unitType,
            final @Nullable java.util.Set<megamek.common.enums.EntityMovementMode> movementModes,
            final @Nullable java.util.function.Predicate<megamek.common.MekSummary> filter) {

        String factionCode = (enemyFaction != null) ? enemyFaction.getShortName() : "IND";
        int year = campaign.getGameYear();

        UnitGeneratorParameters params = new UnitGeneratorParameters();
        params.setFaction(factionCode);
        params.setUnitType(unitType);
        params.setWeightClass(weightClass);
        params.setYear(year);
        params.setQuality(quality);
        if (movementModes != null) {
            params.getMovementModes().addAll(movementModes);
        }
        if (filter != null) {
            params.setFilter(filter);
        }

        MekSummary ms = campaign.getUnitGenerator().generate(params);
        if (ms == null) {
            params.setWeightClass(AtBDynamicScenarioFactory.UNIT_WEIGHT_UNSPECIFIED);
            ms = campaign.getUnitGenerator().generate(params);
        }
        if (ms == null) {
            LOGGER.warn("Unit generator returned null for faction '{}' year {}; skipping unit.", factionCode, year);
            return null;
        }
        var entity = AtBDynamicScenarioFactory.createEntityWithCrew(enemyFaction, skill, campaign, ms);
        if (entity == null) {
            LOGGER.warn("createEntityWithCrew returned null for '{}'; skipping unit.", ms.getName());
            return null;
        }
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setId(UUID.randomUUID());
        unit.setProtoEntity(new UnitTemplate(entity.getChassis(), entity.getModel(), factionCode));
        unit.setPilotName(entity.getCrew() != null ? entity.getCrew().getName(0) : "Unknown");
        unit.setGunnery(entity.getCrew() != null ? entity.getCrew().getGunnery() : 4);
        unit.setPiloting(entity.getCrew() != null ? entity.getCrew().getPiloting() : 5);
        unit.setPilotPersistentId(UUID.randomUUID());
        return unit;
    }
```

(b) Add militia constants near the other constants (~72):
```java
    /** Fraction of militia units that are conventional infantry (rest are ground combat vehicles). */
    static final double MILITIA_INFANTRY_FRACTION = 0.25;
    /** Ground combat-vehicle movement modes for militia (no VTOLs). */
    private static final java.util.Set<megamek.common.enums.EntityMovementMode> MILITIA_VEE_MODES =
            java.util.EnumSet.of(
                    megamek.common.enums.EntityMovementMode.TRACKED,
                    megamek.common.enums.EntityMovementMode.WHEELED,
                    megamek.common.enums.EntityMovementMode.HOVER,
                    megamek.common.enums.EntityMovementMode.WIGE);
    private static final int MILITIA_QUALITY = 1;            // low (F/E)
    private static final SkillLevel MILITIA_BASE_SKILL = SkillLevel.GREEN;
```

(c) A militia unit generator that rolls type per `MILITIA_INFANTRY_FRACTION`:
```java
    private static StratConOpForUnit generateMilitiaUnit(final Campaign campaign,
            final AtBContract contract, final Faction enemyFaction,
            final SkillLevel skill, final int weightClass) {
        boolean infantry = ThreadLocalRandom.current().nextDouble() < MILITIA_INFANTRY_FRACTION;
        if (infantry) {
            return generateUnit(campaign, contract, enemyFaction, skill, MILITIA_QUALITY,
                    AtBDynamicScenarioFactory.UNIT_WEIGHT_UNSPECIFIED,
                    megamek.common.units.UnitType.INFANTRY, null, null);
        }
        return generateUnit(campaign, contract, enemyFaction, skill, MILITIA_QUALITY, weightClass,
                megamek.common.units.UnitType.TANK, MILITIA_VEE_MODES,
                ms -> ms.getWalkMp() >= 1);
    }
```

(d) A militia formation builder + the public seeder and reinforcement adder. Model the formation assembly on the existing `buildFormation` / `addReinforcementFormations` (read them in this file). Each militia formation: `setMilitia(true)`, skill from `jitterSkill(MILITIA_BASE_SKILL, <profile favoring green/regular>)` clamped so it stays in `[GREEN, REGULAR]`, name via `FormationNamer` militia variant, units via `generateMilitiaUnit`. Public entry points:
```java
    public static void seedMilitiaPool(Campaign campaign, AtBContract contract,
            StratConOpForRoster roster, List<StratConTrackState> tracks);   // no-op unless contract.isAttacker()
    public static int addMilitiaReinforcementFormations(Campaign campaign, AtBContract contract,
            StratConOpForRoster roster, StratConTrackState targetTrack, int formationCount);
```
`seedMilitiaPool` reads `ContractTypeMilitiaReinforcementProfile.getProfile(contract.getContractType())`, rolls `count` in `[minStarting, maxStarting]`, and distributes formations across `tracks` (weighted like the existing track picker). `addMilitiaReinforcementFormations` mirrors `addReinforcementFormations` but flags militia and uses `generateMilitiaUnit`.

(e) Call `seedMilitiaPool` from the StratCon build path (where `buildForContract` finishes assembling the roster) **only when** `campaign.getCampaignOptions().isUseStaticOpForMilitia()` and `contract.isAttacker()`. (Add it right after the line OpFor roster is built.)

(f) `FormationNamer`: add a militia naming variant (a `boolean militia` parameter or a `nextMilitiaName(...)`) that yields names like `"Militia <N>"`; read the existing namer for its scheme and mirror.

- [ ] **Step 4: Run → PASS.** `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForRosterBuilderTest" --tests "mekhq.campaign.stratCon.opfor.FormationNamerTest"`

- [ ] **Step 5: Commit**
```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilder.java MekHQ/src/mekhq/campaign/stratCon/opfor/FormationNamer.java MekHQ/unittests/mekhq/campaign/stratCon/opfor/*.java
git commit -m "feat(opfor): militia unit generation, starting-pool seeder, reinforcement adder

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: OOB militia label  *(Stage 2, needs Task 1)*

**Files:**
- Modify: `MekHQ/src/mekhq/gui/stratCon/OpForRosterPanel.java` (formation header rendering ~259)
- Modify: `MekHQ/resources/mekhq/resources/AtBStratCon.properties` (a `opForRosterPanel.militiaTag` key)
- Test: `MekHQ/unittests/mekhq/gui/stratCon/OpForRosterPanelTest.java`

- [ ] **Step 1: Write a failing test** asserting a militia formation's rendered header contains the militia tag (mirror the existing fog-of-war rendering tests in that file).
- [ ] **Step 2: Run → fail.** `./gradlew :MekHQ:test --tests "mekhq.gui.stratCon.OpForRosterPanelTest"`
- [ ] **Step 3: Implement** — when `formation.isMilitia()`, append the localized "Planetary Militia" tag to the formation header label (respecting the existing fog-of-war masking). Add the resource key.
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit**
```bash
git add MekHQ/src/mekhq/gui/stratCon/OpForRosterPanel.java MekHQ/resources/mekhq/resources/AtBStratCon.properties MekHQ/unittests/mekhq/gui/stratCon/OpForRosterPanelTest.java
git commit -m "feat(ui): label planetary militia formations in the enemy OOB

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: `MilitiaReinforcementService`  *(Stage 3, needs Tasks 2+4+5)*

**Files:**
- Create: `MekHQ/src/mekhq/campaign/stratCon/opfor/MilitiaReinforcementService.java`
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/MilitiaReinforcementServiceTest.java`

Model on `OpForReinforcementService.java` (read it fully), with these deltas:
- Public `maybeReinforce(Campaign, AtBContract, AtBMoraleLevel oldMorale, AtBMoraleLevel newMorale)`:
  no-op unless StratCon (`contract.getStratconCampaignState() != null`), `campaign.getCampaignOptions().isUseStaticOpForMilitia()`, and `contract.isAttacker()`.
- Package-private `shouldAttemptReinforcement(MilitiaProfile profile, AtBMoraleLevel oldMorale, AtBMoraleLevel newMorale, int eventsFired)` — identical gate logic to the OpFor version (allowed, `eventsFired >= eventCap`, null check, `newMorale.getLevel() <= oldMorale.getLevel()` reject, `newMorale.getLevel() >= triggerThreshold.getLevel()`).
- Reads/increments `roster.getMilitiaReinforcementEventsFired()` / `incrementMilitiaReinforcementEventsFired()`.
- Profile via `ContractTypeMilitiaReinforcementProfile.getProfile(...)`.
- Adds formations via `StratConOpForRosterBuilder.addMilitiaReinforcementFormations(...)`.
- Posts a "planetary militia mobilizing" report and fires `OpForRosterChangedEvent`.

- [ ] **Step 1: Write failing tests** mirroring `OpForReinforcementServiceTest` for `shouldAttemptReinforcement`: fires on upward shift at/above threshold under cap; rejects flat/downward shift; rejects at cap; plus `maybeReinforce` no-ops when `!isAttacker()`.
- [ ] **Step 2: Run → fail (class missing).** `./gradlew :MekHQ:compileTestJava`
- [ ] **Step 3: Implement** per the deltas above.
- [ ] **Step 4: Run → PASS.** `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.MilitiaReinforcementServiceTest"`
- [ ] **Step 5: Commit**
```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/MilitiaReinforcementService.java MekHQ/unittests/mekhq/campaign/stratCon/opfor/MilitiaReinforcementServiceTest.java
git commit -m "feat(opfor): morale-driven planetary militia reinforcement service

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: New-day hook  *(Stage 3, needs Task 7)*

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/CampaignNewDayManager.java` (~line 1108, after the existing two reinforcement calls)

- [ ] **Step 1: Add the call** immediately after the `AllyReinforcementService.maybeReinforce(...)` line:
```java
        MilitiaReinforcementService.maybeReinforce(campaign, contract, oldMorale, newMorale);
```
Add the import `import mekhq.campaign.stratCon.opfor.MilitiaReinforcementService;` if not already present.
- [ ] **Step 2: Compile.** `./gradlew :MekHQ:compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**
```bash
git add MekHQ/src/mekhq/campaign/CampaignNewDayManager.java
git commit -m "feat(opfor): invoke militia reinforcement in the monthly new-day hook

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Docs  *(Stage 4)*

**Files:**
- Modify: `MekHQ/docs/StratCon/Static-OpFor.md`

- [ ] **Step 1: Document the feature** — add a "Planetary Militia" subsection covering: the `militia` flag, victory exclusion (`livingLineUnits`), the vehicle/infantry composition + low skill, the starting pool + morale reinforcement (attacker-only, StratCon-only), the `useStaticOpForMilitia` option, the separate militia cap counter, and the OOB label. Update the package map (§2), configuration table (§7), and add a §10 design-decision bullet. Note militia do NOT gate the contract win.
- [ ] **Step 2: Commit**
```bash
git add MekHQ/docs/StratCon/Static-OpFor.md
git commit -m "docs(stratcon): document planetary militia reinforcements

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Full verification  *(Stage 4)*

- [ ] **Step 1:** `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.*" --tests "mekhq.gui.stratCon.*" --tests "mekhq.campaign.ResolveScenarioTrackerTest"` → all pass.
- [ ] **Step 2:** `./gradlew compileJava` → BUILD SUCCESSFUL.

---

## Notes / deliberate scope limits

- **StratCon-only, attacker-only.** Pure-AtB militia and defender-contract militia are out of scope.
- **No per-scenario militia cap.** Militia are eligible for normal BV-budget deployment selection; revisit only if they crowd out line OpFor in practice.
- **Line OpFor stays Mek-only.** Only the militia path generates vehicles/infantry; broadening the line OpFor mix is separate future work.
- **`StratConOpForUnit.getFormationId()`** is assumed to exist (used by fold logic). If absent, add it before Task 4.
