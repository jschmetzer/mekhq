# StratCon Static OpFor

A persistent, fog-of-war **order of battle** for AtB/StratCon contracts. Instead of
generating a fresh random enemy force every scenario, a contract is given a fixed roster of
named formations and units that **persists for the life of the contract**, takes and remembers
damage across scenarios, and is whittled down as the player destroys, salvages, or captures it.
A parallel **allied** roster models employer support with the same machinery.

This document describes the feature as built on the `feature/stratcon-static-opfor` branch. All
classes under `mekhq.campaign.stratCon.opfor` (and its `intel` subpackage) are new; the rest of
the feature is integration hooks on existing classes.

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
  `MekSummaryCache` lookup key.

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
  formations. `MIN_FORMATIONS=2`, `MAX_FORMATIONS=20`, `DEFAULT_ATB_TRACK_NAME="Sector 0"`.
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
count = player combat teams + `ContractTypeOpForModifier` modifier, clamped to `[2, 20]` (ally
floor is 0). Skill/quality are jittered per the contract-type profile. Enemy formations start
`UNKNOWN`; **allied formations start `FULL_INTEL`** (employer briefing).

### 4.2 Deploy (per scenario)
`AtBDynamicScenarioFactory` checks `StratConOpForDeployer.shouldUseStaticPath` /
`shouldUseStaticAllyPath` per force template. If static, `selectAndDeploy(...)` replaces the
generated `BotForce`; if it returns null (no living formations / all materialization failed), the
dynamic path is used as fallback.

Selection (`selectFormations`): living formations on the track, sorted by weight-class match then
least-recently-deployed, greedily filled to the BV budget; if nothing fits, the smallest single
formation is still deployed. Each deployed formation/unit is stamped with the scenario UUID
**`new UUID(scenario.getId(), 0L)`** (`lastDeployedScenarioId`) and advanced `UNKNOWN → OBSERVED`.
`OpForUnitMaterializer.deploy` builds the entity, wires `unit.id → entity.externalId` and
`pilotPersistentId → crew.externalId`, and re-applies persistent damage.

### 4.3 Resolve (scenario fold)
After a battle, `ResolveScenarioTracker` calls `StratConOpForRoster.foldResolutionInto(...)` for
the enemy roster (and the allied roster). It scopes to units whose `lastDeployedScenarioId` matches
the scenario UUID and are still `READY`, then assigns:

- **DESTROYED** — in the devastated set or `entity.isDestroyed()`.
- **SALVAGED** — the unit's entity external id is in the **recovered-salvage** set.
- **CAPTURED** — captured-pilot reconciliation by `pilotPersistentId` (multi-slot crews; solo Mek
  pilots are a known gap).
- otherwise survives on field → persistent damage updated; retreated → unchanged.

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
  *track → formation → unit*. Each track and formation header is a `▾/▸` toggle (expanded by
  default). Unit lines read `Chassis Model — Pilot (G#/P#)` with a red status badge when not
  `READY`, and honor the fog-of-war masking above. One instance drives the Enemy OOB tab, another
  the Allied OOB tab.
- **`StratConTab`** — adds the **Enemy OOB** and **Allied OOB** tabs (alongside **Sector Info**) and
  refreshes them on `OpForRosterChangedEvent`.
- **`IntelLogDialog`** (`gui/dialog/`) — opened from Reports → Intelligence Log; a sortable table
  plus per-outcome and per-faction summary counts.

---

## 7. Configuration

Campaign options (`CampaignOptions`):

| Option | Default | Effect |
|---|---|---|
| `useStaticOpForRoster` | `false` | Master gate; builds rosters on contract acceptance. |
| `staticOpForPaddingFactor` | `1.25` | Declared/serialized; reserved for future sizing calibration (not yet consumed). |
| `staticOpForFormationCountFloor` | `3` | Declared/serialized; reserved (effective floor is `MIN_FORMATIONS=2`). |

User-facing strings live in `MekHQ/resources/mekhq/resources/AtBStratCon.properties` under the
`opForRosterPanel.*` and `alliedRosterPanel.*` keys.

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
- **UI:** `gui/stratCon/OpForRosterPanelTest` (fog-of-war rendering + unit experience line).
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
