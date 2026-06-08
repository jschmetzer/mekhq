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

    // -------------------------------------------------------------------------
    // Tier 1 — base (healthy formation)
    // -------------------------------------------------------------------------
    private static final int T1_AGGRESSION   = 3;
    private static final int T1_BRAVERY      = 3;
    private static final int T1_SELF_PRES    = 7;
    private static final int T1_HERD         = 6;
    private static final int T1_FALL_SHAME   = 7;

    // -------------------------------------------------------------------------
    // Tier 2 — formation depleted (< 50 % living)
    // -------------------------------------------------------------------------
    private static final int T2_AGGRESSION   = 2;
    private static final int T2_BRAVERY      = 2;
    private static final int T2_SELF_PRES    = 8;
    private static final int T2_HERD         = 7;
    private static final int T2_FALL_SHAME   = 8;

    // -------------------------------------------------------------------------
    // Tier 3 — roster critically low (< 30 % living)
    // -------------------------------------------------------------------------
    private static final int T3_AGGRESSION   = 1;
    private static final int T3_BRAVERY      = 1;
    private static final int T3_SELF_PRES    = 10;
    private static final int T3_HERD         = 8;
    private static final int T3_FALL_SHAME   = 9;

    // -------------------------------------------------------------------------
    // Thresholds
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

        // Determine tier — Tier 3 wins if both conditions hold
        int aggression;
        int bravery;
        int selfPres;
        int herd;
        int fallShame;

        if (rosterFraction < ROSTER_CRITICAL_THRESHOLD) {
            aggression = T3_AGGRESSION;
            bravery    = T3_BRAVERY;
            selfPres   = T3_SELF_PRES;
            herd       = T3_HERD;
            fallShame  = T3_FALL_SHAME;
        } else if (formationFraction < FORMATION_DEPLETION_THRESHOLD) {
            aggression = T2_AGGRESSION;
            bravery    = T2_BRAVERY;
            selfPres   = T2_SELF_PRES;
            herd       = T2_HERD;
            fallShame  = T2_FALL_SHAME;
        } else {
            aggression = T1_AGGRESSION;
            bravery    = T1_BRAVERY;
            selfPres   = T1_SELF_PRES;
            herd       = T1_HERD;
            fallShame  = T1_FALL_SHAME;
        }

        BehaviorSettings settings = new BehaviorSettings();
        settings.setDescription("STATIC_OPFOR_CONSERVATIVE");
        settings.setHyperAggressionIndex(aggression);
        settings.setBraveryIndex(bravery);
        settings.setSelfPreservationIndex(selfPres);
        settings.setHerdMentalityIndex(herd);
        settings.setFallShameIndex(fallShame);
        settings.setForcedWithdrawal(true);
        settings.setRetreatEdge(CardinalEdge.NEAREST);

        return settings;
    }
}
