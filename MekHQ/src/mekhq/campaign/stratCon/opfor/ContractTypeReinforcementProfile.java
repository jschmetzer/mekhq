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

import megamek.common.annotations.Nullable;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;

/**
 * Lookup table mapping {@link AtBContractType} to a {@link Profile} describing
 * when and how reinforcements arrive over the contract's life.
 *
 * <p>Reinforcements model the in-fiction enemy commander pressing an advantage —
 * committing additional parked forces (planetary militia, allied pirate bands,
 * second-echelon reserves) to exploit a winning position. They taper off as the
 * enemy is ground down, rather than arriving while the enemy is collapsing.</p>
 *
 * <p>The trigger fires on monthly morale checks when (a) the contract's morale
 * has shifted <em>upward</em> (enemy is winning more than they were last month)
 * and (b) current morale is at or above the profile's threshold.</p>
 *
 * <p>Each profile has a hard cap on total events to prevent long contracts from
 * snowballing the OpFor roster beyond playability.</p>
 */
public final class ContractTypeReinforcementProfile {

    /**
     * Reinforcement parameters for a single contract type.
     *
     * @param triggerThreshold reinforcements eligible only when contract morale is at or above
     *                         this value (higher = enemy winning/ascendant); the enemy commits
     *                         reinforcements while it is on the offensive, not while collapsing
     * @param probability      per-eligible-month chance to actually fire (0.0 to 1.0)
     * @param minFormations    minimum formations to add per event (inclusive)
     * @param maxFormations    maximum formations to add per event (inclusive)
     * @param eventCap         total reinforcement events allowed over contract life
     */
    public record Profile(
            AtBMoraleLevel triggerThreshold,
            double probability,
            int minFormations,
            int maxFormations,
            int eventCap) {

        /** Returns true when this profile actually permits reinforcements. */
        public boolean isReinforcementAllowed() {
            return probability > 0.0 && eventCap > 0 && maxFormations > 0;
        }
    }

    /** Sentinel returned for contract types where reinforcements never fire. */
    public static final Profile NEVER = new Profile(
            AtBMoraleLevel.STALEMATE, 0.0, 0, 0, 0);

    /** Heavy-defender contracts press hardest: only while DOMINATING, but generous size and cap. */
    private static final Profile HEAVY_DEFENDER = new Profile(
            AtBMoraleLevel.DOMINATING, 0.60, 2, 4, 6);

    /** Standard garrison-style engagements: ADVANCING trigger, moderate parameters. */
    private static final Profile GARRISON = new Profile(
            AtBMoraleLevel.ADVANCING, 0.50, 2, 3, 4);

    /** Raid-style contracts: ADVANCING trigger, small reinforcement bursts. */
    private static final Profile RAID = new Profile(
            AtBMoraleLevel.ADVANCING, 0.50, 1, 2, 3);

    /** Security-style: ADVANCING trigger; small bursts. */
    private static final Profile SECURITY = new Profile(
            AtBMoraleLevel.ADVANCING, 0.40, 1, 2, 3);

    /** Irregular contracts (pirate hunt, riot, etc.): ADVANCING trigger, rare and small. */
    private static final Profile IRREGULAR = new Profile(
            AtBMoraleLevel.ADVANCING, 0.30, 1, 1, 2);

    private ContractTypeReinforcementProfile() {
    }

    /**
     * Returns the reinforcement profile for the given contract type.
     *
     * <p>Returns {@link #NEVER} for unknown types or those where reinforcements
     * shouldn't fire (covert work, cadre training).</p>
     *
     * @param type the contract type (may be {@code null})
     * @return the profile (never null)
     */
    public static Profile getProfile(@Nullable final AtBContractType type) {
        if (type == null) {
            return NEVER;
        }
        return switch (type) {
            case PLANETARY_ASSAULT -> HEAVY_DEFENDER;
            case GARRISON_DUTY, RELIEF_DUTY -> GARRISON;
            case OBJECTIVE_RAID, DIVERSIONARY_RAID -> RAID;
            case SECURITY_DUTY, RETAINER, RECON_RAID,
                 EXTRACTION_RAID, OBSERVATION_RAID -> SECURITY;
            case PIRATE_HUNTING, RIOT_DUTY,
                 GUERRILLA_WARFARE, MOLE_HUNTING -> IRREGULAR;
            case CADRE_DUTY, ASSASSINATION, ESPIONAGE,
                 SABOTAGE, TERRORISM -> NEVER;
            default -> NEVER;
        };
    }
}
