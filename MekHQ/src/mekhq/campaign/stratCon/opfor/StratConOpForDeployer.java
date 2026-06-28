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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import megamek.client.bot.princess.PrincessException;
import megamek.common.annotations.Nullable;
import megamek.common.units.Entity;
import megamek.common.units.UnitType;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.BotForce;
import mekhq.campaign.mission.Scenario;
import megamek.client.ui.util.PlayerColour;
import megamek.common.icons.Camouflage;
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
 *   <li>Collect living formations assigned to that track. If the track has none
 *       left (already cleared), fall back to the global pool of living formations
 *       so stragglers parked on quiet tracks can still be engaged — the win
 *       condition is global, so the roster must remain fully reachable.</li>
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
        StratConTrackState track = scenario.getTrackForScenario(
                campaign, contract.getStratConCampaignState());
        if (track == null) {
            LOGGER.warn("selectAndDeploy: could not resolve track for scenario '{}'; falling back to dynamic path",
                    scenario.getName());
            return null;
        }
        return selectAndDeployInternal(Side.OPFOR, track.getDisplayableName(),
                scenarioUuid(scenario), roster, forceTemplate, targetBV, contract, campaign);
    }

    /**
     * Track-name overload for callers without a {@link StratConScenario} wrapper
     * (e.g. pure-AtB contracts with {@code useStaticOpForRoster} enabled). The
     * track name + scenario UUID are passed directly; the deployer skips
     * StratCon-specific track resolution and uses the provided values verbatim.
     */
    public static @Nullable BotForce selectAndDeploy(
            final String trackName,
            final UUID currentScenarioId,
            final StratConOpForRoster roster,
            final ScenarioForceTemplate forceTemplate,
            final double targetBV,
            final AtBContract contract,
            final Campaign campaign) {
        return selectAndDeployInternal(Side.OPFOR, trackName, currentScenarioId,
                roster, forceTemplate, targetBV, contract, campaign);
    }

    /**
     * Selects formations from the allied roster and builds a {@link BotForce}
     * for the given scenario.
     *
     * <p>Symmetric to {@link #selectAndDeploy} but uses the ally bot name /
     * colour / camouflage and assigns the Allied team; bot behavior is left at
     * Princess defaults.</p>
     */
    public static @Nullable BotForce selectAndDeployAlly(
            final StratConScenario scenario,
            final StratConOpForRoster roster,
            final ScenarioForceTemplate forceTemplate,
            final double targetBV,
            final AtBContract contract,
            final Campaign campaign) {
        StratConTrackState track = scenario.getTrackForScenario(
                campaign, contract.getStratConCampaignState());
        if (track == null) {
            LOGGER.warn("selectAndDeployAlly: could not resolve track for scenario '{}'; falling back to dynamic path",
                    scenario.getName());
            return null;
        }
        return selectAndDeployInternal(Side.ALLY, track.getDisplayableName(),
                scenarioUuid(scenario), roster, forceTemplate, targetBV, contract, campaign);
    }

    /** Track-name allied overload. See {@link #selectAndDeploy(String, UUID, StratConOpForRoster, ScenarioForceTemplate, double, AtBContract, Campaign)}. */
    public static @Nullable BotForce selectAndDeployAlly(
            final String trackName,
            final UUID currentScenarioId,
            final StratConOpForRoster roster,
            final ScenarioForceTemplate forceTemplate,
            final double targetBV,
            final AtBContract contract,
            final Campaign campaign) {
        return selectAndDeployInternal(Side.ALLY, trackName, currentScenarioId,
                roster, forceTemplate, targetBV, contract, campaign);
    }

    /** Distinguishes OpFor vs Ally branches in the internal helper. */
    enum Side { OPFOR, ALLY }

    /**
     * Shared selection-and-deploy implementation. Track resolution, formation
     * selection, unit materialisation, intel advancement, and recency stamping
     * are identical for both sides; bot name / colour / camo / team / behaviour
     * branch on {@code side}.
     */
    private static @Nullable BotForce selectAndDeployInternal(
            final Side side,
            final String trackName,
            final @Nullable UUID currentScenarioId,
            final StratConOpForRoster roster,
            final ScenarioForceTemplate forceTemplate,
            final double targetBV,
            final AtBContract contract,
            final Campaign campaign) {

        String logTag = (side == Side.OPFOR) ? "selectAndDeploy" : "selectAndDeployAlly";

        // Defer slots the ground roster cannot satisfy (DropShip, infantry, aero,
        // civilians, ...) to dynamic generation, which produces the correct unit
        // types. Without this, roster Meks would deploy under a mismatched force
        // label (e.g. a "Hostile DropShip" force made of Meks).
        if (!isStaticEligible(forceTemplate)) {
            LOGGER.info("{}: force template '{}' requires unit type '{}' the static roster cannot "
                    + "satisfy; falling back to dynamic path", logTag, forceTemplate.getForceName(),
                    forceTemplate.getAllowedUnitTypeName());
            return null;
        }

        // Sort and select formations, excluding any formation already committed to a
        // still-unfought scenario (another pending scenario, or another Opposing slot of
        // this same scenario) so the same units never appear in two places at once.
        Set<UUID> committedScenarioIds = committedScenarioIds(contract, currentScenarioId);
        List<StratConOpForFormation> selected = selectFormations(
                roster, trackName, forceTemplate.getMaxWeightClass(), targetBV, committedScenarioIds);

        if (selected.isEmpty()) {
            LOGGER.info("{}: no matching formations on track '{}'; falling back to dynamic path",
                    logTag, trackName);
            return null;
        }

        // Materialise entities
        List<Entity> entities = new ArrayList<>();
        for (StratConOpForFormation formation : selected) {
            for (StratConOpForUnit unit : formation.livingUnits(roster)) {
                // Self-heal legacy saves: a catastrophically damaged unit persisted as
                // READY before the fold-time fix would re-spawn as a wreck MegaMek cannot
                // load. Mark it DESTROYED so the formation can finally be eliminated, and
                // skip deployment.
                if (unit.isUnredeployableWreck()) {
                    unit.setStatus(Status.DESTROYED);
                    unit.setRevealed(true);
                    LOGGER.info("{}: self-healed non-redeployable wreck unit id={} to DESTROYED",
                            logTag, unit.getId());
                    continue;
                }
                Entity entity = OpForUnitMaterializer.deploy(unit, campaign);
                if (entity == null) {
                    LOGGER.warn("{}: materialisation failed for unit id={}; skipping",
                            logTag, unit.getId());
                } else {
                    entities.add(entity);
                }
            }
        }

        if (entities.isEmpty()) {
            LOGGER.warn("{}: all units failed to materialise on track '{}'; falling back to dynamic path",
                    logTag, trackName);
            return null;
        }

        // Build BotForce — branch on side for bot identity and team
        BotForce botForce = new BotForce();
        botForce.setFixedEntityList(entities);
        if (side == Side.OPFOR) {
            // Label and colour the force from the challenger's OWN identity, not the (possibly drifted) live contract
            // enemy. This is the root-cause fix for the DC-label/pirate-units bug.
            botForce.setName(challengerBotForceName(roster, contract, forceTemplate));
            PlayerColour colour = challengerColour(roster, contract);
            botForce.setColour(colour);
            botForce.setCamouflage(new Camouflage(Camouflage.COLOUR_CAMOUFLAGE, colour.name()));
            botForce.setTeam(ScenarioForceTemplate.TEAM_IDS.get(ForceAlignment.Opposing.ordinal()));

            // OpFor: conservative behaviour, posture-aware (don't auto-delete the finite roster)
            StratConOpForFormation behaviorSource = selected.stream()
                    .min(Comparator.comparingInt(f -> f.livingUnits(roster).size()))
                    .orElse(selected.get(0));
            OpForBehaviorSettingsBuilder.Posture posture =
                    OpForBehaviorSettingsBuilder.getPosture(contract.getContractType());
            try {
                botForce.setBehaviorSettings(
                        OpForBehaviorSettingsBuilder.forFormation(behaviorSource, roster, posture));
            } catch (PrincessException e) {
                LOGGER.warn("{}: could not build behavior settings; using default", logTag, e);
            }
        } else {
            botForce.setName(contract.getAllyBotName() + " " + forceTemplate.getForceName());
            botForce.setColour(contract.getAllyColour());
            botForce.setCamouflage(contract.getAllyCamouflage().clone());
            botForce.setTeam(ScenarioForceTemplate.TEAM_IDS.get(ForceAlignment.Allied.ordinal()));
            // Allies use the engaging-support profile (replaces Princess defaults).
            try {
                botForce.setBehaviorSettings(AllyBehaviorSettingsBuilder.buildSettings());
            } catch (PrincessException e) {
                LOGGER.warn("{}: could not build ally behavior settings; using default", logTag, e);
            }
        }

        // State updates (identical for both sides)
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
     * Returns {@code true} when the static roster can satisfy this force
     * template's required unit type.
     *
     * <p>The roster represents a ground force (BattleMeks plus militia), so only
     * the standard mixed-ground slot
     * ({@link ScenarioForceTemplate#SPECIAL_UNIT_TYPE_ATB_MIX}) and the pure-Mek
     * slot ({@link UnitType#MEK}) are eligible. Slots that require DropShips
     * ({@link UnitType#DROPSHIP}), infantry, aerospace, civilians, an aero mix,
     * etc. cannot be satisfied from the roster and must fall back to dynamic
     * generation — otherwise roster Meks would be deployed under a mismatched
     * force label such as a "DropShip" or "Infantry" force composed of Meks.</p>
     *
     * @param forceTemplate the enemy/ally force-template slot being filled
     * @return {@code true} if the static path may fill this slot from the roster
     */
    static boolean isStaticEligible(final ScenarioForceTemplate forceTemplate) {
        int allowedUnitType = forceTemplate.getAllowedUnitType();
        return (allowedUnitType == ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_MIX)
                || (allowedUnitType == UnitType.MEK);
    }

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
     * Display name for an OPFOR challenger's bot force: the challenger roster's own faction bot name, falling back to
     * the contract enemy name only when the roster carries no stamped identity (legacy/unstamped rosters).
     *
     * @param roster        the challenger roster being deployed
     * @param contract      the contract (fallback identity source)
     * @param forceTemplate the force template (supplies the force-name suffix)
     *
     * @return the bot force name
     */
    static String challengerBotForceName(final StratConOpForRoster roster, final AtBContract contract,
            final ScenarioForceTemplate forceTemplate) {
        String name = (roster.getEnemyBotName() != null) ? roster.getEnemyBotName() : contract.getEnemyBotName();
        return name + " " + forceTemplate.getForceName();
    }

    /**
     * Player colour for an OPFOR challenger's bot force: the challenger's own colour, falling back to the contract
     * colour when the roster carries no (or a malformed) stamped colour.
     *
     * @param roster   the challenger roster being deployed
     * @param contract the contract (fallback colour source)
     *
     * @return the player colour
     */
    static PlayerColour challengerColour(final StratConOpForRoster roster, final AtBContract contract) {
        if (roster.getEnemyColour() != null) {
            try {
                return PlayerColour.valueOf(roster.getEnemyColour());
            } catch (IllegalArgumentException ex) {
                // Malformed stored colour — degrade to the contract colour rather than throwing.
            }
        }
        return contract.getEnemyColour();
    }

    /**
     * Routing predicate for the allied static path: returns {@code true} only
     * when both conditions hold:
     * <ul>
     *   <li>the force alignment is {@link ForceAlignment#Allied}, and</li>
     *   <li>a non-null allied {@link StratConOpForRoster} is present.</li>
     * </ul>
     * Opposing, Third-party, and PlanetOwner templates fall through; Allied
     * templates with a null roster fall through to the dynamic ally path.
     *
     * @param alignment the resolved force alignment for the template
     * @param roster    the allied roster on the contract, or {@code null}
     * @return {@code true} iff the static ally path should be taken
     */
    public static boolean shouldUseStaticAllyPath(
            final ForceAlignment alignment,
            final @Nullable StratConOpForRoster roster) {
        return (alignment == ForceAlignment.Allied) && (roster != null);
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
        return selectFormations(roster, trackName, templateWeightClass, targetBV, Set.of());
    }

    /**
     * Overload that excludes formations already committed to a still-unfought scenario.
     *
     * <p>Forces are generated when a scenario is created/revealed, and several scenarios
     * can sit unfought at once. A formation committed to one of them must not be drawn
     * into another scenario's force, nor into a second Opposing slot of the same scenario
     * (e.g. a main OpFor plus a Convoy) — otherwise the same pilots/units appear in
     * multiple places at once. {@code advanceIntelForSelected} stamps each deployed
     * formation's {@code lastDeployedScenarioId}; this filters out any whose stamp points
     * at a scenario still in {@code committedScenarioIds}. Once a scenario is fought its
     * id leaves that set, freeing its surviving formations for future scenarios.</p>
     *
     * @param committedScenarioIds bridge UUIDs of every unfought scenario currently
     *                             holding formations (incl. the one being assembled);
     *                             empty/{@code null} skips the exclusion (single-slot
     *                             callers / tests)
     */
    static List<StratConOpForFormation> selectFormations(
            final StratConOpForRoster roster,
            final String trackName,
            final int templateWeightClass,
            final double targetBV,
            final @Nullable Set<UUID> committedScenarioIds) {

        List<StratConOpForFormation> candidates = roster.livingFormationsForTrack(trackName);
        excludeCommittedFormations(candidates, committedScenarioIds);

        // Global deploy fallback: once this track has no living formations of its
        // own, draw stragglers from other tracks so formations parked on quiet
        // tracks can still be engaged and destroyed. The contract-win condition
        // (StratConOpForRoster.checkEliminationStatus) is global across all tracks,
        // so without this the roster could never be fully eliminated and the win
        // would never fire. On-track formations are always preferred — the fallback
        // only engages when the track is already cleared. Militia are excluded:
        // they don't count toward the win and deploy only on their own track via
        // the per-track path above, so pulling them here would deploy militia as
        // standard line OpFor.
        if (candidates.isEmpty()) {
            candidates = roster.livingFormations();
            candidates.removeIf(StratConOpForFormation::isMilitia);
            excludeCommittedFormations(candidates, committedScenarioIds);
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        // Sort: weight-class match first (0 = match, 1 = no match), then
        // never-deployed first (null lastDeployedScenarioId = 0, non-null = 1).
        candidates.sort(Comparator
                .comparingInt((StratConOpForFormation f) ->
                        (f.getWeightClass() == templateWeightClass) ? 0 : 1)
                .thenComparingInt(f ->
                        f.getLastDeployedScenarioId() == null ? 0 : 1));

        List<StratConOpForFormation> selected = new ArrayList<>();
        double accumulatedBV = 0.0;

        // Pass 1: greedy fit — accumulate formations whose BV fits in the remaining budget.
        // Don't break on overshoot — a too-large formation just gets skipped; smaller
        // formations later in the list may still fit.
        for (StratConOpForFormation formation : candidates) {
            double formationBV = formation.currentBV(roster);
            if ((accumulatedBV + formationBV) <= targetBV) {
                selected.add(formation);
                accumulatedBV += formationBV;
            }
        }

        // Pass 2: if pass 1 selected nothing, accept the smallest single formation even
        // if it overshoots targetBV. The scenario should never be empty when living
        // formations exist on this track; the deployer's job is to put SOMETHING on the
        // field, not to honour a tight BV ceiling. Difficulty calibration happens via
        // roster sizing at contract creation, not per-scenario budget.
        if (selected.isEmpty()) {
            candidates.stream()
                    .min(Comparator.comparingDouble(f -> f.currentBV(roster)))
                    .ifPresent(selected::add);
        }

        return selected;
    }

    /**
     * Removes from {@code candidates} any formation committed to a still-unfought
     * scenario — i.e. whose {@code lastDeployedScenarioId} is in {@code committedScenarioIds}.
     * No-op when the set is {@code null} or empty.
     */
    private static void excludeCommittedFormations(
            final List<StratConOpForFormation> candidates,
            final @Nullable Set<UUID> committedScenarioIds) {
        if ((committedScenarioIds != null) && !committedScenarioIds.isEmpty()) {
            candidates.removeIf(f -> committedScenarioIds.contains(f.getLastDeployedScenarioId()));
        }
    }

    /**
     * Collects the bridge UUIDs of every scenario on {@code contract} that is still
     * unfought ({@link mekhq.campaign.mission.enums.ScenarioStatus#isCurrent()}), plus
     * the scenario currently being assembled. These are the scenarios whose static-OpFor
     * formations are "in use": a formation stamped with any of these ids must not be drawn
     * into another force until that scenario is resolved. UUIDs use the same
     * {@code new UUID(scenarioIntId, 0L)} bridge as {@code lastDeployedScenarioId}.
     *
     * @param contract          the active contract (may be {@code null})
     * @param currentScenarioId the scenario being assembled, or {@code null}
     * @return a set of committed scenario bridge UUIDs; never {@code null}
     */
    private static Set<UUID> committedScenarioIds(
            final @Nullable AtBContract contract,
            final @Nullable UUID currentScenarioId) {
        Set<UUID> ids = new HashSet<>();
        if (currentScenarioId != null) {
            ids.add(currentScenarioId);
        }
        if (contract != null) {
            for (Scenario scenario : contract.getScenarios()) {
                if ((scenario != null) && scenario.getStatus().isCurrent()) {
                    ids.add(new UUID(scenario.getId(), 0L));
                }
            }
        }
        return ids;
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
