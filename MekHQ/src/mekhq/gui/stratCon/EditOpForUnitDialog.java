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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.ResourceBundle;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import megamek.client.ui.dialogs.UnitLoadingDialog;
import megamek.common.loaders.MekSummaryCache;
import megamek.common.units.Entity;
import mekhq.campaign.Campaign;
import mekhq.campaign.stratCon.opfor.OpForRosterEditOps;
import mekhq.campaign.stratCon.opfor.Status;
import mekhq.campaign.stratCon.opfor.StratConOpForFormation;
import mekhq.campaign.stratCon.opfor.StratConOpForRoster;
import mekhq.campaign.stratCon.opfor.StratConOpForUnit;
import mekhq.campaign.stratCon.opfor.UnitTemplate;
import mekhq.gui.dialog.MekHQUnitSelectorDialog;

/**
 * Modal sub-dialog of {@link OpForRosterEditorDialog} for editing a single
 * {@link StratConOpForUnit}. The chassis/model can be replaced via the standard
 * {@link MekHQUnitSelectorDialog}; pilot name, skills, status, fog-of-war
 * reveal, and owning formation are editable inline. On OK the values are
 * validated via {@link OpForRosterEditOps#validateUnit} and written back into
 * the passed unit (with a reassignment applied to the roster if the formation
 * changed); on Cancel nothing is touched.
 */
public class EditOpForUnitDialog extends JDialog {

    private static final String RESOURCE_BUNDLE_NAME = "mekhq/resources/AtBStratCon";

    private final transient StratConOpForRoster roster;
    private final transient StratConOpForUnit unit;
    private final transient Campaign campaign;
    private final JFrame frame;
    private final ResourceBundle resources;

    // Pending chassis selection (applied on OK). Initialised from the unit.
    private transient UnitTemplate pendingTemplate;
    private int pendingUnitType;

    private JLabel unitLabel;
    private JTextField pilotNameField;
    private JComboBox<Integer> gunneryCombo;
    private JComboBox<Integer> pilotingCombo;
    private JComboBox<Status> statusCombo;
    private JCheckBox revealedCheck;
    private JComboBox<StratConOpForFormation> formationCombo;

    private boolean confirmed = false;

    /**
     * @param parent   the owning editor dialog
     * @param frame    top-level frame, needed by the unit selector
     * @param campaign the active campaign, needed by the unit selector
     * @param roster   the (working-copy) roster, for formation reassignment
     * @param unit     the unit to edit (mutated in place on OK)
     */
    public EditOpForUnitDialog(final JDialog parent, final JFrame frame, final Campaign campaign,
            final StratConOpForRoster roster, final StratConOpForUnit unit) {
        super(parent, true);
        this.frame = frame;
        this.campaign = campaign;
        this.roster = roster;
        this.unit = unit;
        this.resources = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
        this.pendingTemplate = unit.getProtoEntity();
        this.pendingUnitType = unit.getUnitType();
        setTitle(resources.getString("unitEditor.title"));
        initComponents();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        unitLabel = new JLabel(describeTemplate(pendingTemplate));
        JButton changeUnit = new JButton(resources.getString("unitEditor.changeUnit"));
        changeUnit.addActionListener(e -> onChangeUnit());
        JPanel unitRow = new JPanel(new BorderLayout(8, 0));
        unitRow.add(unitLabel, BorderLayout.CENTER);
        unitRow.add(changeUnit, BorderLayout.EAST);
        addRow(fields, gbc, row++, "unitEditor.unit", unitRow);

        pilotNameField = new JTextField(unit.getPilotName() != null ? unit.getPilotName() : "", 18);
        addRow(fields, gbc, row++, "unitEditor.pilotName", pilotNameField);

        gunneryCombo = skillCombo(unit.getGunnery());
        addRow(fields, gbc, row++, "unitEditor.gunnery", gunneryCombo);

        pilotingCombo = skillCombo(unit.getPiloting());
        addRow(fields, gbc, row++, "unitEditor.piloting", pilotingCombo);

        statusCombo = new JComboBox<>(Status.values());
        statusCombo.setSelectedItem(unit.getStatus() != null ? unit.getStatus() : Status.READY);
        addRow(fields, gbc, row++, "unitEditor.status", statusCombo);

        revealedCheck = new JCheckBox(resources.getString("unitEditor.revealed"), unit.isRevealed());
        gbc.gridx = 1;
        gbc.gridy = row++;
        fields.add(revealedCheck, gbc);

        formationCombo = new JComboBox<>();
        for (StratConOpForFormation f : roster.getFormations()) {
            formationCombo.addItem(f);
        }
        formationCombo.setRenderer(new SimpleRenderer<StratConOpForFormation>(f ->
                (f == null || f.getName() == null)
                        ? resources.getString("opForEditor.unnamedFormation") : f.getName()));
        formationCombo.setSelectedItem(
                OpForRosterEditOps.findFormation(roster, unit.getFormationId()));
        addRow(fields, gbc, row++, "unitEditor.formation", formationCombo);

        add(fields, BorderLayout.CENTER);
        add(buildButtonRow(), BorderLayout.SOUTH);
    }

    private JComboBox<Integer> skillCombo(final int current) {
        JComboBox<Integer> combo = new JComboBox<>();
        for (int v = OpForRosterEditOps.MIN_SKILL; v <= OpForRosterEditOps.MAX_SKILL; v++) {
            combo.addItem(v);
        }
        if ((current >= OpForRosterEditOps.MIN_SKILL) && (current <= OpForRosterEditOps.MAX_SKILL)) {
            combo.setSelectedItem(current);
        }
        return combo;
    }

    private JPanel buildButtonRow() {
        JPanel buttons = new JPanel();
        JButton ok = new JButton(resources.getString("unitEditor.ok"));
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton(resources.getString("unitEditor.cancel"));
        cancel.addActionListener(e -> dispose());
        buttons.add(ok);
        buttons.add(cancel);
        getRootPane().setDefaultButton(ok);
        return buttons;
    }

    private void onChangeUnit() {
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
        String factionCode = (pendingTemplate != null) ? pendingTemplate.getFactionCode() : null;
        pendingTemplate = new UnitTemplate(entity.getChassis(), entity.getModel(), factionCode);
        pendingUnitType = entity.getUnitType();
        unitLabel.setText(describeTemplate(pendingTemplate));
        pack();
    }

    private void onOk() {
        String pilotName = pilotNameField.getText().trim();
        int gunnery = (Integer) gunneryCombo.getSelectedItem();
        int piloting = (Integer) pilotingCombo.getSelectedItem();

        List<String> errors = OpForRosterEditOps.validateUnit(pilotName, gunnery, piloting,
                pendingTemplate);
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", errors),
                    resources.getString("opForEditor.validationErrors.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        unit.setProtoEntity(pendingTemplate);
        unit.setUnitType(pendingUnitType);
        unit.setPilotName(pilotName);
        unit.setGunnery(gunnery);
        unit.setPiloting(piloting);
        unit.setStatus((Status) statusCombo.getSelectedItem());
        unit.setRevealed(revealedCheck.isSelected());

        StratConOpForFormation target = (StratConOpForFormation) formationCombo.getSelectedItem();
        if ((target != null) && !target.getId().equals(unit.getFormationId())) {
            OpForRosterEditOps.reassignUnit(roster, unit.getId(), target.getId());
        }

        confirmed = true;
        dispose();
    }

    private String describeTemplate(final UnitTemplate template) {
        if ((template == null) || (template.getChassis() == null)) {
            return resources.getString("unitEditor.noUnitSelected");
        }
        String model = (template.getModel() != null) ? template.getModel() : "";
        return (template.getChassis() + " " + model).trim();
    }

    private void addRow(final JPanel panel, final GridBagConstraints gbc, final int row,
            final String labelKey, final java.awt.Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(resources.getString(labelKey)), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    /** @return {@code true} if the user confirmed (OK); {@code false} on cancel/close */
    public boolean wasConfirmed() {
        return confirmed;
    }
}
