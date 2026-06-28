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

/**
 * Lifecycle state of a single challenger roster on a multi-challenger garrison contract.
 *
 * <p>A garrison world is struck over the life of a contract by successive distinct forces. Each is a separate
 * {@link StratConOpForRoster} that moves from {@link #ACTIVE} to one of the two terminal states as it is repelled or
 * destroyed.</p>
 */
public enum ChallengerStatus {
    /** Currently contesting the world — eligible for deployment. */
    ACTIVE,
    /** Routed/withdrawn; survivors gone. */
    WITHDRAWN,
    /** Eliminated by the player. */
    DEFEATED;

    /** @return {@code true} for any state other than {@link #ACTIVE}. */
    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
