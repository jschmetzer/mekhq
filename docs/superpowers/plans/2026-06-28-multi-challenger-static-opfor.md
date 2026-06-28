# Multi-Challenger Static OpFor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use workbench:executing-waves (recommended) to implement this plan wave-by-wave with parallel worktree agents, or workbench:executing-plans for inline sequential execution.
>
> **This plan uses workbench wave format.** Tasks are grouped into waves. All tasks within a wave are independent and SHOULD be executed as parallel worktree agents in a single dispatch. Barriers exist only *between* waves.

**Goal:** On garrison StratCon/AtB contracts, model successive distinct enemy forces ("challengers") over the contract's life — each a persistent, independently-attriting `StratConOpForRoster` labeled and RAT-sourced from its **own** faction — fixing the DC-label/pirate-units bug at its root.

**Architecture:** Generalize the single enemy roster into a lifecycle-tagged **list of challengers** on `StratConCampaignState` (and the pure-AtB `AtBContract` field). Each challenger carries its own faction identity captured at build time. The existing rout-end `updateEnemy` rail spawns a new challenger and retires the old one. The deployer/factory iterate **active** challengers, building a bot force per challenger from that challenger's identity. For garrison contracts, attrition-win is retired in favor of defend-the-term, with a per-challenger-defeat intel milestone.

**Tech Stack:** Java 21, JAXB (XML save persistence), JUnit 5 + Mockito, Gradle. Spec: [docs/superpowers/specs/2026-06-28-garrison-multi-challenger-opfor-design.md](../specs/2026-06-28-garrison-multi-challenger-opfor-design.md).

**Constraint:** Local fork branch `feature/stratcon-static-opfor`. No upstream PR (megamek-coding AI policy).

---

## Wave Plan (the DAG)

```
Wave 1 (foundation):          T1
Wave 2 (parallel, needs W1):  T2, T3, T6
Wave 3 (needs W2):            T4
Wave 4 (parallel, needs W3):  T5, T7, T8
Wave 5 (parallel, needs W4):  T9, T10
```

- **Wave 1 — T1** (data-model root). Everything reads the new fields/enum; it ships first, alone.
- **Wave 2 — T2, T3, T6.** Independence: T2 touches only `StratConOpForRosterBuilder.java`, T3 only `StratConCampaignState.java`, T6 only `StratConOpForDeployer.java`. Disjoint files; each consumes only T1's roster accessors (produced output), not each other's. No cross-references between the three.
- **Wave 3 — T4.** `AtBContract.java` roster-field generalization + migration consumes T3's `StratConCampaignState` list accessors. Sole task in the wave (it is the integration point the Wave-4 trio each build on).
- **Wave 4 — T5, T7, T8.** Independence: T5 touches `AtBContract.java` (`checkMorale`/`updateEnemy`), T7 touches `AtBDynamicScenarioFactory.java`, T8 touches `StratConOpForRoster.java`+`ResolveScenarioTracker.java`. Disjoint files. Each consumes T4's accessors (`getActiveOpForChallengers()`) as input but produces output the others don't read within the wave.
- **Wave 5 — T9, T10.** T9 touches GUI files (`OpForRosterPanel`, `StratConTab`, `OpForRosterEditorDialog`) + `AtBStratCon.properties`; T10 touches only docs (`Static-OpFor.md`, player guide). Disjoint.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `…/stratCon/opfor/ChallengerStatus.java` (NEW) | `ACTIVE/WITHDRAWN/DEFEATED` enum + `isTerminal()` | T1 |
| `…/stratCon/opfor/StratConOpForRoster.java` | + identity/lifecycle fields & accessors | T1 |
| `…/stratCon/opfor/StratConOpForRosterBuilder.java` | capture identity at build; `buildChallenger…` | T2 |
| `…/stratCon/StratConCampaignState.java` | `opForChallengers` list; accessors; JAXB; legacy migration; identity backfill | T3 |
| `…/stratCon/opfor/StratConOpForDeployer.java` | label/RAT bot force from the challenger's own identity | T6 |
| `…/mission/AtBContract.java` | atb challenger list; unified accessors; serialize/load+migrate; relink+backfill; `updateEnemy` spawn | T4, T5 |
| `…/mission/AtBDynamicScenarioFactory.java` | iterate active challengers, deploy one force each | T7 |
| `…/stratCon/opfor/StratConOpForRoster.java` (win path) + `…/ResolveScenarioTracker.java` | defend-the-term scoping + defeat milestone | T8 |
| `…/gui/stratCon/OpForRosterPanel.java`, `…/gui/StratConTab.java`, `…/gui/stratCon/OpForRosterEditorDialog.java`, `resources/…/AtBStratCon.properties` | multi-challenger render + GM editor selector | T9 |
| `MekHQ/docs/StratCon/Static-OpFor.md`, `…/Static-OpFor-Player-Guide.md` | document the challenger model | T10 |

All `src` paths are under `MekHQ/src/mekhq/campaign/`; tests under `MekHQ/unittests/mekhq/campaign/`.

**Build/test commands** (run from `/Users/jasonschmetzer/projects/mekhq-static-opfor`):
- Single test class: `./gradlew :MekHQ:test --tests "fully.qualified.ClassName"`
- Compile: `./gradlew :MekHQ:compileJava`

---

## Wave 1

### Task T1: ChallengerStatus enum + roster identity/lifecycle fields

> **Ladder note:** Reused `StratConOpForRoster` as the challenger carrier (rung 4) rather than a new wrapper class — identity lives on the roster that already holds per-force reinforcement counters; no second JAXB element (rung 1, YAGNI).

**Files:**
- Create: `MekHQ/src/mekhq/campaign/stratCon/opfor/ChallengerStatus.java`
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRoster.java` (fields near line 95–110; accessors near line 441)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForRosterChallengerTest.java` (new)

- [ ] **Step 1: Write the failing test**

```java
package mekhq.campaign.stratCon.opfor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StratConOpForRosterChallengerTest {
    @Test
    void identityAndLifecycleRoundTripThroughAccessors() {
        StratConOpForRoster roster = new StratConOpForRoster();
        roster.setFactionCode("PIR");
        roster.setEnemyBotName("Tortuga Fusiliers");
        roster.setStatus(ChallengerStatus.ACTIVE);
        roster.setArrivedDate(LocalDate.of(3151, 1, 1));

        assertEquals("PIR", roster.getFactionCode());
        assertEquals("Tortuga Fusiliers", roster.getEnemyBotName());
        assertEquals(ChallengerStatus.ACTIVE, roster.getStatus());
        assertEquals(LocalDate.of(3151, 1, 1), roster.getArrivedDate());
    }

    @Test
    void newRosterDefaultsToActiveStatus() {
        assertEquals(ChallengerStatus.ACTIVE, new StratConOpForRoster().getStatus());
    }

    @Test
    void challengerStatusTerminalFlag() {
        assertFalse(ChallengerStatus.ACTIVE.isTerminal());
        assertTrue(ChallengerStatus.WITHDRAWN.isTerminal());
        assertTrue(ChallengerStatus.DEFEATED.isTerminal());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForRosterChallengerTest"`
Expected: FAIL — `ChallengerStatus` and the new setters/getters do not exist (compile error).

- [ ] **Step 3a: Create the enum**

`MekHQ/src/mekhq/campaign/stratCon/opfor/ChallengerStatus.java` (use the standard MegaMek copyright header, current year 2026, as on `EliminationResult.java`):

```java
package mekhq.campaign.stratCon.opfor;

/**
 * Lifecycle state of a single challenger roster on a multi-challenger garrison contract.
 */
public enum ChallengerStatus {
    /** Currently contesting the world — eligible for deployment. */
    ACTIVE,
    /** Routed/withdrawn; survivors gone. */
    WITHDRAWN,
    /** Eliminated by the player. */
    DEFEATED;

    /** @return {@code true} for any state other than {@link #ACTIVE}. */
    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
```

- [ ] **Step 3b: Add fields to StratConOpForRoster** (after the `militiaReinforcementEventsFired` field, ~line 110)

```java
    /** Challenger faction code, captured at build time. Drives bot-force label + RAT. */
    @XmlElement
    private String factionCode;

    /** Display name captured at build time (e.g. "Draconis Combine" or a pirate band name). */
    @XmlElement
    private String enemyBotName;

    /** Player colour for this challenger's bot forces. */
    @XmlElement
    private String enemyColour;

    /** Lifecycle status. Defaults ACTIVE so legacy single-roster saves load as the active challenger. */
    @XmlElement
    private ChallengerStatus status = ChallengerStatus.ACTIVE;

    /** Arrival date (ISO-8601 string for JAXB friendliness). */
    @XmlElement(name = "arrivedDate")
    private String arrivedDateIso;

    /** End (withdraw/defeat) date (ISO-8601 string). */
    @XmlElement(name = "endedDate")
    private String endedDateIso;
```

> Note: `Camouflage` is not directly JAXB-friendly here; the challenger persists `enemyColour` (string) and re-derives camouflage at deploy time via `new Camouflage(Camouflage.COLOUR_CAMOUFLAGE, enemyColour)`. This mirrors how `enemyColour`/`enemyCamouflage` relate on the contract and avoids a custom adapter (ladder rung 6).

- [ ] **Step 3c: Add accessors** (near the reinforcement-counter accessors, ~line 472). Add `import java.time.LocalDate;` if absent.

```java
    public String getFactionCode() { return factionCode; }
    public void setFactionCode(final String factionCode) { this.factionCode = factionCode; }

    public String getEnemyBotName() { return enemyBotName; }
    public void setEnemyBotName(final String enemyBotName) { this.enemyBotName = enemyBotName; }

    public String getEnemyColour() { return enemyColour; }
    public void setEnemyColour(final String enemyColour) { this.enemyColour = enemyColour; }

    public ChallengerStatus getStatus() { return (status == null) ? ChallengerStatus.ACTIVE : status; }
    public void setStatus(final ChallengerStatus status) { this.status = status; }

    public LocalDate getArrivedDate() {
        return (arrivedDateIso == null || arrivedDateIso.isBlank()) ? null : LocalDate.parse(arrivedDateIso);
    }
    public void setArrivedDate(final LocalDate date) {
        this.arrivedDateIso = (date == null) ? null : date.toString();
    }

    public LocalDate getEndedDate() {
        return (endedDateIso == null || endedDateIso.isBlank()) ? null : LocalDate.parse(endedDateIso);
    }
    public void setEndedDate(final LocalDate date) {
        this.endedDateIso = (date == null) ? null : date.toString();
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForRosterChallengerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/ChallengerStatus.java \
        MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRoster.java \
        MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForRosterChallengerTest.java
git commit -m "feat(opfor): challenger identity + lifecycle on StratConOpForRoster"
```

---

## Wave 2 (parallel: T2, T3, T6)

### Task T2: Builder captures challenger identity

> **Ladder note:** Reused `buildRosterInternal` (rung 4); added a thin post-build identity stamp rather than threading 4 new params through the core loop (rung 6).

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilder.java` (`buildForContract` ~136, `buildForAtBContract` ~175)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilderTest.java` (existing — add a method)

- [ ] **Step 1: Write the failing test** (add to the existing test class)

```java
    @Test
    void buildForAtBContractStampsChallengerIdentity() {
        Campaign campaign = MultiChallengerTestFixtures.miniCampaign();
        AtBContract contract = MultiChallengerTestFixtures.garrisonContract(campaign, "PIR", "Pirates");

        StratConOpForRoster roster = StratConOpForRosterBuilder.buildForAtBContract(campaign, contract);

        assertEquals("PIR", roster.getFactionCode());
        assertEquals("Pirates", roster.getEnemyBotName());
        assertEquals(ChallengerStatus.ACTIVE, roster.getStatus());
        assertNotNull(roster.getArrivedDate());
    }
```

> If `MultiChallengerTestFixtures` does not exist, create it under `unittests/.../opfor/` as a small helper that builds a Mockito `Campaign` with `getGameYear()`/`getLocalDate()` stubbed and an `AtBContract` with `getEnemyCode()`, `getEnemyBotName()`, `getEnemyColour()`, `getContractType().isGarrisonType()` stubbed — mirror the stubbing already used in `StratConOpForRosterBuilderTest`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForRosterBuilderTest"`
Expected: FAIL — identity fields are null.

- [ ] **Step 3: Add a private stamp helper and call it from every public builder**

Add to `StratConOpForRosterBuilder`:

```java
    /** Stamps challenger identity onto a freshly-built enemy roster. */
    private static StratConOpForRoster stampChallengerIdentity(final StratConOpForRoster roster,
            final AtBContract contract, final Campaign campaign) {
        roster.setFactionCode(contract.getEnemyCode());
        roster.setEnemyBotName(contract.getEnemyBotName());
        roster.setEnemyColour(contract.getEnemyColour().name());
        roster.setStatus(ChallengerStatus.ACTIVE);
        roster.setArrivedDate(campaign.getLocalDate());
        return roster;
    }
```

In `buildForContract` and `buildForAtBContract`, wrap the returned roster: change `return buildRosterInternal(...);`-style returns (and the `roster` local in `buildForContract`) so the final return is `return stampChallengerIdentity(roster, contract, campaign);`. (Do NOT stamp the ally builders — allies are not challengers.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForRosterBuilderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilder.java \
        MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilderTest.java \
        MekHQ/unittests/mekhq/campaign/stratCon/opfor/MultiChallengerTestFixtures.java
git commit -m "feat(opfor): stamp challenger identity at roster build"
```

### Task T3: StratConCampaignState challenger list + accessors + migration

> **Ladder note:** Kept the legacy `opForRoster` element as a read-only migration shim (rung 6 — minimum that preserves old saves); reused JAXB `@XmlElementWrapper` pattern already used for `tracks` (rung 4).

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/StratConCampaignState.java` (fields ~88–91; accessors ~150–195; `Deserialize` ~406)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/StratConCampaignStateChallengerTest.java` (new); existing `StratConCampaignStateJaxbTest`

- [ ] **Step 1: Write the failing test**

```java
package mekhq.campaign.stratCon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import mekhq.campaign.stratCon.opfor.ChallengerStatus;
import mekhq.campaign.stratCon.opfor.StratConOpForRoster;
import org.junit.jupiter.api.Test;

class StratConCampaignStateChallengerTest {
    private static StratConOpForRoster challenger(String code, ChallengerStatus status) {
        StratConOpForRoster r = new StratConOpForRoster();
        r.setFactionCode(code);
        r.setStatus(status);
        return r;
    }

    @Test
    void getActiveChallengersExcludesTerminal() {
        StratConCampaignState state = new StratConCampaignState();
        state.addChallenger(challenger("PIR", ChallengerStatus.DEFEATED));
        StratConOpForRoster dc = challenger("DC", ChallengerStatus.ACTIVE);
        state.addChallenger(dc);

        List<StratConOpForRoster> active = state.getActiveChallengers();

        assertEquals(1, active.size());
        assertSame(dc, active.get(0));
    }

    @Test
    void primaryChallengerIsNewestActive() {
        StratConCampaignState state = new StratConCampaignState();
        state.addChallenger(challenger("PIR", ChallengerStatus.ACTIVE));
        StratConOpForRoster dc = challenger("DC", ChallengerStatus.ACTIVE);
        state.addChallenger(dc);

        assertSame(dc, state.getPrimaryChallenger());
    }

    @Test
    void getOpForRosterReturnsPrimaryActiveForBackCompat() {
        StratConCampaignState state = new StratConCampaignState();
        StratConOpForRoster pir = challenger("PIR", ChallengerStatus.ACTIVE);
        state.setOpForRoster(pir); // legacy single-set call path

        assertSame(pir, state.getOpForRoster());
        assertEquals(1, state.getActiveChallengers().size());
    }

    @Test
    void getOpForRosterNullWhenNoActive() {
        assertNull(new StratConCampaignState().getOpForRoster());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.StratConCampaignStateChallengerTest"`
Expected: FAIL — `addChallenger` / `getActiveChallengers` / `getPrimaryChallenger` missing.

- [ ] **Step 3a: Replace the single field with a list + a legacy shim**

Replace the existing `private … StratConOpForRoster opForRoster;` (line ~88) and its `@XmlElement` getter/setter with:

```java
    @XmlElementWrapper(name = "opForChallengers")
    @XmlElement(name = "challenger")
    private List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> opForChallengers = new ArrayList<>();

    /** Legacy single-roster element (pre-multi-challenger saves). Migrated in {@link #migrateLegacyRoster()}. */
    @XmlElement(name = "opForRoster")
    private mekhq.campaign.stratCon.opfor.StratConOpForRoster legacyOpForRoster;
```

- [ ] **Step 3b: Add accessors** (replacing the old `getOpForRoster`/`setOpForRoster`)

```java
    @XmlTransient
    public List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> getOpForChallengers() {
        return opForChallengers;
    }

    public void addChallenger(final mekhq.campaign.stratCon.opfor.StratConOpForRoster challenger) {
        if (challenger != null) {
            opForChallengers.add(challenger);
        }
    }

    @XmlTransient
    public List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> getActiveChallengers() {
        return opForChallengers.stream()
                .filter(c -> c.getStatus() == mekhq.campaign.stratCon.opfor.ChallengerStatus.ACTIVE)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Newest active challenger (last appended), or {@code null} if none active. */
    @XmlTransient
    public @Nullable mekhq.campaign.stratCon.opfor.StratConOpForRoster getPrimaryChallenger() {
        List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> active = getActiveChallengers();
        return active.isEmpty() ? null : active.get(active.size() - 1);
    }

    /** Back-compat accessor: the primary active challenger. */
    @XmlTransient
    public @Nullable mekhq.campaign.stratCon.opfor.StratConOpForRoster getOpForRoster() {
        return getPrimaryChallenger();
    }

    /** Back-compat setter used at contract acceptance: seeds the first challenger. */
    public void setOpForRoster(
            final @Nullable mekhq.campaign.stratCon.opfor.StratConOpForRoster roster) {
        if (roster != null) {
            addChallenger(roster);
        }
    }

    /** Migrates a legacy single-roster save into the challenger list (idempotent). */
    public void migrateLegacyRoster() {
        if (legacyOpForRoster != null && opForChallengers.isEmpty()) {
            legacyOpForRoster.setStatus(mekhq.campaign.stratCon.opfor.ChallengerStatus.ACTIVE);
            opForChallengers.add(legacyOpForRoster);
            legacyOpForRoster = null;
        }
    }

    /** Backfills null challenger identity from the contract (called after relink). */
    public void backfillChallengerIdentities(final AtBContract contract) {
        for (mekhq.campaign.stratCon.opfor.StratConOpForRoster c : opForChallengers) {
            if (c.getFactionCode() == null) {
                c.setFactionCode(contract.getEnemyCode());
                c.setEnemyBotName(contract.getEnemyBotName());
                c.setEnemyColour(contract.getEnemyColour().name());
            }
        }
    }
```

Add imports as needed: `java.util.ArrayList`, `java.util.List`, `megamek.common.annotations.Nullable`, `mekhq.campaign.mission.AtBContract`.

- [ ] **Step 3c: Call migration from `Deserialize`** — after the `for (StratConTrackState track : ...)` loop in `Deserialize` (~line 423), add:

```java
            resultingCampaignState.migrateLegacyRoster();
```

- [ ] **Step 4: Run tests** (new + existing JAXB)

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.StratConCampaignStateChallengerTest" --tests "mekhq.campaign.stratCon.StratConCampaignStateJaxbTest"`
Expected: PASS. (If `StratConCampaignStateJaxbTest` asserted on `getOpForRoster()` identity equality after round-trip, update it to assert via `getActiveChallengers()`.)

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/stratCon/StratConCampaignState.java \
        MekHQ/unittests/mekhq/campaign/stratCon/StratConCampaignStateChallengerTest.java
git commit -m "feat(opfor): challenger list + legacy-roster migration on StratConCampaignState"
```

### Task T6: Deployer labels/RATs from the challenger's own identity (root-cause fix)

> **Ladder note:** Minimal change at the single naming site (rung 6); reuses existing camo derivation from a colour name.

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForDeployer.java` (OPFOR naming block ~258–262)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForDeployerTest.java` (existing — add a method)

- [ ] **Step 1: Write the failing test** — the regression guard for the original bug.

```java
    @Test
    void opForBotForceLabeledFromRosterFactionNotContract() {
        // Contract enemy has DRIFTED to Draconis Combine, but the roster is the Pirate challenger.
        Campaign campaign = MultiChallengerTestFixtures.miniCampaign();
        AtBContract contract = MultiChallengerTestFixtures.garrisonContract(campaign, "DC", "Draconis Combine");
        StratConOpForRoster roster = MultiChallengerTestFixtures.deployablePirateChallenger(campaign);
        roster.setEnemyBotName("Tortuga Fusiliers");
        roster.setFactionCode("PIR");

        BotForce force = StratConOpForDeployer.selectAndDeploy(
                "Sector 0", new java.util.UUID(1L, 0L), roster,
                MultiChallengerTestFixtures.mixedGroundTemplate(), 5000.0, contract, campaign);

        assertNotNull(force);
        assertTrue(force.getName().startsWith("Tortuga Fusiliers"),
                "bot force must be named from the roster's faction, not contract.getEnemyBotName()");
    }
```

> Reuse the materialization/fixture helpers the existing `StratConOpForDeployerTest` already uses to produce a deployable roster; add the helper(s) referenced above to `MultiChallengerTestFixtures` if not already present.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForDeployerTest"`
Expected: FAIL — name starts with "Draconis Combine".

- [ ] **Step 3: Read identity from the roster** — replace the OPFOR branch lines (~258–261):

```java
            String challengerName = (roster.getEnemyBotName() != null)
                    ? roster.getEnemyBotName() : contract.getEnemyBotName();
            botForce.setName(challengerName + " " + forceTemplate.getForceName());
            PlayerColour challengerColour = (roster.getEnemyColour() != null)
                    ? PlayerColour.valueOf(roster.getEnemyColour()) : contract.getEnemyColour();
            botForce.setColour(challengerColour);
            botForce.setCamouflage(new Camouflage(Camouflage.COLOUR_CAMOUFLAGE, challengerColour.name()));
            botForce.setTeam(ScenarioForceTemplate.TEAM_IDS.get(ForceAlignment.Opposing.ordinal()));
```

Add imports if absent: `megamek.common.icons.Camouflage`, `megamek.common.enums.PlayerColour`. (The ally branch is unchanged.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConOpForDeployerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForDeployer.java \
        MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConOpForDeployerTest.java \
        MekHQ/unittests/mekhq/campaign/stratCon/opfor/MultiChallengerTestFixtures.java
git commit -m "fix(opfor): label/RAT bot force from challenger identity, not drifted contract enemy"
```

---

## Wave 3

### Task T4: AtBContract challenger list, unified accessors, serialize/load+migrate, relink+backfill

> **Ladder note:** Mirrors the StratCon-state list shape (rung 4 reuse); keeps the manual `serializeAs`/`loadFieldsFromXmlNode` style already in `AtBContract` rather than introducing JAXB there (rung 6 — match local pattern).

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/mission/AtBContract.java` (fields ~150; serialize ~869; load ~911; relink ~921; unified accessors ~993)
- Test: `MekHQ/unittests/mekhq/campaign/mission/AtBContractChallengerTest.java` (new); existing `AtBContractTest`

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void getActiveOpForChallengersPrefersStratConStateList() {
        AtBContract contract = MultiChallengerTestFixtures.garrisonContract(campaign, "PIR", "Pirates");
        StratConCampaignState state = mock(StratConCampaignState.class);
        StratConOpForRoster dc = new StratConOpForRoster();
        dc.setStatus(ChallengerStatus.ACTIVE);
        when(state.getActiveChallengers()).thenReturn(List.of(dc));
        contract.setStratConCampaignState(state);

        assertEquals(List.of(dc), contract.getActiveOpForChallengers());
    }

    @Test
    void getOpForRosterReturnsPrimaryFromList() {
        AtBContract contract = MultiChallengerTestFixtures.garrisonContract(campaign, "PIR", "Pirates");
        StratConOpForRoster pir = new StratConOpForRoster();
        pir.setStatus(ChallengerStatus.ACTIVE);
        contract.addAtbChallenger(pir);

        assertSame(pir, contract.getOpForRoster());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.mission.AtBContractChallengerTest"`
Expected: FAIL — `getActiveOpForChallengers` / `addAtbChallenger` missing.

- [ ] **Step 3a: Generalize the atb field** — replace `private … atbOpForRoster;` (line 150) with:

```java
    private final java.util.List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> atbOpForChallengers =
            new java.util.ArrayList<>();
```

- [ ] **Step 3b: Unified accessors** — replace `getOpForRoster()` body (~993) and add list accessors:

```java
    public @Nullable mekhq.campaign.stratCon.opfor.StratConOpForRoster getOpForRoster() {
        java.util.List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> active = getActiveOpForChallengers();
        return active.isEmpty() ? null : active.get(active.size() - 1);
    }

    /** All ACTIVE challengers, from StratCon state if present else the pure-AtB list. */
    public java.util.List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> getActiveOpForChallengers() {
        StratConCampaignState state = getStratConCampaignState();
        if (state != null) {
            return state.getActiveChallengers();
        }
        return atbOpForChallengers.stream()
                .filter(c -> c.getStatus() == mekhq.campaign.stratCon.opfor.ChallengerStatus.ACTIVE)
                .collect(java.util.stream.Collectors.toList());
    }

    /** All challengers (any status) for the pure-AtB backing store. */
    public java.util.List<mekhq.campaign.stratCon.opfor.StratConOpForRoster> getAtbOpForChallengers() {
        return atbOpForChallengers;
    }

    public void addAtbChallenger(final mekhq.campaign.stratCon.opfor.StratConOpForRoster roster) {
        if (roster != null) {
            atbOpForChallengers.add(roster);
        }
    }
```

Replace the old `setAtbOpForRoster(...)` callers (in `acceptContract` ~1037) with `addAtbChallenger(StratConOpForRosterBuilder.buildForAtBContract(campaign, this))`.

- [ ] **Step 3c: Serialize the list** — replace the `atbOpForRoster` serialize block (~869):

```java
        for (mekhq.campaign.stratCon.opfor.StratConOpForRoster challenger : atbOpForChallengers) {
            challenger.serializeAs(printWriter, "atbOpForChallenger");
        }
```

- [ ] **Step 3d: Load the list + migrate legacy** — replace the `atbOpForRoster` load branch (~911):

```java
                } else if (item.getNodeName().equalsIgnoreCase("atbOpForChallenger")) {
                    atbOpForChallengers.add(
                            mekhq.campaign.stratCon.opfor.StratConOpForRoster.deserialize(item));
                } else if (item.getNodeName().equalsIgnoreCase("atbOpForRoster")) {
                    // Legacy single-roster save → one ACTIVE challenger.
                    mekhq.campaign.stratCon.opfor.StratConOpForRoster legacy =
                            mekhq.campaign.stratCon.opfor.StratConOpForRoster.deserialize(item);
                    legacy.setStatus(mekhq.campaign.stratCon.opfor.ChallengerStatus.ACTIVE);
                    atbOpForChallengers.add(legacy);
                }
```

- [ ] **Step 3e: Relink + backfill** — in the relink block (~921), after `getStratConCampaignState().setContract(this);` add:

```java
            getStratConCampaignState().migrateLegacyRoster();
            getStratConCampaignState().backfillChallengerIdentities(this);
```

And after the pure-AtB load, backfill the atb list:

```java
        for (mekhq.campaign.stratCon.opfor.StratConOpForRoster c : atbOpForChallengers) {
            if (c.getFactionCode() == null) {
                c.setFactionCode(getEnemyCode());
                c.setEnemyBotName(getEnemyBotName());
                c.setEnemyColour(getEnemyColour().name());
            }
        }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.mission.AtBContractChallengerTest" --tests "mekhq.campaign.mission.AtBContractTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/mission/AtBContract.java \
        MekHQ/unittests/mekhq/campaign/mission/AtBContractChallengerTest.java
git commit -m "feat(opfor): AtBContract challenger list, unified accessors, legacy migration + backfill"
```

---

## Wave 4 (parallel: T5, T7, T8)

### Task T5: `updateEnemy` spawns a challenger + retires the outgoing one

> **Ladder note:** Hooks the existing rout-end `updateEnemy` rail (rung 4) — no new scheduler; gated to in-scope contracts so non-garrison behavior is untouched (rung 1).

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/mission/AtBContract.java` (`updateEnemy(Campaign, LocalDate, String)` ~294–361 tail)
- Test: `MekHQ/unittests/mekhq/campaign/mission/AtBContractChallengerTest.java`

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void updateEnemySpawnsNewChallengerAndRetiresOldForGarrisonStaticContract() {
        when(campaign.getCampaignOptions().isUseStaticOpForRoster()).thenReturn(true);
        AtBContract contract = MultiChallengerTestFixtures.garrisonStaticContract(campaign, "PIR", "Pirates");
        StratConOpForRoster pir = new StratConOpForRoster();
        pir.setFactionCode("PIR");
        pir.setStatus(ChallengerStatus.ACTIVE);
        contract.addAtbChallenger(pir); // no StratCon state in this fixture → uses atb list

        contract.updateEnemy(campaign, LocalDate.of(3151, 6, 1), "DC"); // forced new faction

        assertEquals(ChallengerStatus.WITHDRAWN, pir.getStatus());
        List<StratConOpForRoster> active = contract.getActiveOpForChallengers();
        assertEquals(1, active.size());
        assertEquals("DC", active.get(0).getFactionCode());
    }

    @Test
    void updateEnemyDoesNotSpawnChallengerWhenStaticOpForOff() {
        when(campaign.getCampaignOptions().isUseStaticOpForRoster()).thenReturn(false);
        AtBContract contract = MultiChallengerTestFixtures.garrisonContract(campaign, "PIR", "Pirates");

        contract.updateEnemy(campaign, LocalDate.of(3151, 6, 1), "DC");

        assertTrue(contract.getAtbOpForChallengers().isEmpty());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.mission.AtBContractChallengerTest"`
Expected: FAIL — no challenger spawned.

- [ ] **Step 3: Append spawn/retire at the end of `updateEnemy(Campaign, LocalDate, String)`** (after `checkForSpecialClanSalvageClause(...)`):

```java
        maybeSpawnChallenger(campaign, today);
    }

    /**
     * For in-scope (garrison-type + static OpFor) contracts, retire the outgoing challenger(s) and build a new
     * ACTIVE challenger for the now-current enemy faction. No-op otherwise, so non-garrison and dynamic-OpFor
     * contracts are unaffected.
     */
    private void maybeSpawnChallenger(final Campaign campaign, final LocalDate today) {
        if (!campaign.getCampaignOptions().isUseStaticOpForRoster()
                || !getContractType().isGarrisonType()) {
            return;
        }

        // Retire current active challengers.
        for (mekhq.campaign.stratCon.opfor.StratConOpForRoster c : getActiveOpForChallengers()) {
            boolean noLiving = c.livingLineUnits().isEmpty();
            c.setStatus(noLiving
                    ? mekhq.campaign.stratCon.opfor.ChallengerStatus.DEFEATED
                    : mekhq.campaign.stratCon.opfor.ChallengerStatus.WITHDRAWN);
            c.setEndedDate(today);
        }

        // Build the new challenger for the freshly-rolled faction.
        StratConCampaignState state = getStratConCampaignState();
        mekhq.campaign.stratCon.opfor.StratConOpForRoster fresh = (state != null)
                ? mekhq.campaign.stratCon.opfor.StratConOpForRosterBuilder.buildForContract(campaign, this, state)
                : mekhq.campaign.stratCon.opfor.StratConOpForRosterBuilder.buildForAtBContract(campaign, this);
        if (state != null) {
            state.addChallenger(fresh);
        } else {
            addAtbChallenger(fresh);
        }
    }
```

> The retire loop reads `getActiveOpForChallengers()` *before* appending the new one, so the new challenger is never retired in the same pass.

- [ ] **Step 4: Run tests**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.mission.AtBContractChallengerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/mission/AtBContract.java \
        MekHQ/unittests/mekhq/campaign/mission/AtBContractChallengerTest.java
git commit -m "feat(opfor): spawn new challenger + retire old on rout-end for garrison static contracts"
```

### Task T7: Factory deploys one bot force per active challenger

> **Ladder note:** Wraps the existing single-roster hook in a per-challenger loop (rung 6); reuses `selectAndDeploy` unchanged.

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/mission/AtBDynamicScenarioFactory.java` (static-OpFor hook ~362–410)
- Test: `MekHQ/unittests/mekhq/campaign/mission/AtBDynamicScenarioFactoryStaticOpForTest.java` (new, focused on the iteration helper)

- [ ] **Step 1: Extract the per-challenger deploy into a testable static helper and write its test.**

Add a package-visible helper to `AtBDynamicScenarioFactory`:

```java
    /** Deploys one OPFOR bot force per active challenger; returns the forces (possibly empty). */
    static List<BotForce> deployActiveChallengers(final List<StratConOpForRoster> challengers,
            final AtBScenario scenario, final ScenarioForceTemplate forceTemplate, final double targetBV,
            final AtBContract contract, final Campaign campaign) {
        List<BotForce> out = new ArrayList<>();
        StratConScenario stratConScenario =
                StratConCampaignState.getStratConScenarioFromAtBScenario(campaign, scenario);
        for (StratConOpForRoster challenger : challengers) {
            BotForce force;
            if (stratConScenario != null) {
                force = StratConOpForDeployer.selectAndDeploy(
                        stratConScenario, challenger, forceTemplate, targetBV, contract, campaign);
            } else {
                String trackName = StratConOpForRosterBuilder.DEFAULT_ATB_TRACK_NAME;
                java.util.UUID id = new java.util.UUID(scenario.getId(), 0L);
                force = StratConOpForDeployer.selectAndDeploy(
                        trackName, id, challenger, forceTemplate, targetBV, contract, campaign);
            }
            if (force != null) {
                out.add(force);
            }
        }
        return out;
    }
```

Test:

```java
    @Test
    void deployActiveChallengersProducesOneForcePerChallenger() {
        // Two active challengers, both deployable on the mixed-ground template.
        Campaign campaign = MultiChallengerTestFixtures.miniCampaign();
        AtBContract contract = MultiChallengerTestFixtures.garrisonContract(campaign, "DC", "Draconis Combine");
        AtBScenario scenario = MultiChallengerTestFixtures.atbScenario(7);
        StratConOpForRoster pir = MultiChallengerTestFixtures.deployablePirateChallenger(campaign);
        StratConOpForRoster dc = MultiChallengerTestFixtures.deployableChallenger(campaign, "DC", "Draconis Combine");

        List<BotForce> forces = AtBDynamicScenarioFactory.deployActiveChallengers(
                List.of(pir, dc), scenario, MultiChallengerTestFixtures.mixedGroundTemplate(),
                5000.0, contract, campaign);

        assertEquals(2, forces.size());
        assertTrue(forces.stream().anyMatch(f -> f.getName().startsWith("Tortuga Fusiliers")));
        assertTrue(forces.stream().anyMatch(f -> f.getName().startsWith("Draconis Combine")));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.mission.AtBDynamicScenarioFactoryStaticOpForTest"`
Expected: FAIL — helper does not exist.

- [ ] **Step 3: Implement the helper (above) and rewire the hook** — in the static-OpFor hook (~372–405), replace the single `staticOpFor` deploy branch so the OpFor side iterates challengers:

```java
        if (staticOpFor) {
            List<BotForce> challengerForces = deployActiveChallengers(
                    contract.getActiveOpForChallengers(), scenario, forceTemplate, targetBV, contract, campaign);
            for (BotForce force : challengerForces) {
                scenario.addBotForce(force, forceTemplate, campaign);
                generatedLanceCount += force.getFullEntityList(campaign).size() / 4;
            }
            if (!challengerForces.isEmpty()) {
                continue;
            }
            LOGGER.info("Static OpFor produced no force for scenario '{}'; falling back to dynamic generation.",
                    scenario.getName());
        } else if (staticAlly) {
            // unchanged ally branch (single roster)
            ...
        }
```

Keep the ally branch exactly as it was. `shouldUseStaticPath(alignment, opForRoster)` still gates entry; pass `contract.getOpForRoster()` (primary) to that predicate as today — it only needs non-null to enter, then the loop handles all active challengers.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.mission.AtBDynamicScenarioFactoryStaticOpForTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/campaign/mission/AtBDynamicScenarioFactory.java \
        MekHQ/unittests/mekhq/campaign/mission/AtBDynamicScenarioFactoryStaticOpForTest.java
git commit -m "feat(opfor): deploy one bot force per active challenger"
```

### Task T8: Defend-the-term win scoping + defeat milestone

> **Ladder note:** Adds a garrison guard to the existing win path (rung 6); reuses `IntelLog`/`IntelLogEntry` (rung 4) for the milestone.

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRoster.java` (`checkEliminationStatus` ~532)
- Modify: `MekHQ/src/mekhq/campaign/ResolveScenarioTracker.java` (CONTRACT_WON branches ~2135, ~2184)
- Test: existing `CheckEliminationStatusTest`; new `MekHQ/unittests/.../opfor/ChallengerDefeatTest.java`

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void garrisonContractDoesNotWinByEliminatingOneChallenger() {
        AtBContract contract = MultiChallengerTestFixtures.garrisonContract(campaign, "PIR", "Pirates");
        StratConOpForRoster roster = new StratConOpForRoster(); // empty → no living line units
        roster.setStatus(ChallengerStatus.ACTIVE);

        EliminationResult result = roster.checkEliminationStatus(campaign, contract, null);

        assertEquals(EliminationResult.STILL_ACTIVE, result,
                "garrison contracts defend the term; clearing one challenger must not win");
        assertEquals(ChallengerStatus.DEFEATED, roster.getStatus(),
                "the cleared challenger is marked DEFEATED");
    }

    @Test
    void nonGarrisonContractStillWinsByElimination() {
        AtBContract contract = MultiChallengerTestFixtures.raidContract(campaign, "PIR", "Pirates");
        StratConOpForRoster roster = new StratConOpForRoster();

        assertEquals(EliminationResult.CONTRACT_WON,
                roster.checkEliminationStatus(campaign, contract, null));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.ChallengerDefeatTest"`
Expected: FAIL — garrison currently returns CONTRACT_WON.

- [ ] **Step 3: Scope the win in `checkEliminationStatus`** — replace the leading `if (livingLineUnits().isEmpty())` block:

```java
        if (livingLineUnits().isEmpty()) {
            // For garrison-type contracts, clearing a challenger is a milestone, not a contract win:
            // the garrison defends for its term and another challenger may arrive.
            if (contract.getContractType().isGarrisonType()) {
                if (getStatus() == ChallengerStatus.ACTIVE) {
                    setStatus(ChallengerStatus.DEFEATED);
                }
                return EliminationResult.STILL_ACTIVE;
            }
            return EliminationResult.CONTRACT_WON;
        }
```

- [ ] **Step 4: Add the defeat milestone in `ResolveScenarioTracker`** — at both CONTRACT_WON branches (~2135, ~2184), the `STILL_ACTIVE` garrison case now falls through; add, immediately after computing `eliminationResult`, a milestone when a challenger just became DEFEATED:

```java
            if (atbContract.getContractType().isGarrisonType()
                    && contractOpForRoster.getStatus() == ChallengerStatus.DEFEATED
                    && contractOpForRoster.getEndedDate() == null) {
                contractOpForRoster.setEndedDate(campaign.getLocalDate());
                campaign.getIntelLog().addEntry(new IntelLogEntry(
                        contractOpForRoster.getFactionCode(), atbContract.getName(),
                        campaign.getLocalDate(), null,
                        contractOpForRoster.getEnemyBotName(), null,
                        IntelLogEntry.Outcome.OBSERVED));
            }
```

> Confirm the accessor for the campaign-level intel log (`campaign.getIntelLog()`); if the project exposes it differently, use that accessor. The milestone is best-effort and must not throw.

- [ ] **Step 5: Run tests + commit**

Run: `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.ChallengerDefeatTest" --tests "mekhq.campaign.stratCon.opfor.CheckEliminationStatusTest"`
Expected: PASS.

```bash
git add MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRoster.java \
        MekHQ/src/mekhq/campaign/ResolveScenarioTracker.java \
        MekHQ/unittests/mekhq/campaign/stratCon/opfor/ChallengerDefeatTest.java
git commit -m "feat(opfor): defend-the-term win scoping + challenger-defeat intel milestone"
```

---

## Wave 5 (parallel: T9, T10)

### Task T9: Multi-challenger OOB rendering + GM editor selector

> **Ladder note:** Reuses one `OpForRosterPanel` per challenger inside a container (rung 4) rather than refactoring the tree renderer; the GM editor gains a selector but reuses `OpForRosterEditOps` unchanged (rung 1 — no new edit primitives).

**Files:**
- Modify: `MekHQ/src/mekhq/gui/StratConTab.java` (`getActiveRoster` ~320; panel wiring ~182)
- Modify: `MekHQ/src/mekhq/gui/stratCon/OpForRosterPanel.java` (add a list-aware render entry)
- Modify: `MekHQ/src/mekhq/gui/stratCon/OpForRosterEditorDialog.java` (challenger selector)
- Modify: `MekHQ/resources/mekhq/resources/AtBStratCon.properties` (new keys)
- Test: `MekHQ/unittests/mekhq/gui/stratCon/OpForRosterPanelTest` (existing — extend)

- [ ] **Step 1: Write the failing test** — the panel renders each active challenger's faction header.

```java
    @Test
    void rendersOneSectionPerActiveChallenger() {
        StratConOpForRoster pir = challengerWithName("Tortuga Fusiliers");
        StratConOpForRoster dc = challengerWithName("Draconis Combine");
        OpForRosterPanel panel = new OpForRosterPanel(() -> List.of(pir, dc));

        panel.refresh();

        String text = SwingTestUtilities.collectLabelText(panel); // existing helper or a small JLabel walker
        assertTrue(text.contains("Tortuga Fusiliers"));
        assertTrue(text.contains("Draconis Combine"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :MekHQ:test --tests "mekhq.gui.stratCon.OpForRosterPanelTest"`
Expected: FAIL — no list-aware constructor.

- [ ] **Step 3a: Add a list-aware path to `OpForRosterPanel`.** Add a `Supplier<List<StratConOpForRoster>>` constructor; in `refresh()`, when the list supplier is set, render each challenger as a faction-titled sub-section (reuse the existing per-roster build by extracting the current single-roster body into `renderRoster(StratConOpForRoster, String titlePrefix)` and calling it once per active challenger, then a collapsed "Past challengers" group for terminal ones). Title prefix = `roster.getEnemyBotName()`; new key `opForRosterPanel.challengerHeader` = `{0} — {1}` (name, status). Keep the existing single-roster constructor delegating to a one-element list for back-compat.

- [ ] **Step 3b: Wire `StratConTab.getActiveRoster` to a list supplier** — add:

```java
    private List<StratConOpForRoster> getActiveChallengers() {
        if (listCurrentTrack == null) { return List.of(); }
        TrackDropdownItem tdi = listCurrentTrack.getSelectedValue();
        return (tdi == null) ? List.of() : tdi.contract.getActiveOpForChallengers();
    }
```

and change the Enemy OOB panel construction (~182) to `new OpForRosterPanel(this::getActiveChallengers, getCampaignGui().getCampaign(), this::getActiveTrack)`. Allied panel unchanged.

- [ ] **Step 3c: GM editor selector** — in `OpForRosterEditorDialog`, accept `List<StratConOpForRoster>` challengers + a combo box; the existing edit logic operates on the selected challenger's live roster. New keys: `opForEditor.challengerLabel` = `Challenger:`.

- [ ] **Step 3d: Add resource keys** to `AtBStratCon.properties`:

```properties
opForRosterPanel.challengerHeader={0} — {1}
opForRosterPanel.pastChallengers=Past challengers
opForRosterPanel.statusActive=active
opForRosterPanel.statusWithdrawn=withdrawn
opForRosterPanel.statusDefeated=defeated
opForEditor.challengerLabel=Challenger\:
```

- [ ] **Step 4: Run test + manual check**

Run: `./gradlew :MekHQ:test --tests "mekhq.gui.stratCon.OpForRosterPanelTest"`
Expected: PASS. Then `./gradlew :MekHQ:run` and confirm the Enemy OOB shows per-challenger sections (manual; Swing render).

- [ ] **Step 5: Commit**

```bash
git add MekHQ/src/mekhq/gui/stratCon/OpForRosterPanel.java MekHQ/src/mekhq/gui/StratConTab.java \
        MekHQ/src/mekhq/gui/stratCon/OpForRosterEditorDialog.java \
        MekHQ/resources/mekhq/resources/AtBStratCon.properties \
        MekHQ/unittests/mekhq/gui/stratCon/OpForRosterPanelTest.java
git commit -m "feat(opfor): multi-challenger OOB rendering + GM editor challenger selector"
```

### Task T10: Documentation

> **Ladder note:** Docs-only; no code. Required by the static-opfor branch doc convention.

**Files:**
- Modify: `MekHQ/docs/StratCon/Static-OpFor.md` (new section), `MekHQ/docs/StratCon/Static-OpFor-Player-Guide.md`

- [ ] **Step 1:** Add a "Multi-challenger garrison contracts" section to `Static-OpFor.md` covering: the challenger list model (`StratConOpForRoster` identity/lifecycle + `ChallengerStatus`); the rout-end `updateEnemy` spawn rail; defend-the-term win scoping; per-challenger deploy/label/RAT; legacy single-roster migration; the new accessors (`getActiveOpForChallengers`, `getPrimaryChallenger`). Cross-reference the spec.
- [ ] **Step 2:** Add a short player-guide note: successive challengers strike the garrison, each its own force; you defend the term rather than winning by wiping one out; the Enemy OOB shows each active challenger.
- [ ] **Step 3: Commit**

```bash
git add MekHQ/docs/StratCon/Static-OpFor.md MekHQ/docs/StratCon/Static-OpFor-Player-Guide.md
git commit -m "docs(opfor): document multi-challenger garrison model"
```

---

## Final integration check (after Wave 5)

- [ ] `./gradlew :MekHQ:compileJava` clean.
- [ ] `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.*" --tests "mekhq.campaign.mission.AtBContract*" --tests "mekhq.gui.stratCon.*"` green.
- [ ] Manual: garrison static contract, force a rout-end, confirm a new-faction challenger arrives with matching label+units, the old one moves to "Past challengers", and clearing one does not end the contract.
- [ ] Run the pre-push council; address findings; push to `origin/feature/stratcon-static-opfor` only.

---

## Self-Review

- **Wave coverage:** T1–T10 all appear in the wave plan; independence proofs given per wave.
- **Spec coverage:** §4 data model→T1/T3/T4; §5 lifecycle→T5; §6 deploy fix→T6/T7; §7 win/milestone→T8; §8 contract fields→T4 (primary accessor); §9 persistence/migration→T3/T4, UI→T9; §11 tests→each task; §12 deferrals respected (no new CampaignOptions, allied single, no concurrent-fronts); §14 docs→T10.
- **Type consistency:** `getActiveOpForChallengers()` (AtBContract), `getActiveChallengers()` (StratConCampaignState), `getPrimaryChallenger()`, `addChallenger`/`addAtbChallenger`, `ChallengerStatus.{ACTIVE,WITHDRAWN,DEFEATED}`, `getFactionCode/getEnemyBotName/getEnemyColour/getStatus/getArrivedDate/getEndedDate` used consistently across T1/T3/T4/T5/T6/T8.
- **Open verification flagged for execution:** the campaign intel-log accessor name in T8 (`campaign.getIntelLog()`), and whether `StratConCampaignStateJaxbTest`/`AtBContractTest` assert on the old single-roster accessor (update to the list accessors). These are called out inline rather than guessed.
