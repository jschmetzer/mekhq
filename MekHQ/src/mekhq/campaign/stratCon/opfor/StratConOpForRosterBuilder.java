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
 * <p>The roster size is determined by the contract's expected scenario frequency,
 * difficulty, and the campaign options' padding factor and formation floor.</p>
 */
public final class StratConOpForRosterBuilder {

    private static final MMLogger LOGGER = MMLogger.create(StratConOpForRosterBuilder.class);

    /** Base BV assumed per scenario when sizing the roster. */
    static final int BASE_SCENARIO_BV = 10_000;

    /** Approximate number of scenario rolls per week per required lance. */
    private static final double WEEKLY_ROLLS_PER_LANCE = 4.0;

    /** Approximate average BV per scenario (used in floor calculation). */
    private static final double AVG_SCENARIO_BV = BASE_SCENARIO_BV;

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

        Faction enemyFaction = contract.getEnemy();
        SkillLevel skill = contract.getEnemySkill();
        int quality = contract.getEnemyQuality();
        FormationNamer namer = new FormationNamer(contract.getEnemyCode());

        double finalRosterBv = computeFinalRosterBv(campaign, contract, campaignState);
        int formationCountFloor = campaign.getCampaignOptions().getStaticOpForFormationCountFloor();

        List<StratConTrackState> tracks = campaignState.getTracks();

        StratConOpForRoster roster = new StratConOpForRoster();
        double currentBv = 0.0;
        int formationsGenerated = 0;

        while ((currentBv < finalRosterBv) || (formationsGenerated < formationCountFloor)) {
            FormationBuildResult result = buildFormation(
                    campaign, contract, enemyFaction, skill, quality, namer);

            // Register all generated units in the roster before computing BV
            for (StratConOpForUnit unit : result.units) {
                roster.addUnit(unit);
            }

            // Assign to a track via weighted random (weight = required lance count)
            StratConTrackState pickedTrack = pickTrack(tracks);
            if (pickedTrack != null) {
                result.formation.setAssignedTrackName(pickedTrack.getDisplayableName());
            }

            roster.addFormation(result.formation);
            currentBv += result.formation.currentBV(roster);
            formationsGenerated++;

            // Safety valve to prevent infinite loops if BV estimation is broken
            if (formationsGenerated > 200) {
                LOGGER.warn("Roster builder safety limit reached after 200 formations; stopping.");
                break;
            }
        }

        return roster;
    }

    // =========================================================================
    // Package-visible for testing
    // =========================================================================

    /**
     * Computes the target roster BV for the contract.
     *
     * <p>Formula: sum expected scenarios per month across all tracks, multiply by
     * contract length, difficulty, and padding.  Apply a formation-floor minimum.</p>
     *
     * @param campaign      the active campaign
     * @param contract      the contract
     * @param campaignState the StratCon campaign state
     * @return target roster BV (always positive)
     */
    static double computeFinalRosterBv(final Campaign campaign,
            final AtBContract contract,
            final StratConCampaignState campaignState) {

        // Sum expected scenarios across all tracks
        double expectedScenariosPerMonth = 0.0;
        for (StratConTrackState track : campaignState.getTracks()) {
            double oddsPerRoll = track.getScenarioOdds() / 100.0;
            double weeklyRolls = track.getRequiredLanceCount() * WEEKLY_ROLLS_PER_LANCE;
            expectedScenariosPerMonth += oddsPerRoll * weeklyRolls;
        }

        int contractLengthMonths = contract.getLength();
        double difficultyMultiplier = getDifficultyMultiplier(campaign);
        double paddingFactor = campaign.getCampaignOptions().getStaticOpForPaddingFactor();

        double target = expectedScenariosPerMonth
                * contractLengthMonths
                * AVG_SCENARIO_BV
                * difficultyMultiplier
                * paddingFactor;

        // Formation floor: ensure at least N formations worth of BV
        int formationCountFloor = campaign.getCampaignOptions().getStaticOpForFormationCountFloor();
        Faction enemyFaction = contract.getEnemy();
        int unitsPerFormation = (enemyFaction != null) ? FormationSchema.formationSize(enemyFaction) : 4;
        double oneFormationTargetBV = unitsPerFormation * AVG_SCENARIO_BV;
        double floor = formationCountFloor * oneFormationTargetBV;

        return Math.max(target, floor);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

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
            // Try without weight-class constraint
            params.setWeightClass(EntityWeightClass.WEIGHT_ULTRA_LIGHT);
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

    /**
     * Returns the difficulty multiplier for the campaign's current skill setting.
     *
     * <p>Mirrors the equivalent private method in
     * {@link AtBDynamicScenarioFactory}.</p>
     */
    private static double getDifficultyMultiplier(final Campaign campaign) {
        return switch (campaign.getCampaignOptions().getSkillLevel()) {
            case NONE, ULTRA_GREEN -> 0.5;
            case GREEN -> 0.75;
            case REGULAR -> 1.0;
            case VETERAN -> 1.25;
            case ELITE -> 1.5;
            case HEROIC -> 1.75;
            case LEGENDARY -> 2.0;
        };
    }
}
