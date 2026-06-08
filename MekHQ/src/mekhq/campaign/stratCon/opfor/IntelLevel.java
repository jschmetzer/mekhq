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
package mekhq.campaign.stratCon.opfor;

/**
 * Intelligence level for a static OpFor formation, describing how much the
 * player knows about its composition.
 *
 * <p>Ordinal order is significant: higher ordinal = more intel. Use
 * {@link #isAtLeast(IntelLevel)} for comparisons.</p>
 */
public enum IntelLevel {
    UNKNOWN,
    OBSERVED,
    FULL_INTEL;

    /**
     * Returns {@code true} if this level is at least as high as {@code other}.
     *
     * @param other the minimum level to compare against
     * @return {@code true} if {@code this.ordinal() >= other.ordinal()}
     */
    public boolean isAtLeast(IntelLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
