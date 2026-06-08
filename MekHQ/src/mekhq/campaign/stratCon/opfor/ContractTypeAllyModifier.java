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

import mekhq.campaign.mission.enums.AtBContractType;

/**
 * Lookup table mapping {@link AtBContractType} to the formation-count modifier
 * and jitter profile applied to the static <em>allied</em> roster.
 *
 * <p>The allied roster sizing model mirrors the OpFor model:
 * <em>initial allied formations = player combat teams + ally modifier</em>,
 * clamped to a sane range. The modifier reflects how much friendly support
 * the employer actually commits to the player's mission. Garrison- and
 * relief-type contracts get real support; raid- and covert-type contracts
 * leave you on your own.</p>
 *
 * <p>This is the symmetric counterpart to {@link ContractTypeOpForModifier}.</p>
 */
public final class ContractTypeAllyModifier {

    private ContractTypeAllyModifier() {
    }

    /**
     * Returns the allied formation-count modifier for the given contract type.
     *
     * <p>Defaults to {@code 0} if the type is unknown.</p>
     *
     * @param type the contract type (may be {@code null})
     * @return signed modifier added to the player's combat-team count for ally sizing
     */
    public static int getModifier(final AtBContractType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            // Local garrison fights alongside you
            case GARRISON_DUTY -> 3;
            case RELIEF_DUTY -> 2;

            // Standard support
            case SECURITY_DUTY, RIOT_DUTY, RETAINER -> 1;

            // No-meaningful-support default
            case PIRATE_HUNTING, DIVERSIONARY_RAID, MOLE_HUNTING -> 0;

            // You signed up for solo work
            case CADRE_DUTY, RECON_RAID, EXTRACTION_RAID,
                 OBSERVATION_RAID, OBJECTIVE_RAID,
                 GUERRILLA_WARFARE -> -1;

            // Spearhead alone / covert
            case PLANETARY_ASSAULT, ASSASSINATION, ESPIONAGE,
                 SABOTAGE, TERRORISM -> -2;
        };
    }

    /**
     * Returns the skill/quality jitter profile for allied forces of the given
     * contract type.
     *
     * <p>Employer forces tend to be standard-trained regulars; "elite tilt"
     * applies to high-investment defender contracts where the employer commits
     * better quality. "Irregular tilt" applies to militia/insurgent contexts.</p>
     *
     * @param type the contract type (may be {@code null})
     * @return the jitter profile (never null)
     */
    public static ContractTypeOpForModifier.JitterProfile getJitterProfile(
            final AtBContractType type) {
        if (type == null) {
            return ContractTypeOpForModifier.JitterProfile.BALANCED;
        }
        return switch (type) {
            case GARRISON_DUTY, RELIEF_DUTY ->
                    ContractTypeOpForModifier.JitterProfile.ELITE_TILT;
            case RIOT_DUTY, GUERRILLA_WARFARE ->
                    ContractTypeOpForModifier.JitterProfile.IRREGULAR_TILT;
            default -> ContractTypeOpForModifier.JitterProfile.BALANCED;
        };
    }
}
