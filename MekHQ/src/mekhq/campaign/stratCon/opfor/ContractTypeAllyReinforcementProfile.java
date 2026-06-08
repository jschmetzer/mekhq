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
 * Lookup table mapping {@link AtBContractType} to a reinforcement profile for
 * employer-provided allied support, fired when the player's situation degrades.
 *
 * <p>Symmetric to {@link ContractTypeReinforcementProfile} but mirrored across
 * the morale axis: ally reinforcements fire on monthly checks when contract
 * morale shifts <em>upward</em> (enemy is winning, player is losing) past a
 * contract-type-specific threshold. The employer's commander sends backup to
 * salvage the mission.</p>
 */
public final class ContractTypeAllyReinforcementProfile {

    /**
     * Reuses {@link ContractTypeReinforcementProfile.Profile} for the parameter
     * shape — same five fields. The semantic difference is in how
     * {@code triggerThreshold} is interpreted: for OpFor reinforcements the
     * check is {@code newMorale <= threshold}, for Ally reinforcements it is
     * {@code newMorale >= threshold}.
     */

    /** Sentinel returned for contract types where allied reinforcements never fire. */
    public static final ContractTypeReinforcementProfile.Profile NEVER =
            ContractTypeReinforcementProfile.NEVER;

    /** Heavy-employer-investment garrison contracts: ADVANCING trigger, generous. */
    private static final ContractTypeReinforcementProfile.Profile HEAVY_SUPPORT =
            new ContractTypeReinforcementProfile.Profile(
                    AtBMoraleLevel.ADVANCING, 0.50, 2, 3, 4);

    /** Standard garrison-style: ADVANCING trigger, moderate. */
    private static final ContractTypeReinforcementProfile.Profile MODERATE_SUPPORT =
            new ContractTypeReinforcementProfile.Profile(
                    AtBMoraleLevel.ADVANCING, 0.40, 1, 2, 3);

    /** Planetary assault: DOMINATING trigger, small (employer is reluctant — you knew the risks). */
    private static final ContractTypeReinforcementProfile.Profile RELUCTANT_SUPPORT =
            new ContractTypeReinforcementProfile.Profile(
                    AtBMoraleLevel.DOMINATING, 0.30, 1, 2, 3);

    private ContractTypeAllyReinforcementProfile() {
    }

    /**
     * Returns the allied reinforcement profile for the given contract type.
     *
     * <p>Most raid / covert / cadre work returns {@link #NEVER} — you signed
     * up for solo work and the employer doesn't send reinforcements.</p>
     *
     * @param type the contract type (may be {@code null})
     * @return the profile (never null)
     */
    public static ContractTypeReinforcementProfile.Profile getProfile(
            @Nullable final AtBContractType type) {
        if (type == null) {
            return NEVER;
        }
        return switch (type) {
            case GARRISON_DUTY, RELIEF_DUTY -> HEAVY_SUPPORT;
            case SECURITY_DUTY, RIOT_DUTY, RETAINER -> MODERATE_SUPPORT;
            case PLANETARY_ASSAULT -> RELUCTANT_SUPPORT;

            // No allied reinforcements — you're on your own.
            case PIRATE_HUNTING, OBJECTIVE_RAID, DIVERSIONARY_RAID,
                 RECON_RAID, EXTRACTION_RAID, OBSERVATION_RAID,
                 GUERRILLA_WARFARE, MOLE_HUNTING, CADRE_DUTY,
                 ASSASSINATION, ESPIONAGE, SABOTAGE, TERRORISM -> NEVER;
        };
    }
}
