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

import java.awt.Component;
import java.util.function.Function;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

/**
 * A {@link DefaultListCellRenderer} that derives each cell's display text from a
 * caller-supplied function. Used by the GM OpFor editor combos to show
 * human-readable labels for constant/enum values.
 *
 * @param <T> the list element type
 */
public class SimpleRenderer<T> extends DefaultListCellRenderer {

    private final transient Function<T, String> labeller;

    /**
     * @param labeller maps a (possibly {@code null}) element to its display text
     */
    public SimpleRenderer(final Function<T, String> labeller) {
        this.labeller = labeller;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Component getListCellRendererComponent(final JList<?> list, final Object value,
            final int index, final boolean isSelected, final boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(labeller.apply((T) value));
        return this;
    }
}
