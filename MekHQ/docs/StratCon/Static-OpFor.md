# StratCon Static OpFor

A persistent, fog-of-war **order of battle** for AtB/StratCon contracts. Instead of
generating a fresh random enemy force every scenario, a contract is given a fixed roster of
named formations and units that **persists for the life of the contract**, takes and remembers
damage across scenarios, and is whittled down as the player destroys, salvages, or captures it.
A parallel **allied** roster models employer support with the same machinery.

This document describes the feature as built on the `feature/stratcon-static-opfor` branch. All
classes under `mekhq.campaign.stratCon.opfor` (and its `intel` subpackage) are new; the rest of
the feature is integration hooks on existing classes.

> **Player documentation:** for the non-technical, how-to-play write-up (turning it on, reading the
> OOB tabs, fog of war, winning by attrition, militia, the Intelligence Log, the GM editor), see
> [`Static-OpFor-Player-Guide.md`](Static-OpFor-Player-Guide.md) in this folder.

---

## 1. Overview

| Stock (dynamic) OpFor | Static OpFor |
|---|---|
| Random force generated per scenario | One roster built when the contract is accepted |
| No memory between scenarios | Units carry damage, status, and intel across scenarios |
| Enemy strength is opaque | Fog-of-war OOB the player can scout and grind down |
| No "win by attrition" | Eliminating the roster wins the contract |

**When it activates.** The master gate is the campaign option **`useStaticOpForRoster`**
(default off). When on, accepting a contract builds enemy + allied rosters. It works in two
modes:

- **StratCon mode** (`useStratCon` on): rosters live on the contract's `StratConCampaignState`.
- **Pure-AtB mode** (`useStratCon` off): rosters live directly on the `AtBContract`, and all
  formations are assigned to a single synthetic track, `"Sector 0"`
  (`StratConOpForRosterBuilder.DEFAULT_ATB_TRACK_NAME`).

Subcontracts do not get their own roster; they delegate to the parent contract.

---

## 2. Package map

All paths under `MekHQ/src/mekhq/campaign/stratCon/opfor/`.

### Core model
- **`StratConOpForRoster`** — the whole OOB for one side of one contract. Holds the unit list,
  a transient `unitsById` index (rebuilt in `afterUnmarshal`), the formations, the list of
  formations destroyed this contract, and the reinforcement-event counter. Home of
  `foldResolutionInto(...)` and `checkEliminationStatus(...)`.
- **`StratConOpForFormation`** — a lance/star/Level II: id, name, weight class, quality, skill,
  `assignedTrackName`, the member `unitIds`, its `IntelLevel`, and `lastDeployedScenarioId`.
- **`StratConOpForUnit`** — one unit: id (also written to the deployed entity's external id),
  `UnitTemplate protoEntity`, pilot name + gunnery/piloting, `pilotPersistentId` (written to the
  crew's external id for capture reconciliation), `formationId`, `Status`, `revealed` flag,
  `PersistentDamageState`, and `lastDeployedScenarioId`.
- **`UnitTemplate`** — chassis / model / faction code; the `chassis + " " + model`
  `MekSummaryCache` lookup key. Built via `UnitTemplate.fromEntity(entity, factionCode)`, which stores
  `entity.getFullChassis()` (**not** the bare `getChassis()`) so the key reproduces
  `entity.getShortNameRaw()` — the exact string the cache is keyed on. Clan units carry an Inner Sphere
  reporting name between chassis and model (e.g. `Koshi (Mist Lynx) A`); keying off the bare chassis
  (`Koshi A`) misses the cache, the unit fails to materialise, and the whole static force falls back to
  dynamic generation. (Rosters saved before this fix have the bare chassis baked in and are not
  retroactively repaired.)

### Damage persistence
- **`PersistentDamageState`** — cross-scenario damage for one unit: location internals/blow-offs,
  system criticals (`SystemCritical`: engine/gyro/sensor/life-support), aero system hits, actuator
  hits, ConvInfantry strength, BattleArmor trooper losses.
- **`LocationDamage`** — per-location internals reduction / destroyed / blown-off.
- **`ActuatorHit`** — a (location, slot) pair, de-duplicated in a set.
- **`OpForDamageReader`** — `readPersistentDamageFrom(Entity)`; converts a post-battle entity into
  a `PersistentDamageState`. (Checks BattleArmor before ConvInfantry so BA isn't mis-handled.)

### Enums
- **`Status`** — `READY`, `DESTROYED`, `SALVAGED`, `CAPTURED`; `isTerminal()` is true for all but
  `READY`. (There is no `ESCAPED`; a unit that survives stays `READY`.)
- **`IntelLevel`** — `UNKNOWN` → `OBSERVED` → `FULL_INTEL`; `isAtLeast(...)`.
- **`EliminationResult`** — `STILL_ACTIVE`, `TRACK_PACIFIED`, `CONTRACT_WON`.

### Sizing / configuration tables
- **`ContractTypeOpForModifier`** / **`ContractTypeAllyModifier`** — per-contract-type formation-count
  modifiers and skill/quality "jitter" profiles.
- **`ContractTypeReinforcementProfile`** / **`ContractTypeAllyReinforcementProfile`** — per-type
  reinforcement gates (morale threshold, probability, min/max formations, event cap).
- **`FormationSchema`** — standard formation size by faction (Clan 5, ComStar/WoB 6, else 4).
- **`FacilityRosterEffect`** / **`FacilityCaptureEffects`** — facility capture/loss → roster deltas.

### Behavior / naming
- **`OpForBehaviorSettingsBuilder`** — Princess `BehaviorSettings` with three conservation tiers
  (by formation/roster health) and a `Posture` (defender/attacker/irregular).
- **`AllyBehaviorSettingsBuilder`** — fixed "engaging support" profile for allied bots.
- **`FormationNamer`** — sequential NATO names with faction-aware suffix (Lance/Star/Level II).

### Services
- **`StratConOpForRosterBuilder`** — builds rosters at contract acceptance and adds reinforcement
  formations. `ABSOLUTE_MIN_FORMATIONS=1`, `MAX_FORMATIONS=20`, `DEFAULT_ATB_TRACK_NAME="Sector 0"`.
- **`StratConOpForDeployer`** — selects formations for a scenario and builds the `BotForce`.
- **`OpForUnitMaterializer`** — turns a `StratConOpForUnit` into a deployable `Entity`, wiring
  external ids and re-applying persistent damage.
- **`OpForReinforcementService`** / **`AllyReinforcementService`** — monthly morale-driven
  reinforcement.

### Intel
- **`intel/IntelLog`** + **`intel/IntelLogEntry`** — append-only, campaign-level intelligence log
  of observed enemy units (outcome: killed/captured/salvaged/observed).

---

## 3. Persistence

The roster is JAXB-serialized (`@XmlRootElement("opForRoster")`); `afterUnmarshal` rebuilds the
`unitsById` index. The roster attaches in one of two places:

- **StratCon:** `StratConCampaignState.getOpForRoster()` / `getAlliedRoster()` (`@XmlElement`).
  `StratConCampaignState`'s back-reference to its `AtBContract` is `@XmlTransient` and is re-linked
  on load in `AtBContract.loadFieldsFromXmlNode` (right after `StratConCampaignState.Deserialize`).
- **Pure-AtB:** `AtBContract.atbOpForRoster` / `atbAlliedRoster`, serialized via
  `serializeAs(pw, "atbOpForRoster")` / `"atbAlliedRoster"`.

`AtBContract.getOpForRoster()` / `getAlliedRoster()` are the unified accessors: they prefer the
`StratConCampaignState` roster and fall back to the direct AtB field.

---

## 4. Lifecycle

### 4.1 Build (contract acceptance)
`StratConOpForRosterBuilder.buildForContract` / `buildForAtBContract` (+ ally variants). Formation
count = `ceil(playerCombatTeams * staticOpForPaddingFactor) + ContractTypeOpForModifier` — padding
scales only the player-team term, not the contract modifier — clamped to
`[max(1, staticOpForFormationCountFloor), MAX_FORMATIONS=20]` (the floor option is itself clamped to
`[1, MAX_FORMATIONS]`). The ally count mirrors the padding but keeps a floor of `0` (covert work can
give zero allied support). Skill/quality are jittered per the contract-type profile. Enemy formations start
`UNKNOWN`; **allied formations start `FULL_INTEL`** (employer briefing).

### 4.2 Deploy (per scenario)
`AtBDynamicScenarioFactory` checks `StratConOpForDeployer.shouldUseStaticPath` /
`shouldUseStaticAllyPath` per force template. If static, `selectAndDeploy(...)` replaces the
generated `BotForce`; if it returns null (no living formations / all materialization failed), the
dynamic path is used as fallback.

**Special-unit-type slots fall back to dynamic generation.** The roster is a ground force
(BattleMeks + militia), so `selectAndDeployInternal` first checks `isStaticEligible(forceTemplate)`
and returns null for any slot whose `allowedUnitType` it cannot satisfy — eligible only for the
standard mixed-ground slot (`SPECIAL_UNIT_TYPE_ATB_MIX`) and the pure-Mek slot (`UnitType.MEK`).
Slots requiring DropShips (e.g. *Deep Raid*'s objective), infantry (*Irregular Force* /
*Crowd Control*), aerospace, or civilians defer to the stock dynamic generator, which produces the
correct unit types. Without this guard, roster Meks were deployed under a mismatched force label
(a "DropShip" or "Infantry" bot force composed of Meks).

Selection (`selectFormations`): living formations on the track, sorted by weight-class match then
least-recently-deployed, greedily filled to the BV budget; if nothing fits, the smallest single
formation is still deployed.

**Committed-formation dedup.** Forces are generated when a scenario is created/revealed, and several
scenarios can sit unfought at once — plus a single scenario can have more than one Opposing template
(e.g. a main OpFor plus a *Convoy*). A formation must therefore not be drawn into a force while it is
already committed to *any* still-unfought scenario, or the same pilots/units appear in two places at
once. `advanceIntelForSelected` stamps every deployed formation's `lastDeployedScenarioId`;
`selectAndDeployInternal` builds the set of bridge UUIDs for all `ScenarioStatus.isCurrent()`
scenarios on the contract (via `committedScenarioIds`, including the one being assembled) and
`selectFormations` excludes any formation stamped with one of them. Once a scenario is fought its id
leaves that set, freeing its surviving formations for future scenarios.

**Global deploy fallback (win reachability).** If the scenario's own track has **no living
formations left** (already cleared), `selectFormations` falls back to `roster.livingFormations()`
— the global pool across all tracks — so stragglers parked on quiet tracks can still be drawn into
battle and destroyed. On-track formations are always preferred; the fallback only engages once the
track is empty. This is load-bearing: `checkEliminationStatus` (§4.4) is **global** across every
track, but the builder scatters formations across all tracks at acceptance
(`StratConOpForRosterBuilder.pickTrackName`) while the deployer is otherwise track-scoped — without
the fallback, a formation on a track the player never fights stays `READY` forever, so
`livingLineUnits()` never empties and `CONTRACT_WON` never fires (the contract then only ends via the
generic StratCon victory-points early-end, which requires a manual *Complete Mission*). Retreating
enemies keep their status — they stay `READY`, carry forward their accrued damage, and must be
re-engaged (there is no terminal "escaped" status); the fallback simply guarantees they get the chance
to redeploy.

Each deployed formation/unit is stamped with the scenario UUID
**`new UUID(scenario.getId(), 0L)`** (`lastDeployedScenarioId`) and advanced `UNKNOWN → OBSERVED`.
`OpForUnitMaterializer.deploy` builds the entity, wires `unit.id → entity.externalId` and
`pilotPersistentId → crew.externalId`, and re-applies persistent damage.

### 4.3 Resolve (scenario fold)
After a battle, `ResolveScenarioTracker` calls `StratConOpForRoster.foldResolutionInto(...)` for
the enemy roster (and the allied roster). It scopes to units whose `lastDeployedScenarioId` matches
the scenario UUID and are still `READY`, then assigns:

- **DESTROYED** — in the devastated set, or `entity.isDestroyed()`, **or the entity's crew is dead**
  (`entity.getCrew().isDead()`) — head/center-torso kills leave the crew dead but frequently do *not*
  flip `isDestroyed()` until the next phase boundary (which may never occur post-battle). Without the
  crew-death check such a unit was treated as a survivor, persisted a blown-off head, and re-spawned
  next scenario as an undeployable headless wreck that softlocked generation. The crew-death branch
  runs *after* the salvage/retreat checks, so a recovered chassis stays SALVAGED; ejected/captured
  crews and torso-cockpit survivors report `isDead() == false` and are correctly excluded.
- **DESTROYED (non-redeployable wreck)** — after the crew-death check and before "survives on field",
  `OpForUnitMaterializer.isNonViable(entity)` catches a unit that is doomed, removed as
  devastated/salvageable, or (for Meks) has lost its head, center torso, or a leg, taken a destroyed
  engine, or is permanently immobilized. This is the same `isDestroyed()`-not-yet-flagged trap as the
  crew-death case but for a unit whose **pilot ejected** (crew alive): without it the blown-apart Mek
  was persisted as a survivor, the formation never registered as eliminated, and it re-spawned as a
  wreck MegaMek could not load.
- **SALVAGED** — the unit's entity external id is in the **recovered-salvage** set.
- **CAPTURED** — captured-pilot reconciliation by `pilotPersistentId` (multi-slot crews); solo Mek
  pilots fall back to matching `OppositionPersonnelStatus.sourceUnitExternalId` (== the unit id),
  scenario-scoped, because the pilot id is lost when the ejected crew entity is generated.
- otherwise survives on field → persistent damage updated. A unit that **retreated /
  force-withdrew** is folded the same way as an on-field survivor: it stays `READY` and unrevealed
  but now **carries forward the damage it took before withdrawing** (previously a retreated unit kept
  only its prior damage and discarded that scenario's damage — it healed for free).

**Non-redeployable wreck guard (defence in depth + legacy-save self-heal).** `isNonViable` also
guards `OpForUnitMaterializer.deploy` (a wreck never reaches the bot force). For saves written before
the fold-time classification existed, the deployer additionally checks
`StratConOpForUnit.isUnredeployableWreck()` (the persisted-state counterpart, scoped to Meks) on each
selected unit and marks such a unit DESTROYED instead of deploying it — so a stuck-READY wreck heals
to DESTROYED the next time its formation would deploy, letting the formation finally be eliminated.

**Recovered-salvage sourcing.** `ResolveScenarioTracker.collectRecoveredEnemySalvage` unions
**`potentialSalvage`** with the disposition buckets (`actualSalvage`, `leftoverSalvage`,
`ransomedSalvage`). `potentialSalvage` is the authoritative, salvage-mode-independent set of every
recovered wreck — critical because under **CamOps salvage**, units surrendered to the employer are
tracked only inside the salvage picker and never reach `leftoverSalvage`. Sourcing from
`potentialSalvage` ensures kept, surrendered, and sold wrecks all count as removed from the OpFor.

A formation that loses ≥ 50% of its units is promoted to `FULL_INTEL`; terminal transitions set the
unit's `revealed` flag and write an `IntelLog` entry. A `OpForRosterChangedEvent` is fired so the
OOB tabs refresh.

### 4.4 Eliminate
`checkEliminationStatus` returns `CONTRACT_WON` when no units remain (calls
`completeMission(..., SUCCESS)` so payout runs), `TRACK_PACIFIED` when a track's units are gone
(StratCon only), else `STILL_ACTIVE`.

### 4.5 Reinforce
Monthly, after the morale check, both services fire on an **upward** morale shift — i.e. while the
enemy is **ascendant** (player on the back foot) — to at least the profile's threshold:
`OpForReinforcementService` (enemy commits more while winning) and `AllyReinforcementService`
(employer sends help while the player is losing). Reinforcements therefore taper off as the player
grinds the enemy down (falling morale), rather than the enemy getting fresh troops as it collapses.
Both are gated by the contract-type profile and an event cap. The deterministic eligibility is the
testable `OpForReinforcementService.shouldAttemptReinforcement(...)`; thresholds mirror across the
two sides (Advancing for most contract types, Dominating for the heaviest, e.g. Planetary Assault).
Facility capture/loss (`FacilityCaptureEffects`) also adjusts rosters and **bypasses** the cap.

**Rubber-band reinforcement skill/quality (scale to player strength).** Enemy reinforcement
formations are no longer frozen at the contract's accept-time enemy skill. Each reinforcement wave is
generated at the **higher of** the contract's original enemy skill and the player's **current
campaign-wide average crew skill** (read from campaign reputation), and quality is bumped by however
many skill steps the reinforcement skill gained over that baseline (clamped to the A-rating ceiling).
Reinforcements never drop **below** the original enemy skill/quality, but they keep pace as the
player's force gains experience over a long contract — so late-contract waves stay challenging instead
of arriving green against a compounding player force. This affects **reinforcements only** (the
initial roster still reflects contract-accept), and it scales **skill and quality only** — formation
count/size stays governed by the contract-type reinforcement profile above.

**Facility effects are symmetric and bounded.** `onPlayerLoss` is the exact inverse of
`onPlayerCapture`: every facility flip is bounded to magnitude 1, so capturing-then-losing (or
losing-then-recapturing) the **same** facility nets zero and a facility that changes hands repeatedly
over a long contract no longer drifts the rosters without bound. Accumulation across **distinct**
facilities still reflects territory control. (Previously a facility loss applied larger, uncapped
deltas than the matching capture — e.g. a Command Center loss was enemy `+2` / ally `-3` — so
repeated flips ratcheted the rosters.)

**Multi-challenger targeting (v1.8.1).** With more than one active challenger, both reinforcement
services iterate `getActiveChallengers()` and reinforce **each** active challenger independently
(per-roster cap counters), rather than only the newest — so an older challenger under morale pressure
still grows. `FacilityCaptureEffects` applies its enemy delta to a **single** active challenger contesting the
affected track — the one most invested in it (most living formations on it, via `enemyDeltaTarget`).
The delta is one bounded facility effect (deltas are capped, e.g. a Command Center loss is `-1`), so it
lands on exactly one roster rather than being re-applied in full to every contesting challenger (which
would multiply the capped effect). A shrink with no on-track challenger is a no-op; a reinforcement falls
back to the primary active challenger only when no challenger is on the track. Previously all three used
the singular `getOpForRoster()` (newest active), so effects silently skipped every other active
challenger.

---

## 5. Fog of war

| IntelLevel | Formation header | Units |
|---|---|---|
| `UNKNOWN` | "Unidentified formation" | hidden |
| `OBSERVED` | name, weight class, strength | `???` unless the unit is individually `revealed` |
| `FULL_INTEL` | + skill level | all shown |

Upgrades: `UNKNOWN→OBSERVED` on first deployment; `→FULL_INTEL` at ≥ 50% formation losses; a unit
becomes `revealed` when it reaches a terminal status. Allied formations are always `FULL_INTEL`.

---

## 6. UI

- **`OpForRosterPanel`** (`gui/stratCon/`) — renders a roster as a **nested, collapsible tree**:
  *track → formation → unit*. Key features:

  - **Summary header** at the top of the panel: `Line OpFor: {remaining} / {total} formations`
    where *total* counts non-militia formations and *remaining* counts those with at least one
    living unit. When any militia formations exist, the header appends ` · Militia: {active} active`.
    This surfaces the win metric at a glance.

  - **Militia grouping** — within each track, line (non-militia) formations are listed first;
    if the track contains militia formations a muted italic **"Planetary Militia"** subheader
    separates them. Militia formation headers are rendered in gray. The old inline
    `[Planetary Militia]` tag on the formation header is removed in favour of this grouping.

  - **Persistent expand/collapse state** — expand/collapse state is stored in a
    `Map<String, Boolean> collapseState` keyed by `"T:" + trackName` (tracks) and
    `"F:" + formationId` (formations). State survives `refresh()` calls, so the user's
    view is not reset after each battle or reinforcement event. A small **Expand all /
    Collapse all** toolbar at the top of the panel sets all keys at once.

  - **Status color-coding** — terminal unit statuses are color-coded in unit lines:
    `DESTROYED` → red, `SALVAGED` → dark goldenrod (`#B8860B`), `CAPTURED` → blue (`#1E6FBA`).
    Terminal lines also carry HTML strike-through. The formation destroyed label remains red.

  - **Condition label (damaged-but-alive units)** — a visible, non-terminal unit that is carrying
    persistent damage shows a per-unit condition word after its status: **"battle-worn"** for a unit
    with damage, **"crippled"** for one with severe damage (a blown-off / destroyed location, or an
    engine/gyro critical). Undamaged units show no label; terminal units still show their terminal
    status (destroyed / salvaged / captured) rather than a condition word.

  - **Unit-type glyph** — visible (non-masked) unit lines are prefixed with a short tag:
    `[M]` Mek, `[V]` Vehicle (Tank/VTOL), `[I]` Infantry/Battle Armor. The tag is driven
    by `StratConOpForUnit.unitType` (see model section below); masked (`???`) units never
    show a tag.

  One instance drives the Enemy OOB tab, another the Allied OOB tab.

- **`StratConOpForUnit.unitType`** (`campaign/stratCon/opfor/`) — an `int` field
  (`@XmlElement`, default `-1` unknown) holding the `megamek.common.units.UnitType` constant
  for this unit. Set at roster-build time in `StratConOpForRosterBuilder.generateUnit` from
  `entity.getUnitType()`. Persisted via JAXB so the value survives save/load. Older saves
  that lack the element default to `-1` (no glyph shown).

- **`StratConTab`** — adds the **Enemy OOB** and **Allied OOB** tabs (alongside **Sector Info**) and
  refreshes them on `OpForRosterChangedEvent`. Passes the `Campaign` and a current-track supplier
  into each `OpForRosterPanel` so the GM editor (below) can be gated and can fire its change event.
- **`IntelLogDialog`** (`gui/dialog/`) — opened from Reports → Intelligence Log; a sortable table
  plus per-outcome and per-faction summary counts. Title, column headers, summary labels, and the
  Close button are localized via the `AtBStratCon` bundle (`intelLog.*` keys); the Reports menu
  entry is `miIntelLog.text` in the `MekHQMenuBar` bundle and is built through the standard
  `MekHQMenuBar.createMenuItem(...)` helper like every other report item.

### 6.1 GM roster editor

A **GM-mode** editor lets a game master rewrite a contract's roster directly. It is a sandbox /
save-repair tool, deliberately gated so it cannot be used in normal play to trivialise the
win-by-attrition loop.

- **Entry point** — `OpForRosterPanel` shows an **"Edit OpFor…"** button on its expand/collapse
  toolbar **only when** `campaign.isGM()` is true (the panel is otherwise read-only; the no-arg
  legacy constructor keeps editing disabled, e.g. in tests).
- **`OpForRosterEditorDialog`** (`gui/stratCon/`) — the master modal. Edits a **deep copy** of the
  live roster (`StratConOpForRoster.copy()`, an in-memory JAXB round-trip) so **Cancel discards
  everything**. Left column lists formations (Add / Edit / Delete); right column lists the selected
  formation's units (Add / Edit / Remove). On **OK** the working copy's contents are written back
  into the *live* roster object via `setFormations` / `setUnitList` (preserving the live object's
  identity, so the contract's `@XmlTransient` back-link stays valid) and an
  `OpForRosterChangedEvent` is fired to refresh the panel. Fog of war is ignored while editing.
- **`EditOpForFormationDialog`** / **`EditOpForUnitDialog`** (`gui/stratCon/`) — field sub-dialogs.
  Formation: name, weight class, quality (0–5), skill, assigned track, intel level, militia flag.
  Unit: chassis/model (via the standard `MekHQUnitSelectorDialog`), pilot name, gunnery/piloting
  (0–8), status, fog-of-war reveal flag, and owning formation (reassignment). Both validate on OK
  through `OpForRosterEditOps`.
- **`OpForRosterEditOps`** (`campaign/stratCon/opfor/`) — the **non-UI** edit + validation logic,
  unit-tested in `OpForRosterEditOpsTest`. Composes the roster's `addUnit` / `removeUnit` /
  `addFormation` / `removeFormation` primitives while maintaining the formation→unit membership
  links: `addFormation`, `deleteFormation` (cascades to member units), `addUnit`, `removeUnit`,
  `reassignUnit`, `validateFormation`, `validateUnit`. Keeping this out of Swing is what makes it
  testable.
- **Roster support** — `StratConOpForRoster` gained `removeUnit(UUID)` / `removeFormation(UUID)`
  (symmetric to the existing `addUnit` / `addFormation`) and `copy()`. `setUnitList` now rebuilds
  the transient `unitsById` index (extracted `rebuildIndex()`, shared with `afterUnmarshal`) so a
  wholesale list swap leaves the lookup map consistent.

---

## 7. Configuration

Campaign options (`CampaignOptions`):

| Option | Default | Effect |
|---|---|---|
| `useStaticOpForRoster` | `false` | Master gate; builds rosters on contract acceptance. |
| `staticOpForPaddingFactor` | `1.25` | Multiplies the player-team term of the enemy and allied formation counts (`ceil`). |
| `staticOpForFormationCountFloor` | `3` | Minimum enemy formation count, itself clamped to `[1, MAX_FORMATIONS]`; supersedes the former hard floor of 2. Does not apply to the ally side. |
| `useStaticOpForMilitia` | `true` | Enables planetary-militia reinforcement of the defending OpFor on **attacker** contracts (see §11). Effective only when `useStaticOpForRoster` is also on. |

User-facing strings live in `MekHQ/resources/mekhq/resources/AtBStratCon.properties` under the
`opForRosterPanel.*` and `alliedRosterPanel.*` keys; the GM editor adds `opForEditor.*`,
`formationEditor.*`, and `unitEditor.*` keys; the Intelligence Log dialog adds `intelLog.*` keys.
The Reports-menu entry is `miIntelLog.text` in `MekHQMenuBar.properties`.

---

## 8. Tests

Unit tests live in `MekHQ/unittests/mekhq/campaign/stratCon/opfor/` (+ `intel/`), with feature
integration covered in `ResolveScenarioTrackerTest` and `AtBContractTest`:

- **Model / fold:** `StratConOpForRosterTest`, `FoldResolutionIntoTest`, `CheckEliminationStatusTest`.
- **Build / deploy:** `StratConOpForRosterBuilderTest`, `StratConOpForDeployerTest`,
  `OpForUnitMaterializerTest`, `OpForDamageReaderTest`, `StratConContractInitializerHookTest`.
- **Config tables:** `IntelLevelTest`, `FormationNamerTest`, `FormationSchemaTest`,
  `OpForBehaviorSettingsBuilderTest`, `AllyBehaviorSettingsBuilderTest`,
  `ContractTypeReinforcementProfileTest`, `ContractTypeAllyReinforcementProfileTest`,
  `FacilityRosterEffectTest`.
- **Reinforcement:** `OpForReinforcementServiceTest`, `AllyReinforcementServiceTest`.
- **Persistence / intel:** `StratConCampaignStateJaxbTest`, `intel/IntelLogTest`.
- **UI:** `gui/stratCon/OpForRosterPanelTest` (fog-of-war rendering + unit experience line);
  `gui/dialog/IntelLogDialogTest` (Intelligence Log localization: bundle keys resolve, and a live
  dialog sources its title + column headers from the bundle).
- **GM editor:** `OpForRosterEditOpsTest` (add/delete/reassign/validate, cascade delete,
  `setUnitList` index rebuild, and `copy()` deep-independence).
- **Salvage sourcing:** `ResolveScenarioTrackerTest.collectRecoveredEnemySalvage_*`.

---

## 9. Integration points (hooks on stock classes)

| Class | Hook |
|---|---|
| `AtBContract` | roster fields + unified accessors; build on `acceptContract`; JAXB load/relink |
| `StratConContractInitializer` | builds rosters during `initializeCampaignState` |
| `AtBDynamicScenarioFactory` | routes scenario force generation to the static deployer |
| `ResolveScenarioTracker` | folds battle results into the rosters; recovered-salvage sourcing |
| `StratConRulesManager` | facility-capture effects |
| `CampaignNewDayManager` | monthly reinforcement services |
| `StratConTab`, `MekHQMenuBar` | OOB tabs + Intelligence Log menu item |
| `CampaignOptions` (+ marshaller/unmarshaller) | the three options above |
| `events/OpForRosterChangedEvent` | UI-refresh event |

---

## 10. Notable design decisions / fixes

- **Contract back-reference relink on load.** `StratConCampaignState.contract` is `@XmlTransient`;
  it must be re-set after deserialization. The relink belongs in the `StratConCampaignState`
  parse branch of `AtBContract.loadFieldsFromXmlNode` (not in a roster branch), so saves without an
  allied-roster element still relink correctly.
- **CamOps salvage.** Recovered-salvage detection sources from `potentialSalvage`, not the
  disposition buckets, because CamOps employer-surrender never populates `leftoverSalvage`
  (see §4.3).
- **Payout on contract win.** `CONTRACT_WON` calls `completeMission(SUCCESS)` rather than setting
  status directly, so the employer payment path runs.
- **Scenario identity.** A scenario's int id is mapped to a UUID via `new UUID(id, 0L)` everywhere
  (deployment stamping and fold filtering) so the two always agree.
- **Sizing calibration.** `computeInitialFormationCount` consumes `staticOpForPaddingFactor`
  (multiplying only the player-team term, `ceil`) and `staticOpForFormationCountFloor` (clamped to
  `[1, MAX_FORMATIONS]`, replacing the former hard-coded `MIN_FORMATIONS=2`). The ally count mirrors
  padding but keeps a floor of 0. Padding/floor only affect contracts accepted after the change;
  already-built rosters are untouched.
- **Blank enemy faction falls back to Independent.** Reinforcement and unit generation now treat a
  **blank** enemy faction code the same as a `null` one — both fall back to the `"IND"` independent
  RAT table. Previously an empty (but non-null) faction code was passed straight through, so every RAT
  lookup failed and the OpFor silently shrank instead of drawing from the independent table.
- **Solo-Mek capture.** Captured solo Mek pilots lose their `pilotPersistentId` when the ejected
  crew entity is generated, so `foldResolutionInto` falls back to
  `OppositionPersonnelStatus.sourceUnitExternalId` (the source Mek's external id, which equals the
  `StratConOpForUnit` id), scenario-scoped, to reconcile them. The fallback's `UUID.fromString`
  parse is guarded so a malformed external id logs a warning instead of aborting scenario
  resolution.

---

## 11. Planetary Militia (v1.7)

When the **player is the attacker** (`AtBContract.isAttacker()` — so the static OpFor is the
planetary **defender**), the defender can be bolstered by **planetary militia**: low-to-mid-skill
combat vehicles with occasional conventional infantry. Gated by `useStaticOpForMilitia` (default on)
and StratCon-only for now. Militia are **transient with respect to victory** — they fight, take
damage, and appear in the OOB, but they do **not** keep the contract open.

### Marker
- **`StratConOpForFormation.militia`** (boolean, JAXB) — flags a formation as militia. A unit's
  militia status is derived from its owning formation via `formationId`.

### Victory exclusion (the load-bearing rule)
- **`StratConOpForRoster.livingLineUnits()`** / **`livingLineUnitsForTrack(...)`** — living units
  whose formation is **not** militia. `checkEliminationStatus` uses these instead of `livingUnits()`,
  so `CONTRACT_WON` fires the moment the last **line** unit dies even if militia remain (the win
  fires during resolve, so militia never deploy alone). `livingUnits()` / `livingUnitsForTrack()`
  still count everyone — deployment, fold, and intel include militia.
- **Militia-only rosters are not instantly won.** A roster that has **only** militia formations and
  **never** had any line units is not declared `CONTRACT_WON` on its first resolution: its empty
  line-unit set is *structural* (it never contained line units), not *earned* by combat. A roster
  that **did** have line units and lost them all still counts as won, exactly as before — the win
  condition is "the last line unit you were given has died," not "there are currently no line units."

### Composition (`StratConOpForRosterBuilder`)
- The unit generator is parameterized by `UnitType` (the legacy `MEK`-only path is preserved for the
  line OpFor). Militia units are ~75% ground combat vehicles (`TANK` with movement modes
  `{TRACKED, WHEELED, HOVER, WIGE}`, trailer filter `walkMp >= 1`, no VTOLs) and ~25% conventional
  infantry (`MILITIA_INFANTRY_FRACTION`). Skill is `GREEN` jittered and clamped to `[GREEN, REGULAR]`;
  quality is low. Names come from `FormationNamer.nextMilitiaName(...)`.

### Starting pool + reinforcement
- **`seedMilitiaPool(...)`** — at contract acceptance (attacker + option on), seeds a small militia
  allotment scaled by contract type (mirrors the line initial build: formations are added even if a
  generation slot comes back empty).
- **`MilitiaReinforcementService.maybeReinforce(...)`** — monthly, mirrors `OpForReinforcementService`
  (upward morale shift + per-type threshold ⇒ more militia while the defender is ascendant), gated on
  `isAttacker()`, using its **own** cap counter `StratConOpForRoster.militiaReinforcementEventsFired`
  (separate from the line cap). Invoked as the third call in `CampaignNewDayManager`.
- **`ContractTypeMilitiaReinforcementProfile`** — per-type `MilitiaProfile`
  (`triggerThreshold, probability, minFormations, maxFormations, eventCap, minStarting, maxStarting`).
  Generous for `PLANETARY_ASSAULT`, lighter for raids, `NEVER` for types the player never attacks.

### OOB
- `OpForRosterPanel` tags militia formation headers with the localized `opForRosterPanel.militiaTag`
  ("Planetary Militia"), honoring the existing fog-of-war masking.

### Scope limits
- StratCon-only and attacker-only. Line OpFor remains Mek-only (only the militia path generates
  vehicles/infantry). No per-scenario militia cap — militia are eligible for normal BV-budget
  deployment selection.

---

## 12. Multi-challenger garrison contracts (v1.8)

On **garrison-type** contracts (`AtBContractType.isGarrisonType()`) with `useStaticOpForRoster`
enabled, a world is struck over the life of the contract by **successive distinct enemy forces** —
each a separately-tracked finite roster, labeled and RAT-sourced from **its own** faction. Design
spec: [`docs/superpowers/specs/2026-06-28-garrison-multi-challenger-opfor-design.md`](../../../docs/superpowers/specs/2026-06-28-garrison-multi-challenger-opfor-design.md).

This fixes a root-cause bug: the garrison "mix it up" rout-end faction reroll
(`AtBContract.updateEnemy`) used to drift the contract enemy label while the persistent roster kept
the original faction — so a Pirate roster could deploy under a "Draconis Combine" bot-force label.

### Data model
The single enemy roster generalizes to an **ordered list of challengers**. Each challenger *is* a
`StratConOpForRoster` carrying identity + lifecycle captured at build time:
- `factionCode`, `enemyBotName`, `enemyColour` — drive the bot-force label/colour and the RAT.
- `status` (`ChallengerStatus.{ACTIVE, WITHDRAWN, DEFEATED}`), `arrivedDate` / `endedDate`.

Storage: `StratConCampaignState.opForChallengers` (StratCon) and `AtBContract.atbOpForChallengers`
(pure-AtB). `getActiveChallengers()` / `getPrimaryChallenger()` and the contract-level
`getActiveOpForChallengers()` / `getOpForRoster()` (= newest active) are the accessors; the allied
roster stays single. Built at acceptance as a one-element list.

### Lifecycle (rides the existing morale/rout rail — no new scheduler)
At **rout-end**, `AtBContract.updateEnemy` calls `maybeSpawnChallenger`: for in-scope contracts it
**retires** the active challenger(s) (`DEFEATED` if wiped out, else `WITHDRAWN`, with `endedDate`)
and **builds a new `ACTIVE` challenger** for the rerolled faction. The rout itself is the lull;
sequential-with-overlap arises naturally when a challenger routs with survivors.

### Deploy
`AtBDynamicScenarioFactory`'s static hook iterates `getActiveOpForChallengers()` and builds one bot
force per active challenger; `StratConOpForDeployer.challengerBotForceName` / `challengerColour`
read the **challenger's own** identity (falling back to the contract only for unstamped legacy
rosters). During an overlap window a scenario may field more than one enemy force, each its own
faction.

### Resolve (must mirror deploy — the per-challenger fold)
Because deploy fans out across challengers, resolve **must** too. `ResolveScenarioTracker` selects the
challenger(s) that actually fought this scenario — `StratConOpForRoster.wasDeployedTo(scenarioUuid)`,
matched on the formation `lastDeployedScenarioId` stamp — and folds the result and runs
`checkEliminationStatus` on **each** of them, not on `getOpForRoster()` (the newest active challenger).
This applies to both the StratCon and pure-AtB branches. Selection spans challengers of **any** status
(`getOpForChallengers()` / `getAtbOpForChallengers()`), so a challenger that routs to WITHDRAWN/DEFEATED
between deploy and resolve still owns the units it fought with. There is deliberately **no fallback**:
if nothing matches the stamp, no static force fought this scenario, so nothing is folded or eliminated
— an unmatched fallback to the primary roster could falsely pacify a track or mis-defeat an uninvolved
challenger.

> Regression fixed (v1.8.1): resolution previously used the singular `getOpForRoster()` for the fold
> and elimination check, so with two active challengers the force you defeated could be recorded
> against the **wrong** challenger — `foldResolutionInto` self-scopes by unit id, so folding into the
> non-participating roster was a silent no-op. Covered by `StratConOpForRosterDeployedToTest` and the
> `ChallengerDefeatTest` non-garrison DEFEATED case.

### Win condition
A cleared challenger of **any** contract type is marked `DEFEATED` (with a best-effort `IntelLog`
milestone), so it drops out of `getActiveChallengers()` and shows defeated in the OOB. What that means
for the contract is decided by the **caller**: garrison contracts **defend the term** and are never
won by attrition; other contract types are won only once **every** active challenger is cleared
(`getActiveChallengers().isEmpty()`), not on the first roster to clear. `checkEliminationStatus` still
returns `CONTRACT_WON` per-roster (used for reporting/back-compat), but the resolve caller gates the
actual `ContractAutoWonEvent` on the all-cleared condition above.

### Persistence & migration
Each challenger serializes in its list; a legacy single-roster element (`opForRoster` /
`atbOpForRoster`) migrates on load into a one-element `ACTIVE` list, with faction identity backfilled
from the contract's current enemy fields after the `@XmlTransient` back-link relink. JAXB
property-access: computed collection getters are `@XmlTransient` to avoid double-serialization.

### OOB UI
`OpForRosterPanel.forChallengers` renders one faction-titled section per active challenger (each with
its own expand/collapse toolbar and GM "Edit OpFor" button editing that challenger). Resource key
`opForRosterPanel.challengerHeader`.

### Scope limits / follow-ups
- Garrison-type contracts only; non-garrison and the allied roster are unchanged. No new
  `CampaignOptions` (tuning ships as constants). Concurrent multi-front (N simultaneous fronts) is
  out of scope — sequential-with-overlap covers it.
- Same-named tracks across challengers currently share track-level expand/collapse state; only
  ACTIVE challengers are shown in the OOB (no past-challenger history view yet).
