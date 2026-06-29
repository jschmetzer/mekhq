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
 * NOTICE: The MegaMek Organization is a non-profit group of volunteers
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import javax.swing.SwingUtilities;

import megamek.common.units.EntityWeightClass;
import megamek.common.units.UnitType;
import mekhq.campaign.Campaign;
import mekhq.campaign.stratCon.StratConTrackState;
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
 *
 * <p>Collapse state is persisted across {@link #refresh()} calls in the
 * {@link #collapseState} map so the user's view is not reset after each
 * battle or reinforcement.</p>
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

    /** Key prefix for track-level collapse state entries. */
    private static final String KEY_TRACK_PREFIX = "T:";
    /** Key prefix for formation-level collapse state entries. */
    private static final String KEY_FORMATION_PREFIX = "F:";

    private final Supplier<StratConOpForRoster> rosterSupplier;

    /**
     * Supplies the active enemy challengers when this panel renders a multi-challenger garrison OpFor. When non-null
     * it takes precedence over {@link #rosterSupplier}: each active challenger is rendered as its own faction-titled
     * section. {@code null} for the single-roster (allied / legacy) panels.
     */
    private final Supplier<List<StratConOpForRoster>> challengersSupplier;
    private final ResourceBundle resources;

    /**
     * Campaign used to gate the GM editor; {@code null} disables editing
     * (read-only panel, e.g. in tests).
     */
    private final transient Campaign campaign;

    /**
     * Supplies the currently selected track, used as the
     * {@link mekhq.campaign.events.OpForRosterChangedEvent} payload after a GM
     * edit; may be {@code null}.
     */
    private final transient Supplier<StratConTrackState> trackSupplier;

    /**
     * Persistent expand/collapse state keyed by stable strings:
     * {@code "T:" + trackName} for tracks, {@code "F:" + formationId} for
     * formations. {@code true} = expanded (default).
     */
    private final Map<String, Boolean> collapseState = new HashMap<>();

    /**
     * Creates the panel.
     *
     * @param rosterSupplier provides the current {@link StratConOpForRoster},
     *                       or {@code null} if none is active; called only on
     *                       each explicit {@link #refresh()}
     */
    public OpForRosterPanel(final Supplier<StratConOpForRoster> rosterSupplier) {
        this(rosterSupplier, null, null);
    }

    /**
     * Creates the panel with GM-editing enabled.
     *
     * @param rosterSupplier provides the current {@link StratConOpForRoster},
     *                       or {@code null} if none is active; called only on
     *                       each explicit {@link #refresh()}
     * @param campaign       the active campaign; when non-null and the campaign
     *                       is in GM mode, an "Edit OpFor" button is shown.
     *                       {@code null} keeps the panel read-only
     * @param trackSupplier  supplies the currently selected track for the
     *                       roster-changed event after an edit; may be {@code null}
     */
    public OpForRosterPanel(final Supplier<StratConOpForRoster> rosterSupplier,
            final Campaign campaign, final Supplier<StratConTrackState> trackSupplier) {
        this(rosterSupplier, null, campaign, trackSupplier);
    }

    /**
     * Creates a multi-challenger OpFor panel that renders one faction-titled section per active challenger.
     *
     * @param challengersSupplier provides the active enemy challengers (never the single-roster path)
     * @param campaign            the active campaign; GM mode enables per-challenger edit buttons. May be {@code null}
     * @param trackSupplier       supplies the current track for the roster-changed event; may be {@code null}
     *
     * @return a configured multi-challenger panel
     */
    public static OpForRosterPanel forChallengers(
            final Supplier<List<StratConOpForRoster>> challengersSupplier,
            final Campaign campaign, final Supplier<StratConTrackState> trackSupplier) {
        return new OpForRosterPanel(null, challengersSupplier, campaign, trackSupplier);
    }

    private OpForRosterPanel(final Supplier<StratConOpForRoster> rosterSupplier,
            final Supplier<List<StratConOpForRoster>> challengersSupplier,
            final Campaign campaign, final Supplier<StratConTrackState> trackSupplier) {
        this.rosterSupplier = rosterSupplier;
        this.challengersSupplier = challengersSupplier;
        this.campaign = campaign;
        this.trackSupplier = trackSupplier;
        this.resources = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /**
     * Clears and rebuilds all child components from the current roster snapshot.
     *
     * <p>The roster is rendered as a nested, collapsible tree: each assigned track is a
     * collapsible section containing its formations, and each formation is a collapsible
     * section containing one line per unit. Expand/collapse state is preserved across
     * rebuilds via {@link #collapseState}.</p>
     *
     * <p>At the top of the panel a summary header shows the line-formation win metric
     * ({@code Line OpFor: remaining / total formations}) plus a militia count when
     * any militia formations exist. Below that, a toolbar provides "Expand all" /
     * "Collapse all" controls.</p>
     */
    public void refresh() {
        removeAll();

        if (challengersSupplier != null) {
            List<StratConOpForRoster> challengers = challengersSupplier.get();
            if ((challengers == null) || challengers.isEmpty()) {
                add(leftAligned(new JLabel(resources.getString("opForRosterPanel.noRoster"))));
            } else {
                for (StratConOpForRoster challenger : challengers) {
                    add(challengerHeader(challenger));
                    renderRoster(challenger);
                }
            }
        } else {
            StratConOpForRoster roster = rosterSupplier.get();
            if (roster == null) {
                add(leftAligned(new JLabel(resources.getString("opForRosterPanel.noRoster"))));
            } else {
                renderRoster(roster);
            }
        }

        // Absorb extra vertical space so sections stay top-packed rather than stretched.
        add(Box.createVerticalGlue());

        revalidate();
        repaint();
    }

    /** Bold, underlined faction title for one challenger's section in the multi-challenger view. */
    private Component challengerHeader(final StratConOpForRoster challenger) {
        String name = (challenger.getEnemyBotName() != null)
                ? challenger.getEnemyBotName()
                : resources.getString("opForRosterPanel.title");
        JLabel header = new JLabel("<html><b><u>"
                + escapeHtml(MessageFormat.format(resources.getString("opForRosterPanel.challengerHeader"), name))
                + "</u></b></html>");
        header.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
        return leftAligned(header);
    }

    /** Renders a single roster's summary header, expand/collapse toolbar, and track→formation→unit tree. */
    private void renderRoster(final StratConOpForRoster roster) {
        // Partition formations into line and militia
        List<StratConOpForFormation> lineFormations = new ArrayList<>();
        List<StratConOpForFormation> militiaFormations = new ArrayList<>();
        for (StratConOpForFormation f : roster.getFormations()) {
            if (f.isMilitia()) {
                militiaFormations.add(f);
            } else {
                lineFormations.add(f);
            }
        }

        // Compute summary counts
        long lineTotal = lineFormations.size();
        long lineRemaining = lineFormations.stream()
                .filter(f -> !f.isDestroyed(roster))
                .count();
        long militiaActive = militiaFormations.stream()
                .filter(f -> !f.isDestroyed(roster))
                .count();

        // Summary header
        String summaryText = MessageFormat.format(
                resources.getString("opForRosterPanel.summaryLine"),
                lineRemaining, lineTotal);
        if (!militiaFormations.isEmpty()) {
            summaryText += MessageFormat.format(
                    resources.getString("opForRosterPanel.summaryMilitia"),
                    militiaActive);
        }
        JLabel summaryLabel = new JLabel("<html><b>" + escapeHtml(summaryText) + "</b></html>");
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        add(leftAligned(summaryLabel));

        // Expand / Collapse all toolbar
        add(buildExpandCollapseToolbar(roster));

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
    }

    /**
     * Builds a small toolbar with "Expand all" and "Collapse all" buttons that set
     * every known collapse-state key and trigger a {@link #refresh()}.
     */
    private JPanel buildExpandCollapseToolbar(final StratConOpForRoster roster) {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

        JButton expandAll = new JButton(resources.getString("opForRosterPanel.expandAll"));
        expandAll.setFocusPainted(false);
        expandAll.addActionListener(e -> {
            for (String key : collapseState.keySet()) {
                collapseState.put(key, true);
            }
            // Pre-populate keys for any new formations not yet in the map
            populateDefaultKeys(roster);
            refresh();
        });

        JButton collapseAll = new JButton(resources.getString("opForRosterPanel.collapseAll"));
        collapseAll.setFocusPainted(false);
        collapseAll.addActionListener(e -> {
            populateDefaultKeys(roster);
            for (String key : collapseState.keySet()) {
                collapseState.put(key, false);
            }
            refresh();
        });

        toolbar.add(expandAll);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(collapseAll);
        toolbar.add(Box.createHorizontalGlue());

        // GM-only roster editor entry point.
        if ((campaign != null) && campaign.isGM()) {
            JButton editButton = new JButton(resources.getString("opForEditor.button"));
            editButton.setFocusPainted(false);
            editButton.addActionListener(e -> openEditor(roster));
            toolbar.add(editButton);
        }

        toolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolbar.getPreferredSize().height));
        return toolbar;
    }

    /**
     * Opens the GM roster editor for the given roster and refreshes the panel
     * afterward. No-op when editing is disabled (no campaign) or no roster is
     * active.
     */
    private void openEditor(final StratConOpForRoster roster) {
        if ((campaign == null) || (roster == null)) {
            return;
        }
        java.awt.Window ancestor = SwingUtilities.getWindowAncestor(this);
        JFrame frame = (ancestor instanceof JFrame jFrame) ? jFrame : null;
        StratConTrackState track = (trackSupplier != null) ? trackSupplier.get() : null;
        try {
            OpForRosterEditorDialog dialog =
                    new OpForRosterEditorDialog(frame, campaign, roster, track);
            dialog.setVisible(true);
        } catch (RuntimeException ex) {
            // copy() failed (e.g. JAXB error) — never apply a half-built roster.
            javax.swing.JOptionPane.showMessageDialog(this,
                    resources.getString("opForEditor.copyFailed"),
                    resources.getString("opForEditor.title"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        // The editor fires OpForRosterChangedEvent on OK, which refreshes via the
        // tab handler; refresh here too so a standalone panel stays in sync.
        refresh();
    }

    /**
     * Sets the collapse state for the given key and triggers a refresh.
     *
     * <p>Package-private for use by tests; production callers should use the
     * "Expand all" / "Collapse all" toolbar buttons.</p>
     *
     * @param key      a stable state key ({@code "T:trackName"} or {@code "F:formationId"})
     * @param expanded {@code true} to expand, {@code false} to collapse
     */
    void setCollapseState(final String key, final boolean expanded) {
        collapseState.put(key, expanded);
    }

    /**
     * Returns the stable formation-key prefix used in {@link #collapseState}.
     * Package-private for tests.
     */
    static String formationKey(final UUID formationId) {
        return KEY_FORMATION_PREFIX + formationId;
    }

    /**
     * Inserts default (expanded) keys for every track and formation in the roster
     * that doesn't already have an entry in {@link #collapseState}.
     */
    private void populateDefaultKeys(final StratConOpForRoster roster) {
        for (StratConOpForFormation f : roster.getFormations()) {
            String trackName = f.getAssignedTrackName();
            if (trackName == null) {
                trackName = "";
            }
            collapseState.putIfAbsent(KEY_TRACK_PREFIX + trackName, true);
            collapseState.putIfAbsent(KEY_FORMATION_PREFIX + f.getId(), true);
        }
    }

    /**
     * Builds a collapsible section for one track: a bold toggle header over an indented body
     * holding each formation's collapsible section. Line formations are rendered first; if
     * the track contains any militia formations, a muted italic "Planetary Militia" subheader
     * precedes them.
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

        List<StratConOpForFormation> lineFormations = new ArrayList<>();
        List<StratConOpForFormation> militiaFormations = new ArrayList<>();
        for (StratConOpForFormation f : formations) {
            if (f.isMilitia()) {
                militiaFormations.add(f);
            } else {
                lineFormations.add(f);
            }
        }

        for (StratConOpForFormation formation : lineFormations) {
            body.add(buildFormationSection(formation, roster));
        }

        if (!militiaFormations.isEmpty()) {
            // Muted italic subheader for the militia group
            JLabel subheader = new JLabel(resources.getString("opForRosterPanel.militiaSubheader"));
            Font baseFont = subheader.getFont();
            subheader.setFont(baseFont.deriveFont(Font.ITALIC, baseFont.getSize()));
            subheader.setForeground(Color.GRAY);
            subheader.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
            body.add(leftAligned(subheader));

            for (StratConOpForFormation formation : militiaFormations) {
                body.add(buildFormationSection(formation, roster));
            }
        }

        String title = trackName.isEmpty() ? "(Unassigned)" : trackName;
        String trackKey = KEY_TRACK_PREFIX + trackName;
        return buildCollapsible(title, Font.BOLD, 1.0f, body, trackKey);
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
     * <p>Militia formation headers are rendered in muted gray. The inline
     * {@code [Planetary Militia]} tag is NOT appended here — grouping under the
     * "Planetary Militia" subheader in {@link #buildTrackSection} replaces it.</p>
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

        // NOTE: The old inline [Planetary Militia] tag is intentionally omitted here.
        // Militia formations are grouped under the "Planetary Militia" subheader in buildTrackSection.

        String headerText;
        if (destroyed) {
            headerText = "<html>" + escapeHtml(headerCore) + " <span color='red'>"
                    + escapeHtml(resources.getString("opForRosterPanel.destroyedLabel")) + "</span></html>";
        } else {
            headerText = headerCore;
        }

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

        // Militia formation headers are muted gray
        String formationKey = KEY_FORMATION_PREFIX + formation.getId();
        JPanel section = buildCollapsible(headerText, Font.PLAIN, 0.0f, body, formationKey);

        if (formation.isMilitia()) {
            // Tint the header row's text label gray
            applyMutedColorToFirstLabel(section);
        }

        return section;
    }

    /**
     * Walks the direct children of the first headerRow JPanel in a collapsible section and
     * applies {@link Color#GRAY} to the text label. Used to mute militia formation headers.
     */
    private static void applyMutedColorToFirstLabel(final JPanel section) {
        for (Component child : section.getComponents()) {
            if (child instanceof JPanel headerRow) {
                for (Component c : headerRow.getComponents()) {
                    if (c instanceof JLabel label) {
                        label.setForeground(Color.GRAY);
                    }
                }
                return; // only the first JPanel (header row)
            }
        }
    }

    /**
     * Builds the single-line text for one unit, respecting fog-of-war.
     *
     * @param unit  the unit to render
     * @param intel the owning formation's intel level
     * @return {@code "???"} when the unit is masked; otherwise a formatted line with chassis,
     *         pilot, experience, optional unit-type tag, and a colored status span for
     *         non-{@link Status#READY} units
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

        String typeTag = typeTag(unit.getUnitType());
        String core;
        if (typeTag.isEmpty()) {
            core = chassisModel + " — " + pilotName + "  " + experience;
        } else {
            core = typeTag + " " + chassisModel + " — " + pilotName + "  " + experience;
        }

        if (unit.getStatus() != Status.READY) {
            String colorHex = statusColorHex(unit.getStatus());
            String colorAttr = colorHex.isEmpty() ? "color='red'" : "color='" + colorHex + "'";
            return "<html><strike>" + escapeHtml(core) + "</strike> <span " + colorAttr + ">"
                    + escapeHtml(unit.getStatus().name()) + "</span></html>";
        }
        return core;
    }

    /**
     * Returns the hex color string (with leading {@code #}) for a terminal unit status,
     * used in HTML span attributes.
     *
     * @param status the unit's status
     * @return color hex string, or {@code "red"} for DESTROYED, or empty string if READY
     */
    private static String statusColorHex(final Status status) {
        return switch (status) {
            case DESTROYED -> "red";
            case SALVAGED -> "#B8860B";
            case CAPTURED -> "#1E6FBA";
            default -> "";
        };
    }

    /**
     * Returns a short bracketed type tag for visible (non-masked) unit lines.
     *
     * <p>{@code [M]} for Meks, {@code [V]} for vehicles (TANK/VTOL), {@code [I]} for
     * infantry and battle armour. Returns an empty string for unknown types ({@code -1})
     * or any other type without a defined glyph.</p>
     *
     * @param unitType a {@link UnitType} constant, or {@code -1} for unknown
     * @return the short tag string, or {@code ""} if no tag applies
     */
    private static String typeTag(final int unitType) {
        return switch (unitType) {
            case UnitType.MEK -> "[M]";
            case UnitType.TANK, UnitType.VTOL -> "[V]";
            case UnitType.INFANTRY, UnitType.BATTLE_ARMOR -> "[I]";
            default -> "";
        };
    }

    /**
     * Wraps a header and body into a collapsible section. Clicking the header toggles the body's
     * visibility and swaps the expand/collapse glyph. Initial visibility is read from
     * {@link #collapseState} (defaults to expanded if the key is absent).
     *
     * @param headerText     header text (may be an HTML string)
     * @param fontStyle      {@link Font} style constant for the header (e.g. {@link Font#BOLD})
     * @param fontSizeDelta  point size added to the header font
     * @param body           the collapsible body
     * @param stateKey       stable key used to look up and persist collapse state
     * @return the section panel
     */
    private JPanel buildCollapsible(final String headerText, final int fontStyle,
            final float fontSizeDelta, final JComponent body, final String stateKey) {
        JPanel section = verticalPanel();

        // Restore persisted state; default is expanded
        boolean expanded = collapseState.getOrDefault(stateKey, true);
        body.setVisible(expanded);
        collapseState.put(stateKey, expanded);

        JLabel triangle = new JLabel((expanded ? GLYPH_EXPANDED : GLYPH_COLLAPSED) + " ");
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
                boolean nowExpanded = !body.isVisible();
                body.setVisible(nowExpanded);
                collapseState.put(stateKey, nowExpanded);
                triangle.setText((nowExpanded ? GLYPH_EXPANDED : GLYPH_COLLAPSED) + " ");
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
