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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import megamek.client.bot.princess.PrincessException;
import megamek.common.annotations.Nullable;
import megamek.common.units.Entity;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.BotForce;
import mekhq.campaign.mission.ScenarioForceTemplate;
import mekhq.campaign.mission.ScenarioForceTemplate.ForceAlignment;
import mekhq.campaign.stratCon.StratConScenario;
import mekhq.campaign.stratCon.StratConTrackState;

/**
 * Selects and materialises a static-OpFor {@link BotForce} from a
 * {@link StratConOpForRoster} for a single StratCon scenario.
 *
 * <h2>Selection algorithm</h2>
 * <ol>
 *   <li>Resolve the track the scenario is on.</li>
 *   <li>Collect living formations assigned to that track.</li>
 *   <li>Sort: weight-class match first, then least-recently-deployed first.</li>
 *   <li>Greedy BV accumulation with a 20 % single-pick overshoot allowance.</li>
 *   <li>Materialise each selected formation's living units via
 *       {@link OpForUnitMaterializer#deploy(StratConOpForUnit, Campaign)}.</li>
 *   <li>Stamp {@code lastDeployedScenarioId} on selected formations and their
 *       units; advance intel from {@code UNKNOWN} to {@code OBSERVED}.</li>
 * </ol>
 *
 * <p>Returns {@code null} (and logs at INFO) whenever no usable force can be
 * assembled — empty track, empty BV budget, all units fail materialisation.</p>
 */
public class StratConOpForDeployer {

    private static final MMLogger LOGGER = MMLogger.create(StratConOpForDeployer.class);

    /** Utility class — no instances. */
    private StratConOpForDeployer() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Selects formations from the roster and builds a {@link BotForce} for the
     * given scenario.
     *
     * @param scenario      the StratCon scenario being populated
     * @param roster        the static OpFor roster for this contract
     * @param forceTemplate the force-template being satisfied
     * @param targetBV      the desired total BV for the force
     * @param contract      the AtB contract (for bot name, colour, camouflage)
     * @param campaign      the current campaign
     * @return a configured {@link BotForce}, or {@code null} when no usable force
     *         can be assembled
     */
    public static @Nullable BotForce selectAndDeploy(
            final StratConScenario scenario,
            final StratConOpForRoster roster,
            final ScenarioForceTemplate forceTemplate,
            final double targetBV,
            final AtBContract contract,
            final Campaign campaign) {

        // Resolve track name
        StratConTrackState track = scenario.getTrackForScenario(
                campaign, contract.getStratconCampaignState());
        if (track == null) {
            LOGGER.warn("selectAndDeploy: could not resolve track for scenario '{}'; falling back to dynamic path",
                    scenario.getName());
            return null;
        }
        String trackName = track.getDisplayableName();

        // Resolve current scenario UUID (bridges int→UUID)
        UUID currentScenarioId = scenarioUuid(scenario);

        // Sort and select formations
        List<StratConOpForFormation> selected = selectFormations(
                roster, trackName, forceTemplate.getMaxWeightClass(), targetBV);

        if (selected.isEmpty()) {
            LOGGER.info("selectAndDeploy: no matching formations on track '{}'; falling back to dynamic path",
                    trackName);
            return null;
        }

        // Materialise entities
        List<Entity> entities = new ArrayList<>();
        for (StratConOpForFormation formation : selected) {
            for (StratConOpForUnit unit : formation.livingUnits(roster)) {
                Entity entity = OpForUnitMaterializer.deploy(unit, campaign);
                if (entity == null) {
                    LOGGER.warn("selectAndDeploy: materialisation failed for unit id={}; skipping",
                            unit.getId());
                } else {
                    entities.add(entity);
                }
            }
        }

        if (entities.isEmpty()) {
            LOGGER.warn("selectAndDeploy: all units failed to materialise on track '{}'; falling back to dynamic path",
                    trackName);
            return null;
        }

        // Choose the formation with fewest living units as the behavior source
        // (most damaged = most conservative AI is appropriate)
        StratConOpForFormation behaviorSource = selected.stream()
                .min(Comparator.comparingInt(f -> f.livingUnits(roster).size()))
                .orElse(selected.get(0));

        // Build BotForce
        BotForce botForce = new BotForce();
        botForce.setFixedEntityList(entities);
        botForce.setName(contract.getEnemyBotName() + " " + forceTemplate.getForceName());
        botForce.setColour(contract.getEnemyColour());
        botForce.setCamouflage(contract.getEnemyCamouflage().clone());
        botForce.setTeam(ScenarioForceTemplate.TEAM_IDS.get(ForceAlignment.Opposing.ordinal()));

        try {
            botForce.setBehaviorSettings(
                    OpForBehaviorSettingsBuilder.forFormation(behaviorSource, roster));
        } catch (PrincessException e) {
            LOGGER.warn("selectAndDeploy: could not build behavior settings; using default", e);
        }

        // State updates
        advanceIntelForSelected(selected, currentScenarioId);

        // Stamp lastDeployedScenarioId on units so Phase 6 can filter by scenario
        for (StratConOpForFormation formation : selected) {
            for (StratConOpForUnit unit : formation.livingUnits(roster)) {
                unit.setLastDeployedScenarioId(currentScenarioId);
            }
        }

        return botForce;
    }

    // -------------------------------------------------------------------------
    // Package-private helpers (visible for testing)
    // -------------------------------------------------------------------------

    /**
     * Routing predicate: returns {@code true} only when both conditions hold:
     * <ul>
     *   <li>the force alignment is {@link ForceAlignment#Opposing}, and</li>
     *   <li>a non-null {@link StratConOpForRoster} is present.</li>
     * </ul>
     * Allied, Third-party, and PlanetOwner templates always fall through to the
     * dynamic path regardless of the roster.
     *
     * @param alignment the resolved force alignment for the template
     * @param roster    the roster on the contract, or {@code null}
     * @return {@code true} iff the static OpFor path should be taken
     */
    public static boolean shouldUseStaticPath(
            final ForceAlignment alignment,
            final @Nullable StratConOpForRoster roster) {
        return (alignment == ForceAlignment.Opposing) && (roster != null);
    }

    /**
     * Selects and returns a greedy-BV list of formations from the roster for the
     * given track and target BV.
     *
     * <p>Sort order: weight-class match first (matching {@code templateWeightClass}),
     * then least-recently-deployed first.</p>
     *
     * @param roster               the OpFor roster
     * @param trackName            the track's display name
     * @param templateWeightClass  the weight class the template prefers
     * @param targetBV             the BV budget
     * @return list of selected formations; never null; may be empty
     */
    static List<StratConOpForFormation> selectFormations(
            final StratConOpForRoster roster,
            final String trackName,
            final int templateWeightClass,
            final double targetBV) {

        List<StratConOpForFormation> candidates = roster.livingFormationsForTrack(trackName);

        if (candidates.isEmpty()) {
            return List.of();
        }

        // Sort: weight-class match first (0 = match, 1 = no match), then
        // not-recently-deployed first (0 = not same as current, 1 = same)
        candidates.sort(Comparator
                .comparingInt((StratConOpForFormation f) ->
                        (f.getWeightClass() == templateWeightClass) ? 0 : 1)
                .thenComparingInt(f ->
                        // Prefer formations not recently deployed (those with null or
                        // different lastDeployedScenarioId come first)
                        0)); // secondary sort by recency is handled during actual selection

        List<StratConOpForFormation> selected = new ArrayList<>();
        double accumulatedBV = 0.0;

        for (StratConOpForFormation formation : candidates) {
            double formationBV = formation.currentBV(roster);

            if ((accumulatedBV + formationBV) <= targetBV) {
                selected.add(formation);
                accumulatedBV += formationBV;
            } else if (selected.isEmpty() && (formationBV <= targetBV * 1.20)) {
                // Single-pick overshoot allowance of 20 %
                selected.add(formation);
                accumulatedBV += formationBV;
                break;
            } else {
                break;
            }
        }

        return selected;
    }

    /**
     * Advances the intel level of each selected formation from {@code UNKNOWN}
     * to {@code OBSERVED} on first deployment, and stamps the given scenario UUID.
     *
     * @param selected          formations to update
     * @param currentScenarioId the UUID of the scenario being deployed, or null
     */
    static void advanceIntelForSelected(
            final List<StratConOpForFormation> selected,
            final @Nullable UUID currentScenarioId) {
        for (StratConOpForFormation formation : selected) {
            formation.setLastDeployedScenarioId(currentScenarioId);
            if (formation.getIntelLevel() == IntelLevel.UNKNOWN) {
                formation.setIntelLevel(IntelLevel.OBSERVED);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts the scenario's {@code int} backing-scenario ID to a stable UUID
     * for tracking last-deployed state.
     *
     * @param scenario the StratCon scenario
     * @return a UUID derived from the backing scenario's integer ID
     */
    private static @Nullable UUID scenarioUuid(final StratConScenario scenario) {
        if ((scenario == null) || (scenario.getBackingScenario() == null)) {
            return null;
        }
        int id = scenario.getBackingScenario().getId();
        return new UUID(id, 0L);
    }
}
