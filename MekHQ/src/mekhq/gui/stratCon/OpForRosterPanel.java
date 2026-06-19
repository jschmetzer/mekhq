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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
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

    /** Left indent (px) applied to each nested level. */
    private static final int INDENT = 16;
    /** Trailing gap (px) below a formation's unit list. */
    private static final int FORMATION_GAP = 4;
    /** Trailing gap (px) below a whole track section. */
    private static final int SECTION_GAP = 8;
    /** Glyph shown on an expanded collapsible header. */
    private static final String GLYPH_EXPANDED = "▾";   // ▾
    /** Glyph shown on a collapsed collapsible header. */
    private static final String GLYPH_COLLAPSED = "▸";  // ▸

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
     *
     * <p>The roster is rendered as a nested, collapsible tree: each assigned track is a
     * collapsible section containing its formations, and each formation is a collapsible
     * section containing one line per unit. Both levels start expanded.</p>
     */
    public void refresh() {
        removeAll();

        StratConOpForRoster roster = rosterSupplier.get();
        if (roster == null) {
            add(leftAligned(new JLabel(resources.getString("opForRosterPanel.noRoster"))));
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
            byTrack.computeIfAbsent(trackName, k -> new ArrayList<>()).add(formation);
        }

        for (Map.Entry<String, List<StratConOpForFormation>> entry : byTrack.entrySet()) {
            add(buildTrackSection(entry.getKey(), entry.getValue(), roster));
        }

        // Absorb extra vertical space so sections stay top-packed rather than stretched.
        add(Box.createVerticalGlue());

        revalidate();
        repaint();
    }

    /**
     * Builds a collapsible section for one track: a bold toggle header over an indented body
     * holding each formation's collapsible section.
     *
     * @param trackName  the track name ("" for unassigned)
     * @param formations the formations on this track
     * @param roster     the owning roster (needed to resolve unit records)
     * @return the track section panel
     */
    private JComponent buildTrackSection(final String trackName,
            final List<StratConOpForFormation> formations, final StratConOpForRoster roster) {
        JPanel body = verticalPanel();
        body.setBorder(BorderFactory.createEmptyBorder(0, INDENT, SECTION_GAP, 0));
        for (StratConOpForFormation formation : formations) {
            body.add(buildFormationSection(formation, roster));
        }

        String title = trackName.isEmpty() ? "(Unassigned)" : trackName;
        return buildCollapsible(title, Font.BOLD, 1.0f, body);
    }

    /**
     * Builds a collapsible section for one formation: a toggle header (name, weight class, skill,
     * strength) over an indented body with one line per unit.
     *
     * <p>The level of detail shown scales with the formation's {@link IntelLevel}:</p>
     * <ul>
     *   <li>{@link IntelLevel#UNKNOWN} — only "Unidentified formation" shown (no children).</li>
     *   <li>{@link IntelLevel#OBSERVED} — name, weight class, strength; units masked unless
     *       individually {@link StratConOpForUnit#isRevealed()}.</li>
     *   <li>{@link IntelLevel#FULL_INTEL} — all details including skill; every unit's chassis,
     *       pilot, and experience shown regardless of revealed flag.</li>
     * </ul>
     *
     * @param formation the formation to render
     * @param roster    the owning roster (needed to resolve unit records)
     * @return the formation section component
     */
    private JComponent buildFormationSection(final StratConOpForFormation formation,
            final StratConOpForRoster roster) {
        IntelLevel intel = formation.getIntelLevel();
        if (intel == null) {
            intel = IntelLevel.UNKNOWN;
        }

        if (intel == IntelLevel.UNKNOWN) {
            String trackName = formation.getAssignedTrackName();
            if (trackName == null) {
                trackName = "";
            }
            JLabel label = new JLabel(MessageFormat.format(
                    resources.getString("opForRosterPanel.unidentified"), trackName));
            label.setBorder(BorderFactory.createEmptyBorder(0, INDENT, 0, 0));
            return leftAligned(label);
        }

        // OBSERVED or FULL_INTEL — build the formation header text
        int living = formation.livingUnits(roster).size();
        int total = formation.getUnitIds().size();
        boolean destroyed = formation.isDestroyed(roster);

        String weightClassName = weightClassDisplayName(formation.getWeightClass());
        String strengthText = MessageFormat.format(
                resources.getString("opForRosterPanel.formationStrength"), living, total);

        String headerCore;
        if (intel == IntelLevel.FULL_INTEL) {
            String skill = (formation.getSkillLevel() != null)
                    ? formation.getSkillLevel().toString()
                    : "?";
            headerCore = formation.getName() + "  [" + weightClassName + ", " + skill + "]  " + strengthText;
        } else {
            headerCore = formation.getName() + "  [" + weightClassName + "]  " + strengthText;
        }

        String headerText = destroyed
                ? "<html>" + escapeHtml(headerCore) + " <span color='red'>"
                        + escapeHtml(resources.getString("opForRosterPanel.destroyedLabel")) + "</span></html>"
                : headerCore;

        // Body: one line per unit
        JPanel body = verticalPanel();
        body.setBorder(BorderFactory.createEmptyBorder(0, INDENT, FORMATION_GAP, 0));
        for (UUID unitId : formation.getUnitIds()) {
            StratConOpForUnit unit = roster.getUnit(unitId);
            if (unit == null) {
                continue;
            }
            body.add(leftAligned(new JLabel(buildUnitLine(unit, intel))));
        }

        return buildCollapsible(headerText, Font.PLAIN, 0.0f, body);
    }

    /**
     * Builds the single-line text for one unit, respecting fog-of-war.
     *
     * @param unit  the unit to render
     * @param intel the owning formation's intel level
     * @return {@code "???"} when the unit is masked; otherwise
     *         {@code "Chassis Model — Pilot (G#/P#)"}, with a red status badge appended (as HTML)
     *         when the unit is not {@link Status#READY}
     */
    private static String buildUnitLine(final StratConOpForUnit unit, final IntelLevel intel) {
        if ((intel != IntelLevel.FULL_INTEL) && !unit.isRevealed()) {
            return "???";
        }

        String chassisModel = "";
        if (unit.getProtoEntity() != null) {
            chassisModel = (unit.getProtoEntity().getChassis() + " "
                    + unit.getProtoEntity().getModel()).trim();
        }
        String pilotName = (unit.getPilotName() != null) ? unit.getPilotName() : "Unknown";
        String experience = "(G" + unit.getGunnery() + "/P" + unit.getPiloting() + ")";
        String core = chassisModel + " — " + pilotName + "  " + experience;

        if (unit.getStatus() != Status.READY) {
            return "<html>" + escapeHtml(core) + " <span color='red'>"
                    + escapeHtml(unit.getStatus().name()) + "</span></html>";
        }
        return core;
    }

    /**
     * Wraps a header and body into a collapsible section. Clicking the header toggles the body's
     * visibility and swaps the expand/collapse glyph. The section starts expanded.
     *
     * @param headerText     header text (may be an HTML string)
     * @param fontStyle      {@link Font} style constant for the header (e.g. {@link Font#BOLD})
     * @param fontSizeDelta  point size added to the header font
     * @param body           the collapsible body
     * @return the section panel
     */
    private JPanel buildCollapsible(final String headerText, final int fontStyle,
            final float fontSizeDelta, final JComponent body) {
        JPanel section = verticalPanel();

        JLabel triangle = new JLabel(GLYPH_EXPANDED + " ");
        JLabel text = new JLabel(headerText);
        Font headerFont = text.getFont().deriveFont(fontStyle, text.getFont().getSize() + fontSizeDelta);
        text.setFont(headerFont);
        triangle.setFont(headerFont);

        JPanel headerRow = new JPanel();
        headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.X_AXIS));
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerRow.add(triangle);
        headerRow.add(text);
        // Keep BoxLayout from stretching the header to fill vertical space.
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerRow.getPreferredSize().height));
        headerRow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                boolean expanded = !body.isVisible();
                body.setVisible(expanded);
                triangle.setText((expanded ? GLYPH_EXPANDED : GLYPH_COLLAPSED) + " ");
                // Revalidate the whole panel so the enclosing scroll pane reclaims freed space.
                OpForRosterPanel.this.revalidate();
                OpForRosterPanel.this.repaint();
            }
        });

        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(headerRow);
        section.add(body);
        return section;
    }

    /**
     * Creates an empty, left-aligned vertical {@link BoxLayout} panel.
     */
    private static JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /**
     * Left-aligns a component for use inside a vertical {@link BoxLayout} and returns it.
     */
    private static <T extends JComponent> T leftAligned(final T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    /**
     * Minimal HTML-escaping for text interpolated into an HTML {@link JLabel}.
     */
    private static String escapeHtml(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
