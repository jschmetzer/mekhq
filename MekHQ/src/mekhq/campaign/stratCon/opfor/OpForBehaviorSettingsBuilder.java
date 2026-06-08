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

import megamek.client.bot.princess.BehaviorSettings;
import megamek.client.bot.princess.CardinalEdge;
import megamek.client.bot.princess.PrincessException;

/**
 * Builds conservative {@link BehaviorSettings} for the static-OpFor bot,
 * scaling aggression downward as the formation and overall roster sustain
 * casualties.
 *
 * <h2>Design rationale</h2>
 * <p>The finite roster must not be squandered by reckless Princess behavior.
 * Three tiers are defined, each strictly more conservative than the previous:</p>
 *
 * <table border="1">
 *   <caption>Behavior tiers</caption>
 *   <tr><th>Tier</th><th>Condition</th>
 *       <th>Aggression</th><th>Bravery</th>
 *       <th>SelfPres</th><th>Herd</th><th>FallShame</th></tr>
 *   <tr><td>1 (base)</td><td>healthy</td>
 *       <td>3</td><td>3</td><td>7</td><td>6</td><td>7</td></tr>
 *   <tr><td>2</td><td>formationFraction &lt; 0.5</td>
 *       <td>2</td><td>2</td><td>8</td><td>7</td><td>8</td></tr>
 *   <tr><td>3</td><td>rosterFraction &lt; 0.3</td>
 *       <td>1</td><td>1</td><td>10</td><td>8</td><td>9</td></tr>
 * </table>
 *
 * <p>Princess default for all indices is 5.  All our tiers are below 5 for
 * aggression/bravery and above 5 for self-preservation/herd/fallShame.</p>
 *
 * <p>All methods are {@code static}; this class is not intended to be
 * instantiated.</p>
 */
public class OpForBehaviorSettingsBuilder {

    /**
     * Contract-derived posture for the OpFor's base (Tier 1) behavior. Tier 2 and
     * Tier 3 are posture-independent — once a formation or roster is depleted,
     * preservation takes over regardless of starting posture.
     */
    public enum Posture {
        /** Player attacks; OpFor defends. Conservative hold-and-fall-back. */
        DEFENDER,
        /** Player defends; OpFor presses. Aggressive but not reckless. */
        ATTACKER,
        /** Pirates / guerrillas / militia. Loose coordination, opportunistic. */
        IRREGULAR
    }

    // -------------------------------------------------------------------------
    // Tier 1 — base values per posture
    // -------------------------------------------------------------------------
    // DEFENDER (player attacking) — current conservative tuning
    private static final int T1_DEF_AGGRESSION = 3, T1_DEF_BRAVERY = 3,
            T1_DEF_SELF_PRES = 7, T1_DEF_HERD = 6, T1_DEF_FALL_SHAME = 7;

    // ATTACKER (OpFor pressing the player) — aggressive but disciplined
    private static final int T1_ATK_AGGRESSION = 5, T1_ATK_BRAVERY = 5,
            T1_ATK_SELF_PRES = 6, T1_ATK_HERD = 6, T1_ATK_FALL_SHAME = 6;

    // IRREGULAR (pirates / guerrillas / militia) — loose, opportunistic
    private static final int T1_IRR_AGGRESSION = 4, T1_IRR_BRAVERY = 3,
            T1_IRR_SELF_PRES = 6, T1_IRR_HERD = 4, T1_IRR_FALL_SHAME = 5;

    // -------------------------------------------------------------------------
    // Tier 2 — formation depleted (≤ 50 % living), posture-independent
    // -------------------------------------------------------------------------
    private static final int T2_AGGRESSION   = 2;
    private static final int T2_BRAVERY      = 2;
    private static final int T2_SELF_PRES    = 8;
    private static final int T2_HERD         = 7;
    private static final int T2_FALL_SHAME   = 8;

    // -------------------------------------------------------------------------
    // Tier 3 — roster critically low (≤ 30 % living), posture-independent
    // -------------------------------------------------------------------------
    private static final int T3_AGGRESSION   = 1;
    private static final int T3_BRAVERY      = 1;
    private static final int T3_SELF_PRES    = 10;
    private static final int T3_HERD         = 8;
    private static final int T3_FALL_SHAME   = 9;

    // -------------------------------------------------------------------------
    // Thresholds — use ≤ so 50 % living triggers Tier 2 and 30 % triggers Tier 3.
    // -------------------------------------------------------------------------
    private static final double FORMATION_DEPLETION_THRESHOLD = 0.5;
    private static final double ROSTER_CRITICAL_THRESHOLD     = 0.3;

    /** Utility class — no instantiation. */
    private OpForBehaviorSettingsBuilder() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link BehaviorSettings} instance calibrated to the current
     * health of {@code formation} and the overall {@code roster}.
     *
     * <p>Tier 3 takes precedence over Tier 2; Tier 2 takes precedence over
     * Tier 1.  Both forced withdrawal and retreat toward the nearest board edge
     * are always enabled.</p>
     *
     * @param formation the formation whose behavior is being configured
     * @param roster    the full OpFor roster (used to compute roster-level health)
     * @return a freshly-constructed {@link BehaviorSettings}
     * @throws PrincessException if the description string is null or blank
     *         (should never occur with a hard-coded description)
     */
    public static BehaviorSettings forFormation(final StratConOpForFormation formation,
            final StratConOpForRoster roster) throws PrincessException {
        return forFormation(formation, roster, Posture.DEFENDER);
    }

    /**
     * Posture-aware variant. Tier 1 base values vary by posture; Tier 2 and
     * Tier 3 are posture-independent (preservation overrides intent when the
     * formation or roster is damaged).
     *
     * @param formation the formation whose behavior is being configured
     * @param roster    the full OpFor roster (used to compute roster-level health)
     * @param posture   the contract-derived starting posture
     */
    public static BehaviorSettings forFormation(final StratConOpForFormation formation,
            final StratConOpForRoster roster,
            final Posture posture) throws PrincessException {

        // Formation-level health fraction
        int totalInFormation = formation.getUnitIds().size();
        int livingInFormation = formation.livingUnits(roster).size();
        double formationFraction = (totalInFormation > 0)
                ? (double) livingInFormation / totalInFormation
                : 0.0;

        // Roster-level health fraction
        int totalInRoster = roster.getUnitList().size();
        int livingInRoster = roster.livingUnits().size();
        double rosterFraction = (totalInRoster > 0)
                ? (double) livingInRoster / totalInRoster
                : 0.0;

        // Determine tier — Tier 3 wins if both conditions hold.
        // Use ≤ so 50% / 30% boundaries actually trigger the next tier.
        int aggression;
        int bravery;
        int selfPres;
        int herd;
        int fallShame;
        String tag;

        if (rosterFraction <= ROSTER_CRITICAL_THRESHOLD) {
            aggression = T3_AGGRESSION;
            bravery    = T3_BRAVERY;
            selfPres   = T3_SELF_PRES;
            herd       = T3_HERD;
            fallShame  = T3_FALL_SHAME;
            tag = "STATIC_OPFOR_T3_CRITICAL";
        } else if (formationFraction <= FORMATION_DEPLETION_THRESHOLD) {
            aggression = T2_AGGRESSION;
            bravery    = T2_BRAVERY;
            selfPres   = T2_SELF_PRES;
            herd       = T2_HERD;
            fallShame  = T2_FALL_SHAME;
            tag = "STATIC_OPFOR_T2_DEPLETED";
        } else {
            switch (posture) {
                case ATTACKER -> {
                    aggression = T1_ATK_AGGRESSION;
                    bravery    = T1_ATK_BRAVERY;
                    selfPres   = T1_ATK_SELF_PRES;
                    herd       = T1_ATK_HERD;
                    fallShame  = T1_ATK_FALL_SHAME;
                    tag = "STATIC_OPFOR_T1_ATTACKER";
                }
                case IRREGULAR -> {
                    aggression = T1_IRR_AGGRESSION;
                    bravery    = T1_IRR_BRAVERY;
                    selfPres   = T1_IRR_SELF_PRES;
                    herd       = T1_IRR_HERD;
                    fallShame  = T1_IRR_FALL_SHAME;
                    tag = "STATIC_OPFOR_T1_IRREGULAR";
                }
                case DEFENDER -> {
                    aggression = T1_DEF_AGGRESSION;
                    bravery    = T1_DEF_BRAVERY;
                    selfPres   = T1_DEF_SELF_PRES;
                    herd       = T1_DEF_HERD;
                    fallShame  = T1_DEF_FALL_SHAME;
                    tag = "STATIC_OPFOR_T1_DEFENDER";
                }
                default -> throw new IllegalStateException("Unknown posture: " + posture);
            }
        }

        BehaviorSettings settings = new BehaviorSettings();
        settings.setDescription(tag);
        settings.setHyperAggressionIndex(aggression);
        settings.setBraveryIndex(bravery);
        settings.setSelfPreservationIndex(selfPres);
        settings.setHerdMentalityIndex(herd);
        settings.setFallShameIndex(fallShame);
        settings.setForcedWithdrawal(true);
        settings.setRetreatEdge(CardinalEdge.NEAREST);

        return settings;
    }

    /**
     * Maps an {@link AtBContractType} to the OpFor's starting {@link Posture}.
     *
     * <ul>
     *   <li>OpFor as ATTACKER — player defends — Garrison Duty, Relief Duty,
     *       Security Duty.</li>
     *   <li>OpFor as IRREGULAR — pirate-style / militia-style — Pirate Hunting,
     *       Guerrilla Warfare, Riot Duty (irregulars not regular attackers).</li>
     *   <li>OpFor as DEFENDER (default) — player attacks fixed positions —
     *       Planetary Assault, Objective Raid, Diversionary Raid, Recon Raid,
     *       Extraction Raid, etc.</li>
     * </ul>
     *
     * @param type the contract type (may be null)
     * @return the posture (never null; defaults to DEFENDER)
     */
    public static Posture getPosture(
            final mekhq.campaign.mission.enums.AtBContractType type) {
        if (type == null) {
            return Posture.DEFENDER;
        }
        return switch (type) {
            case GARRISON_DUTY, RELIEF_DUTY, SECURITY_DUTY -> Posture.ATTACKER;
            case PIRATE_HUNTING, GUERRILLA_WARFARE, RIOT_DUTY -> Posture.IRREGULAR;
            default -> Posture.DEFENDER;
        };
    }
}
