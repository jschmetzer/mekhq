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
package mekhq.campaign.stratCon.opfor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import megamek.common.enums.SkillLevel;
import megamek.common.units.EntityWeightClass;
import megamek.common.units.UnitType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OpForRosterEditOps} — the GM-mode roster mutation and
 * validation logic — plus the roster invariant primitives it relies on
 * ({@code removeUnit}, {@code removeFormation}, and index rebuild on
 * {@code setUnitList}).
 */
class OpForRosterEditOpsTest {

    private static UnitTemplate template() {
        return new UnitTemplate("Atlas", "AS7-D", "DC");
    }

    private static StratConOpForFormation addLance(final StratConOpForRoster roster) {
        return OpForRosterEditOps.addFormation(roster, "First Lance",
                EntityWeightClass.WEIGHT_MEDIUM, 3, SkillLevel.REGULAR, "Alpha", false);
    }

    // ---- addFormation -------------------------------------------------------

    @Test
    void addFormationRegistersFormationWithFields() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForFormation formation = addLance(roster);

        assertNotNull(formation.getId(), "formation should be assigned an id");
        assertEquals(1, roster.getFormations().size());
        assertSame(formation, roster.getFormations().get(0));
        assertEquals("First Lance", formation.getName());
        assertEquals(EntityWeightClass.WEIGHT_MEDIUM, formation.getWeightClass());
        assertEquals(3, formation.getUnitQuality());
        assertEquals(SkillLevel.REGULAR, formation.getSkillLevel());
        assertEquals("Alpha", formation.getAssignedTrackName());
        assertFalse(formation.isMilitia());
    }

    // ---- addUnit ------------------------------------------------------------

    @Test
    void addUnitLinksUnitToRosterAndFormation() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation formation = addLance(roster);

        StratConOpForUnit unit = OpForRosterEditOps.addUnit(roster, formation.getId(),
                template(), UnitType.MEK, "Jane Doe", 4, 5);

        assertNotNull(unit.getId());
        assertEquals(formation.getId(), unit.getFormationId(), "unit should know its formation");
        assertTrue(formation.getUnitIds().contains(unit.getId()),
                "formation should list the new unit's id");
        assertSame(unit, roster.getUnit(unit.getId()), "unit must be reachable via the id index");
        assertEquals(UnitType.MEK, unit.getUnitType());
        assertEquals("Jane Doe", unit.getPilotName());
    }

    @Test
    void addUnitToUnknownFormationThrows() {
        StratConOpForRoster roster = new StratConOpForRoster();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> OpForRosterEditOps.addUnit(roster, UUID.randomUUID(),
                        template(), UnitType.MEK, "Nobody", 4, 5));
    }

    // ---- removeUnit ---------------------------------------------------------

    @Test
    void removeUnitClearsListIndexAndFormationLink() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation formation = addLance(roster);
        StratConOpForUnit a = OpForRosterEditOps.addUnit(roster, formation.getId(),
                template(), UnitType.MEK, "A", 4, 5);
        StratConOpForUnit b = OpForRosterEditOps.addUnit(roster, formation.getId(),
                template(), UnitType.MEK, "B", 4, 5);

        OpForRosterEditOps.removeUnit(roster, a.getId());

        assertNull(roster.getUnit(a.getId()), "removed unit must drop out of the index");
        assertFalse(roster.getUnitList().contains(a), "removed unit must drop out of the list");
        assertFalse(formation.getUnitIds().contains(a.getId()),
                "formation must no longer reference the removed unit");
        assertTrue(formation.getUnitIds().contains(b.getId()), "sibling unit must remain");
        assertSame(b, roster.getUnit(b.getId()));
    }

    // ---- deleteFormation cascades ------------------------------------------

    @Test
    void deleteFormationCascadesToItsUnits() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation keep = addLance(roster);
        StratConOpForFormation drop = OpForRosterEditOps.addFormation(roster, "Second Lance",
                EntityWeightClass.WEIGHT_HEAVY, 2, SkillLevel.GREEN, "Alpha", false);
        StratConOpForUnit keptUnit = OpForRosterEditOps.addUnit(roster, keep.getId(),
                template(), UnitType.MEK, "Keep", 4, 5);
        StratConOpForUnit doomedUnit = OpForRosterEditOps.addUnit(roster, drop.getId(),
                template(), UnitType.MEK, "Doomed", 4, 5);

        OpForRosterEditOps.deleteFormation(roster, drop.getId());

        assertFalse(roster.getFormations().contains(drop), "formation must be gone");
        assertNull(roster.getUnit(doomedUnit.getId()), "its units must be gone from the index");
        assertFalse(roster.getUnitList().contains(doomedUnit), "its units must be gone from the list");
        assertSame(keptUnit, roster.getUnit(keptUnit.getId()), "other formations untouched");
        assertEquals(1, roster.getFormations().size());
    }

    // ---- reassignUnit -------------------------------------------------------

    @Test
    void reassignUnitMovesItBetweenFormations() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation from = addLance(roster);
        StratConOpForFormation to = OpForRosterEditOps.addFormation(roster, "Second Lance",
                EntityWeightClass.WEIGHT_HEAVY, 2, SkillLevel.GREEN, "Alpha", false);
        StratConOpForUnit unit = OpForRosterEditOps.addUnit(roster, from.getId(),
                template(), UnitType.MEK, "Mover", 4, 5);

        OpForRosterEditOps.reassignUnit(roster, unit.getId(), to.getId());

        assertFalse(from.getUnitIds().contains(unit.getId()), "old formation drops the unit");
        assertTrue(to.getUnitIds().contains(unit.getId()), "new formation gains the unit");
        assertEquals(to.getId(), unit.getFormationId(), "unit points at its new formation");
    }

    // ---- validation ---------------------------------------------------------

    @Test
    void validateFormationFlagsBlankNameAndBadQuality() {
        assertTrue(OpForRosterEditOps.validateFormation("  ", 3, SkillLevel.REGULAR).size() >= 1,
                "blank name should produce an error");
        assertFalse(OpForRosterEditOps.validateFormation("OK", 6, SkillLevel.REGULAR).isEmpty(),
                "quality above max should produce an error");
        assertFalse(OpForRosterEditOps.validateFormation("OK", -1, SkillLevel.REGULAR).isEmpty(),
                "quality below min should produce an error");
        assertFalse(OpForRosterEditOps.validateFormation("OK", 3, SkillLevel.NONE).isEmpty(),
                "SkillLevel.NONE is not a valid combat skill and should produce an error");
        assertTrue(OpForRosterEditOps.validateFormation("OK", 3, SkillLevel.REGULAR).isEmpty(),
                "a valid formation should produce no errors");
    }

    @Test
    void validateUnitFlagsBadSkillsAndMissingTemplate() {
        assertFalse(OpForRosterEditOps.validateUnit("Pilot", 9, 5, template()).isEmpty(),
                "gunnery above max should produce an error");
        assertFalse(OpForRosterEditOps.validateUnit("Pilot", 4, -1, template()).isEmpty(),
                "piloting below min should produce an error");
        assertFalse(OpForRosterEditOps.validateUnit("Pilot", 4, 5, null).isEmpty(),
                "missing unit template should produce an error");
        assertFalse(OpForRosterEditOps.validateUnit("Pilot", 4, 5,
                new UnitTemplate("  ", "", "DC")).isEmpty(),
                "blank chassis should produce an error");
        assertTrue(OpForRosterEditOps.validateUnit("Pilot", 4, 5, template()).isEmpty(),
                "a valid unit should produce no errors");
    }

    // ---- index-rebuild on setUnitList (apply-on-OK path) --------------------

    @Test
    void setUnitListRebuildsTheIdIndex() {
        StratConOpForRoster source = new StratConOpForRoster();
        StratConOpForFormation formation = addLance(source);
        StratConOpForUnit unit = OpForRosterEditOps.addUnit(source, formation.getId(),
                template(), UnitType.MEK, "Indexed", 4, 5);

        // Simulate the apply-on-OK swap: a fresh roster receives the edited contents.
        StratConOpForRoster live = new StratConOpForRoster();
        live.setUnitList(new java.util.ArrayList<>(source.getUnitList()));
        live.setFormations(new java.util.ArrayList<>(source.getFormations()));

        assertSame(unit, live.getUnit(unit.getId()),
                "getUnit must resolve after setUnitList rebuilds the index");
    }

    // ---- copy() independence (Cancel-safe editing) --------------------------

    @Test
    void copyProducesAnIndependentRoster() {
        StratConOpForRoster original = new StratConOpForRoster();
        StratConOpForFormation formation = addLance(original);
        StratConOpForUnit unit = OpForRosterEditOps.addUnit(original, formation.getId(),
                template(), UnitType.MEK, "Original", 4, 5);

        StratConOpForRoster copy = original.copy();

        assertEquals(1, copy.getUnitList().size(), "copy should carry the same unit count");
        assertNotNull(copy.getUnit(unit.getId()), "copy must rebuild its own id index");

        // Mutating the copy must not touch the original.
        OpForRosterEditOps.removeUnit(copy, unit.getId());
        assertNull(copy.getUnit(unit.getId()), "unit removed from copy");
        assertNotNull(original.getUnit(unit.getId()), "original must be untouched by copy edits");
        assertEquals(1, formation.getUnitIds().size(),
                "original formation membership must be untouched");
    }
}
