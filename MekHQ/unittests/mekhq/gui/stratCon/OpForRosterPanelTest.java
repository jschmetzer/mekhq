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

import megamek.common.units.UnitType;

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
     * Recursively collects the text of all <em>visible</em> {@link JLabel} components
     * in the given container hierarchy. Invisible containers (collapsed bodies) are
     * skipped so that fog-of-war and collapse-state tests correctly reflect what is
     * actually rendered to the user.
     *
     * @param container root container to walk
     * @return list of all visible label texts (including HTML strings); never null
     */
    private static List<String> collectLabelTexts(final Container container) {
        List<String> texts = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
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
     * A FULL_INTEL unit line should display the pilot's experience as gunnery/piloting,
     * e.g. {@code (G4/P5)}.
     */
    @Test
    void testFullIntelShowsPilotExperience() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForUnit unit = buildUnit(null, "Jia Wei", "Locust", "LCT-1V", false, Status.READY);
        unit.setGunnery(4);
        unit.setPiloting(5);
        buildFormation("Recon Lance", IntelLevel.FULL_INTEL, List.of(unit), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        boolean hasExperience = labels.stream().anyMatch(t -> t.contains("G4/P5"));

        assertTrue(hasExperience,
                "FULL_INTEL unit line should show pilot experience as gunnery/piloting (G4/P5)");
    }

    /**
     * A militia formation at OBSERVED intel should have "Planetary Militia" appended
     * to its header, while a non-militia formation at the same intel level must not
     * show that tag.
     */
    @Test
    void testMilitiaFormationShowsMilitiaTag() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit unit = buildUnit(null, "Sven Larsson",
                "Vedette", "VDT-1R", true, Status.READY);
        StratConOpForFormation formation = buildFormation("Local Guard",
                IntelLevel.OBSERVED, List.of(unit), roster);
        formation.setMilitia(true);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        boolean hasMilitiaTag = labels.stream().anyMatch(t -> t.contains("Planetary Militia"));

        assertTrue(hasMilitiaTag,
                "Militia formation header must include 'Planetary Militia' tag; labels were: " + labels);
    }

    /**
     * A non-militia formation must NOT show the "Planetary Militia" tag.
     */
    @Test
    void testNonMilitiaFormationLacksTag() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit unit = buildUnit(null, "Hans Richter",
                "Warhammer", "WHM-6R", true, Status.READY);
        buildFormation("Line Alpha", IntelLevel.OBSERVED, List.of(unit), roster);
        // militia flag stays false (default)

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        boolean hasMilitiaTag = labels.stream().anyMatch(t -> t.contains("Planetary Militia"));

        assertFalse(hasMilitiaTag,
                "Non-militia formation header must NOT include 'Planetary Militia' tag");
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

    // -------------------------------------------------------------------------
    // New tests for Task 2 — summary header, militia grouping, collapse state,
    // status colors, unit-type tag
    // -------------------------------------------------------------------------

    /**
     * Summary header shows remaining/total line (non-militia) formation count.
     * With N line formations and one destroyed, remaining == N-1.
     */
    @Test
    void summaryHeader_showsLineFormationCount() {
        StratConOpForRoster roster = new StratConOpForRoster();

        // Living formation
        StratConOpForUnit u1 = buildUnit(null, "Pilot One", "Atlas", "AS7-D", true, Status.READY);
        buildFormation("Alpha Lance", IntelLevel.FULL_INTEL, List.of(u1), roster);

        // Destroyed formation (all units terminal)
        StratConOpForUnit u2 = buildUnit(null, "Pilot Two", "Hunchback", "HBK-4G", true, Status.DESTROYED);
        buildFormation("Beta Lance", IntelLevel.FULL_INTEL, List.of(u2), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        // Expect header text like "Line OpFor: 1 / 2 formations"
        boolean hasSummary = labels.stream().anyMatch(t -> t.contains("1") && t.contains("2")
                && (t.contains("Line") || t.contains("OpFor")));

        assertTrue(hasSummary,
                "Summary header must show remaining/total line formation count; labels: " + labels);
    }

    /**
     * Summary header shows militia count when militia formations are present;
     * it does NOT appear when there are no militia formations.
     */
    @Test
    void summaryHeader_showsMilitiaCountWhenPresent() {
        // Roster WITH militia
        StratConOpForRoster rosterWith = new StratConOpForRoster();
        StratConOpForUnit lineUnit = buildUnit(null, "Line Pilot", "Atlas", "AS7-D", true, Status.READY);
        buildFormation("Line Lance", IntelLevel.FULL_INTEL, List.of(lineUnit), rosterWith);

        StratConOpForUnit milUnit = buildUnit(null, "Militia Pilot", "Vedette", "VDT-1R", true, Status.READY);
        StratConOpForFormation milFormation = buildFormation("Militia Guard",
                IntelLevel.FULL_INTEL, List.of(milUnit), rosterWith);
        milFormation.setMilitia(true);

        OpForRosterPanel panelWith = new OpForRosterPanel(() -> rosterWith);
        panelWith.refresh();

        List<String> labelsWith = collectLabelTexts(panelWith);
        boolean hasMilitiaSection = labelsWith.stream()
                .anyMatch(t -> t.contains("Militia") && t.contains("active"));
        assertTrue(hasMilitiaSection,
                "Summary must mention militia count when militia exist; labels: " + labelsWith);

        // Roster WITHOUT militia
        StratConOpForRoster rosterWithout = new StratConOpForRoster();
        StratConOpForUnit lineUnit2 = buildUnit(null, "Pilot B", "Atlas", "AS7-D", true, Status.READY);
        buildFormation("Solo Lance", IntelLevel.FULL_INTEL, List.of(lineUnit2), rosterWithout);

        OpForRosterPanel panelWithout = new OpForRosterPanel(() -> rosterWithout);
        panelWithout.refresh();

        List<String> labelsWithout = collectLabelTexts(panelWithout);
        boolean hasNoMilitiaSection = labelsWithout.stream()
                .noneMatch(t -> t.contains("Militia") && t.contains("active"));
        assertTrue(hasNoMilitiaSection,
                "Summary must NOT mention militia when none exist; labels: " + labelsWithout);
    }

    /**
     * Militia formations render under a "Planetary Militia" subheader, and the
     * old inline {@code [Planetary Militia]} tag in the formation header is gone.
     */
    @Test
    void militiaFormations_renderUnderMilitiaSubheader() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit lineUnit = buildUnit(null, "Line Pilot", "Atlas", "AS7-D", true, Status.READY);
        buildFormation("Line Lance", IntelLevel.FULL_INTEL, List.of(lineUnit), roster);

        StratConOpForUnit milUnit = buildUnit(null, "Mil Pilot", "Vedette", "VDT-1R", true, Status.READY);
        StratConOpForFormation milFormation = buildFormation("Militia Guard",
                IntelLevel.FULL_INTEL, List.of(milUnit), roster);
        milFormation.setMilitia(true);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);

        // Subheader must appear
        boolean hasSubheader = labels.stream().anyMatch(t -> t.contains("Planetary Militia"));
        assertTrue(hasSubheader,
                "Planetary Militia subheader must appear in the track; labels: " + labels);

        // Old inline tag format "  [Planetary Militia]" inside the formation header must be gone.
        // The militia formation header should NOT contain the bracketed tag — it's a standalone subheader now.
        boolean hasInlineTag = labels.stream().anyMatch(
                t -> t.contains("Militia Guard") && t.contains("[Planetary Militia]"));
        assertFalse(hasInlineTag,
                "Formation header must NOT contain the old inline [Planetary Militia] tag; labels: " + labels);
    }

    /**
     * Collapse state survives a {@code refresh()} call — a formation collapsed via
     * the panel's state API must remain collapsed after the panel is rebuilt.
     */
    @Test
    void collapseState_survivesRefresh() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForUnit unit = buildUnit(null, "Test Pilot", "Atlas", "AS7-D", false, Status.READY);
        StratConOpForFormation formation = buildFormation("Alpha Lance",
                IntelLevel.FULL_INTEL, List.of(unit), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        // Confirm the pilot is visible initially
        List<String> beforeLabels = collectLabelTexts(panel);
        assertTrue(beforeLabels.stream().anyMatch(t -> t.contains("Test Pilot")),
                "Pilot must be visible before collapse; labels: " + beforeLabels);

        // Collapse the formation via the panel's own state API, then refresh
        panel.setCollapseState(OpForRosterPanel.formationKey(formation.getId()), false);
        panel.refresh();

        List<String> afterLabels = collectLabelTexts(panel);
        // The unit's pilot name should NOT appear because the formation body is collapsed
        boolean pilotVisible = afterLabels.stream().anyMatch(t -> t.contains("Test Pilot"));
        assertFalse(pilotVisible,
                "After collapse + refresh, collapsed body must remain hidden; labels: " + afterLabels);
    }

    /**
     * Status colors: SALVAGED shows goldenrod hex, CAPTURED shows blue hex,
     * DESTROYED shows red (existing behavior, but now via statusColorHex helper).
     */
    @Test
    void terminalUnit_usesStatusColor() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit salvaged = buildUnit(null, "Sal Pilot", "Atlas", "AS7-D", true, Status.SALVAGED);
        StratConOpForUnit captured = buildUnit(null, "Cap Pilot", "Hunchback", "HBK-4G", true, Status.CAPTURED);
        StratConOpForUnit destroyed = buildUnit(null, "Des Pilot", "Locust", "LCT-1V", true, Status.DESTROYED);
        buildFormation("Mixed Lance", IntelLevel.FULL_INTEL, List.of(salvaged, captured, destroyed), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        String allText = String.join(" ", labels);

        assertTrue(allText.contains("#B8860B"),
                "SALVAGED unit must use goldenrod color #B8860B; labels: " + labels);
        assertTrue(allText.contains("#1E6FBA"),
                "CAPTURED unit must use blue color #1E6FBA; labels: " + labels);
        assertTrue(allText.toLowerCase().contains("red") || allText.contains("#FF0000")
                || allText.contains("color='red'") || allText.contains("color=\"red\""),
                "DESTROYED unit must use red; labels: " + labels);
    }

    /**
     * Visible (non-masked) units show a unit-type tag based on their {@code unitType} field.
     * MEK → [M], TANK → [V], INFANTRY → [I]. Masked units must NOT show a tag.
     */
    @Test
    void unitLine_showsTypeTag() {
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit mek = buildUnit(null, "Mek Pilot", "Atlas", "AS7-D", true, Status.READY);
        mek.setUnitType(UnitType.MEK);

        StratConOpForUnit tank = buildUnit(null, "Tank Pilot", "Vedette", "VDT-1R", true, Status.READY);
        tank.setUnitType(UnitType.TANK);

        StratConOpForUnit infantry = buildUnit(null, "Infantry Pilot", "Warrior H", "WHE-H", true, Status.READY);
        infantry.setUnitType(UnitType.INFANTRY);

        StratConOpForUnit masked = buildUnit(null, "Unknown Pilot", "Locust", "LCT-1V", false, Status.READY);
        masked.setUnitType(UnitType.MEK);

        buildFormation("Type Lance", IntelLevel.OBSERVED, List.of(mek, tank, infantry, masked), roster);

        OpForRosterPanel panel = new OpForRosterPanel(() -> roster);
        panel.refresh();

        List<String> labels = collectLabelTexts(panel);
        String allText = String.join(" ", labels);

        assertTrue(allText.contains("[M]"),
                "MEK unit must show [M] tag; labels: " + labels);
        assertTrue(allText.contains("[V]"),
                "TANK unit must show [V] tag; labels: " + labels);
        assertTrue(allText.contains("[I]"),
                "INFANTRY unit must show [I] tag; labels: " + labels);

        // Masked unit should show "???" but NOT "[M]" (the masked entry)
        // Since we have one revealed MEK, [M] appears once; but "Unknown Pilot" must not appear
        boolean hasUnknownPilotWithTag = labels.stream()
                .anyMatch(t -> t.contains("Unknown Pilot") && t.contains("[M]"));
        assertFalse(hasUnknownPilotWithTag,
                "Masked unit must not show type tag; labels: " + labels);
    }

}
