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
 * Lookup table mapping {@link AtBContractType} to the static OpFor's break
 * threshold — the fraction of the core battalion's establishment strength at or
 * below which the force is liable to break and withdraw rather than fight to
 * annihilation.
 *
 * <p>Higher threshold = breaks sooner (while still relatively strong); lower =
 * fights longer before quitting. The disposition mirrors the bot-posture split
 * in {@link OpForBehaviorSettingsBuilder}: a committed planetary defender with
 * its back to the wall holds nearly to the end; raiders, pirates, and irregulars
 * preserve themselves and quit early; everyone else breaks around a third
 * strength.</p>
 *
 * <p>This is the <em>contract-type baseline</em> only. The break evaluation
 * further modifies it by the enemy's campaign morale and fight-to-the-death
 * faction traits (Clan / Word of Blake), which live in the resolution path where
 * the enemy faction is in scope.</p>
 */
public final class ContractTypeBreakProfile {

    /** Committed defenders with their backs to the wall hold to near-annihilation. */
    public static final double HOLD_FAST = 0.15;

    /** Most forces break once worn to roughly a third of establishment strength. */
    public static final double STANDARD = 0.30;

    /** Raiders, pirates, and irregulars preserve themselves and quit early. */
    public static final double BRITTLE = 0.45;

    private ContractTypeBreakProfile() {
    }

    /**
     * Returns the base break threshold for the given contract type.
     *
     * <p>Defaults to {@link #STANDARD} for {@code null} or unknown types.</p>
     *
     * @param type the contract type (may be {@code null})
     * @return the base break threshold, a fraction in {@code (0, 1)}
     */
    public static double getBreakThreshold(final AtBContractType type) {
        if (type == null) {
            return STANDARD;
        }
        return switch (type) {
            // Committed planetary defender — holds to the last
            case PLANETARY_ASSAULT -> HOLD_FAST;

            // Raiders / pirates / irregulars — self-preserving, quit early
            case PIRATE_HUNTING, GUERRILLA_WARFARE, RIOT_DUTY,
                 RECON_RAID, DIVERSIONARY_RAID, OBSERVATION_RAID,
                 EXTRACTION_RAID, MOLE_HUNTING -> BRITTLE;

            // Everyone else breaks around a third strength
            default -> STANDARD;
        };
    }
}
