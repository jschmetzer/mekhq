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
package mekhq.campaign.stratCon.opfor;

import megamek.client.bot.princess.BehaviorSettings;
import megamek.client.bot.princess.CardinalEdge;
import megamek.client.bot.princess.PrincessException;

/**
 * Builds a {@link BehaviorSettings} instance for allied bots in static-OpFor
 * scenarios. Replaces the previous Princess-default behavior with an
 * "engaging support" profile: actively closes, stays cohesive, doesn't flee
 * unless broken.
 *
 * <p>Princess default is 5 across every index. Our ally profile pushes
 * aggression and herd above neutral while keeping bravery and self-preservation
 * at neutral — allies engage but don't run away cheaply, and stay grouped so
 * they can mutually support.</p>
 */
public final class AllyBehaviorSettingsBuilder {

    private static final int ALLY_AGGRESSION  = 6; // closes with enemies
    private static final int ALLY_BRAVERY     = 5; // neutral — flees when actually crippled
    private static final int ALLY_SELF_PRES   = 5; // neutral defense
    private static final int ALLY_HERD        = 7; // stays grouped for mutual support
    private static final int ALLY_FALL_SHAME  = 5; // neutral

    private AllyBehaviorSettingsBuilder() {
    }

    /**
     * Builds an ally {@link BehaviorSettings}. Currently posture-independent;
     * future passes may vary by contract type.
     *
     * @return a freshly-constructed {@link BehaviorSettings}
     * @throws PrincessException if the description string is invalid
     */
    public static BehaviorSettings buildSettings() throws PrincessException {
        BehaviorSettings settings = new BehaviorSettings();
        settings.setDescription("STATIC_ALLY_SUPPORT");
        settings.setHyperAggressionIndex(ALLY_AGGRESSION);
        settings.setBraveryIndex(ALLY_BRAVERY);
        settings.setSelfPreservationIndex(ALLY_SELF_PRES);
        settings.setHerdMentalityIndex(ALLY_HERD);
        settings.setFallShameIndex(ALLY_FALL_SHAME);
        settings.setForcedWithdrawal(true);
        settings.setRetreatEdge(CardinalEdge.NEAREST);
        return settings;
    }
}
