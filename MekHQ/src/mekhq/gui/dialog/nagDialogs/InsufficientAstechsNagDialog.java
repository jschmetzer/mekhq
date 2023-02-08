/*
 * Copyright (c) 2021 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.gui.dialog.nagDialogs;

import mekhq.MekHQ;
import mekhq.MHQConstants;
import mekhq.campaign.Campaign;
import mekhq.gui.baseComponents.AbstractMHQNagDialog;

import javax.swing.*;

public class InsufficientAstechsNagDialog extends AbstractMHQNagDialog {
    //region Constructors
    public InsufficientAstechsNagDialog(final JFrame frame, final Campaign campaign) {
        super(frame, "InsufficientAstechsNagDialog", "InsufficientAstechsNagDialog.title",
                "", campaign, MHQConstants.NAG_INSUFFICIENT_ASTECHS);
    }
    //endregion Constructors

    @Override
    protected boolean checkNag() {
        if (MekHQ.getMHQOptions().getNagDialogIgnore(getKey())) {
            return false;
        }

        final int need = getCampaign().getAstechNeed();
        if (need > 0) {
            setDescription(String.format(resources.getString("InsufficientAstechsNagDialog.text"), need));
            return true;
        } else {
            return false;
        }
    }
}
