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

import static mekhq.gui.enums.PersonnelTableModelColumn.DEPLOYED;
import static mekhq.gui.enums.PersonnelTableModelColumn.RANK;
import static mekhq.gui.enums.PersonnelTableModelColumn.STRATEGY;
import static mekhq.gui.enums.PersonnelTableModelColumn.XP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import mekhq.gui.enums.PersonnelTabView;
import mekhq.gui.enums.PersonnelTableModelColumn;
import org.junit.jupiter.api.Test;

class PersonnelColumnVisibilityTest {
    @Test
    void filterHiddenSubtractsHiddenColumns() {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        visibility.setHidden(PersonnelTabView.GENERAL, XP, true);

        Set<PersonnelTableModelColumn> result =
              visibility.filterHidden(PersonnelTabView.GENERAL, Set.of(RANK, XP, DEPLOYED));

        assertEquals(Set.of(RANK, DEPLOYED), result);
    }

    @Test
    void filterHiddenIsScopedPerView() {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        visibility.setHidden(PersonnelTabView.GENERAL, XP, true);

        // COMBAT did not hide XP, so it remains visible there.
        Set<PersonnelTableModelColumn> result =
              visibility.filterHidden(PersonnelTabView.COMBAT, Set.of(RANK, XP));

        assertTrue(result.contains(XP));
    }

    @Test
    void filterHiddenNeverReturnsEmptyForNonEmptyBase() {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        visibility.setHidden(PersonnelTabView.GENERAL, XP, true);

        Set<PersonnelTableModelColumn> result = visibility.filterHidden(PersonnelTabView.GENERAL, Set.of(XP));

        assertFalse(result.isEmpty(), "Hiding the last remaining column must be prevented");
    }

    @Test
    void filterHiddenSafetyNetClearsResurrectedColumnFromHiddenState() {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        // The user hides every column that is currently a candidate in this view (a third, option-gated column had
        // been keeping the view non-empty)...
        visibility.setHidden(PersonnelTabView.GENERAL, RANK, true);
        visibility.setHidden(PersonnelTabView.GENERAL, XP, true);

        // ...then that gated column drops out of the candidate set, so every remaining candidate is hidden and the
        // safety net must resurrect one to avoid an empty table.
        Set<PersonnelTableModelColumn> shown = visibility.filterHidden(PersonnelTabView.GENERAL, Set.of(RANK, XP));

        assertEquals(1, shown.size(), "safety net should show exactly one column");
        PersonnelTableModelColumn resurrected = shown.iterator().next();

        // The resurrected column is actually displayed, so it must not still be recorded as hidden; otherwise the
        // persisted preference silently disagrees with the display.
        assertFalse(visibility.getHiddenColumns(PersonnelTabView.GENERAL).contains(resurrected),
              "resurrected column must be cleared from the hidden set to keep persisted state consistent");

        // And once the gated column returns, the resurrected column stays visible rather than silently disappearing.
        assertTrue(visibility.filterHidden(PersonnelTabView.GENERAL, Set.of(RANK, XP, DEPLOYED)).contains(resurrected),
              "resurrected column must remain visible after the candidate set grows again");
    }

    @Test
    void setHiddenFalseUnhidesColumn() {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        visibility.setHidden(PersonnelTabView.GENERAL, XP, true);
        visibility.setHidden(PersonnelTabView.GENERAL, XP, false);

        assertTrue(visibility.filterHidden(PersonnelTabView.GENERAL, Set.of(RANK, XP)).contains(XP));
    }

    @Test
    void resetViewClearsOverridesForThatViewOnly() {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        visibility.setHidden(PersonnelTabView.GENERAL, XP, true);
        visibility.setHidden(PersonnelTabView.COMBAT, STRATEGY, true);

        visibility.resetView(PersonnelTabView.GENERAL);

        assertTrue(visibility.filterHidden(PersonnelTabView.GENERAL, Set.of(RANK, XP)).contains(XP));
        assertFalse(visibility.filterHidden(PersonnelTabView.COMBAT, Set.of(RANK, STRATEGY)).contains(STRATEGY));
    }

    @Test
    void serializeDeserializeRoundTrips() {
        PersonnelColumnVisibility visibility = new PersonnelColumnVisibility();
        visibility.setHidden(PersonnelTabView.GENERAL, XP, true);
        visibility.setHidden(PersonnelTabView.GENERAL, DEPLOYED, true);
        visibility.setHidden(PersonnelTabView.COMBAT, STRATEGY, true);

        PersonnelColumnVisibility restored = PersonnelColumnVisibility.deserialize(visibility.serialize());

        assertEquals(Set.of(XP, DEPLOYED), restored.getHiddenColumns(PersonnelTabView.GENERAL));
        assertEquals(Set.of(STRATEGY), restored.getHiddenColumns(PersonnelTabView.COMBAT));
    }

    @Test
    void emptyVisibilitySerializesToEmptyString() {
        assertEquals("", new PersonnelColumnVisibility().serialize());
    }

    @Test
    void deserializeOfBlankYieldsNoOverrides() {
        PersonnelColumnVisibility restored = PersonnelColumnVisibility.deserialize("   ");

        assertTrue(restored.getHiddenColumns(PersonnelTabView.GENERAL).isEmpty());
    }

    @Test
    void deserializeSkipsUnknownTokensWithoutThrowing() {
        // Unknown view name, unknown column name, and a malformed entry must all be skipped,
        // while the valid GENERAL=XP,DEPLOYED override survives.
        String serialized = "GENERAL=XP,NOT_A_COLUMN,DEPLOYED;BOGUS_VIEW=RANK;malformed-entry";

        PersonnelColumnVisibility restored = PersonnelColumnVisibility.deserialize(serialized);

        assertEquals(Set.of(XP, DEPLOYED), restored.getHiddenColumns(PersonnelTabView.GENERAL));
    }
}
