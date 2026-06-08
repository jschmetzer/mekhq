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
package mekhq.campaign.stratCon.opfor;

import mekhq.campaign.force.CombatTeam;
import mekhq.campaign.universe.Faction;

/**
 * Determines standard formation sizes for static OpFor roster generation.
 *
 * <p>Delegates to {@link CombatTeam#getStandardFormationSize(Faction)}, which
 * encodes the BattleTech rules: Clan = 5, ComStar/WoB = 6, IS/Periphery = 4.</p>
 */
public final class FormationSchema {

    /** Utility class — no instantiation. */
    private FormationSchema() {
    }

    /**
     * Returns the standard formation size for the given faction.
     *
     * @param faction the enemy faction whose formation size to look up
     * @return number of units in a standard formation for this faction
     */
    public static int formationSize(final Faction faction) {
        return CombatTeam.getStandardFormationSize(faction);
    }
}
