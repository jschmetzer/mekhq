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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import megamek.common.annotations.Nullable;
import megamek.common.enums.SkillLevel;
import megamek.common.loaders.MekSummary;
import megamek.common.loaders.MekSummaryCache;
import megamek.common.units.EntityMovementMode;
import megamek.common.units.EntityWeightClass;
import megamek.common.units.UnitType;
import megamek.logging.MMLogger;
import megamek.client.ui.util.PlayerColour;
import mekhq.campaign.Campaign;
import mekhq.campaign.camOpsReputation.ReputationController;
import mekhq.campaign.mission.AtBDynamicScenarioFactory;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.UnitGeneratorParameters;

import static megamek.common.units.UnitType.MEK;

/**
 * Builds a static OpFor roster for a StratCon contract.
 *
 * <p>All public methods are static; this class is not instantiated.</p>
 *
 * <p>The roster size is {@code ceil(playerCombatTeams * padding)} plus a contract-type
 * modifier from {@link ContractTypeOpForModifier}, clamped to
 * {@code [max(ABSOLUTE_MIN_FORMATIONS, floor), MAX_FORMATIONS]}. This represents the
 * slice of the planetary garrison actually committed against the player's mission —
 * not the planet's full force. Reinforcements (v1.1) layer on via morale-driven events;
 * initial sizing is static.</p>
 */
public final class StratConOpForRosterBuilder {

    private static final MMLogger LOGGER = MMLogger.create(StratConOpForRosterBuilder.class);

    /** Absolute floor on formation count; the configurable floor option cannot go below this. */
    static final int ABSOLUTE_MIN_FORMATIONS = 1;

    /** Maximum formation count regardless of player size or contract type. */
    static final int MAX_FORMATIONS = 20;

    /** Lowest skill level a jittered formation can drop to. */
    private static final SkillLevel JITTER_SKILL_FLOOR = SkillLevel.GREEN;

    /** Highest skill level a jittered formation can rise to. */
    private static final SkillLevel JITTER_SKILL_CEILING = SkillLevel.ELITE;

    /** Lowest quality value (F-rating). */
    private static final int QUALITY_FLOOR = 0;

    /** Highest quality value (A-rating). */
    private static final int QUALITY_CEILING = 5;

    /** Fraction of militia units generated as conventional infantry (rest are ground combat vehicles). */
    static final double MILITIA_INFANTRY_FRACTION = 0.25;

    /** Ground combat-vehicle movement modes for militia (no VTOLs, no naval). */
    private static final Set<EntityMovementMode> MILITIA_VEE_MODES = EnumSet.of(
            EntityMovementMode.TRACKED,
            EntityMovementMode.WHEELED,
            EntityMovementMode.HOVER,
            EntityMovementMode.WIGE);

    /** Militia unit quality — low (F/E range). */
    private static final int MILITIA_QUALITY = 1;

    /** Militia baseline skill — green troops with occasional regulars. */
    private static final SkillLevel MILITIA_BASE_SKILL = SkillLevel.GREEN;

    /** Jitter profile used for militia skill — skews below baseline, ceiling clamped to REGULAR. */
    private static final ContractTypeOpForModifier.JitterProfile MILITIA_JITTER =
            ContractTypeOpForModifier.JitterProfile.IRREGULAR_TILT;

    /** Utility class — no instantiation. */
    private StratConOpForRosterBuilder() {
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Builds a complete static OpFor roster for the given contract.
     *
     * <p>Generates a fixed number of formations — the count computed by
     * {@link #computeInitialFormationCount} — with no BV accumulation. Each
     * formation is assigned to a track via uniform-random selection.</p>
     *
     * @param campaign      the active campaign
     * @param contract      the AtB contract being initialised
     * @param campaignState the StratCon campaign state attached to the contract
     * @return a freshly-populated roster; never null
     */
    public static StratConOpForRoster buildForContract(final Campaign campaign,
            final AtBContract contract,
            final StratConCampaignState campaignState) {

        int formationCount = computeInitialFormationCount(campaign, contract);
        ContractTypeOpForModifier.JitterProfile jitterProfile =
                ContractTypeOpForModifier.getJitterProfile(contract.getContractType());

        StratConOpForRoster roster = buildRosterInternal(
                "OpFor",
                campaign, contract,
                trackNamesFromCampaignState(campaignState),
                contract.getEnemy(),
                contract.getEnemyCode(),
                contract.getEnemySkill(),
                contract.getEnemyQuality(),
                formationCount,
                jitterProfile);

        // Seed militia starting pool when enabled and the player is the attacker.
        if ((campaign.getCampaignOptions().isUseStaticOpForMilitia())
                && contract.isPlayerAttacker()
                && (campaignState != null)) {
            List<StratConTrackState> tracks = campaignState.getTracks();
            if (tracks != null && !tracks.isEmpty()) {
                seedMilitiaPool(campaign, contract, roster, tracks);
            }
        }

        return stampChallengerIdentity(roster, contract, campaign);
    }

    /**
     * Stamps challenger identity (faction code, bot name, colour, ACTIVE status, arrival date) onto a freshly-built
     * enemy roster so the deployer labels and RATs each bot force from the challenger's own faction rather than the
     * (possibly drifted) live contract enemy. Allies are not challengers and are not stamped.
     */
    private static StratConOpForRoster stampChallengerIdentity(final StratConOpForRoster roster,
            final AtBContract contract, final Campaign campaign) {
        roster.setFactionCode(contract.getEnemyCode());
        roster.setEnemyBotName(contract.getEnemyBotName());
        PlayerColour colour = contract.getEnemyColour();
        roster.setEnemyColour((colour == null) ? null : colour.name());
        roster.setStatus(ChallengerStatus.ACTIVE);
        roster.setArrivedDate(campaign.getLocalDate());
        return roster;
    }

    /**
     * v1.6: builds a static OpFor roster for a contract that has no
     * {@link StratConCampaignState} (pure AtB with {@code useStaticOpForRoster}
     * enabled). All formations are assigned to a synthetic track called
     * {@value #DEFAULT_ATB_TRACK_NAME}; the deployer uses the same name when
     * substituting roster units into AtB scenarios.
     */
    public static StratConOpForRoster buildForAtBContract(final Campaign campaign,
            final AtBContract contract) {

        int formationCount = computeInitialFormationCount(campaign, contract);
        ContractTypeOpForModifier.JitterProfile jitterProfile =
                ContractTypeOpForModifier.getJitterProfile(contract.getContractType());

        return stampChallengerIdentity(buildRosterInternal(
                "OpFor",
                campaign, contract,
                java.util.List.of(DEFAULT_ATB_TRACK_NAME),
                contract.getEnemy(),
                contract.getEnemyCode(),
                contract.getEnemySkill(),
                contract.getEnemyQuality(),
                formationCount,
                jitterProfile), contract, campaign);
    }

    /** Default synthetic track name for pure-AtB rosters. Must match the value used by the AtB hook in AtBDynamicScenarioFactory. */
    public static final String DEFAULT_ATB_TRACK_NAME = "Sector 0";

    /**
     * v1.6: builds a static allied roster for a contract that has no
     * {@link StratConCampaignState}. Symmetric to {@link #buildForAtBContract}.
     */
    public static StratConOpForRoster buildAllyForAtBContract(final Campaign campaign,
            final AtBContract contract) {

        int formationCount = computeInitialAllyFormationCount(campaign, contract);
        ContractTypeOpForModifier.JitterProfile jitterProfile =
                ContractTypeAllyModifier.getJitterProfile(contract.getContractType());

        StratConOpForRoster roster = buildRosterInternal(
                "Ally",
                campaign, contract,
                java.util.List.of(DEFAULT_ATB_TRACK_NAME),
                contract.getEmployerFaction(),
                contract.getEmployerCode(),
                contract.getAllySkill(),
                contract.getAllyQuality(),
                formationCount,
                jitterProfile);

        for (StratConOpForFormation formation : roster.getFormations()) {
            formation.setIntelLevel(IntelLevel.FULL_INTEL);
        }
        return roster;
    }

    private static java.util.List<String> trackNamesFromCampaignState(
            final StratConCampaignState campaignState) {
        if (campaignState == null) {
            return java.util.List.of(DEFAULT_ATB_TRACK_NAME);
        }
        java.util.List<StratConTrackState> tracks = campaignState.getTracks();
        if (tracks == null || tracks.isEmpty()) {
            return java.util.List.of(DEFAULT_ATB_TRACK_NAME);
        }
        java.util.List<String> names = new java.util.ArrayList<>(tracks.size());
        for (StratConTrackState track : tracks) {
            names.add(track.getDisplayableName());
        }
        return names;
    }

    /**
     * Builds a complete static allied roster for the given contract.
     *
     * <p>Symmetric to {@link #buildForContract} but uses the employer faction,
     * ally skill/quality, and {@link ContractTypeAllyModifier} for sizing.</p>
     *
     * @param campaign      the active campaign
     * @param contract      the AtB contract being initialised
     * @param campaignState the StratCon campaign state attached to the contract
     * @return a freshly-populated allied roster; never null
     */
    public static StratConOpForRoster buildAllyForContract(final Campaign campaign,
            final AtBContract contract,
            final StratConCampaignState campaignState) {

        int formationCount = computeInitialAllyFormationCount(campaign, contract);
        ContractTypeOpForModifier.JitterProfile jitterProfile =
                ContractTypeAllyModifier.getJitterProfile(contract.getContractType());

        StratConOpForRoster roster = buildRosterInternal(
                "Ally",
                campaign, contract,
                trackNamesFromCampaignState(campaignState),
                contract.getEmployerFaction(),
                contract.getEmployerCode(),
                contract.getAllySkill(),
                contract.getAllyQuality(),
                formationCount,
                jitterProfile);

        // Ally formations are employer-provided — the player knows them from the
        // moment the contract is signed. Promote every formation to FULL_INTEL so
        // the UI doesn't show friendly forces as "Unidentified".
        for (StratConOpForFormation formation : roster.getFormations()) {
            formation.setIntelLevel(IntelLevel.FULL_INTEL);
        }
        return roster;
    }

    /**
     * Core roster-build loop shared by OpFor and Ally builders. Generates
     * {@code formationCount} formations with the given baselines and jitter
     * profile, assigning each to a track via uniform-random selection.
     */
    private static StratConOpForRoster buildRosterInternal(final String label,
            final Campaign campaign,
            final AtBContract contract,
            final List<String> trackNames,
            final Faction faction,
            final String factionCode,
            final SkillLevel baselineSkill,
            final int baselineQuality,
            final int formationCount,
            final ContractTypeOpForModifier.JitterProfile jitterProfile) {

        FormationNamer namer = new FormationNamer(factionCode);

        LOGGER.info("Static {} roster: building {} formations for contract '{}' "
                + "(player teams: {}, contract type: {}, jitter: {}/{}/{})",
                label,
                formationCount,
                contract.getName(),
                campaign.getCombatTeamsAsList().size(),
                contract.getContractType(),
                jitterProfile.pBaseline(), jitterProfile.pAbove(), jitterProfile.pBelow());

        StratConOpForRoster roster = new StratConOpForRoster();

        for (int i = 0; i < formationCount; i++) {
            SkillLevel formationSkill = jitterSkill(baselineSkill, jitterProfile);
            int formationQuality = jitterQuality(baselineQuality, jitterProfile);

            FormationBuildResult result = buildFormation(
                    campaign, contract, faction, formationSkill, formationQuality, namer);

            for (StratConOpForUnit unit : result.units) {
                roster.addUnit(unit);
            }

            String pickedTrackName = pickTrackName(trackNames);
            if (pickedTrackName != null) {
                result.formation.setAssignedTrackName(pickedTrackName);
            }

            roster.addFormation(result.formation);
        }

        return roster;
    }

    /**
     * Uniform-random pick across the provided list of track names (each name is
     * equally likely). Returns {@code null} for an empty list (caller-supplied
     * trackNames should be non-empty in practice; this is a safety guard).
     */
    private static String pickTrackName(final List<String> trackNames) {
        if (trackNames == null || trackNames.isEmpty()) {
            return null;
        }
        if (trackNames.size() == 1) {
            return trackNames.get(0);
        }
        int idx = (int) (Math.random() * trackNames.size());
        return trackNames.get(idx);
    }

    // =========================================================================
    // Package-visible for testing
    // =========================================================================

    /**
     * Computes how many formations to generate for the contract.
     *
     * <p>Baseline is {@code ceil(playerCombatTeams * staticOpForPaddingFactor)}; modified
     * by the contract type (see {@link ContractTypeOpForModifier}); clamped to
     * {@code [max(ABSOLUTE_MIN_FORMATIONS, staticOpForFormationCountFloor), MAX_FORMATIONS]}.
     * This represents the engagement slice — not the planet's full garrison.</p>
     *
     * @param campaign the active campaign
     * @param contract the contract
     * @return formation count to generate (always in [max(ABSOLUTE_MIN_FORMATIONS, floor), MAX_FORMATIONS])
     */
    static int computeInitialFormationCount(final Campaign campaign,
            final AtBContract contract) {
        int playerFormations = campaign.getCombatTeamsAsList().size();
        int modifier = ContractTypeOpForModifier.getModifier(contract.getContractType());
        double padding = campaign.getCampaignOptions().getStaticOpForPaddingFactor();
        int floorOption = campaign.getCampaignOptions().getStaticOpForFormationCountFloor();

        int raw = (int) Math.ceil(playerFormations * padding) + modifier;
        // Clamp the configurable floor into [ABSOLUTE_MIN_FORMATIONS, MAX_FORMATIONS] so a
        // hand-edited save value above the cap cannot push the count past MAX_FORMATIONS.
        int floor = Math.max(ABSOLUTE_MIN_FORMATIONS, Math.min(MAX_FORMATIONS, floorOption));
        return Math.max(floor, Math.min(MAX_FORMATIONS, raw));
    }

    /**
     * Computes how many allied formations to generate for the contract.
     *
     * <p>Mirrors {@link #computeInitialFormationCount} but uses
     * {@link ContractTypeAllyModifier}. Floor is {@code 0} rather than
     * {@link #ABSOLUTE_MIN_FORMATIONS} — some contracts (covert work) should genuinely
     * give zero allied support. The configurable floor option does not apply to the
     * ally side.</p>
     *
     * @param campaign the active campaign
     * @param contract the contract
     * @return ally formation count to generate (always in [0, MAX_FORMATIONS])
     */
    static int computeInitialAllyFormationCount(final Campaign campaign,
            final AtBContract contract) {
        int playerFormations = campaign.getCombatTeamsAsList().size();
        int modifier = ContractTypeAllyModifier.getModifier(contract.getContractType());
        double padding = campaign.getCampaignOptions().getStaticOpForPaddingFactor();

        int raw = (int) Math.ceil(playerFormations * padding) + modifier;
        return Math.max(0, Math.min(MAX_FORMATIONS, raw));
    }

    /**
     * Returns a skill level near the baseline, weighted by the supplied
     * {@link ContractTypeOpForModifier.JitterProfile}. Results are clamped to
     * {@code [JITTER_SKILL_FLOOR, JITTER_SKILL_CEILING]}.
     *
     * @param baseline the contract's baseline enemy skill
     * @param profile  the jitter weights for this contract type
     * @return the jittered skill level (never null)
     */
    static SkillLevel jitterSkill(final SkillLevel baseline,
            final ContractTypeOpForModifier.JitterProfile profile) {
        if (baseline == null) {
            return SkillLevel.REGULAR;
        }
        double roll = ThreadLocalRandom.current().nextDouble();
        int delta;
        if (roll < profile.pBaseline()) {
            return baseline;
        } else if (roll < profile.pBaseline() + profile.pAbove()) {
            delta = 1;
        } else {
            delta = -1;
        }
        int target = baseline.ordinal() + delta;
        target = Math.max(JITTER_SKILL_FLOOR.ordinal(),
                Math.min(JITTER_SKILL_CEILING.ordinal(), target));
        return SkillLevel.values()[target];
    }

    /**
     * Returns a quality value near the baseline, weighted by the supplied
     * {@link ContractTypeOpForModifier.JitterProfile}. Results are clamped to
     * {@code [QUALITY_FLOOR, QUALITY_CEILING]}.
     *
     * @param baseline the contract's baseline enemy quality
     * @param profile  the jitter weights for this contract type
     * @return the jittered quality value
     */
    static int jitterQuality(final int baseline,
            final ContractTypeOpForModifier.JitterProfile profile) {
        double roll = ThreadLocalRandom.current().nextDouble();
        int delta;
        if (roll < profile.pBaseline()) {
            return Math.max(QUALITY_FLOOR, Math.min(QUALITY_CEILING, baseline));
        } else if (roll < profile.pBaseline() + profile.pAbove()) {
            delta = 1;
        } else {
            delta = -1;
        }
        return Math.max(QUALITY_FLOOR, Math.min(QUALITY_CEILING, baseline + delta));
    }

    /**
     * Returns the {@link SkillLevel} enemy reinforcements should be generated at:
     * the higher of the contract's original enemy skill and the player's CURRENT
     * campaign-wide average crew skill.
     *
     * <p>This is the "rubber-band" that keeps late-contract fights sharp: as the
     * player's force gains experience over a long contract, reinforcements keep
     * pace rather than staying frozen at the contract-accept baseline. They never
     * drop below the original enemy skill. Null-safe — falls back to
     * {@code baselineSkill} when the campaign reputation (or its average) is
     * unavailable.</p>
     *
     * @param campaign      the active campaign
     * @param baselineSkill the contract's original enemy skill
     * @return the skill level to generate reinforcements at
     */
    static SkillLevel scaledReinforcementSkill(final Campaign campaign,
            final SkillLevel baselineSkill) {
        ReputationController reputation = campaign.getReputation();
        if (reputation == null) {
            return baselineSkill;
        }
        SkillLevel playerSkill = reputation.getAverageSkillLevel();
        if ((playerSkill == null)
                || (playerSkill.getExperienceLevel() <= baselineSkill.getExperienceLevel())) {
            return baselineSkill;
        }
        return playerSkill;
    }

    /**
     * Returns the unit quality enemy reinforcements should be generated at: the
     * original enemy quality raised by however many skill steps the reinforcement
     * skill has gained over the contract baseline (a more experienced enemy fields
     * better-maintained equipment), clamped to
     * {@code [QUALITY_FLOOR, QUALITY_CEILING]}. Returns the baseline unchanged when
     * the skill has not risen.
     *
     * @param baselineQuality the contract's original enemy quality
     * @param originalSkill   the contract's original enemy skill
     * @param scaledSkill     the (possibly raised) reinforcement skill
     * @return the quality rating to generate reinforcements at
     */
    static int scaledReinforcementQuality(final int baselineQuality,
            final SkillLevel originalSkill, final SkillLevel scaledSkill) {
        int stepsGained = Math.max(0,
                scaledSkill.getExperienceLevel() - originalSkill.getExperienceLevel());
        return Math.max(QUALITY_FLOOR, Math.min(QUALITY_CEILING, baselineQuality + stepsGained));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Adds a batch of allied reinforcement formations to the supplied ally
     * roster. Uses the employer faction and ally skill/quality; new formations
     * are stamped at {@link IntelLevel#FULL_INTEL} (the player knows about
     * incoming employer support).
     *
     * @param campaign       the active campaign
     * @param contract       the contract whose ally roster is being reinforced
     * @param roster         the existing ally roster (mutated in place)
     * @param targetTrack    the track the new formations belong to
     * @param formationCount number of formations to add
     * @return the number of formations actually added
     */
    public static int addAllyReinforcementFormations(final Campaign campaign,
            final AtBContract contract,
            final StratConOpForRoster roster,
            final StratConTrackState targetTrack,
            final int formationCount) {

        if (roster == null || targetTrack == null || formationCount <= 0) {
            return 0;
        }

        Faction employerFaction = contract.getEmployerFaction();
        SkillLevel baselineSkill = contract.getAllySkill();
        int baselineQuality = contract.getAllyQuality();
        FormationNamer namer = new FormationNamer(contract.getEmployerCode());
        ContractTypeOpForModifier.JitterProfile jitterProfile =
                ContractTypeAllyModifier.getJitterProfile(contract.getContractType());
        String trackName = targetTrack.getDisplayableName();

        int added = 0;
        for (int i = 0; i < formationCount; i++) {
            SkillLevel formationSkill = jitterSkill(baselineSkill, jitterProfile);
            int formationQuality = jitterQuality(baselineQuality, jitterProfile);

            FormationBuildResult result = buildFormation(
                    campaign, contract, employerFaction, formationSkill, formationQuality, namer);

            if (result.units.isEmpty()) {
                LOGGER.warn("Ally reinforcement formation generation produced zero units "
                        + "for faction '{}'; skipping phantom formation.",
                        employerFaction != null ? employerFaction.getShortName() : "?");
                continue;
            }

            for (StratConOpForUnit unit : result.units) {
                roster.addUnit(unit);
            }
            result.formation.setAssignedTrackName(trackName);
            // Ally formations start at FULL_INTEL — the employer told the player
            // about them; no fog-of-war for friendly forces.
            result.formation.setIntelLevel(IntelLevel.FULL_INTEL);
            roster.addFormation(result.formation);
            added++;
        }
        return added;
    }

    /**
     * Adds a batch of reinforcement formations to an existing roster, assigned
     * to the supplied track. Uses the contract's baseline skill/quality with
     * the same per-contract-type jitter profile that initial sizing uses.
     *
     * <p>Used by {@link OpForReinforcementService} when morale-driven
     * reinforcements fire.</p>
     *
     * @param campaign     the active campaign
     * @param contract     the contract whose roster is being reinforced
     * @param roster       the existing roster (mutated in place)
     * @param targetTrack  the track the new formations belong to
     * @param formationCount number of formations to add
     * @return the number of formations actually added (may be less than
     *         requested if the unit generator fails repeatedly)
     */
    public static int addReinforcementFormations(final Campaign campaign,
            final AtBContract contract,
            final StratConOpForRoster roster,
            final StratConTrackState targetTrack,
            final int formationCount) {

        if (roster == null || targetTrack == null || formationCount <= 0) {
            return 0;
        }

        Faction enemyFaction = contract.getEnemy();
        // Rubber-band: reinforcements track the player's CURRENT force strength
        // rather than the frozen contract-accept baseline, so late-contract fights
        // stay sharp instead of pitting a compounding player force against a green
        // enemy that never improves. Skill/quality never drop below the original.
        SkillLevel originalSkill = contract.getEnemySkill();
        SkillLevel baselineSkill = scaledReinforcementSkill(campaign, originalSkill);
        int baselineQuality = scaledReinforcementQuality(
                contract.getEnemyQuality(), originalSkill, baselineSkill);
        FormationNamer namer = new FormationNamer(contract.getEnemyCode());
        ContractTypeOpForModifier.JitterProfile jitterProfile =
                ContractTypeOpForModifier.getJitterProfile(contract.getContractType());
        String trackName = targetTrack.getDisplayableName();

        int added = 0;
        for (int i = 0; i < formationCount; i++) {
            SkillLevel formationSkill = jitterSkill(baselineSkill, jitterProfile);
            int formationQuality = jitterQuality(baselineQuality, jitterProfile);

            FormationBuildResult result = buildFormation(
                    campaign, contract, enemyFaction, formationSkill, formationQuality, namer);

            // Skip phantom formations — if the unit generator failed for every unit slot,
            // adding the empty formation would burn the reinforcement cap and trigger a
            // misleading "engaged on..." report for a force that can never deploy.
            if (result.units.isEmpty()) {
                LOGGER.warn("Reinforcement formation generation produced zero units "
                        + "for faction '{}'; skipping phantom formation.",
                        enemyFaction != null ? enemyFaction.getShortName() : "?");
                continue;
            }

            for (StratConOpForUnit unit : result.units) {
                roster.addUnit(unit);
            }
            result.formation.setAssignedTrackName(trackName);
            roster.addFormation(result.formation);
            added++;
        }
        return added;
    }

    /**
     * Seeds the starting militia pool into the supplied roster.
     *
     * <p>No-op when {@code contract.isPlayerAttacker()} is false, or when
     * {@link mekhq.campaign.campaignOptions.CampaignOptions#isUseStaticOpForMilitia()} is
     * false, or when the profile for the contract type has no starting pool
     * ({@link ContractTypeMilitiaReinforcementProfile.MilitiaProfile#hasStartingPool()}
     * is false).</p>
     *
     * <p>The formation count is rolled uniformly in
     * {@code [profile.minStarting(), profile.maxStarting()]} and each formation is
     * assigned to a randomly-picked track and flagged militia.</p>
     *
     * @param campaign  the active campaign
     * @param contract  the contract being initialised
     * @param roster    the roster to seed into (mutated in place)
     * @param tracks    the available StratCon tracks
     */
    public static void seedMilitiaPool(final Campaign campaign,
            final AtBContract contract,
            final StratConOpForRoster roster,
            final List<StratConTrackState> tracks) {

        if (!contract.isPlayerAttacker()) {
            return;
        }
        if (!campaign.getCampaignOptions().isUseStaticOpForMilitia()) {
            return;
        }

        ContractTypeMilitiaReinforcementProfile.MilitiaProfile profile =
                ContractTypeMilitiaReinforcementProfile.getProfile(contract.getContractType());
        if (!profile.hasStartingPool()) {
            return;
        }

        int min = profile.minStarting();
        int max = profile.maxStarting();
        int count = (min == max) ? min
                : min + ThreadLocalRandom.current().nextInt(max - min + 1);

        Faction enemyFaction = contract.getEnemy();
        String factionCode = contract.getEnemyCode();
        FormationNamer namer = new FormationNamer(factionCode);

        for (int i = 0; i < count; i++) {
            SkillLevel skill = clampMilitiaSkill(jitterSkill(MILITIA_BASE_SKILL, MILITIA_JITTER));
            int weightClass = AtBDynamicScenarioFactory.randomForceWeight();

            FormationBuildResult result = buildMilitiaFormation(
                    campaign, contract, enemyFaction, skill, weightClass, namer);

            for (StratConOpForUnit unit : result.units) {
                roster.addUnit(unit);
            }

            StratConTrackState pickedTrack = pickTrack(tracks);
            if (pickedTrack != null) {
                result.formation.setAssignedTrackName(pickedTrack.getDisplayableName());
            }
            roster.addFormation(result.formation);
        }

        LOGGER.info("Static OpFor: seeded {} militia formation(s) for attacker contract '{}'",
                count, contract.getName());
    }

    /**
     * Adds a batch of militia reinforcement formations to the supplied roster,
     * assigned to the given track.
     *
     * <p>Militia formations are flagged with {@link StratConOpForFormation#setMilitia(boolean)}
     * and use the vehicle/infantry composition from
     * {@link #generateMilitiaUnit}.</p>
     *
     * <p>Used by {@code MilitiaReinforcementService} when morale-driven
     * militia events fire.</p>
     *
     * @param campaign       the active campaign
     * @param contract       the contract whose roster is being reinforced
     * @param roster         the existing roster (mutated in place)
     * @param targetTrack    the track the new formations belong to
     * @param formationCount number of formations to add
     * @return the number of formations actually added
     */
    public static int addMilitiaReinforcementFormations(final Campaign campaign,
            final AtBContract contract,
            final StratConOpForRoster roster,
            final StratConTrackState targetTrack,
            final int formationCount) {

        if (roster == null || targetTrack == null || formationCount <= 0) {
            return 0;
        }

        Faction enemyFaction = contract.getEnemy();
        String factionCode = contract.getEnemyCode();
        FormationNamer namer = new FormationNamer(factionCode);
        String trackName = targetTrack.getDisplayableName();

        int added = 0;
        for (int i = 0; i < formationCount; i++) {
            SkillLevel skill = clampMilitiaSkill(jitterSkill(MILITIA_BASE_SKILL, MILITIA_JITTER));
            int weightClass = AtBDynamicScenarioFactory.randomForceWeight();

            FormationBuildResult result = buildMilitiaFormation(
                    campaign, contract, enemyFaction, skill, weightClass, namer);

            if (result.units.isEmpty()) {
                LOGGER.warn("Militia reinforcement formation produced zero units "
                        + "for faction '{}'; skipping phantom formation.",
                        enemyFaction != null ? enemyFaction.getShortName() : "?");
                continue;
            }

            for (StratConOpForUnit unit : result.units) {
                roster.addUnit(unit);
            }
            result.formation.setAssignedTrackName(trackName);
            roster.addFormation(result.formation);
            added++;
        }
        return added;
    }

    /**
     * Clamps a jittered skill level to {@code [GREEN, REGULAR]} so militia
     * never exceed regular quality regardless of the jitter roll.
     *
     * @param skill the jittered skill; must not be null
     * @return the clamped skill level
     */
    private static SkillLevel clampMilitiaSkill(final SkillLevel skill) {
        if (skill.ordinal() > SkillLevel.REGULAR.ordinal()) {
            return SkillLevel.REGULAR;
        }
        if (skill.ordinal() < SkillLevel.GREEN.ordinal()) {
            return SkillLevel.GREEN;
        }
        return skill;
    }

    /**
     * Builds a single militia formation using the vehicle/infantry generator.
     *
     * <p>The formation is flagged {@link StratConOpForFormation#setMilitia(boolean) militia}
     * and named via {@link FormationNamer#nextMilitiaName()}.</p>
     */
    private static FormationBuildResult buildMilitiaFormation(final Campaign campaign,
            final AtBContract contract,
            final Faction enemyFaction,
            final SkillLevel skill,
            final int weightClass,
            final FormationNamer namer) {

        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setId(UUID.randomUUID());
        formation.setName(namer.nextMilitiaName());
        formation.setSkillLevel(skill);
        formation.setUnitQuality(MILITIA_QUALITY);
        formation.setWeightClass(weightClass);
        formation.setMilitia(true);

        int size = (enemyFaction != null) ? FormationSchema.formationSize(enemyFaction) : 4;

        List<UUID> unitIds = new ArrayList<>();
        List<StratConOpForUnit> units = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            StratConOpForUnit unit = generateMilitiaUnit(
                    campaign, contract, enemyFaction, skill, weightClass);
            if (unit != null) {
                unit.setFormationId(formation.getId());
                unitIds.add(unit.getId());
                units.add(unit);
            }
        }

        formation.setUnitIds(unitIds);
        return new FormationBuildResult(formation, units);
    }

    /**
     * Simple value holder returned by {@link #buildFormation}.
     */
    private static final class FormationBuildResult {
        final StratConOpForFormation formation;
        final List<StratConOpForUnit> units;

        FormationBuildResult(final StratConOpForFormation formation,
                final List<StratConOpForUnit> units) {
            this.formation = formation;
            this.units = units;
        }
    }

    /**
     * Builds a single formation, generating unit entities via the campaign's
     * unit generator and wrapping each in a {@link StratConOpForUnit}.
     *
     * <p>Units are returned alongside the formation so the caller can register
     * them in the roster before computing BV.</p>
     */
    private static FormationBuildResult buildFormation(final Campaign campaign,
            final AtBContract contract,
            final Faction enemyFaction,
            final SkillLevel skill,
            final int quality,
            final FormationNamer namer) {

        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setId(UUID.randomUUID());
        formation.setName(namer.nextFormationName());
        formation.setSkillLevel(skill);
        formation.setUnitQuality(quality);

        int weightClass = AtBDynamicScenarioFactory.randomForceWeight();
        formation.setWeightClass(weightClass);

        int size = (enemyFaction != null) ? FormationSchema.formationSize(enemyFaction) : 4;

        List<UUID> unitIds = new ArrayList<>();
        List<StratConOpForUnit> units = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            StratConOpForUnit unit = generateUnit(campaign, contract, enemyFaction, skill, quality, weightClass);
            if (unit != null) {
                unit.setFormationId(formation.getId());
                unitIds.add(unit.getId());
                units.add(unit);
            }
        }

        formation.setUnitIds(unitIds);
        return new FormationBuildResult(formation, units);
    }

    /**
     * Generates a single OpFor Mek unit using the campaign's unit generator.
     *
     * <p>Delegates to the parameterized overload with {@code MEK} unit type and
     * no movement-mode or summary filter.</p>
     *
     * @param campaign      the active campaign
     * @param contract      the AtB contract (for year/faction context)
     * @param enemyFaction  the enemy faction
     * @param skill         the crew skill level
     * @param quality       the unit quality rating
     * @param weightClass   the desired weight class
     * @return a populated {@link StratConOpForUnit}, or {@code null} if generation fails
     */
    private static StratConOpForUnit generateUnit(final Campaign campaign,
            final AtBContract contract,
            final Faction enemyFaction,
            final SkillLevel skill,
            final int quality,
            final int weightClass) {
        return generateUnit(campaign, contract, enemyFaction, skill, quality, weightClass,
                MEK, null, null);
    }

    /**
     * Generates a single OpFor unit of the requested type using the campaign's
     * unit generator.
     *
     * <p>When {@code movementModes} is non-null the generator is restricted to
     * those modes.  When {@code filter} is non-null it is applied as an
     * additional {@link MekSummary} predicate.  If the first generate call
     * returns null the weight-class constraint is dropped and the call is
     * retried once.</p>
     *
     * @param campaign       the active campaign
     * @param contract       the AtB contract (for year/faction context)
     * @param enemyFaction   the enemy faction (may be {@code null})
     * @param skill          the crew skill level
     * @param quality        the unit quality rating
     * @param weightClass    the desired weight class
     * @param unitType       the requested {@link UnitType} constant (e.g. {@code MEK}, {@code TANK})
     * @param movementModes  optional set of permitted movement modes; {@code null} = no restriction
     * @param filter         optional additional MekSummary predicate; {@code null} = no filter
     * @return a populated {@link StratConOpForUnit}, or {@code null} if generation fails
     */
    private static StratConOpForUnit generateUnit(final Campaign campaign,
            final AtBContract contract,
            final Faction enemyFaction,
            final SkillLevel skill,
            final int quality,
            final int weightClass,
            final int unitType,
            final @Nullable Set<EntityMovementMode> movementModes,
            final @Nullable Predicate<MekSummary> filter) {

        // Guard against a blank short name as well as a null faction: an empty
        // faction code passed to the unit generator makes every RAT lookup fail
        // and silently shrinks the OpFor. Fall back to the independent table.
        String factionCode = ((enemyFaction != null) && !enemyFaction.getShortName().isBlank())
                ? enemyFaction.getShortName()
                : "IND";
        int year = campaign.getGameYear();

        UnitGeneratorParameters params = new UnitGeneratorParameters();
        params.setFaction(factionCode);
        params.setUnitType(unitType);
        params.setWeightClass(weightClass);
        params.setYear(year);
        params.setQuality(quality);
        if (movementModes != null) {
            params.getMovementModes().addAll(movementModes);
        }
        if (filter != null) {
            params.setFilter(filter);
        }

        MekSummary ms = campaign.getUnitGenerator().generate(params);
        if (ms == null) {
            // Try without weight-class constraint (-1 bypasses the filter;
            // WEIGHT_ULTRA_LIGHT (0) would wrongly constrain to ultra-lights).
            params.setWeightClass(AtBDynamicScenarioFactory.UNIT_WEIGHT_UNSPECIFIED);
            ms = campaign.getUnitGenerator().generate(params);
        }
        if (ms == null) {
            LOGGER.warn("Unit generator returned null for faction '{}' year {}; skipping unit.", factionCode, year);
            return null;
        }

        // Create the entity so we can get crew details from AtBDynamicScenarioFactory
        var entity = AtBDynamicScenarioFactory.createEntityWithCrew(
                enemyFaction, skill, campaign, ms);
        if (entity == null) {
            LOGGER.warn("createEntityWithCrew returned null for '{}'; skipping unit.", ms.getName());
            return null;
        }

        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setId(UUID.randomUUID());
        unit.setProtoEntity(UnitTemplate.fromEntity(entity, factionCode));
        unit.setPilotName(entity.getCrew() != null ? entity.getCrew().getName(0) : "Unknown");
        unit.setGunnery(entity.getCrew() != null ? entity.getCrew().getGunnery() : 4);
        unit.setPiloting(entity.getCrew() != null ? entity.getCrew().getPiloting() : 5);
        unit.setPilotPersistentId(UUID.randomUUID());
        unit.setUnitType(entity.getUnitType());

        return unit;
    }

    /**
     * Generates a single militia unit — either a ground combat vehicle or
     * conventional infantry, per {@link #MILITIA_INFANTRY_FRACTION}.
     *
     * <p>Vehicles are restricted to the ground movement modes in
     * {@link #MILITIA_VEE_MODES} and must have at least 1 walk MP (no trailers).
     * Infantry is generated without movement restrictions.  Both use
     * {@link #MILITIA_QUALITY}.</p>
     *
     * @param campaign      the active campaign
     * @param contract      the AtB contract
     * @param enemyFaction  the enemy faction (the local defenders)
     * @param skill         the crew skill level for this unit
     * @param weightClass   the desired weight class (used for vehicles only)
     * @return a populated {@link StratConOpForUnit}, or {@code null} if generation fails
     */
    private static StratConOpForUnit generateMilitiaUnit(final Campaign campaign,
            final AtBContract contract,
            final Faction enemyFaction,
            final SkillLevel skill,
            final int weightClass) {
        boolean infantry = ThreadLocalRandom.current().nextDouble() < MILITIA_INFANTRY_FRACTION;
        if (infantry) {
            return generateUnit(campaign, contract, enemyFaction, skill, MILITIA_QUALITY,
                    AtBDynamicScenarioFactory.UNIT_WEIGHT_UNSPECIFIED,
                    UnitType.INFANTRY, null, null);
        }
        return generateUnit(campaign, contract, enemyFaction, skill, MILITIA_QUALITY, weightClass,
                UnitType.TANK, MILITIA_VEE_MODES, ms -> ms.getWalkMp() >= 1);
    }

    /**
     * Picks a track from the list using a weight proportional to each track's
     * required lance count.  Returns {@code null} if the list is empty.
     */
    private static StratConTrackState pickTrack(final List<StratConTrackState> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (StratConTrackState track : tracks) {
            totalWeight += Math.max(1, track.getRequiredLanceCount());
        }

        int roll = (int) (Math.random() * totalWeight);
        int accumulated = 0;
        for (StratConTrackState track : tracks) {
            accumulated += Math.max(1, track.getRequiredLanceCount());
            if (roll < accumulated) {
                return track;
            }
        }
        return tracks.get(tracks.size() - 1);
    }
}
