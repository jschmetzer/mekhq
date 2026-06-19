# OpFor OOB Panel — UI Adjustments Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Five UX improvements to the Enemy OOB panel: a roster summary header surfacing the win metric, militia grouped as a distinct subsection, persistent collapse state + expand/collapse-all, status color-coding, and a unit-type cue.

**Architecture:** Almost all changes are in `OpForRosterPanel.java` (one file, same render methods — a single cohesive change, not parallelizable). Item 5 needs a small model addition: a `unitType` on `StratConOpForUnit` set at generation, so the panel can render a type glyph without a `MekSummaryCache` lookup.

**Tech Stack:** Java 21 Swing, JUnit 5, Gradle. Build from `/Users/jasonschmetzer/projects/mekhq-static-opfor`.

Files touched: `OpForRosterPanel.java`, `OpForRosterPanelTest.java`, `AtBStratCon.properties`, `StratConOpForUnit.java`, `StratConOpForRosterBuilder.java` (one line), plus a small `StratConOpForUnit`/builder test.

Reference current panel: [OpForRosterPanel.java](../../../MekHQ/src/mekhq/gui/stratCon/OpForRosterPanel.java).

---

## Design decisions (locked)

- **#1 Summary header** (top of panel, above the tracks, bold): `Line OpFor: {remaining} / {total} formations`, where `total` = count of non-militia formations and `remaining` = non-militia formations with ≥1 living unit (`!isDestroyed(roster)`). When any militia formations exist, append ` · Militia: {active} active` (militia formations with ≥1 living unit). Resource keys `opForRosterPanel.summaryLine` and `opForRosterPanel.summaryMilitia`.
- **#2 Militia grouping**: within each track, render non-militia (line) formations first; if the track has any militia formations, render a muted italic subheader (`opForRosterPanel.militiaSubheader` = "Planetary Militia") then the militia formations. Militia formation headers render in a muted gray. **Remove** the old inline `[Planetary Militia]` tag (current lines 217-219) — grouping replaces it. Keep all fog-of-war rules unchanged.
- **#3 Collapse persistence + expand/collapse all**: keep a `Map<String, Boolean>` `collapseState` field on the panel (true = expanded), keyed by a stable string (`"T:" + trackName` for tracks, `"F:" + formationId` for formations). `buildCollapsible` takes the key, initializes visibility from the map (default expanded), and writes the new state on toggle. `refresh()` retains the map across rebuilds. Add a small top toolbar with two link/buttons: "Expand all" / "Collapse all" that set every known key and `refresh()`.
- **#4 Status colors**: `DESTROYED` → red, `SALVAGED` → dark goldenrod (`#B8860B`), `CAPTURED` → blue (`#1E6FBA`); terminal statuses also strike-through. Apply in `buildUnitLine` (the status span) and keep the formation "DESTROYED" label red. Add a `statusColorHex(Status)` helper.
- **#5 Unit-type cue**: add `int unitType` (a `megamek.common.units.UnitType` constant; default `-1` unknown) to `StratConOpForUnit` (JAXB `@XmlElement`) with getter/setter; set it in `StratConOpForRosterBuilder.generateUnit` from the generated `entity.getUnitType()`. In the panel, prefix a visible (non-masked) unit line with a short tag: `[M]` Mek, `[V]` Vehicle (TANK/VTOL), `[I]` Infantry/BA, else none. Helper `typeTag(int unitType)`.

---

## Task 1: Model — `unitType` on `StratConOpForUnit` (+ builder wiring)

**Files:**
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForUnit.java`
- Modify: `MekHQ/src/mekhq/campaign/stratCon/opfor/StratConOpForRosterBuilder.java` (in `generateUnit`, after the `entity` is created ~line 634-639)
- Test: `MekHQ/unittests/mekhq/campaign/stratCon/opfor/StratConCampaignStateJaxbTest.java` (or the unit's existing test) for round-trip; `StratConOpForRosterBuilderTest` to assert the generated unit carries a type.

- [ ] **Step 1: Failing test** — `StratConOpForUnit` defaults `unitType == -1` and round-trips a set value.
```java
@Test
void unitType_defaultsUnknown_andRoundTrips() {
    StratConOpForUnit u = new StratConOpForUnit();
    assertEquals(-1, u.getUnitType());
    u.setUnitType(megamek.common.units.UnitType.TANK);
    assertEquals(megamek.common.units.UnitType.TANK, u.getUnitType());
}
```
- [ ] **Step 2: Run → fails (no accessors).** `./gradlew :MekHQ:compileTestJava`
- [ ] **Step 3: Implement** — add `@XmlElement private int unitType = -1;` + `getUnitType()`/`setUnitType(int)` to `StratConOpForUnit` (mirror the existing field/accessor style). In `StratConOpForRosterBuilder.generateUnit`, after the entity is built and before returning the unit, add `unit.setUnitType(entity.getUnitType());`.
- [ ] **Step 4: Run → pass.** `./gradlew :MekHQ:test --tests "mekhq.campaign.stratCon.opfor.StratConCampaignStateJaxbTest" --tests "mekhq.campaign.stratCon.opfor.StratConOpForRosterBuilderTest"`
- [ ] **Step 5: Commit** (`feat(opfor): record unit type on StratConOpForUnit for OOB rendering`).

---

## Task 2: Panel — all five UI adjustments

**Files:**
- Modify: `MekHQ/src/mekhq/gui/stratCon/OpForRosterPanel.java`
- Modify: `MekHQ/resources/mekhq/resources/AtBStratCon.properties`
- Test: `MekHQ/unittests/mekhq/gui/stratCon/OpForRosterPanelTest.java`

Read the current `OpForRosterPanel.java` and `OpForRosterPanelTest.java` first; mirror the existing rendering-test approach (the tests walk the component tree / inspect `JLabel` text). Add resource keys: `summaryLine`, `summaryMilitia`, `militiaSubheader`.

- [ ] **Step 1: Failing tests** (add to `OpForRosterPanelTest`):
  - `summaryHeader_showsLineFormationCount` — a roster with N line formations (one destroyed) renders a header containing `Line OpFor: {remaining} / {N}`.
  - `summaryHeader_showsMilitiaCountWhenPresent` — with militia present, header contains `Militia:` and the active count; with no militia, it does NOT.
  - `militiaFormations_renderUnderMilitiaSubheader` — a militia formation appears under the "Planetary Militia" subheader, and the old inline `[Planetary Militia]` tag is gone.
  - `collapseState_survivesRefresh` — collapse a section, call `refresh()`, assert it stays collapsed (body not visible).
  - `terminalUnit_usesStatusColor` — a SALVAGED unit's line contains the goldenrod hex (or a CAPTURED unit contains the blue hex); a DESTROYED contains red.
  - `unitLine_showsTypeTag` — a visible unit with `unitType == TANK` renders `[V]`; `INFANTRY` renders `[I]`; `MEK` renders `[M]`.
- [ ] **Step 2: Run → fail.** `./gradlew :MekHQ:test --tests "mekhq.gui.stratCon.OpForRosterPanelTest"`
- [ ] **Step 3: Implement** all five per the locked design:
  - Summary header built in `refresh()` before the track loop (compute from `roster.getFormations()`, partitioning on `isMilitia()` and `isDestroyed(roster)`).
  - Militia grouping inside `buildTrackSection` (partition formations; line first, then subheader + militia, militia headers muted gray). Remove the inline militia tag in `buildFormationSection`.
  - Collapse-state map + keys threaded through `buildCollapsible`; `refresh()` keeps the map; add the expand/collapse-all toolbar at the top.
  - `statusColorHex(Status)` helper; apply in `buildUnitLine` and the formation destroyed label; strike-through terminal via `<strike>`.
  - `typeTag(int)` helper; prefix visible unit lines.
- [ ] **Step 4: Run → pass.** `./gradlew :MekHQ:test --tests "mekhq.gui.stratCon.OpForRosterPanelTest"`
- [ ] **Step 5: Commit** (`feat(ui): OOB summary header, militia grouping, persistent collapse, status colors, type cue`).

---

## Task 3: Verify + docs

- [ ] **Step 1:** `./gradlew :MekHQ:test --tests "mekhq.gui.stratCon.*" --tests "mekhq.campaign.stratCon.opfor.*"` → pass.
- [ ] **Step 2:** `./gradlew compileJava` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Update [Static-OpFor.md](../../StratCon/Static-OpFor.md) §6 (UI) to describe the summary header, militia grouping, persistent collapse + expand/collapse-all, status colors, and type cue; note the new `StratConOpForUnit.unitType`. Commit.

---

## Notes
- Single-file panel change → not parallelized.
- Type tag is purely cosmetic; masked (`???`) units never show a tag (fog-of-war preserved).
- Status colors are HTML-span based (the panel already uses HTML labels), keeping tests assertable on the rendered string.
