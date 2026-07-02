/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.gui.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import megamek.logging.MMLogger;
import mekhq.gui.enums.PersonnelTabView;
import mekhq.gui.enums.PersonnelTableModelColumn;

/**
 * Tracks, per {@link PersonnelTabView}, the set of personnel-table columns the user has explicitly hidden relative to
 * that view's built-in default column set.
 *
 * <p>The override is <em>subtractive</em>: only hidden columns are stored. A view's effective visible set is its
 * default set (as returned by {@link PersonnelTabView#getVisibleColumns}) minus the columns recorded here. Storing the
 * hidden delta rather than an absolute visible set means option-gated columns that become available later (e.g. EDGE
 * once Edge is enabled) still appear unless the user explicitly hides them, and future additions to a view's defaults
 * are not masked by a stale saved set.</p>
 *
 * <p>This class is pure model state with no Swing dependencies; the header popup and {@code changePersonnelView} wire
 * it into the UI.</p>
 */
public class PersonnelColumnVisibility {
    private static final MMLogger LOGGER = MMLogger.create(PersonnelColumnVisibility.class);

    private static final String VIEW_SEPARATOR = ";";
    private static final String VIEW_COLUMNS_SEPARATOR = "=";
    private static final String COLUMN_SEPARATOR = ",";

    private final Map<PersonnelTabView, EnumSet<PersonnelTableModelColumn>> hiddenByView =
          new EnumMap<>(PersonnelTabView.class);

    /**
     * Records or clears the hidden state of a single column within a single view.
     *
     * @param view   the view the override applies to
     * @param column the column to hide or show
     * @param hidden {@code true} to hide the column in this view, {@code false} to show it
     */
    public void setHidden(PersonnelTabView view, PersonnelTableModelColumn column, boolean hidden) {
        if (hidden) {
            hiddenByView.computeIfAbsent(view, key -> EnumSet.noneOf(PersonnelTableModelColumn.class)).add(column);
        } else {
            EnumSet<PersonnelTableModelColumn> hidden0 = hiddenByView.get(view);
            if (hidden0 != null) {
                hidden0.remove(column);
                if (hidden0.isEmpty()) {
                    hiddenByView.remove(view);
                }
            }
        }
    }

    /**
     * Clears every column override for a single view, restoring it to its built-in default column set.
     *
     * @param view the view to reset
     */
    public void resetView(PersonnelTabView view) {
        hiddenByView.remove(view);
    }

    /**
     * @param view the view to query
     *
     * @return a copy of the columns the user has hidden in the given view; empty if the view has no overrides
     */
    public Set<PersonnelTableModelColumn> getHiddenColumns(PersonnelTabView view) {
        EnumSet<PersonnelTableModelColumn> hidden = hiddenByView.get(view);
        return (hidden == null) ? EnumSet.noneOf(PersonnelTableModelColumn.class) : EnumSet.copyOf(hidden);
    }

    /**
     * Subtracts the user's hidden columns for a view from a base set of candidate columns.
     *
     * <p>As a safety net, this never returns an empty set for a non-empty {@code base}: if the overrides would hide
     * every candidate column, the lowest-ordinal column of {@code base} is retained. The header popup additionally
     * prevents hiding the last visible column, so this guard should not normally be reached.</p>
     *
     * @param view the view whose overrides to apply
     * @param base the view's candidate columns (already adjusted for campaign options)
     *
     * @return the columns that should remain visible
     */
    public Set<PersonnelTableModelColumn> filterHidden(PersonnelTabView view, Set<PersonnelTableModelColumn> base) {
        EnumSet<PersonnelTableModelColumn> hidden = hiddenByView.get(view);
        if ((hidden == null) || base.isEmpty()) {
            return base;
        }

        EnumSet<PersonnelTableModelColumn> visible = EnumSet.copyOf(base);
        visible.removeAll(hidden);

        if (visible.isEmpty()) {
            // Never let an override hide every candidate column. Resurrect the lowest-ordinal candidate and clear its
            // hidden flag, so the stored overrides stay consistent with what is actually shown; otherwise the column
            // reads as hidden in saved state yet displays, and would silently vanish once the candidate set grows
            // again (e.g. an option-gated column returning).
            PersonnelTableModelColumn resurrected = EnumSet.copyOf(base).iterator().next();
            visible.add(resurrected);
            setHidden(view, resurrected, false);
        }
        return visible;
    }

    /**
     * Serializes all overrides to a single string suitable for persistence. Views with no hidden columns are omitted;
     * an instance with no overrides serializes to the empty string. Format: {@code VIEW=COL,COL;VIEW=COL}.
     *
     * @return the serialized override string
     */
    public String serialize() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<PersonnelTabView, EnumSet<PersonnelTableModelColumn>> entry : hiddenByView.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(VIEW_SEPARATOR);
            }
            builder.append(entry.getKey().name()).append(VIEW_COLUMNS_SEPARATOR);
            boolean first = true;
            for (PersonnelTableModelColumn column : entry.getValue()) {
                if (!first) {
                    builder.append(COLUMN_SEPARATOR);
                }
                builder.append(column.name());
                first = false;
            }
        }
        return builder.toString();
    }

    /**
     * Reconstructs overrides from a string produced by {@link #serialize()}. Parsing is fail-safe: unknown view names,
     * unknown column names, and malformed entries are skipped with a logged warning rather than throwing, so a save
     * written by a different MekHQ version degrades gracefully instead of aborting the load.
     *
     * @param serialized the serialized override string; may be {@code null} or blank
     *
     * @return a populated {@link PersonnelColumnVisibility}
     */
    public static PersonnelColumnVisibility deserialize(String serialized) {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        if ((serialized == null) || serialized.isBlank()) {
            return visibility;
        }

        for (String viewEntry : serialized.split(VIEW_SEPARATOR)) {
            if (viewEntry.isBlank()) {
                continue;
            }
            String[] parts = viewEntry.split(VIEW_COLUMNS_SEPARATOR, 2);
            if (parts.length != 2) {
                LOGGER.warn("Skipping malformed personnel column-visibility entry: {}", viewEntry);
                continue;
            }

            PersonnelTabView view;
            try {
                view = PersonnelTabView.valueOf(parts[0].trim());
            } catch (IllegalArgumentException ex) {
                LOGGER.warn("Skipping unknown personnel view in column-visibility preference: {}", parts[0]);
                continue;
            }

            for (String columnName : parts[1].split(COLUMN_SEPARATOR)) {
                if (columnName.isBlank()) {
                    continue;
                }
                try {
                    visibility.setHidden(view, PersonnelTableModelColumn.valueOf(columnName.trim()), true);
                } catch (IllegalArgumentException ex) {
                    LOGGER.warn("Skipping unknown personnel column in column-visibility preference: {}", columnName);
                }
            }
        }
        return visibility;
    }
}
