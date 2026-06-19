# Static OpFor Hardening Pass — Design

**Date:** 2026-06-19
**Branch:** `feature/stratcon-static-opfor`
**Scope:** Two correctness/calibration fixes that make the existing static OpFor feature
honest and tunable before any larger ("living front") iteration. No new gameplay surface.

Feature reference: [Static-OpFor.md](../../StratCon/Static-OpFor.md).

---

## Goals

1. **Make the two declared-but-dead sizing options actually work** so OpFor difficulty is
   tunable from campaign options instead of silently ignored.
2. **Close the solo-Mek-pilot capture gap** so captured solo Mek pilots are reconciled back
   to their `StratConOpForUnit` and counted as removed from the roster.

## Non-goals

- No enemy maneuver / initiative ("living front") work — deferred to a later iteration.
- No `ESCAPED` / withdrawal status — explicitly dropped from scope. Retreated units continue
  to stay `READY` and redeploy.
- No MegaMek (combat-engine) changes — the capture fix is MekHQ-only.

---

## Item 1 — Wire the two sizing options

### Current state

`StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract)` computes:

```java
int raw = campaign.getCombatTeamsAsList().size()
        + ContractTypeOpForModifier.getModifier(contract.getContractType());
return Math.max(MIN_FORMATIONS, Math.min(MAX_FORMATIONS, raw)); // MIN_FORMATIONS = 2
```

`CampaignOptions.staticOpForPaddingFactor` (default `1.25`) and
`staticOpForFormationCountFloor` (default `3`) are declared, serialized, and surfaced in the
options UI but **never read** by any sizing code. The builder already has the `Campaign` in
scope at this point, so no signature changes are needed.

### Design

```java
int playerFormations = campaign.getCombatTeamsAsList().size();
int modifier = ContractTypeOpForModifier.getModifier(contract.getContractType());
double padding = campaign.getCampaignOptions().getStaticOpForPaddingFactor();
int floorOption = campaign.getCampaignOptions().getStaticOpForFormationCountFloor();

int raw = (int) Math.ceil(playerFormations * padding) + modifier;
int floor = Math.max(ABSOLUTE_MIN_FORMATIONS, floorOption);  // ABSOLUTE_MIN_FORMATIONS = 1
return Math.max(floor, Math.min(MAX_FORMATIONS, raw));
```

Decisions:

- **Padding multiplies only the player-team term.** `raw = ceil(playerFormations * padding)
  + modifier`. The contract-type modifier stays a flat signed add; padding scales with the
  player's force size. Default `1.25` ⇒ a ~25% larger OpFor relative to player team count.
- **Floor option becomes authoritative**, clamped to an absolute safety minimum of `1`
  (a 0-formation static roster would do nothing and fall back to dynamic generation). The old
  hard-coded `MIN_FORMATIONS = 2` is replaced by `ABSOLUTE_MIN_FORMATIONS = 1` plus the
  configurable floor. With the default option value `3`, new contracts get a minimum of 3
  formations vs. the previous effective 2.
- **Ally side mirrors padding.** `computeInitialAllyFormationCount` applies the same padding to
  its player-team term for enemy/ally symmetry, but keeps its floor at `0` (allied support can
  legitimately be absent). The floor *option* does not apply to the ally side.

### Difficulty / save-compat note

Existing saves are unaffected for already-built rosters (rosters are built once at contract
acceptance). New contracts accepted after this change will see padding and the floor applied.
This is an intended, documented difficulty calibration, not a regression.

---

## Item 2 — Solo-Mek-pilot capture fallback (MekHQ-only)

### Root cause

`OpForUnitMaterializer` stamps `pilotPersistentId` onto `crew.externalIdAsString(0)` and the
unit's roster id onto `entity.externalIdAsString`. The multi-slot crew path round-trips the
pilot id, so `foldResolutionInto`'s `byPilotId` reconciliation works. But when a **solo Mek
pilot ejects**, the ejected `MekWarrior`/`EjectedCrew` entity does not inherit the parent Mek's
crew-slot external id; `Utilities.genRandomCrewWithCombinedSkill` then mints a *random* UUID for
the captured `Person`, so `byPilotId.get(person.getId())` finds nothing.

The fix does not chase the pilot id. It carries the **source unit's external id** — which is
already equal to the `StratConOpForUnit.id` — through the capture pipeline. In
`processPrisonerCapture`, the captured `Person` is generated from a `TestUnit unit` whose
`unit.getEntity().getExternalIdAsString()` is the original Mek wreck's id. That variable is never
reassigned (only the local `entity` is reassigned to the ejected crew), so it survives ejection.

### Design

Three small changes, all in MekHQ:

1. **`ResolveScenarioTracker.OppositionPersonnelStatus`** — add a
   `private UUID sourceUnitExternalId` field plus a getter. (Nullable; only set when the source
   entity carries a valid, non-`"-1"` external id.)
2. **`ResolveScenarioTracker.processPrisonerCapture`** — when constructing each
   `OppositionPersonnelStatus`, set `sourceUnitExternalId` from
   `unit.getEntity().getExternalIdAsString()`, guarded against `null` / `"-1"`.
3. **`StratConOpForRoster.foldResolutionInto`** — in the captured-pilot loop, keep the existing
   `byPilotId.get(ops.getPerson().getId())` match as the primary path. When it returns `null`,
   fall back to `unitsById.get(ops.getSourceUnitExternalId())`, accepting the match only if that
   unit was deployed in this scenario (`lastDeployedScenarioId == scenarioUuid`). `CAPTURED`
   continues to override any status the main loop already assigned (e.g. `SALVAGED`), as today.

This fixes the solo-Mek case and is strictly more robust for every other capture, without
altering the working multi-slot path.

### Why not the MegaMek fix

Propagating `pilotPersistentId` to the `EjectedCrew` entity at ejection time would be a
cleaner root-cause fix but requires a cross-repo change to `megamek/`. The fallback keeps the
change inside the feature's own integration surface.

---

## Test plan (TDD — failing tests first)

### Sizing (`StratConOpForRosterBuilderTest`)

- New: padding scales the player-team term (e.g. 4 teams × 1.25 = 5, + modifier).
- New: floor option raises the minimum above the computed raw count.
- New: padding + floor interaction (floor wins when padding-scaled raw is below it).
- New: ally padding applies; ally floor stays 0.
- Update the 5 existing `computeInitialFormationCount_*` tests + the
  `buildForContract_producesExactFormationCount` integration test to stub
  `campaign.getCampaignOptions()` (returning defaults `padding = 1.0` for count-preserving cases,
  or the real defaults with adjusted expectations).

### Capture (`FoldResolutionIntoTest`, `ResolveScenarioTrackerTest`)

- New (`FoldResolutionIntoTest`): a captured solo Mek pilot whose `Person.getId()` matches no
  `pilotPersistentId`, but whose `sourceUnitExternalId` equals a deployed unit's id → that unit
  becomes `CAPTURED`.
- New: a captured `ops` whose `sourceUnitExternalId` belongs to a *different* scenario is **not**
  reconciled (scenario scoping holds).
- New (`ResolveScenarioTrackerTest`): `processPrisonerCapture` populates `sourceUnitExternalId`
  from the source entity's external id.
- Existing `foldResolutionInto_capturedPilot_statusBecomesCaptured` (multi-slot path) must stay
  green unchanged.

### Docs

Update [Static-OpFor.md](../../StratCon/Static-OpFor.md):

- §4.1 — formation count now consumes padding + floor; describe the ally-symmetry and floor rules.
- §7 — the two options are no longer "reserved"; document their effects and defaults.
- §4.3 / §10 — the solo-Mek capture gap is closed; describe the `sourceUnitExternalId` fallback.

---

## Risks / edge cases

- **Padding default 1.25 ≠ identity.** Wiring it changes new-contract sizing for any save that
  kept the default. Intended; documented above.
- **Test mocks.** The existing sizing tests mock `Campaign` without stubbing
  `getCampaignOptions()`; they will NPE once the options are read. All must be updated — a
  mechanical but required change.
- **Capture idempotency.** The unit-id fallback could in principle also match a multi-slot vee
  already handled by the pilot-id path; because it only runs when the pilot-id match is `null`
  and `setStatus(CAPTURED)` is idempotent, there is no double-processing hazard.
- **New constant naming.** `MIN_FORMATIONS` is replaced by `ABSOLUTE_MIN_FORMATIONS`; confirm no
  other references to `MIN_FORMATIONS` exist outside the builder (investigation found none).
