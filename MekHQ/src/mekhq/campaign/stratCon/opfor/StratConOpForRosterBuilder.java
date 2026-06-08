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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import megamek.common.enums.SkillLevel;
import megamek.common.loaders.MekSummary;
import megamek.common.loaders.MekSummaryCache;
import megamek.common.units.EntityWeightClass;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
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
 * <p>The roster size mirrors the player's combat-team count plus a contract-type
 * modifier from {@link ContractTypeOpForModifier}, clamped between {@link #MIN_FORMATIONS}
 * and {@link #MAX_FORMATIONS}. This represents the slice of the planetary garrison
 * actually committed against the player's mission — not the planet's full force.
 * Reinforcements (v1.1) layer on via morale-driven events; initial sizing is static.</p>
 */
public final class StratConOpForRosterBuilder {

    private static final MMLogger LOGGER = MMLogger.create(StratConOpForRosterBuilder.class);

    /** Minimum formation count regardless of player size or contract type. */
    static final int MIN_FORMATIONS = 2;

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

    /** Utility class — no instantiation. */
    private StratConOpForRosterBuilder() {
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Builds a complete static OpFor roster for the given contract.
     *
     * <p>Loops until the accumulated roster BV meets the computed target and at
     * least {@code formationCountFloor} formations have been generated.  Each
     * formation is assigned to a track via weighted-random selection.</p>
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

        return buildRosterInternal(
                "OpFor",
                campaign, contract, campaignState,
                contract.getEnemy(),
                contract.getEnemyCode(),
                contract.getEnemySkill(),
                contract.getEnemyQuality(),
                formationCount,
                jitterProfile);
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
                campaign, contract, campaignState,
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
     * profile, assigning each to a track via weighted-random selection.
     */
    private static StratConOpForRoster buildRosterInternal(final String label,
            final Campaign campaign,
            final AtBContract contract,
            final StratConCampaignState campaignState,
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

        List<StratConTrackState> tracks = campaignState.getTracks();
        StratConOpForRoster roster = new StratConOpForRoster();

        for (int i = 0; i < formationCount; i++) {
            SkillLevel formationSkill = jitterSkill(baselineSkill, jitterProfile);
            int formationQuality = jitterQuality(baselineQuality, jitterProfile);

            FormationBuildResult result = buildFormation(
                    campaign, contract, faction, formationSkill, formationQuality, namer);

            for (StratConOpForUnit unit : result.units) {
                roster.addUnit(unit);
            }

            StratConTrackState pickedTrack = pickTrack(tracks);
            if (pickedTrack != null) {
                result.formation.setAssignedTrackName(pickedTrack.getDisplayableName());
            }

            roster.addFormation(result.formation);
        }

        return roster;
    }

    // =========================================================================
    // Package-visible for testing
    // =========================================================================

    /**
     * Computes how many formations to generate for the contract.
     *
     * <p>Baseline is the player's combat-team count; modified by the contract type
     * (see {@link ContractTypeOpForModifier}); clamped to {@code [MIN_FORMATIONS,
     * MAX_FORMATIONS]}. This represents the engagement slice — not the planet's
     * full garrison.</p>
     *
     * @param campaign the active campaign
     * @param contract the contract
     * @return formation count to generate (always in [MIN, MAX])
     */
    static int computeInitialFormationCount(final Campaign campaign,
            final AtBContract contract) {
        int playerFormations = campaign.getCombatTeamsAsList().size();
        int modifier = ContractTypeOpForModifier.getModifier(contract.getContractType());
        int raw = playerFormations + modifier;
        return Math.max(MIN_FORMATIONS, Math.min(MAX_FORMATIONS, raw));
    }

    /**
     * Computes how many allied formations to generate for the contract.
     *
     * <p>Symmetric to {@link #computeInitialFormationCount} but uses
     * {@link ContractTypeAllyModifier}. Floor is {@code 0} rather than
     * {@link #MIN_FORMATIONS} — some contracts (covert work) should genuinely
     * give zero allied support.</p>
     *
     * @param campaign the active campaign
     * @param contract the contract
     * @return ally formation count to generate (always in [0, MAX_FORMATIONS])
     */
    static int computeInitialAllyFormationCount(final Campaign campaign,
            final AtBContract contract) {
        int playerFormations = campaign.getCombatTeamsAsList().size();
        int modifier = ContractTypeAllyModifier.getModifier(contract.getContractType());
        int raw = playerFormations + modifier;
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
        SkillLevel baselineSkill = contract.getEnemySkill();
        int baselineQuality = contract.getEnemyQuality();
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
     * Generates a single OpFor unit using the campaign's unit generator.
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

        String factionCode = (enemyFaction != null) ? enemyFaction.getShortName() : "IND";
        int year = campaign.getGameYear();

        UnitGeneratorParameters params = new UnitGeneratorParameters();
        params.setFaction(factionCode);
        params.setUnitType(MEK);
        params.setWeightClass(weightClass);
        params.setYear(year);
        params.setQuality(quality);

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
        unit.setProtoEntity(new UnitTemplate(
                entity.getChassis(),
                entity.getModel(),
                factionCode));
        unit.setPilotName(entity.getCrew() != null ? entity.getCrew().getName(0) : "Unknown");
        unit.setGunnery(entity.getCrew() != null ? entity.getCrew().getGunnery() : 4);
        unit.setPiloting(entity.getCrew() != null ? entity.getCrew().getPiloting() : 5);
        unit.setPilotPersistentId(UUID.randomUUID());

        return unit;
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
