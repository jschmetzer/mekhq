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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.GraphicsEnvironment;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javax.swing.JTable;

import mekhq.campaign.Campaign;
import mekhq.campaign.stratCon.opfor.intel.IntelLog;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link IntelLogDialog} and its Reports-menu entry are fully
 * localized — i.e. every user-visible string is sourced from a resource bundle
 * rather than hardcoded, matching the rest of the Static OpFor feature.
 */
class IntelLogDialogTest {

    private static final String STRATCON_BUNDLE = "mekhq.resources.AtBStratCon";
    private static final String MENUBAR_BUNDLE = "mekhq.resources.MekHQMenuBar";

    /**
     * The dialog and menu reference these keys at runtime; they must exist and be
     * non-blank in their bundles. Runs in headless CI (no Swing required).
     */
    @Test
    void localizationKeysResolve() {
        ResourceBundle stratCon = ResourceBundle.getBundle(STRATCON_BUNDLE);
        for (String key : new String[]{
                "intelLog.title",
                "intelLog.column.date", "intelLog.column.faction", "intelLog.column.pilot",
                "intelLog.column.chassis", "intelLog.column.model", "intelLog.column.outcome",
                "intelLog.summary.total", "intelLog.summary.killed", "intelLog.summary.captured",
                "intelLog.summary.salvaged", "intelLog.summary.observed", "intelLog.summary.byFaction",
                "intelLog.close"}) {
            assertFalse(resolve(stratCon, key).isBlank(), "blank/missing key: " + key);
        }

        ResourceBundle menuBar = ResourceBundle.getBundle(MENUBAR_BUNDLE);
        assertFalse(resolve(menuBar, "miIntelLog.text").isBlank(), "blank/missing key: miIntelLog.text");
    }

    /**
     * Wiring guard: a live dialog must take its title and column headers from the
     * bundle, so a translation actually reaches the screen. Skipped when headless.
     */
    @Test
    void dialogTakesTitleAndColumnsFromBundle() {
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Skipping Swing component test in headless environment");

        ResourceBundle stratCon = ResourceBundle.getBundle(STRATCON_BUNDLE);
        Campaign campaign = mock(Campaign.class);
        when(campaign.getIntelLog()).thenReturn(new IntelLog());

        IntelLogDialog dialog = new IntelLogDialog(null, campaign);
        try {
            assertEquals(stratCon.getString("intelLog.title"), dialog.getTitle());

            JTable table = findTable(dialog.getContentPane());
            assertNotNull(table, "JTable not found in dialog content pane");
            assertEquals(stratCon.getString("intelLog.column.date"), table.getColumnName(0));
            assertEquals(stratCon.getString("intelLog.column.faction"), table.getColumnName(1));
            assertEquals(stratCon.getString("intelLog.column.pilot"), table.getColumnName(2));
            assertEquals(stratCon.getString("intelLog.column.chassis"), table.getColumnName(3));
            assertEquals(stratCon.getString("intelLog.column.model"), table.getColumnName(4));
            assertEquals(stratCon.getString("intelLog.column.outcome"), table.getColumnName(5));
        } finally {
            dialog.dispose();
        }
    }

    private static String resolve(final ResourceBundle bundle, final String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            return "";
        }
    }

    private static JTable findTable(final java.awt.Container container) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JTable table) {
                return table;
            }
            if (component instanceof java.awt.Container child) {
                JTable found = findTable(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
