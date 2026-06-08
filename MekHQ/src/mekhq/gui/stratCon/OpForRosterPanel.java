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

import java.awt.FlowLayout;
import java.awt.Font;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import megamek.common.units.EntityWeightClass;
import mekhq.campaign.stratCon.opfor.IntelLevel;
import mekhq.campaign.stratCon.opfor.Status;
import mekhq.campaign.stratCon.opfor.StratConOpForFormation;
import mekhq.campaign.stratCon.opfor.StratConOpForRoster;
import mekhq.campaign.stratCon.opfor.StratConOpForUnit;

/**
 * A scrollable panel that displays the static OpFor order of battle for the
 * current StratCon contract, using a fog-of-war model driven by each
 * formation's {@link IntelLevel}.
 *
 * <p>The panel re-renders itself lazily: call {@link #refresh()} after any
 * roster change to pull fresh data from the supplier.</p>
 */
public class OpForRosterPanel extends JPanel {

    private static final String RESOURCE_BUNDLE_NAME = "mekhq/resources/AtBStratCon";

    private final Supplier<StratConOpForRoster> rosterSupplier;
    private final ResourceBundle resources;

    /**
     * Creates the panel.
     *
     * @param rosterSupplier provides the current {@link StratConOpForRoster},
     *                       or {@code null} if none is active; called only on
     *                       each explicit {@link #refresh()}
     */
    public OpForRosterPanel(final Supplier<StratConOpForRoster> rosterSupplier) {
        this.rosterSupplier = rosterSupplier;
        this.resources = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /**
     * Clears and rebuilds all child components from the current roster snapshot.
     */
    public void refresh() {
        removeAll();

        StratConOpForRoster roster = rosterSupplier.get();
        if (roster == null) {
            add(new JLabel(resources.getString("opForRosterPanel.noRoster")));
            revalidate();
            repaint();
            return;
        }

        // Group formations by assigned track name, sorted alphabetically
        Map<String, List<StratConOpForFormation>> byTrack = new TreeMap<>();
        for (StratConOpForFormation formation : roster.getFormations()) {
            String trackName = formation.getAssignedTrackName();
            if (trackName == null) {
                trackName = "";
            }
            byTrack.computeIfAbsent(trackName, k -> new java.util.ArrayList<>()).add(formation);
        }

        for (Map.Entry<String, List<StratConOpForFormation>> entry : byTrack.entrySet()) {
            String trackName = entry.getKey();

            // Bold track-name header
            JLabel header = new JLabel(trackName.isEmpty() ? "(Unassigned)" : trackName);
            Font currentFont = header.getFont();
            header.setFont(currentFont.deriveFont(Font.BOLD, currentFont.getSize() + 1.0f));
            add(header);

            for (StratConOpForFormation formation : entry.getValue()) {
                add(buildFormationRow(formation, roster));
            }
        }

        revalidate();
        repaint();
    }

    /**
     * Builds a single row representing one formation.
     *
     * <p>The level of detail shown scales with the formation's
     * {@link IntelLevel}:</p>
     * <ul>
     *   <li>{@link IntelLevel#UNKNOWN} — only "Unidentified formation" shown.</li>
     *   <li>{@link IntelLevel#OBSERVED} — name, weight class, strength; units
     *       masked unless individually {@link StratConOpForUnit#isRevealed()}.</li>
     *   <li>{@link IntelLevel#FULL_INTEL} — all details including skill/quality;
     *       every unit's chassis and pilot shown regardless of revealed flag.</li>
     * </ul>
     *
     * @param formation the formation to render
     * @param roster    the owning roster (needed to resolve unit records)
     * @return a panel containing all row sub-components
     */
    private JPanel buildFormationRow(final StratConOpForFormation formation,
            final StratConOpForRoster roster) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));

        IntelLevel intel = formation.getIntelLevel();
        if (intel == null) {
            intel = IntelLevel.UNKNOWN;
        }

        if (intel == IntelLevel.UNKNOWN) {
            String trackName = formation.getAssignedTrackName();
            if (trackName == null) {
                trackName = "";
            }
            row.add(new JLabel(MessageFormat.format(
                    resources.getString("opForRosterPanel.unidentified"), trackName)));
            return row;
        }

        // OBSERVED or FULL_INTEL — show formation header
        List<StratConOpForUnit> livingUnits = formation.livingUnits(roster);
        int living = livingUnits.size();
        int total = formation.getUnitIds().size();
        boolean destroyed = formation.isDestroyed(roster);

        String weightClassName = weightClassDisplayName(formation.getWeightClass());
        String strengthText = MessageFormat.format(
                resources.getString("opForRosterPanel.formationStrength"), living, total);

        // Build header label
        String headerText;
        if (intel == IntelLevel.FULL_INTEL) {
            String skill = (formation.getSkillLevel() != null)
                    ? formation.getSkillLevel().toString()
                    : "?";
            headerText = formation.getName() + "  [" + weightClassName + ", " + skill + "]  " + strengthText;
        } else {
            headerText = formation.getName() + "  [" + weightClassName + "]  " + strengthText;
        }

        JLabel formationLabel = new JLabel(headerText);
        row.add(formationLabel);

        if (destroyed) {
            String destroyedHtml = "<html><span color='red'>"
                    + resources.getString("opForRosterPanel.destroyedLabel")
                    + "</span></html>";
            row.add(new JLabel(destroyedHtml));
        }

        // Per-unit sub-labels
        for (UUID unitId : formation.getUnitIds()) {
            StratConOpForUnit unit = roster.getUnit(unitId);
            if (unit == null) {
                continue;
            }

            JPanel unitRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            unitRow.add(new JLabel("    "));  // indent

            if ((intel == IntelLevel.FULL_INTEL) || unit.isRevealed()) {
                // Show full details
                String chassisModel = "";
                if (unit.getProtoEntity() != null) {
                    chassisModel = (unit.getProtoEntity().getChassis() + " "
                            + unit.getProtoEntity().getModel()).trim();
                }
                String pilotName = (unit.getPilotName() != null) ? unit.getPilotName() : "Unknown";
                String unitText = pilotName + " / " + chassisModel;

                if (unit.getStatus() != Status.READY) {
                    String statusColor = "red";
                    String statusBadge = "<html><span color='" + statusColor + "'>"
                            + unit.getStatus().name()
                            + "</span></html>";
                    unitRow.add(new JLabel(unitText));
                    unitRow.add(new JLabel(statusBadge));
                } else {
                    unitRow.add(new JLabel(unitText));
                }
            } else {
                // Unrevealed — mask identity
                unitRow.add(new JLabel("???"));
            }

            row.add(unitRow);
        }

        return row;
    }

    /**
     * Returns a short human-readable name for an {@code EntityWeightClass} constant.
     *
     * @param weightClass the int constant (e.g. {@code EntityWeightClass.WEIGHT_MEDIUM})
     * @return display name
     */
    private static String weightClassDisplayName(final int weightClass) {
        return switch (weightClass) {
            case EntityWeightClass.WEIGHT_ULTRA_LIGHT -> "Ultra-Light";
            case EntityWeightClass.WEIGHT_LIGHT -> "Light";
            case EntityWeightClass.WEIGHT_MEDIUM -> "Medium";
            case EntityWeightClass.WEIGHT_HEAVY -> "Heavy";
            case EntityWeightClass.WEIGHT_ASSAULT -> "Assault";
            default -> "Unknown";
        };
    }
}
