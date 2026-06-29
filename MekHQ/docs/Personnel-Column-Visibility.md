# Personnel Tab — Per-View Column Visibility

Developer notes for the right-click "show/hide columns" feature on the Personnel tab, plus the
related restoration of the **XP** column and the constructor-ordering fixes it surfaced.

## Overview

The Personnel table is **view-based**: the "Personnel View" dropdown
(`PersonnelTabView` — `GENERAL`, `COMBAT`, `BIOGRAPHICAL`, …) selects a curated set of columns, and
`PersonnelTab.changePersonnelView()` force-applies that set to the table's `XTableColumnModel` on
every view switch.

This feature lets the player **right-click the table header** to toggle individual columns on/off
**within the current view**. Choices are remembered **per view** and persist across sessions. A
"Reset to default columns" item restores the view's built-in set.

The `XP` column was dropped from the `GENERAL` view by an upstream view-switching refactor; it is
restored as the baseline default in `PersonnelTabView.GENERAL`.

## Design: per-view, subtractive override

The override is **subtractive** — for each view we persist only the columns the user has *hidden*
relative to that view's default candidate set. Effective visible set =
`view.getVisibleColumns(options)` **minus** `hiddenByView[view]`.

Storing the hidden delta (rather than an absolute visible set) is deliberate: option-gated columns
that become available later (e.g. `EDGE` once Edge is enabled) still appear unless explicitly
hidden, and a future addition to a view's defaults is not masked by a stale saved set. The popup
only offers the **current view's** candidate columns, so views stay curated — you trim a view, you
don't graft arbitrary columns into it. (A "show any column in any view" variant was considered and
rejected during design.)

## Components

### `PersonnelColumnVisibility` (`gui/model/PersonnelColumnVisibility.java`)
Pure model, no Swing dependency — fully unit-testable.
- `Map<PersonnelTabView, EnumSet<PersonnelTableModelColumn>> hiddenByView`.
- `setHidden(view, column, hidden)` / `resetView(view)` / `getHiddenColumns(view)`.
- `filterHidden(view, base)` — returns `base` minus the view's hidden set. **Never returns empty for
  a non-empty `base`**: if the override would hide everything, the lowest-ordinal candidate is kept
  (the popup also disables the last visible toggle, so this guard is a backstop).
- `serialize()` / `deserialize(String)` — format `VIEW=COL,COL;VIEW=COL`; views with no hidden
  columns are omitted (empty instance → `""`). **Fail-safe parsing**: unknown view/column names and
  malformed entries are skipped with a logged warning, never thrown — a save written by a different
  MekHQ version degrades gracefully (the `PersonalityQuirk.fromString` pattern, not
  `AcademyType.parseFromString`).

### `PersonnelTab` (`gui/PersonnelTab.java`)
- Holds a `PersonnelColumnVisibility columnVisibility`, loaded from `MHQOptions`.
- **`changePersonnelView()`** is the single choke point: after computing the view's candidate set
  (via the shared `candidateColumns(view)` helper, which also applies the FLUFF view's
  group-by-unit surname handling), it layers the override:
  `visibleColumns = columnVisibility.filterHidden(view, candidateColumns(view))`.
- **`maybeShowColumnVisibilityPopup(MouseEvent)`** — a header `MouseListener` firing on
  `isPopupTrigger()` (so left-click sorting, owned by `JTablePreference`, is unaffected). Builds a
  `JPopupMenu` of `JCheckBoxMenuItem`s for the current view's candidate columns (checked = currently
  visible), disables the last visible item, and adds a separator + "Reset to default columns". On
  toggle/reset it updates `columnVisibility`, persists via
  `MHQOptions.setPersonnelColumnVisibility(serialize())`, and calls `changePersonnelView()` to
  re-apply live.

### Persistence (`MHQOptions` / `MHQConstants`)
A single `String` preference `personnelColumnVisibility` under the existing `DISPLAY_NODE`
(`get/set` mirror the `displayDateFormat` idiom). The serialized hidden-set map round-trips through
it.

### Resource
`CampaignGUI.properties` → `personnelColumnVisibilityPopup.reset.text` ("Reset to default columns").

## Constructor-ordering fix (load-bearing)

`columnVisibility` is assigned **inside `initTab()`**, not via a field initializer. `CampaignGuiTab`'s
constructor calls the overridable `initTab()` *before* subclass field initializers run, so a
`private final … = …` initializer would still be null when `initTab()` → `changePersonnelView()`
executes — which crashed the campaign to a blank screen during development. The regression-vector
sweep that found this also fixed the same bug class in two sibling tabs:
- **`StratConTab.listModel`** — a field initializer was clobbering the model `initTab()` builds.
- **`RepairTab.selectedTech`** — a `getSelectedTech()` field initializer ran against an unpopulated
  table.

Rule of thumb: any field a `CampaignGuiTab` subclass reads during `initTab()` must be assigned
inside `initTab()`, never via a field initializer.

## Tests
- `unittests/mekhq/gui/model/PersonnelColumnVisibilityTest` — `filterHidden` subtraction + never-empty
  guard, per-view scoping, reset, serialize/deserialize round-trip, and the fail-safe parse of
  garbage/unknown tokens.
- `unittests/mekhq/gui/enums/PersonnelTabViewTest` — regression guard that `GENERAL` includes `XP`.

The header popup is Swing glue with no isolated unit-test seam; verify manually via
`./gradlew :MekHQ:run` (right-click the Personnel header, toggle a column, switch views and back,
restart to confirm persistence).
