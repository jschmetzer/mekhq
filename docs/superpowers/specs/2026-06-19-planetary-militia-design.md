# Planetary Militia Reinforcements — Design

**Date:** 2026-06-19
**Branch:** `feature/stratcon-static-opfor`
**Feature reference:** [Static-OpFor.md](../../StratCon/Static-OpFor.md)

## Summary

When the player force is the **attacker** (so the static OpFor is the planetary **defender**),
the defender can be bolstered by **planetary militia**: low-to-mid-skill combat vehicles (with
occasional conventional infantry) that join the enemy roster. Militia are seeded as a small
starting pool at contract acceptance and grow via a morale-driven reinforcement mechanic — more
militia commit while the defender is ascendant, fewer while the attacker is winning. Militia are
**transient with respect to victory**: they deploy, take damage, and appear in the OOB, but they
do **not** gate the contract win. If the line OpFor is eliminated while militia remain, the
contract still completes.

This also fixes the incidental "OpFor is almost all Meks" observation by giving the militia path a
vehicle/infantry generator (the line OpFor stays Mek-only for now; broadening it is out of scope).

## Goals

1. Add combat-vehicle (and occasional infantry) forces to the defending OpFor in attacker contracts.
2. Tie militia commitment to the existing morale signal (defender ascendant ⇒ more militia).
3. Keep militia from holding a contract open after the real OpFor is defeated.

## Non-goals

- Broadening the **line** OpFor unit mix beyond Meks (separate future work).
- Pure-AtB support (militia are StratCon-only at first, like the existing reinforcement services).
- VTOLs or aerospace in the militia pool.
- Militia for **defender** contracts (player garrisoning) — militia only appear when the player is
  the attacker and the OpFor is the local defender.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Seeding | Starting pool **and** morale-driven reinforcement |
| Composition | Ground combat vehicles + occasional conventional infantry (no VTOLs) |
| Skill/quality | Mixed green/regular skill; low quality |
| Victory role | **Transient** — excluded from the contract-win / track-pacification condition |
| Config | New campaign option `useStaticOpForMilitia`, default **on**, under the static-OpFor gate |
| Scope | StratCon-only initially; only when `contract.isAttacker()` |
| Faction/RAT | Enemy faction code at low quality (defenders of the contested world) |
| OOB | Shown with a distinct "Planetary Militia" label, normal fog of war |

## Architecture

All new/changed code lives under `mekhq.campaign.stratCon.opfor` plus integration hooks, mirroring
the existing reinforcement feature.

### 1. Militia marker — `StratConOpForFormation`

- Add `private boolean militia = false;` (JAXB `@XmlElement`), with `isMilitia()` / `setMilitia(boolean)`.
- Units inherit their formation's militia status (no per-unit flag needed; the roster resolves a
  unit's militia status via its `formationId`).

### 2. Victory exclusion — `StratConOpForRoster`

The load-bearing change. Today `checkEliminationStatus` counts **all** living units.

- Add `private boolean isMilitiaUnit(StratConOpForUnit unit)` — looks up the unit's formation by
  `formationId` in `unitsById` / the formations list and returns its `isMilitia()`.
- Add `livingLineUnits()` = living units (`!status.isTerminal()`) whose formation is **not** militia.
- Add `livingLineUnitsForTrack(trackName)` = `livingUnitsForTrack(...)` filtered to non-militia.
- `checkEliminationStatus`:
  - `CONTRACT_WON` when `livingLineUnits()` is empty (was: `livingUnits()`), still calling
    `completeMission(SUCCESS)`.
  - `TRACK_PACIFIED` when `livingLineUnitsForTrack(track)` is empty (StratCon only), unchanged
    otherwise.
- Militia still participate everywhere else (deployment, fold/damage, intel, OOB). Only the win
  condition ignores them.

### 3. Separate reinforcement cap — `StratConOpForRoster`

- Add `private int militiaReinforcementEventsFired = 0;` (JAXB `@XmlElement`) with
  `getMilitiaReinforcementEventsFired()` and `incrementMilitiaReinforcementEventsFired()`. Kept
  separate from the line `reinforcementEventsFired` so the two mechanics don't share a cap.

### 4. Composition generator — `StratConOpForRosterBuilder`

- Extract the existing unit-generation body into a parameterized helper that accepts the
  `UnitType` and an optional movement-mode set / filter, instead of the hard-coded
  `params.setUnitType(MEK)` at line 616. The existing Mek path calls it with `MEK`; the militia
  path calls it for vehicles/infantry. (This is the minimal change that also removes the single
  point that forces all-Meks for the militia path.)
- Militia unit composition: per unit roll, ~75% `TANK` with movement modes
  `{TRACKED, WHEELED, HOVER, WIGE}` and filter `walkMp >= 1` (no trailers, no VTOLs), ~25%
  `INFANTRY` (conventional). The exact split is a named constant `MILITIA_INFANTRY_FRACTION = 0.25`.
- Militia skill: baseline `GREEN` with a jitter profile whose ceiling is `REGULAR` (mixed
  green/regular); quality low (baseline quality clamped to the low end, e.g. `1` of 0–5).
- Militia formations are flagged `setMilitia(true)` and named with a "Militia" descriptor (a
  militia variant of `FormationNamer`, or a suffix — see §7).

### 5. Starting pool — `StratConOpForRosterBuilder`

- New `seedMilitiaPool(campaign, contract, roster, tracks)` invoked during the build path **only
  when `contract.isAttacker()`** and `useStaticOpForMilitia` is on.
- Count is contract-type scaled via the militia profile's starting range (see §6): roll in
  `[minStarting, maxStarting]`. Each seeded formation is militia-flagged and assigned to a track
  (or `Sector 0` in pure-AtB — but militia are gated to StratCon for now, so this is the StratCon
  track path).

### 6. Reinforcement service + profile

- `MilitiaReinforcementService.maybeReinforce(campaign, contract, oldMorale, newMorale)`:
  - No-op unless StratCon, `useStaticOpForMilitia` on, and `contract.isAttacker()`.
  - Same eligibility shape as `OpForReinforcementService.shouldAttemptReinforcement` (upward
    morale shift + `newMorale >= triggerThreshold`), but reads/increments
    `militiaReinforcementEventsFired`.
  - Adds formations via a new `StratConOpForRosterBuilder.addMilitiaReinforcementFormations(...)`
    (militia-flagged, vehicle/infantry composition).
  - Posts a campaign report ("planetary militia mobilizing") and fires `OpForRosterChangedEvent`.
- `ContractTypeMilitiaReinforcementProfile` — defines its **own** record
  `MilitiaProfile(AtBMoraleLevel triggerThreshold, double probability, int minFormations,
  int maxFormations, int eventCap, int minStarting, int maxStarting)`. It does not reuse the line
  `ContractTypeReinforcementProfile.Profile` record, because militia needs the two starting-pool
  fields and reusing the line record would contaminate it. Proposed table:

| Profile | threshold | prob | min/max reinf | cap | start min/max | Contract types |
|---|---|---|---|---|---|---|
| ASSAULT | ADVANCING | 0.55 | 1–2 | 5 | 2–4 | PLANETARY_ASSAULT |
| RAID | ADVANCING | 0.40 | 1–1 | 3 | 1–2 | OBJECTIVE_RAID, DIVERSIONARY_RAID, RECON_RAID, EXTRACTION_RAID, OBSERVATION_RAID |
| IRREGULAR | ADVANCING | 0.30 | 1–1 | 2 | 0–1 | PIRATE_HUNTING, GUERRILLA_WARFARE, MOLE_HUNTING |
| NEVER | — | 0.0 | 0 | 0 | 0 | all others (incl. garrison/defensive types the player doesn't attack) |

  Note: the profile only ever applies on attacker contracts; defensive/garrison types map to NEVER
  defensively, but the `isAttacker()` gate is the real guard.

### 7. Naming / OOB — `FormationNamer`, `OpForRosterPanel`

- Militia formations get a recognizable name (e.g. `"<Place> Militia"` or a "Militia" suffix on the
  NATO scheme). Minimal approach: a `militia` parameter to the namer that appends "Militia".
- `OpForRosterPanel` renders militia formations under the existing Enemy OOB with a "Planetary
  Militia" tag on the formation header, honoring the existing fog-of-war rules.

### 8. Config — `CampaignOptions`

- New `boolean useStaticOpForMilitia` (default `true`), with getter/setter, marshaller/unmarshaller,
  and a checkbox in the static-OpFor options UI (`RulesetsTab`). Consumed by the builder
  (seeding) and the service (reinforcement). Effective only when `useStaticOpForRoster` is also on.

### 9. New-day hook — `CampaignNewDayManager`

- Add `MilitiaReinforcementService.maybeReinforce(campaign, contract, oldMorale, newMorale);` as the
  third reinforcement call alongside the existing OpFor and Ally calls.

## Data flow

```
Contract accept (isAttacker, StratCon, useStaticOpForMilitia)
  └─ StratConOpForRosterBuilder.seedMilitiaPool → militia-flagged formations into roster

Monthly (day 1), per active contract:
  checkMorale → (old, new) → MilitiaReinforcementService.maybeReinforce
      └─ if isAttacker & upward shift & threshold & under cap & prob roll
           → addMilitiaReinforcementFormations (militia-flagged) → increment militia cap → event

Per scenario: deployer selects living formations (line + militia) to fill BV → militia fight
Resolve: fold damage into militia units like any unit
Elimination check: CONTRACT_WON when livingLineUnits() empty — militia ignored
```

## Testing strategy (TDD)

- **Militia flag / roster:** `StratConOpForFormation` flag round-trips JAXB; `isMilitiaUnit`,
  `livingLineUnits`, `livingLineUnitsForTrack` correct.
- **Victory exclusion:** `CheckEliminationStatusTest` — a roster with all line units terminal but a
  living militia unit returns `CONTRACT_WON`; a roster with living line units returns
  `STILL_ACTIVE`; track pacification ignores militia.
- **Composition generator:** the parameterized `generateUnit` produces the requested `UnitType`;
  militia split honors `MILITIA_INFANTRY_FRACTION`; militia skill stays within green/regular;
  vehicle filter excludes `walkMp == 0`.
- **Starting pool:** `seedMilitiaPool` seeds within `[minStarting, maxStarting]` only when
  `isAttacker` and the option is on; no militia on defender contracts or with the option off.
- **Reinforcement service:** `MilitiaReinforcementService.shouldAttemptReinforcement` mirrors the
  OpFor gate; gated on `isAttacker`; uses the militia cap counter; profile table values correct.
- **Config:** `useStaticOpForMilitia` JAXB round-trip (campaign options test).
- **Integration:** initializer-hook test seeds militia for an attacker StratCon contract.
- **UI:** `OpForRosterPanelTest` renders the militia label.

## Risks / edge cases

- **Win timing.** Because the win fires the instant the last line unit dies, militia never deploy
  alone; verify `checkEliminationStatus` is evaluated on every resolve (it already is).
- **Profile record shape.** The existing `Profile` record lacks starting-pool fields; militia gets
  its own record to avoid contaminating the line/ally profiles.
- **Deployer balance.** Militia are eligible for normal BV-budget selection; no per-scenario militia
  cap initially (YAGNI). If militia crowd out line OpFor in scenarios, revisit later.
- **Infantry materialization.** Conventional infantry already round-trips through
  `OpForUnitMaterializer` / `OpForDamageReader` (ConvInfantry branch); confirm with a test.
- **Save compat.** New fields default safely (militia=false, militia cap=0, option=true) for old
  saves; old rosters simply have no militia until a new militia event fires.
