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
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.Map;
import java.util.ResourceBundle;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import mekhq.campaign.Campaign;
import mekhq.campaign.stratCon.opfor.intel.IntelLog;
import mekhq.campaign.stratCon.opfor.intel.IntelLogEntry;

/**
 * v2 slice 3 — UI surface for the cross-contract intelligence log.
 *
 * <p>Shows a summary panel (counts by outcome) and a sortable table of all
 * entries. Read-only — the log is append-only at the data layer.</p>
 */
public class IntelLogDialog extends JDialog {

    private static final String RESOURCE_BUNDLE_NAME = "mekhq/resources/AtBStratCon";

    private final transient ResourceBundle resources;

    public IntelLogDialog(final Frame owner, final Campaign campaign) {
        super(owner, false);
        this.resources = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
        setTitle(resources.getString("intelLog.title"));
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(720, 480));

        IntelLog log = campaign.getIntelLog();

        add(buildSummaryPanel(log), BorderLayout.NORTH);
        add(new JScrollPane(buildTable(log)), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildSummaryPanel(final IntelLog log) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        int total = log.getEntries().size();
        int killed = 0, captured = 0, salvaged = 0, observed = 0;
        for (IntelLogEntry entry : log.getEntries()) {
            switch (entry.getOutcome()) {
                case KILLED -> killed++;
                case CAPTURED -> captured++;
                case SALVAGED -> salvaged++;
                case OBSERVED -> observed++;
                case null -> { /* skip malformed entries */ }
            }
        }

        panel.add(new JLabel(String.format(
                "<html><b>%s:</b> %d &nbsp;&nbsp; "
                        + "<b>%s:</b> %d &nbsp;&nbsp; "
                        + "<b>%s:</b> %d &nbsp;&nbsp; "
                        + "<b>%s:</b> %d &nbsp;&nbsp; "
                        + "<b>%s:</b> %d</html>",
                resources.getString("intelLog.summary.total"), total,
                resources.getString("intelLog.summary.killed"), killed,
                resources.getString("intelLog.summary.captured"), captured,
                resources.getString("intelLog.summary.salvaged"), salvaged,
                resources.getString("intelLog.summary.observed"), observed)));

        if (!log.getEntries().isEmpty()) {
            StringBuilder factionLine = new StringBuilder("<html>&nbsp;&nbsp;<i>")
                    .append(resources.getString("intelLog.summary.byFaction")).append(":</i> ");
            Map<String, Long> byFaction = log.countByFaction();
            boolean first = true;
            for (Map.Entry<String, Long> e : byFaction.entrySet()) {
                if (!first) {
                    factionLine.append(", ");
                }
                factionLine.append(escapeHtml(e.getKey())).append("=").append(e.getValue());
                first = false;
            }
            factionLine.append("</html>");
            panel.add(new JLabel(factionLine.toString()));
        }
        return panel;
    }

    private JTable buildTable(final IntelLog log) {
        String[] columns = {
                resources.getString("intelLog.column.date"),
                resources.getString("intelLog.column.faction"),
                resources.getString("intelLog.column.pilot"),
                resources.getString("intelLog.column.chassis"),
                resources.getString("intelLog.column.model"),
                resources.getString("intelLog.column.outcome")
        };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        for (IntelLogEntry entry : log.getEntries()) {
            model.addRow(new Object[]{
                    entry.getDateIso() != null ? entry.getDateIso() : "",
                    safe(entry.getFactionCode()),
                    safe(entry.getPilotName()),
                    safe(entry.getChassis()),
                    safe(entry.getModel()),
                    entry.getOutcome() != null ? entry.getOutcome().name() : ""
            });
        }

        JTable table = new JTable(model);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        return table;
    }

    private static String safe(final String s) {
        return s == null ? "" : s;
    }

    /**
     * Escapes the HTML metacharacters in a string so a save-supplied value (e.g.
     * a GM-edited faction code) cannot inject markup into the summary
     * {@link JLabel}, which renders HTML.
     */
    private static String escapeHtml(final String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton(resources.getString("intelLog.close"));
        close.addActionListener(e -> dispose());
        panel.add(close);
        return panel;
    }
}
