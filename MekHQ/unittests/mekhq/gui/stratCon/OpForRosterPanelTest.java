/*
 * Copyright (C) 2019-2026 The MegaMek Team. All Rights Reserved.
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
package mekhq.gui.stratCon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mekhq.campaign.stratCon.opfor.IntelLevel;
import mekhq.campaign.stratCon.opfor.Status;
import mekhq.campaign.stratCon.opfor.StratConOpForFormation;
import mekhq.campaign.stratCon.opfor.StratConOpForRoster;
import mekhq.campaign.stratCon.opfor.StratConOpForUnit;
import mekhq.campaign.stratCon.opfor.UnitTemplate;

/**
 * Tests for {@link OpForRosterPanel}, verifying that the fog-of-war rendering
 * correctly hides or reveals unit details based on {@link IntelLevel}.
 *
 * <p>Tests are skipped automatically in headless environments.</p>
 */
class OpForRosterPanelTest {

    @BeforeEach
    void skipIfHeadless() {
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Skipping Swing component test in headless environment");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Recursively collects the text of all {@link JLabel} components in the
     * given container hierarchy.
     *
     * @param container root container to walk
     * @return list of all label texts (including HTML strings); never null
     */
    private static List<String> collectLabelTexts(final Container container) {
        List<String> texts = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) {
                String text = label.getText();
                if ((text != null) && !text.isBlank()) {
                    texts.add(text);
                }
            }
            if (component instanceof Container child) {
                texts.addAll(collectLabelTexts(child));
            }
        }
        return texts;
    }

    /**
     * Builds a minimal {@link StratConOpForUnit} suitable for panel rendering tests.
     */
    private static StratConOpForUnit buildUnit(final UUID formationId,
            final String pilotName, final String chassis, final String model,
            final boolean revealed, final Status status) {
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setId(UUID.randomUUID());
        unit.setFormationId(formationId);
        unit.setPilotName(pilotName);
        unit.setProtoEntity(new UnitTemplate(chassis, model, "IS"));
        unit.setRevealed(revealed);
        unit.setStatus(status);
        return unit;
    }

    /**
     * Builds a formation with the given intel level and adds the provided units
     * (by their IDs) to the roster.
     */
    private static StratConOpForFormation buildFormation(final String name,
            final IntelLevel intelLevel, final List<StratConOpForUnit> units,
            final StratConOpForRoster roster) {
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setId(UUID.randomUUID());
        formation.setName(name);
        formation.setIntelLevel(intelLevel);
        formation.setAssignedTrackName("Track Alpha");
        for (StratConOpForUnit unit : units) {
            unit.setFormationId(formation.getId());
            formation.getUnitIds().add(unit.getId());
            roster.addUnit(unit);
        }
        roster.addFormation(formation);
        return formation;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * An UNKNOWN formation should render as "Unidentified formation — …" with no
     * pilot names or chassis visible.
     */
    @Test
    void testUnknownFormationShowsUnidentifiedLabel() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForUnit unit = buildUnit(null, "Kerensky", "Timber Wolf", "Prime", false, Status.READY);
        buildFormation("Alpha Lance", IntelLevel.UNKNOWN, List.of(unit), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        boolean hasUnidentified = labels.stream().anyMatch(t -> t.contains("Unidentified formation"));
        boolean hasPilotName = labels.stream().anyMatch(t -> t.contains("Kerensky"));

        assertTrue(hasUnidentified, "Expected 'Unidentified formation' label for UNKNOWN intel");
        assertFalse(hasPilotName, "Pilot name must not be shown for UNKNOWN intel");
    }

    /**
     * An OBSERVED formation should mask unrevealed units as "???" while showing the
     * real pilot name and chassis for any unit that has been individually revealed.
     */
    @Test
    void testObservedFormationMasksUnrevealedUnits() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit revealed = buildUnit(null, "Natasha Kerensky",
                "Warhammer", "WHM-6R", true, Status.READY);
        StratConOpForUnit unrevealed = buildUnit(null, "Unknown Pilot",
                "Atlas", "AS7-D", false, Status.READY);

        buildFormation("Beta Lance", IntelLevel.OBSERVED, List.of(revealed, unrevealed), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        boolean hasRevealedName = labels.stream().anyMatch(t -> t.contains("Natasha Kerensky"));
        boolean hasMasked = labels.stream().anyMatch(t -> t.contains("???"));
        boolean hasUnrevealedName = labels.stream().anyMatch(t -> t.contains("Unknown Pilot"));

        assertTrue(hasRevealedName, "Revealed unit's pilot name must be shown");
        assertTrue(hasMasked, "Unrevealed unit must appear as '???'");
        assertFalse(hasUnrevealedName, "Unrevealed unit's pilot name must not be shown");
    }

    /**
     * A FULL_INTEL formation must show all pilots and chassis regardless of the
     * individual unit's {@code revealed} flag.
     */
    @Test
    void testFullIntelFormationShowsAllUnits() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit unitA = buildUnit(null, "Kai Allard-Liao",
                "Mauler", "MAL-1R", false, Status.READY);
        StratConOpForUnit unitB = buildUnit(null, "Victor Steiner-Davion",
                "Daishi", "EXE-B2", false, Status.READY);

        buildFormation("Gamma Lance", IntelLevel.FULL_INTEL, List.of(unitA, unitB), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        boolean hasKai = labels.stream().anyMatch(t -> t.contains("Kai Allard-Liao"));
        boolean hasVictor = labels.stream().anyMatch(t -> t.contains("Victor Steiner-Davion"));
        boolean hasMasked = labels.stream().anyMatch(t -> t.contains("???"));

        assertTrue(hasKai, "Kai's name must be shown at FULL_INTEL even if not revealed");
        assertTrue(hasVictor, "Victor's name must be shown at FULL_INTEL even if not revealed");
        assertFalse(hasMasked, "No '???' entries expected at FULL_INTEL");
    }

    /**
     * When the supplier returns {@code null}, the panel should show a single
     * "no roster" message.
     */
    @Test
    void testNullRosterShowsNoRosterLabel() {
        OpForRosterPanel panel = new OpForRosterPanel(() -> null);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        boolean hasNoRosterMsg = labels.stream()
                .anyMatch(t -> t.contains("dynamic OpFor mode active") || t.contains("No static"));

        assertTrue(hasNoRosterMsg, "Expected 'no roster' label when supplier returns null");
    }
}
