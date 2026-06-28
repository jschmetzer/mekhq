# Design — Multi-Challenger Static OpFor for Garrison Contracts

**Date:** 2026-06-28 · **Branch:** `feature/stratcon-static-opfor` · **Scope:** LOCAL fork feature (no upstream PR)

## 1. Problem & root cause

On a garrison StratCon contract whose enemy was Pirates, a generated scenario's bot force was
**labeled "Draconis Combine" but its units were rolled from the Pirate RAT**.

Cause: the static OpFor roster is built once at contract acceptance from the contract enemy and
**persists for the life of the contract** (the win-by-attrition design). But for garrison-type
contracts, [`AtBContract.checkMorale`](../../../MekHQ/src/mekhq/campaign/mission/AtBContract.java)
calls `updateEnemy()` on a **rout-end** to "mix it up" — it rolls a new random enemy faction and
overwrites `enemyCode`/`enemyBotName` (the `routEnded.aNewChallenger` beat). The persistent roster
is *not* rebuilt, and the deployer
([`StratConOpForDeployer` line ~259](../../../MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForDeployer.java))
names each bot force from the **live, drifted** `contract.getEnemyBotName()` while deploying the
original (Pirate) roster. Result: drifted label over stale units.

## 2. Goal

Turn that drift into a designed feature: a garrison world is **struck over time by successive
distinct forces** (Pirates, then Draconis Combine, …), each a separately-tracked finite roster that
persists and attrites independently, with every deployed bot force labeled and RAT-sourced from
**its own** faction. Identity travels with the roster, so the original bug is structurally
impossible.

## 3. Scope

- **In scope:** garrison-type contracts (`getContractType().isGarrisonType()`) with
  `useStaticOpForRoster` enabled.
- **Out of scope (unchanged):** every non-garrison contract type keeps today's single-roster,
  eliminate-to-win behavior; the **allied** roster stays single; full concurrent multi-front
  warfare (we model sequential-with-overlap, not N simultaneous fronts).

## 4. Data model

A garrison contract's single enemy roster generalizes to an **ordered list of challengers**. Each
challenger *is* a `StratConOpForRoster` (the existing force model — formations, units, damage,
reinforcement counters) augmented with **identity + lifecycle** fields:

| Field | Purpose |
|---|---|
| `factionCode` (String) | The challenger's faction, captured at **its** build time. Drives bot-force label + RAT. |
| `enemyBotName` (String) | Display name captured at build time (e.g. "Draconis Combine", or a pirate band name). |
| `enemyColour` / `enemyCamouflage` | Captured at build time so each challenger renders with its own colours. |
| `status` (`ChallengerStatus`) | `ACTIVE` → `WITHDRAWN` (routed, survivors gone) or `DEFEATED` (eliminated). |
| `arrivedDate` / `endedDate` (LocalDate) | For the intel log and OOB history ordering. |

New enum **`ChallengerStatus { ACTIVE, WITHDRAWN, DEFEATED }`** (`isTerminal()` = not `ACTIVE`).

`StratConCampaignState`: the single `opForRoster` becomes **`List<StratConOpForRoster>
opForChallengers`**; the pure-AtB `AtBContract.atbOpForRoster` generalizes to a list likewise. The
allied roster (`alliedRoster` / `atbAlliedRoster`) is **unchanged**. Convenience accessors:
`getActiveChallengers()`, `getPrimaryChallenger()` (newest `ACTIVE`), and a back-compat
`getOpForRoster()` that returns the primary active challenger (so incidental existing callers keep
working).

> **Considered & rejected:** a separate `StratConOpForChallenger` wrapper class around the roster.
> The identity/lifecycle fields live naturally on the roster (which already carries per-force
> reinforcement counters), and a wrapper would add a second JAXB element + back-link plumbing for no
> behavioural gain (efficiency ladder rung 6). Per-challenger reinforcement "just works" because the
> counters are already per-roster.

## 5. Challenger lifecycle (rides the existing morale/rout rail — no new scheduler)

The garrison enemy rhythm already exists: morale falls → **ROUTED** sets `routEndDate = today +
1–3 months` (`MHQMorale`); during the rout **scenario generation is suppressed**
(`StratConRulesManager`: routed ⇒ zero scenario odds) — the built-in **lull**; at **rout-end**,
morale spikes back up and (garrison/retainer) `updateEnemy()` fires the `aNewChallenger` beat.

- **Spawn.** When `updateEnemy` fires on an in-scope contract, instead of relabeling it **builds a
  new `ACTIVE` challenger** for the freshly-rolled faction via `StratConOpForRosterBuilder`
  (capturing identity fields at build time) and appends it to `opForChallengers`. The faction roll
  reuses the existing `RandomFactionGenerator.getEnemy(employer, …)` so Pirates remain possible.
- **Retire.** At spawn time, the outgoing challenger is marked `DEFEATED` if it already had no
  living line units, else `WITHDRAWN`; `endedDate` stamped.
- **Overlap (sequential-with-overlap).** The list structurally allows ≥2 `ACTIVE`. Default
  succession is clean (the rout lull separates challengers). A tunable **lingering-survivors** knob:
  when a challenger is `WITHDRAWN` with living units, its survivors may remain deployable for one
  grace window (default: until the next rout cycle) before removal — producing the brief two-faction
  overlap. Default knob values live in §10; overlap can be disabled by setting the grace to zero.
- **First challenger.** Built at contract acceptance exactly as today (one-element list).

## 6. Deployment + root-cause fix

`StratConOpForDeployer` selects formations across the **active challengers** and builds each
`BotForce` labeled and RAT-sourced from **that challenger's own** `factionCode` / `enemyBotName` /
colours — never the live `contract.getEnemyBotName()`. Selection (BV budget, weight-class match,
least-recently-deployed, committed-formation dedup, global-pool fallback) runs **per active
challenger**; during overlap a scenario may field more than one enemy bot force, each its own
faction. This is the structural fix: label and units can no longer disagree.

## 7. Win condition & milestones

For in-scope contracts, attrition `CONTRACT_WON` is **retired** — the contract completes on its
**garrison timer** (defend the term). `checkEliminationStatus` is scoped so clearing a single
challenger no longer ends the contract; instead:

- Eliminating a challenger sets it `DEFEATED`, writes an **IntelLog** milestone entry, and fires the
  existing "challenger repelled" morale/dialog beat.
- `TRACK_PACIFIED` semantics still apply *within* an active challenger where meaningful.
- The contract still ends via the normal garrison length / StratCon victory-point path.

Non-garrison contracts retain `CONTRACT_WON` (their `checkEliminationStatus` path is unchanged).

## 8. Contract-level enemy fields

`contract.getEnemy()` / `getEnemyCode()` / `getEnemyBotName()` / `getEnemyCamouflage()` — consumed
by objectives (`CommonObjectiveFactory`), morale dialogs, camo, and the **dynamic** bot-force naming
fallback — continue to track the **primary (newest active) challenger**. `updateEnemy` already sets
these when spawning a challenger, so they point at the latest one with no sweeping rename. Only the
static deployer reads per-challenger identity instead of these fields.

## 9. Persistence, migration & UI

- **Persistence.** `opForChallengers` serializes as a JAXB list on `StratConCampaignState` (and the
  pure-AtB field generalizes). The existing `@XmlTransient` contract back-link relink now runs **per
  challenger** in the list.
- **Migration (load-bearing).** A legacy save with a single `opForRoster` element loads into a
  **one-element list**, `status = ACTIVE`, identity backfilled from the contract's current enemy
  fields (best effort). Older saves therefore behave exactly as before until their first rout-end.
- **OOB UI** (`OpForRosterPanel`). Renders each **active challenger** as a top-level faction-labeled
  group (reusing the existing track→formation→unit tree per challenger); `WITHDRAWN`/`DEFEATED`
  challengers collapse into a "Past challengers" history section. The summary header reports
  per-challenger line counts. Fog-of-war rules per challenger are unchanged.
- **GM editor** (`OpForRosterEditorDialog`). Gains a **challenger selector**; otherwise reuses
  `OpForRosterEditOps` per selected roster — no new edit primitives.

## 10. Tuning knobs (defaults; adjustable)

| Knob | Default | Notes |
|---|---|---|
| Overlap grace window | until next rout cycle | `0` disables overlap (pure succession). |
| Max simultaneous `ACTIVE` challengers | 2 | Safety cap; spawn won't exceed it. |
| Challenger faction roll | existing `RandomFactionGenerator.getEnemy` | Pirates remain possible. |
| New per-challenger sizing | reuses existing padding/floor options | No new CampaignOptions needed initially. |

## 11. Testing

Reusing existing patterns (`StratConOpForDeployerTest`, `StratConCampaignStateJaxbTest`,
`AtBContractTest`, `CheckEliminationStatusTest`):

- Challenger **spawn** on rout-end (garrison, static on) appends an `ACTIVE` roster of the new
  faction; outgoing marked `WITHDRAWN`/`DEFEATED`.
- **Per-challenger faction** labeling + RAT in the deployer (the regression test for the original
  bug: name faction == unit faction).
- **Defend-the-term** win scoping: clearing one challenger does **not** fire `CONTRACT_WON` for
  garrison; non-garrison still does.
- **JAXB** list round-trip **and** legacy single-roster → one-element-list migration, including
  per-challenger back-link relink.
- **Overlap:** two `ACTIVE` challengers deploy correct distinct factions in one scenario.
- Lifecycle transitions `ACTIVE → WITHDRAWN/DEFEATED` and intel-log milestone on defeat.

## 12. Efficiency-ladder deferrals (recorded)

- Full concurrent multi-front (N simultaneous fronts) — **deferred**; sequential-with-overlap covers
  the verisimilitude goal.
- New CampaignOptions for the tuning knobs — **deferred**; ship with constants, promote to options
  only if play shows a need.
- Allied multi-challenger — **not pursued**; allies stay single.

## 13. Integration points (hooks on existing classes)

| Class | Change |
|---|---|
| `StratConOpForRoster` | + identity/lifecycle fields; `ChallengerStatus` |
| `StratConCampaignState` | `opForRoster` → `opForChallengers` list; accessors; JAXB; per-item relink |
| `AtBContract` | `atbOpForRoster` → list; `updateEnemy` spawns a challenger for in-scope contracts; migration on load |
| `StratConOpForRosterBuilder` | capture identity at build; build-a-challenger entry point |
| `StratConOpForDeployer` | select/label/RAT per active challenger (the fix) |
| `checkEliminationStatus` / win path | defend-the-term scoping for garrison; defeat milestone |
| reinforcement services | operate per active challenger (already per-roster counters) |
| `OpForRosterPanel`, `OpForRosterEditorDialog` | multi-challenger rendering + editor selector |
| `IntelLog` | challenger-defeated milestone entries |
| `Static-OpFor.md` | new "Multi-challenger garrison" section |

## 14. Documentation

`MekHQ/docs/StratCon/Static-OpFor.md` gains a "Multi-challenger garrison contracts" section; the
player guide gets a short note on successive challengers and defend-the-term.
