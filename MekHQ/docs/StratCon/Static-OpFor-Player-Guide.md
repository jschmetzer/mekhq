# Static OpFor — Player Guide

A player-facing how-to for the **Static OpFor** option. For the technical/developer
reference (classes, persistence, integration hooks), see [`Static-OpFor.md`](Static-OpFor.md)
in this same folder. For general StratCon questions, see
[`stratcon-faq-2.6.md`](stratcon-faq-2.6.md).

---

## What it is

Normally, every AtB/StratCon scenario generates a **fresh random enemy** out of thin air.
Beat them today, and tomorrow's battle conjures up a brand-new force of the same strength.
There is no "front line" that wears down — only an endless tap of bot lances.

**Static OpFor** replaces that tap with a **fixed enemy army** for the whole contract. When you
accept a contract, the game rolls up a finite, named **order of battle** (OOB) — a set of
formations and the units inside them — and that army is *all the enemy has*. From then on:

- Enemy units **persist between scenarios.** The `Mech you cored last week comes back this week
  with that same hole in its chest — or doesn't come back at all, because you destroyed it.
- Every unit you **destroy, salvage, or capture is gone for good.** The enemy army only shrinks
  (except for reinforcements — see below).
- You can **win the contract by attrition** — grind the whole enemy OOB down to nothing and the
  contract completes as a success, payout included.
- You get a **fog-of-war intel picture** you can scout and fill in over time, shown in dedicated
  **Enemy OOB** and **Allied OOB** tabs.

| Stock (dynamic) OpFor | Static OpFor |
|---|---|
| A new random force every scenario | One army, rolled at contract acceptance |
| No memory — damage resets each battle | Units keep their damage and status across scenarios |
| Enemy strength is invisible | A scoutable order-of-battle you grind down |
| You can never "finish off" the enemy | Eliminate the OOB to win the contract |

> Think of it as turning each contract into a **siege** instead of an arcade shooter. You are not
> fighting today's spawn; you are dismantling a specific enemy command, piece by piece.

---

## Turning it on

The Static OpFor options live in **Campaign Options → "Digital GMs" tab → StratCon section**
(near "Use StratCon"). There are four of them:

| Option | Default | What it does |
|---|---|---|
| **Enable Static OpFor Roster** | Off | The master switch. When on, every contract you accept from now on gets a persistent enemy (and allied) OOB. |
| **Roster Size Multiplier** | 1.25 | Scales how big the enemy army is relative to your force. `1.0` = lean (every loss really hurts the enemy); `1.25` = a modest buffer; `1.5` = forgiving (a larger army to chew through). |
| **Minimum Formation Count** | 3 | A floor on the number of enemy formations, so a short contract still has a meaningful army to fight rather than two lances. |
| **Enable Planetary Militia** | On | On contracts where **you are the attacker**, the planet's defenders get bolstered by local militia (see [Planetary militia](#planetary-militia)). Only matters when the master switch is also on. |

Each option has a hover tooltip in the dialog repeating the gist of the above.

**Important: the roster is built when you *accept* a contract.** Turning the option on (or changing
the multiplier/floor) only affects contracts you accept *afterward*. Contracts already in progress
keep whatever they were built with. So set this up *before* you take the contract you want it on.

**StratCon and pure AtB both work:**

- With **Use StratCon** on, the enemy army is spread across the contract's tracks, and you'll see
  it organized by track in the OOB tabs.
- With **Use StratCon** off (pure AtB), the whole army lives in one bucket labelled **"Sector 0."**

Subcontracts don't get their own army — they share the parent contract's.

---

## The order-of-battle tabs

Turn on Static OpFor and open a contract's StratCon view. Alongside **Sector Info** you'll find two
new tabs:

- **Enemy OOB** — your intel picture of the enemy army.
- **Allied OOB** — the support your employer has committed (always fully known to you).

(If a contract was accepted *without* Static OpFor, the Enemy OOB tab simply reads
*"No static enemy roster — dynamic OpFor mode active."*)

Each tab is a collapsible tree: **track → formation → unit.** Use **Expand all / Collapse all** at
the top, or click individual headers. Your expand/collapse choices stick — the view won't reset
itself after every battle.

### Reading the panel

- **Summary header** (top): `Line OpFor: {remaining} / {total} formations` — your at-a-glance
  progress toward winning. *Total* is the number of fighting (non-militia) formations the enemy
  started with; *remaining* is how many still have at least one living unit. When militia are
  present it adds ` · Militia: {n} active`.
- **Unit-type glyphs** on each visible unit line:
  - `[M]` BattleMech
  - `[V]` Vehicle (tank/VTOL)
  - `[I]` Infantry or Battle Armor
- **Status colors** on units you've taken out of the fight:
  - **Destroyed** — red, struck through
  - **Salvaged** — dark goldenrod, struck through
  - **Captured** — blue, struck through

  A formation with no living units left is labelled **DESTROYED** in red.
- **Condition words** on units that are still fighting but hurt — so you can tell at a glance which
  enemies your earlier battles softened up:
  - **battle-worn** — the unit is carrying damage from a previous scenario.
  - **crippled** — the unit is badly hurt (a location shot off or destroyed, or an engine/gyro hit).

  A fresh, undamaged unit shows no condition word.

---

## Fog of war

You don't start out knowing the enemy's full army — you have to *find out*. Each enemy formation
has an intel level that improves as you fight:

| Intel level | What you see |
|---|---|
| **Unknown** | "Unidentified formation" — units hidden |
| **Observed** | Name, weight class, and strength — but individual units show `???` until revealed |
| **Full Intel** | Everything, including skill level |

How intel improves:

- A formation goes **Unknown → Observed** the first time it deploys against you.
- It jumps to **Full Intel** once you've destroyed **half or more** of its units.
- Any single unit becomes individually revealed the moment it's destroyed, salvaged, or captured.

Your **allied** formations are always shown at Full Intel — your employer briefed you on your own
support.

---

## Winning by attrition

This is the headline feature. Because the enemy army is finite, you can **end the contract by
destroying all of it.** When the last living *fighting* unit in the enemy OOB is removed, the
contract completes as a **success** and pays out, exactly as if you'd hit its normal objective.

Along the way you'll see campaign-log lines such as:

- *"{formation}'s {unit} destroyed — 4 reduced to 3 / 5"* as you whittle a formation down.
- *"{track} pacified — no further enemy activity."* when an entire track's enemy presence is gone
  (StratCon only).
- *"Enemy forces eliminated. Contract complete."* on the winning blow.

Three things shrink the enemy army permanently, and **all three count**:

1. **Destroyed** in battle.
2. **Salvaged** — you recovered the wreck. (This includes wrecks surrendered to your employer or
   sold off under CamOps salvage rules — if it left the battlefield as salvage, it's off the enemy
   roster.)
3. **Captured** — you took the pilot prisoner.

And remember: anything that *survives* keeps its **damage**. A `Mech you crippled but didn't finish
will redeploy next time still missing that arm. Grinding the enemy down is cumulative.

You don't have to hunt down every track to mop up the last few enemies, either. Once a track has no
enemy formations left, leftover formations from elsewhere in the contract will be pulled into your
remaining battles — so the stragglers come to you, and the roster stays winnable to the last unit.
(Enemies that *retreat* from a battle aren't gone — they'll be back in a later one, and they now
**keep the damage they took in the fight they fled** instead of healing up between scenarios; only
destroyed, captured, or salvaged units leave the roster for good.)

---

## Reinforcements

The enemy army isn't *completely* static — but reinforcement works the opposite of what you might
fear. Both sides get monthly, morale-driven reinforcements:

- The **enemy reinforces while it's winning** (while it is *ascendant* and you're on the back
  foot). As you grind it down and its morale falls, reinforcements **taper off** — a collapsing
  enemy does *not* get a fresh army handed to it.
- Your **employer sends allied help while *you're* losing** — support arrives when you need it, by
  the same logic.

Reinforcements are gated by contract type and capped, so they can't run forever. **Capturing or
losing facilities** can also shift the rosters (and that effect bypasses the normal cap) — but the
capture and loss are now mirror images of each other, so a facility that changes hands back and forth
nets out to no lasting change; only holding *new* ground moves the needle.

**Enemy reinforcements keep pace with your force.** Over a long contract your crews gain experience,
and enemy reinforcement waves rise to match: each new wave is generated at the higher of the
contract's original enemy skill and your force's *current* average crew skill (and its equipment
quality rises with it). Reinforcements never come in *weaker* than the enemy you originally signed on
to fight, but they won't arrive as green rookies against a veteran company late in the contract — the
late-contract waves stay a real threat. This only affects **reinforcements**; the army you first
scouted at contract acceptance is unchanged, and it's the crews' *skill and equipment* that scale up,
not the *number* of formations (that's still set by the contract type).

The net effect: a contract that's going badly stays tense, and a contract you're dominating winds
down toward a clean attrition victory instead of dragging on.

---

## Planetary militia

When **you are the attacker** — i.e. the Static OpFor is the planet's **defender** — the defenders
can be reinforced by **planetary militia**: mostly low-to-mid-skill combat vehicles, with the
occasional conventional infantry platoon. This is controlled by **Enable Planetary Militia**
(on by default; StratCon contracts only for now).

Two things to know about militia:

- **They show up in the Enemy OOB**, grouped under a muted *"Planetary Militia"* subheader beneath
  the regular (line) formations, and they fight, take damage, and reinforce just like line units.
- **They do not count toward winning.** The contract is won when the last *line* unit dies — militia
  remaining on the field will not keep the contract open. They're flavor and friction, not a victory
  gate. (The summary header tracks them separately as "Militia: {n} active.")

---

## The Intelligence Log

Open it from **Reports → Intelligence Log.** This is a campaign-wide, cross-contract record of every
enemy unit you've had eyes on — killed, captured, salvaged, or merely observed — with the date,
faction, pilot, and chassis/model. The top of the window summarizes counts by outcome and a
breakdown by faction. It's read-only: a running history of who you've fought and how it went.

---

## GM: editing the roster

There's a built-in escape hatch for game-masters and for repairing a save. With **GM mode** enabled
(toggle at the top-right of the main screen), an **"Edit OOB…"** button appears on the Enemy OOB and
Allied OOB tabs. It opens a full editor where you can:

- Add, edit, or delete **formations** (name, weight class, quality, skill, track, intel level, and
  the militia flag).
- Add, edit, remove, or reassign **units** (chassis/model from the standard unit picker, pilot name,
  gunnery/piloting, status, fog-of-war reveal flag, and which formation they belong to).

It edits a working copy, so **Cancel throws away every change** — nothing touches your live campaign
until you click OK. Fog of war is ignored while editing (you see everything). This is deliberately
gated behind GM mode so it can't be used to trivialize the attrition fight in normal play; treat it
as a sandbox and save-repair tool.

---

## Quick FAQ

**Q: I turned it on but my current contract still spawns random enemies.**
The army is built when you *accept* a contract. Enable the option, then accept a new contract.

**Q: Why do some enemy units show `???`?**
Fog of war — you've spotted the formation (Observed) but not yet identified that specific unit.
Destroy units in the formation to raise it to Full Intel, or destroy/capture/salvage the unit
itself to reveal it.

**Q: The enemy keeps getting reinforcements!**
That happens while the enemy is *ascendant* (winning). Win battles, push their morale down, and the
reinforcements dry up. It's a sign you're losing the contract, not a bug.

**Q: I wiped out the visible enemy but the contract didn't end.**
Check the **Line OpFor: x / y** counter — there are still formations you haven't found or finished
(possibly Unknown ones that haven't deployed yet, or units on another track). Militia don't count,
so don't go by the militia line.

**Q: My save's roster looks wrong / I want to set up a specific fight.**
Enable GM mode and use **Edit OOB…**. Cancel discards everything, so experiment freely.

**Q: On a garrison contract, a different faction showed up after I beat the last one.**
That's intended. Garrison worlds are struck by **successive challengers** over the contract: when
one is destroyed or routed it withdraws, and after a lull a new force — often a different faction —
arrives to contest the world. Each challenger is its own finite roster, correctly labeled and
equipped for its faction, and appears as its own section in the **Enemy OOB** tab (occasionally two
overlap briefly). You **defend for the contract's term** rather than winning by wiping out a single
challenger — beating one is a milestone (logged in the Intelligence Log), not the end of the job.
