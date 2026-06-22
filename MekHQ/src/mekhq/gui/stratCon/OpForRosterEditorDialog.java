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
package mekhq.gui.stratCon;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import megamek.common.enums.SkillLevel;
import megamek.client.ui.dialogs.UnitLoadingDialog;
import megamek.common.loaders.MekSummaryCache;
import megamek.common.units.Entity;
import megamek.common.units.EntityWeightClass;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.events.OpForRosterChangedEvent;
import mekhq.campaign.stratCon.StratConTrackState;
import mekhq.campaign.stratCon.opfor.OpForRosterEditOps;
import mekhq.campaign.stratCon.opfor.Status;
import mekhq.campaign.stratCon.opfor.StratConOpForFormation;
import mekhq.campaign.stratCon.opfor.StratConOpForRoster;
import mekhq.campaign.stratCon.opfor.StratConOpForUnit;
import mekhq.campaign.stratCon.opfor.UnitTemplate;
import mekhq.gui.dialog.MekHQUnitSelectorDialog;

/**
 * GM-only master editor for a {@link StratConOpForRoster}. Edits a deep copy of
 * the live roster so changes can be discarded on Cancel; on OK the edited
 * contents are applied back into the live roster (preserving its object
 * identity, so the contract back-link survives) and an
 * {@link OpForRosterChangedEvent} is fired to refresh the OOB panel.
 *
 * <p>Layout: a formation list with Add/Edit/Delete on the left, a unit list
 * (for the selected formation) with Add/Edit/Remove on the right, and OK/Cancel
 * along the bottom.</p>
 */
public class OpForRosterEditorDialog extends JDialog {

    private static final String RESOURCE_BUNDLE_NAME = "mekhq/resources/AtBStratCon";

    private final transient JFrame frame;
    private final transient Campaign campaign;
    private final transient StratConOpForRoster liveRoster;
    private final transient StratConOpForRoster workingRoster;
    private final transient StratConTrackState currentTrack;
    private final transient List<String> trackNames;
    private final ResourceBundle resources;

    private final DefaultListModel<StratConOpForFormation> formationModel = new DefaultListModel<>();
    private final DefaultListModel<StratConOpForUnit> unitModel = new DefaultListModel<>();
    private JList<StratConOpForFormation> formationList;
    private JList<StratConOpForUnit> unitList;

    /**
     * @param frame        top-level frame (for the unit selector)
     * @param campaign     the active campaign (for the unit selector)
     * @param liveRoster   the roster to edit; mutated only on OK
     * @param currentTrack the currently selected track (event payload + default
     *                     track assignment); may be {@code null}
     */
    public OpForRosterEditorDialog(final JFrame frame, final Campaign campaign,
            final StratConOpForRoster liveRoster, final StratConTrackState currentTrack) {
        super(frame, true);
        this.frame = frame;
        this.campaign = campaign;
        this.liveRoster = liveRoster;
        this.workingRoster = liveRoster.copy();
        this.currentTrack = currentTrack;
        this.resources = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
        this.trackNames = computeTrackNames();

        setTitle(resources.getString("opForEditor.title"));
        initComponents();
        reloadFormations();
        pack();
        setMinimumSize(new Dimension(640, 380));
        setLocationRelativeTo(frame);
    }

    /** Track names offered when (re)assigning a formation. */
    private List<String> computeTrackNames() {
        TreeSet<String> names = new TreeSet<>();
        for (StratConOpForFormation f : workingRoster.getFormations()) {
            if (f.getAssignedTrackName() != null) {
                names.add(f.getAssignedTrackName());
            }
        }
        if (currentTrack != null) {
            names.add(currentTrack.getDisplayableName());
        }
        return new ArrayList<>(names);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel columns = new JPanel(new GridLayout(1, 2, 8, 0));
        columns.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        columns.add(buildFormationColumn());
        columns.add(buildUnitColumn());
        add(columns, BorderLayout.CENTER);

        add(buildOkCancelRow(), BorderLayout.SOUTH);
    }

    private JPanel buildFormationColumn() {
        JPanel column = new JPanel(new BorderLayout(0, 4));
        column.add(new JLabel(resources.getString("opForEditor.formations")), BorderLayout.NORTH);

        formationList = new JList<>(formationModel);
        formationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        formationList.setCellRenderer(new SimpleRenderer<StratConOpForFormation>(this::describeFormation));
        formationList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                reloadUnits();
            }
        });
        column.add(new JScrollPane(formationList), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(makeButton("opForEditor.addFormation", e -> onAddFormation()));
        buttons.add(makeButton("opForEditor.editFormation", e -> onEditFormation()));
        buttons.add(makeButton("opForEditor.deleteFormation", e -> onDeleteFormation()));
        column.add(buttons, BorderLayout.SOUTH);
        return column;
    }

    private JPanel buildUnitColumn() {
        JPanel column = new JPanel(new BorderLayout(0, 4));
        column.add(new JLabel(resources.getString("opForEditor.units")), BorderLayout.NORTH);

        unitList = new JList<>(unitModel);
        unitList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        unitList.setCellRenderer(new SimpleRenderer<StratConOpForUnit>(this::describeUnit));
        column.add(new JScrollPane(unitList), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(makeButton("opForEditor.addUnit", e -> onAddUnit()));
        buttons.add(makeButton("opForEditor.editUnit", e -> onEditUnit()));
        buttons.add(makeButton("opForEditor.removeUnit", e -> onRemoveUnit()));
        column.add(buttons, BorderLayout.SOUTH);
        return column;
    }

    private JPanel buildOkCancelRow() {
        JPanel row = new JPanel();
        JButton ok = makeButton("opForEditor.ok", e -> onOk());
        row.add(ok);
        row.add(makeButton("opForEditor.cancel", e -> dispose()));
        getRootPane().setDefaultButton(ok);
        return row;
    }

    private JButton makeButton(final String key, final java.awt.event.ActionListener action) {
        JButton button = new JButton(resources.getString(key));
        button.addActionListener(action);
        return button;
    }

    // ---- formation actions --------------------------------------------------

    private void onAddFormation() {
        String defaultTrack = (currentTrack != null) ? currentTrack.getDisplayableName() : null;
        StratConOpForFormation formation = OpForRosterEditOps.addFormation(workingRoster,
                "New Formation", EntityWeightClass.WEIGHT_MEDIUM, 3, SkillLevel.REGULAR,
                defaultTrack, false);
        EditOpForFormationDialog dialog =
                new EditOpForFormationDialog(this, formation, trackNames);
        dialog.setVisible(true);
        if (!dialog.wasConfirmed()) {
            // Roll back the speculatively-added formation so Cancel leaves no trace.
            OpForRosterEditOps.deleteFormation(workingRoster, formation.getId());
            reloadFormations();
            return;
        }
        reloadFormations();
        formationList.setSelectedValue(formation, true);
    }

    private void onEditFormation() {
        StratConOpForFormation formation = formationList.getSelectedValue();
        if (formation == null) {
            warn("opForEditor.noFormationSelected");
            return;
        }
        EditOpForFormationDialog dialog =
                new EditOpForFormationDialog(this, formation, trackNames);
        dialog.setVisible(true);
        reloadFormations();
        formationList.setSelectedValue(formation, true);
    }

    private void onDeleteFormation() {
        StratConOpForFormation formation = formationList.getSelectedValue();
        if (formation == null) {
            warn("opForEditor.noFormationSelected");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                MessageFormat.format(resources.getString("opForEditor.confirmDeleteFormation.text"),
                        describeFormation(formation), formation.getUnitIds().size()),
                resources.getString("opForEditor.confirmDeleteFormation.title"),
                JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        OpForRosterEditOps.deleteFormation(workingRoster, formation.getId());
        reloadFormations();
    }

    // ---- unit actions -------------------------------------------------------

    private void onAddUnit() {
        StratConOpForFormation formation = formationList.getSelectedValue();
        if (formation == null) {
            warn("opForEditor.noFormationSelected");
            return;
        }
        UnitLoadingDialog unitLoadingDialog = new UnitLoadingDialog(frame);
        if (!MekSummaryCache.getInstance().isInitialized()) {
            unitLoadingDialog.setVisible(true);
        }
        MekHQUnitSelectorDialog usd = new MekHQUnitSelectorDialog(frame, unitLoadingDialog,
                campaign, false);
        usd.setVisible(true);
        Entity entity = usd.getSelectedEntity();
        if (entity == null) {
            return;
        }

        UnitTemplate template = new UnitTemplate(entity.getChassis(), entity.getModel(),
                inheritFactionCode(formation));
        String pilotName = (entity.getCrew() != null) ? entity.getCrew().getName(0) : "Unknown";
        int gunnery = (entity.getCrew() != null) ? entity.getCrew().getGunnery() : 4;
        int piloting = (entity.getCrew() != null) ? entity.getCrew().getPiloting() : 5;

        StratConOpForUnit unit = OpForRosterEditOps.addUnit(workingRoster, formation.getId(),
                template, entity.getUnitType(), pilotName, gunnery, piloting);
        reloadUnits();
        unitList.setSelectedValue(unit, true);
        reloadFormations();
        formationList.setSelectedValue(formation, true);
    }

    private void onEditUnit() {
        StratConOpForUnit unit = unitList.getSelectedValue();
        if (unit == null) {
            warn("opForEditor.noUnitSelected");
            return;
        }
        EditOpForUnitDialog dialog =
                new EditOpForUnitDialog(this, frame, campaign, workingRoster, unit);
        dialog.setVisible(true);
        // A reassignment may have moved the unit out of the selected formation.
        reloadFormations();
        reloadUnits();
    }

    private void onRemoveUnit() {
        StratConOpForUnit unit = unitList.getSelectedValue();
        if (unit == null) {
            warn("opForEditor.noUnitSelected");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                MessageFormat.format(resources.getString("opForEditor.confirmRemoveUnit.text"),
                        describeUnit(unit)),
                resources.getString("opForEditor.confirmRemoveUnit.title"),
                JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        OpForRosterEditOps.removeUnit(workingRoster, unit.getId());
        reloadUnits();
        reloadFormations();
    }

    // ---- apply / cancel -----------------------------------------------------

    private void onOk() {
        // Apply the edited working-copy contents back into the live roster,
        // preserving the live object's identity (the contract holds a reference).
        liveRoster.setFormations(new ArrayList<>(workingRoster.getFormations()));
        liveRoster.setUnitList(new ArrayList<>(workingRoster.getUnitList()));
        liveRoster.setFormationsDestroyedThisContract(
                new ArrayList<>(workingRoster.getFormationsDestroyedThisContract()));
        liveRoster.setReinforcementEventsFired(workingRoster.getReinforcementEventsFired());
        liveRoster.setMilitiaReinforcementEventsFired(
                workingRoster.getMilitiaReinforcementEventsFired());

        MekHQ.triggerEvent(new OpForRosterChangedEvent(currentTrack));
        dispose();
    }

    // ---- list (re)load + rendering helpers ----------------------------------

    private void reloadFormations() {
        StratConOpForFormation selected = formationList.getSelectedValue();
        formationModel.clear();
        for (StratConOpForFormation f : workingRoster.getFormations()) {
            formationModel.addElement(f);
        }
        if (selected != null && formationModel.contains(selected)) {
            formationList.setSelectedValue(selected, true);
        } else if (!formationModel.isEmpty()) {
            formationList.setSelectedIndex(0);
        } else {
            unitModel.clear();
        }
    }

    private void reloadUnits() {
        unitModel.clear();
        StratConOpForFormation formation = formationList.getSelectedValue();
        if (formation == null) {
            return;
        }
        for (java.util.UUID unitId : formation.getUnitIds()) {
            StratConOpForUnit unit = workingRoster.getUnit(unitId);
            if (unit != null) {
                unitModel.addElement(unit);
            }
        }
    }

    private String describeFormation(final StratConOpForFormation formation) {
        if (formation == null) {
            return "";
        }
        String name = (formation.getName() != null && !formation.getName().isBlank())
                ? formation.getName() : resources.getString("opForEditor.unnamedFormation");
        int living = formation.livingUnits(workingRoster).size();
        int total = formation.getUnitIds().size();
        String track = (formation.getAssignedTrackName() != null)
                ? formation.getAssignedTrackName()
                : resources.getString("formationEditor.trackUnassigned");
        return name + "  [" + track + "]  " + living + "/" + total;
    }

    private String describeUnit(final StratConOpForUnit unit) {
        if (unit == null) {
            return resources.getString("opForEditor.unknownUnit");
        }
        String chassisModel = "";
        if (unit.getProtoEntity() != null) {
            String model = (unit.getProtoEntity().getModel() != null)
                    ? unit.getProtoEntity().getModel() : "";
            chassisModel = (unit.getProtoEntity().getChassis() + " " + model).trim();
        }
        String pilot = (unit.getPilotName() != null) ? unit.getPilotName() : "Unknown";
        String statusSuffix = (unit.getStatus() != null && unit.getStatus() != Status.READY)
                ? "  (" + unit.getStatus().name() + ")" : "";
        return chassisModel + " — " + pilot + "  (G" + unit.getGunnery() + "/P"
                + unit.getPiloting() + ")" + statusSuffix;
    }

    /** Inherits a faction code from an existing unit in the formation, if any. */
    private String inheritFactionCode(final StratConOpForFormation formation) {
        for (java.util.UUID unitId : formation.getUnitIds()) {
            StratConOpForUnit existing = workingRoster.getUnit(unitId);
            if (existing != null && existing.getProtoEntity() != null
                    && existing.getProtoEntity().getFactionCode() != null) {
                return existing.getProtoEntity().getFactionCode();
            }
        }
        return null;
    }

    private void warn(final String key) {
        JOptionPane.showMessageDialog(this, resources.getString(key),
                getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }
}
