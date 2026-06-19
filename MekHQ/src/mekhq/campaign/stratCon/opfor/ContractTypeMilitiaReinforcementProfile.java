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
 * NOTICE: The MegaMek Organization is a non-profit group of volunteers
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

import megamek.common.annotations.Nullable;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;

/**
 * Lookup table mapping {@link AtBContractType} to a {@link MilitiaProfile} describing
 * when and how planetary-militia reinforcements arrive over the contract's life.
 *
 * <p>Planetary militia represent the local defending forces that commit to combat
 * while the defender is ascendant — ground vehicles and conventional infantry that
 * reinforce the static OpFor without gating the contract win. They are seeded as a
 * starting pool at contract acceptance and grow through morale-driven reinforcement.</p>
 *
 * <p>The trigger fires on monthly morale checks when (a) the contract's morale
 * has shifted <em>upward</em> (enemy is winning more than they were last month)
 * and (b) current morale is at or above the profile's threshold.</p>
 *
 * <p>Militia reinforcements use a separate cap ({@code militiaReinforcementEventsFired}
 * on {@link StratConOpForRoster}) so they do not compete with line-OpFor reinforcements.</p>
 *
 * <p>Only applies to attacker contracts in StratCon mode. Returns {@link #NEVER} for
 * defender contracts, covert work, and contract types where militia are not thematic.</p>
 */
public final class ContractTypeMilitiaReinforcementProfile {

    /**
     * Militia reinforcement parameters for a single contract type.
     *
     * @param triggerThreshold militia reinforce only when contract morale is at or above
     *                         this value (higher = enemy winning/ascendant)
     * @param probability      per-eligible-month chance to actually fire (0.0 to 1.0)
     * @param minFormations    minimum militia formations to add per event (inclusive)
     * @param maxFormations    maximum militia formations to add per event (inclusive)
     * @param eventCap         total militia reinforcement events allowed over contract life
     * @param minStarting      minimum militia formations in the starting pool (inclusive)
     * @param maxStarting      maximum militia formations in the starting pool (inclusive)
     */
    public record MilitiaProfile(
            AtBMoraleLevel triggerThreshold,
            double probability,
            int minFormations,
            int maxFormations,
            int eventCap,
            int minStarting,
            int maxStarting) {

        /** Returns true when this profile actually permits militia reinforcements. */
        public boolean isReinforcementAllowed() {
            return (probability > 0.0) && (eventCap > 0) && (maxFormations > 0);
        }

        /** Returns true when this profile seeds a starting militia pool. */
        public boolean hasStartingPool() {
            return maxStarting > 0;
        }
    }

    /** Sentinel returned for contract types where militia never appear. */
    private static final MilitiaProfile NEVER =
            new MilitiaProfile(AtBMoraleLevel.STALEMATE, 0.0, 0, 0, 0, 0, 0);

    /**
     * Planetary assault: full militia mobilization — significant starting pool,
     * generous reinforcement cap, and solid per-event size.
     */
    private static final MilitiaProfile ASSAULT =
            new MilitiaProfile(AtBMoraleLevel.ADVANCING, 0.55, 1, 2, 5, 2, 4);

    /**
     * Raid-style contracts: lighter militia presence, small starting pool,
     * limited reinforcement budget.
     */
    private static final MilitiaProfile RAID =
            new MilitiaProfile(AtBMoraleLevel.ADVANCING, 0.40, 1, 1, 3, 1, 2);

    /**
     * Irregular contracts (pirate hunt, guerrilla, mole hunt): occasional militia
     * showing up, minimal starting pool, low cap — these engagements are more covert.
     */
    private static final MilitiaProfile IRREGULAR =
            new MilitiaProfile(AtBMoraleLevel.ADVANCING, 0.30, 1, 1, 2, 0, 1);

    private ContractTypeMilitiaReinforcementProfile() {
    }

    /**
     * Returns the militia reinforcement profile for the given contract type.
     *
     * <p>Returns the {@link #NEVER} sentinel for unknown types, defender-favored
     * contracts (garrison, security, cadre), and covert contracts where planetary
     * militia are not thematic.</p>
     *
     * @param type the contract type (may be {@code null})
     * @return the profile (never null)
     */
    public static MilitiaProfile getProfile(@Nullable final AtBContractType type) {
        if (type == null) {
            return NEVER;
        }
        return switch (type) {
            case PLANETARY_ASSAULT -> ASSAULT;
            case OBJECTIVE_RAID, DIVERSIONARY_RAID,
                 RECON_RAID, EXTRACTION_RAID, OBSERVATION_RAID -> RAID;
            case PIRATE_HUNTING, GUERRILLA_WARFARE, MOLE_HUNTING -> IRREGULAR;
            default -> NEVER;
        };
    }
}
