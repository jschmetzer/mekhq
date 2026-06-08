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
 * and skill/quality jitter profile applied to the static OpFor roster.
 *
 * <p>The roster sizing model is: <em>initial formations = player combat teams
 * + contract-type modifier</em>, clamped to a sane range. The modifier reflects
 * how much of the planetary garrison is committed against the player's mission
 * at contract start. Garrison- and assault-type contracts ramp up; covert and
 * cadre work ramps down.</p>
 *
 * <p>The {@link JitterProfile} per contract type weights how far each formation's
 * skill and quality drift from the contract baseline. Heavy defender contracts
 * tilt upward (the defender commits real talent), pirate / cadre / irregular
 * contracts tilt downward (rabble with rare aces), the rest are balanced.</p>
 *
 * <p>Reinforcements (v1.1) layer on top of this baseline via morale-driven
 * monthly events; the initial roster does <em>not</em> attempt to model the
 * planet's full force.</p>
 */
public final class ContractTypeOpForModifier {

    /**
     * Probability weights for skill/quality jitter around the contract baseline.
     *
     * @param pBaseline probability the formation stays at the contract baseline
     * @param pAbove    probability the formation jitters one tier above baseline
     * @param pBelow    probability the formation jitters one tier below baseline
     */
    public record JitterProfile(double pBaseline, double pAbove, double pBelow) {

        /** Balanced default: 70% baseline, 15% above, 15% below. */
        public static final JitterProfile BALANCED = new JitterProfile(0.70, 0.15, 0.15);

        /** Defender-commits-talent: 60% baseline, 30% above, 10% below. */
        public static final JitterProfile ELITE_TILT = new JitterProfile(0.60, 0.30, 0.10);

        /** Rabble-with-rare-aces: 60% baseline, 10% above, 30% below. */
        public static final JitterProfile IRREGULAR_TILT = new JitterProfile(0.60, 0.10, 0.30);
    }

    private ContractTypeOpForModifier() {
    }

    /**
     * Returns the formation-count modifier for the given contract type.
     *
     * <p>Defaults to {@code 0} if the type is unknown.</p>
     *
     * @param type the contract type (may be {@code null})
     * @return signed modifier added to the player's combat-team count
     */
    public static int getModifier(final AtBContractType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            // Heavy garrison / assault — player faces a committed defender
            case PLANETARY_ASSAULT -> 3;
            case GARRISON_DUTY, RELIEF_DUTY -> 2;

            // Standard garrison-style engagements
            case SECURITY_DUTY, RIOT_DUTY, OBJECTIVE_RAID,
                 DIVERSIONARY_RAID, RETAINER -> 1;

            // Baseline match — player engages a slice equal to their own size
            case PIRATE_HUNTING, RECON_RAID, EXTRACTION_RAID,
                 OBSERVATION_RAID, GUERRILLA_WARFARE, MOLE_HUNTING -> 0;

            // Light / covert work — player faces less than their own size
            case CADRE_DUTY, ASSASSINATION, ESPIONAGE,
                 SABOTAGE, TERRORISM -> -1;
        };
    }

    /**
     * Returns the skill/quality jitter profile for the given contract type.
     *
     * <p>Heavy defender contracts (Planetary Assault, Garrison Duty, Relief Duty,
     * Assassination targets) tilt upward; pirate / cadre / irregular contracts
     * tilt downward; everything else is balanced.</p>
     *
     * @param type the contract type (may be {@code null})
     * @return the jitter profile (never null)
     */
    public static JitterProfile getJitterProfile(final AtBContractType type) {
        if (type == null) {
            return JitterProfile.BALANCED;
        }
        return switch (type) {
            case PLANETARY_ASSAULT, GARRISON_DUTY, RELIEF_DUTY, ASSASSINATION ->
                    JitterProfile.ELITE_TILT;
            case PIRATE_HUNTING, CADRE_DUTY, RIOT_DUTY, GUERRILLA_WARFARE,
                 SABOTAGE, TERRORISM ->
                    JitterProfile.IRREGULAR_TILT;
            default -> JitterProfile.BALANCED;
        };
    }
}
