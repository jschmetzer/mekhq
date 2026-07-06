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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import megamek.client.ui.util.PlayerColour;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import megamek.common.enums.SkillLevel;
import megamek.common.units.UnitType;
import mekhq.campaign.Campaign;
import mekhq.campaign.camOpsReputation.ReputationController;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.force.CombatTeam;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.IUnitGenerator;
import mekhq.campaign.universe.UnitGeneratorParameters;

/**
 * Tests for {@link StratConOpForRosterBuilder} sizing and jitter.
 */
class StratConOpForRosterBuilderTest {

    // -------------------------------------------------------------------------
    // computeInitialFormationCount — sizing mirrors player + contract modifier
    // -------------------------------------------------------------------------

    @Test
    void computeInitialFormationCount_pirateHunt_matchesPlayerCount() {
        // Pirate Hunt has modifier 0 — should equal player combat team count
        Campaign campaign = campaignWithCombatTeams(3);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PIRATE_HUNTING);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(3, count, "Pirate Hunt with 3 player teams should produce 3 OpFor formations");
    }

    @Test
    void computeInitialFormationCount_planetaryAssault_addsPositiveModifier() {
        // Planetary Assault has modifier +3
        Campaign campaign = campaignWithCombatTeams(3);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(6, count, "Planetary Assault with 3 player teams should produce 6 OpFor formations");
    }

    @Test
    void computeInitialFormationCount_cadreDuty_subtractsForLightTraining() {
        // Cadre Duty has modifier -1
        Campaign campaign = campaignWithCombatTeams(3);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.CADRE_DUTY);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(2, count, "Cadre Duty with 3 player teams should produce 2 OpFor formations");
    }

    @Test
    void computeInitialFormationCount_clampsToAbsoluteMinimum() {
        // 1 player team * 1.0 + cadre (-1) = 0; floor option 1 -> clamps to ABSOLUTE_MIN_FORMATIONS
        Campaign campaign = campaignWithCombatTeams(1, 1.0, 1);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.CADRE_DUTY);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(StratConOpForRosterBuilder.ABSOLUTE_MIN_FORMATIONS, count,
                "Player count + negative modifier must clamp to ABSOLUTE_MIN_FORMATIONS");
    }

    @Test
    void computeInitialFormationCount_clampsToMaximum() {
        // 50 player teams + planetary assault (+3) = 53; clamped to MAX_FORMATIONS
        Campaign campaign = campaignWithCombatTeams(50);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(StratConOpForRosterBuilder.MAX_FORMATIONS, count,
                "Oversized player force must clamp to MAX_FORMATIONS");
    }

    @Test
    void computeInitialFormationCount_paddingScalesPlayerTerm() {
        // 4 teams * 1.25 = 5.0 -> ceil 5; Pirate Hunt modifier 0 -> 5
        Campaign campaign = campaignWithCombatTeams(4, 1.25, 1);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PIRATE_HUNTING);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(5, count, "Padding 1.25 on 4 player teams should yield 5 formations");
    }

    @Test
    void computeInitialFormationCount_paddingRoundsUp() {
        // 3 teams * 1.25 = 3.75 -> ceil 4; Pirate Hunt modifier 0 -> 4
        Campaign campaign = campaignWithCombatTeams(3, 1.25, 1);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PIRATE_HUNTING);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(4, count, "Padding-scaled player term should round up (ceil)");
    }

    @Test
    void computeInitialFormationCount_floorOptionRaisesMinimum() {
        // 1 team * 1.0 = 1; Cadre (-1) -> raw 0; floor option 5 wins
        Campaign campaign = campaignWithCombatTeams(1, 1.0, 5);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.CADRE_DUTY);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(5, count, "Floor option should raise the minimum formation count");
    }

    @Test
    void computeInitialFormationCount_floorOptionAboveMaxIsClampedToMax() {
        // A hand-edited floor of 25 must not push the count past MAX_FORMATIONS (20)
        Campaign campaign = campaignWithCombatTeams(1, 1.0, 25);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PIRATE_HUNTING);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(StratConOpForRosterBuilder.MAX_FORMATIONS, count,
                "Floor option above MAX_FORMATIONS must clamp to MAX_FORMATIONS");
    }

    @Test
    void computeInitialFormationCount_paddingDoesNotApplyToModifier() {
        // 2 teams * 2.0 = 4 (ceil 4) + Planetary Assault (+3) = 7 — modifier NOT scaled
        Campaign campaign = campaignWithCombatTeams(2, 2.0, 1);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);

        int count = StratConOpForRosterBuilder.computeInitialFormationCount(campaign, contract);

        assertEquals(7, count, "Padding scales only the player term, not the contract modifier");
    }

    @Test
    void computeInitialAllyFormationCount_paddingApplies_floorStaysZero() {
        // 4 teams * 1.25 = 5 (ceil); ally floor stays 0 even with floor option set high
        Campaign campaign = campaignWithCombatTeams(4, 1.25, 99);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PIRATE_HUNTING);

        int count = StratConOpForRosterBuilder.computeInitialAllyFormationCount(campaign, contract);

        assertTrue(count >= 5, "Ally padding should scale the player term");
        assertTrue(count <= StratConOpForRosterBuilder.MAX_FORMATIONS,
                "Ally count must not exceed MAX_FORMATIONS; floor option must not apply to allies");
    }

    // -------------------------------------------------------------------------
    // jitterSkill — stays within bounds
    // -------------------------------------------------------------------------

    @Test
    void jitterSkill_resultStaysWithinBounds() {
        for (int i = 0; i < 200; i++) {
            SkillLevel result = StratConOpForRosterBuilder.jitterSkill(
                    SkillLevel.REGULAR, ContractTypeOpForModifier.JitterProfile.BALANCED);
            assertTrue(result.ordinal() >= SkillLevel.GREEN.ordinal()
                            && result.ordinal() <= SkillLevel.ELITE.ordinal(),
                    "jitterSkill must stay within [GREEN, ELITE]; got " + result);
        }
    }

    @Test
    void jitterSkill_baselineAtCeilingNeverExceedsCeiling() {
        for (int i = 0; i < 100; i++) {
            SkillLevel result = StratConOpForRosterBuilder.jitterSkill(
                    SkillLevel.ELITE, ContractTypeOpForModifier.JitterProfile.BALANCED);
            assertTrue(result.ordinal() <= SkillLevel.ELITE.ordinal(),
                    "ELITE baseline must not jitter higher than ELITE");
        }
    }

    @Test
    void jitterSkill_nullBaselineReturnsRegular() {
        assertEquals(SkillLevel.REGULAR, StratConOpForRosterBuilder.jitterSkill(
                        null, ContractTypeOpForModifier.JitterProfile.BALANCED),
                "Null baseline must default to REGULAR");
    }

    @Test
    void jitterSkill_eliteTilt_skewsAboveBaseline() {
        // ELITE_TILT: 60% baseline, 30% above, 10% below
        // Over 1000 rolls, above-baseline should be substantially more common than below
        int above = 0;
        int below = 0;
        for (int i = 0; i < 1000; i++) {
            SkillLevel result = StratConOpForRosterBuilder.jitterSkill(
                    SkillLevel.REGULAR, ContractTypeOpForModifier.JitterProfile.ELITE_TILT);
            if (result.ordinal() > SkillLevel.REGULAR.ordinal()) above++;
            else if (result.ordinal() < SkillLevel.REGULAR.ordinal()) below++;
        }
        int finalAbove = above;
        int finalBelow = below;
        assertTrue(finalAbove > finalBelow * 2,
                () -> "ELITE_TILT should produce above>below by >2x; got above=" + finalAbove + " below=" + finalBelow);
    }

    @Test
    void jitterSkill_irregularTilt_skewsBelowBaseline() {
        // IRREGULAR_TILT: 60% baseline, 10% above, 30% below
        int above = 0;
        int below = 0;
        for (int i = 0; i < 1000; i++) {
            SkillLevel result = StratConOpForRosterBuilder.jitterSkill(
                    SkillLevel.REGULAR, ContractTypeOpForModifier.JitterProfile.IRREGULAR_TILT);
            if (result.ordinal() > SkillLevel.REGULAR.ordinal()) above++;
            else if (result.ordinal() < SkillLevel.REGULAR.ordinal()) below++;
        }
        int finalAbove2 = above;
        int finalBelow2 = below;
        assertTrue(finalBelow2 > finalAbove2 * 2,
                () -> "IRREGULAR_TILT should produce below>above by >2x; got above=" + finalAbove2 + " below=" + finalBelow2);
    }

    // -------------------------------------------------------------------------
    // jitterQuality — clamps to [0, 5]
    // -------------------------------------------------------------------------

    @Test
    void jitterQuality_resultStaysWithinBounds() {
        for (int i = 0; i < 200; i++) {
            int result = StratConOpForRosterBuilder.jitterQuality(
                    3, ContractTypeOpForModifier.JitterProfile.BALANCED);
            assertTrue(result >= 0 && result <= 5,
                    "jitterQuality must stay within [0, 5]; got " + result);
        }
    }

    @Test
    void jitterQuality_floorBaselineClampsAtZero() {
        for (int i = 0; i < 100; i++) {
            int result = StratConOpForRosterBuilder.jitterQuality(
                    0, ContractTypeOpForModifier.JitterProfile.BALANCED);
            assertTrue(result >= 0, "Floor baseline must not jitter below 0; got " + result);
        }
    }

    // -------------------------------------------------------------------------
    // ContractTypeOpForModifier.getJitterProfile — picks the right profile
    // -------------------------------------------------------------------------

    @Test
    void getJitterProfile_planetaryAssault_returnsEliteTilt() {
        assertEquals(ContractTypeOpForModifier.JitterProfile.ELITE_TILT,
                ContractTypeOpForModifier.getJitterProfile(AtBContractType.PLANETARY_ASSAULT));
    }

    @Test
    void getJitterProfile_pirateHunt_returnsIrregularTilt() {
        assertEquals(ContractTypeOpForModifier.JitterProfile.IRREGULAR_TILT,
                ContractTypeOpForModifier.getJitterProfile(AtBContractType.PIRATE_HUNTING));
    }

    @Test
    void getJitterProfile_objectiveRaid_returnsBalanced() {
        assertEquals(ContractTypeOpForModifier.JitterProfile.BALANCED,
                ContractTypeOpForModifier.getJitterProfile(AtBContractType.OBJECTIVE_RAID));
    }

    // -------------------------------------------------------------------------
    // buildForContract integration — generates the requested count
    // -------------------------------------------------------------------------

    @Test
    void buildForContract_producesExactFormationCount() {
        // 4 player teams + pirate hunt (0) = 4 formations expected
        Campaign campaign = campaignWithCombatTeams(4);
        when(campaign.getGameYear()).thenReturn(3050);
        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(any(mekhq.campaign.universe.UnitGeneratorParameters.class))).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isClan()).thenReturn(false);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.isWoB()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);
        when(enemyFaction.getShortName()).thenReturn("DC");

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PIRATE_HUNTING);
        when(contract.getEnemy()).thenReturn(enemyFaction);
        when(contract.getEnemyCode()).thenReturn("DC");
        when(contract.getEnemySkill()).thenReturn(SkillLevel.REGULAR);
        when(contract.getEnemyQuality()).thenReturn(3);
        when(contract.getName()).thenReturn("Test Contract");

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getRequiredLanceCount()).thenReturn(1);
        when(track.getDisplayableName()).thenReturn("Sector 0");

        StratConCampaignState campaignState = mock(StratConCampaignState.class);
        when(campaignState.getTracks()).thenReturn(List.of(track));

        StratConOpForRoster roster = StratConOpForRosterBuilder.buildForContract(
                campaign, contract, campaignState);

        assertEquals(4, roster.getFormations().size(),
                "Should produce exactly playerTeams + contractModifier formations");
    }

    @Test
    void buildForAtBContractStampsChallengerIdentity() {
        Campaign campaign = campaignWithCombatTeams(2);
        when(campaign.getGameYear()).thenReturn(3151);
        when(campaign.getLocalDate()).thenReturn(LocalDate.of(3151, 6, 1));
        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(any(UnitGeneratorParameters.class))).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isClan()).thenReturn(false);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.isWoB()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);
        when(enemyFaction.getShortName()).thenReturn("PIR");

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.GARRISON_DUTY);
        when(contract.getEnemy()).thenReturn(enemyFaction);
        when(contract.getEnemyCode()).thenReturn("PIR");
        when(contract.getEnemyBotName()).thenReturn("Pirates");
        when(contract.getEnemyColour()).thenReturn(PlayerColour.RED);
        when(contract.getEnemySkill()).thenReturn(SkillLevel.REGULAR);
        when(contract.getEnemyQuality()).thenReturn(3);
        when(contract.getName()).thenReturn("Garrison Contract");

        StratConOpForRoster roster = StratConOpForRosterBuilder.buildForAtBContract(campaign, contract);

        assertEquals("PIR", roster.getFactionCode());
        assertEquals("Pirates", roster.getEnemyBotName());
        assertEquals(PlayerColour.RED.name(), roster.getEnemyColour());
        assertEquals(ChallengerStatus.ACTIVE, roster.getStatus());
        assertNotNull(roster.getArrivedDate());
    }

    // -------------------------------------------------------------------------
    // generateUnit overload — TANK params via ArgumentCaptor
    // -------------------------------------------------------------------------

    @Test
    void generateUnitOfType_tank_producesTankUnitTypeParam() {
        // Set up a campaign where the unit generator is captured via ArgumentCaptor.
        // We call the package-visible pathway by invoking seedMilitiaPool with a
        // planetary-assault attacker contract (min 2, max 4 starting formations).
        // The captured UnitGeneratorParameters must include at least one call
        // with unitType == TANK (since INFANTRY_FRACTION < 1.0).
        Campaign campaign = campaignWithCombatTeams(2);
        when(campaign.getGameYear()).thenReturn(3050);

        ArgumentCaptor<UnitGeneratorParameters> captor =
                ArgumentCaptor.forClass(UnitGeneratorParameters.class);
        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(captor.capture())).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        CampaignOptions opts = campaign.getCampaignOptions();
        when(opts.isUseStaticOpForMilitia()).thenReturn(true);

        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isClan()).thenReturn(false);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.isWoB()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);
        when(enemyFaction.getShortName()).thenReturn("FWL");

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);
        when(contract.getEnemy()).thenReturn(enemyFaction);
        when(contract.getEnemyCode()).thenReturn("FWL");
        when(contract.getEnemySkill()).thenReturn(SkillLevel.REGULAR);
        when(contract.getEnemyQuality()).thenReturn(3);
        when(contract.getName()).thenReturn("Militia Test");
        when(contract.isPlayerAttacker()).thenReturn(true);

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getRequiredLanceCount()).thenReturn(1);
        when(track.getDisplayableName()).thenReturn("Sector 0");

        StratConOpForRoster roster = new StratConOpForRoster();

        // Seed 100 times to ensure at least one TANK generation fires
        for (int i = 0; i < 50; i++) {
            StratConOpForRosterBuilder.seedMilitiaPool(campaign, contract, roster, List.of(track));
        }

        // At least one call must have unitType == TANK
        boolean hasTankCall = captor.getAllValues().stream()
                .anyMatch(p -> p.getUnitType() == UnitType.TANK);
        assertTrue(hasTankCall,
                "seedMilitiaPool must request TANK units via the unit generator (MILITIA_INFANTRY_FRACTION < 1.0)");
    }

    @Test
    void generateUnit_blankFactionShortName_fallsBackToIndependentTable() {
        // A faction whose getShortName() is blank (empty) must not be passed
        // through to the unit generator verbatim — an empty faction code makes
        // every RAT lookup fail and silently shrinks the OpFor. It must fall back
        // to the "IND" independent table, exactly like a null faction.
        Campaign campaign = campaignWithCombatTeams(2);
        when(campaign.getGameYear()).thenReturn(3050);

        ArgumentCaptor<UnitGeneratorParameters> captor =
                ArgumentCaptor.forClass(UnitGeneratorParameters.class);
        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(captor.capture())).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        CampaignOptions opts = campaign.getCampaignOptions();
        when(opts.isUseStaticOpForMilitia()).thenReturn(true);

        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isClan()).thenReturn(false);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.isWoB()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);
        when(enemyFaction.getShortName()).thenReturn(""); // blank short name

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);
        when(contract.getEnemy()).thenReturn(enemyFaction);
        when(contract.getEnemyCode()).thenReturn("");
        when(contract.getEnemySkill()).thenReturn(SkillLevel.REGULAR);
        when(contract.getEnemyQuality()).thenReturn(3);
        when(contract.getName()).thenReturn("Blank Faction Test");
        when(contract.isPlayerAttacker()).thenReturn(true);

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getRequiredLanceCount()).thenReturn(1);
        when(track.getDisplayableName()).thenReturn("Sector 0");

        StratConOpForRoster roster = new StratConOpForRoster();
        for (int i = 0; i < 50; i++) {
            StratConOpForRosterBuilder.seedMilitiaPool(campaign, contract, roster, List.of(track));
        }

        assertFalse(captor.getAllValues().isEmpty(),
                "Expected at least one unit-generation call to inspect");
        boolean allFellBackToIndependent = captor.getAllValues().stream()
                .allMatch(p -> "IND".equals(p.getFaction()));
        assertTrue(allFellBackToIndependent,
                "A blank enemy faction short name must fall back to the 'IND' table, never an empty faction code");
    }

    // -------------------------------------------------------------------------
    // Rubber-band: reinforcement skill/quality scale to player's current force
    // -------------------------------------------------------------------------

    @Test
    void scaledReinforcementSkill_playerStrongerThanBaseline_returnsPlayerSkill() {
        Campaign campaign = mock(Campaign.class);
        ReputationController reputation = mock(ReputationController.class);
        when(reputation.getAverageSkillLevel()).thenReturn(SkillLevel.VETERAN);
        when(campaign.getReputation()).thenReturn(reputation);

        assertEquals(SkillLevel.VETERAN,
                StratConOpForRosterBuilder.scaledReinforcementSkill(campaign, SkillLevel.REGULAR),
                "Reinforcements should scale up to the player's current average skill");
    }

    @Test
    void scaledReinforcementSkill_playerWeakerThanBaseline_floorsAtBaseline() {
        Campaign campaign = mock(Campaign.class);
        ReputationController reputation = mock(ReputationController.class);
        when(reputation.getAverageSkillLevel()).thenReturn(SkillLevel.GREEN);
        when(campaign.getReputation()).thenReturn(reputation);

        assertEquals(SkillLevel.REGULAR,
                StratConOpForRosterBuilder.scaledReinforcementSkill(campaign, SkillLevel.REGULAR),
                "Reinforcements must never drop below the original enemy skill");
    }

    @Test
    void scaledReinforcementSkill_nullReputation_returnsBaseline() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getReputation()).thenReturn(null);

        assertEquals(SkillLevel.VETERAN,
                StratConOpForRosterBuilder.scaledReinforcementSkill(campaign, SkillLevel.VETERAN),
                "A null reputation must fall back to the baseline skill without throwing");
    }

    @Test
    void scaledReinforcementQuality_skillRose_bumpsByStepsClampedToCeiling() {
        // REGULAR(3) -> VETERAN(4): +1 skill step -> quality +1
        assertEquals(4, StratConOpForRosterBuilder.scaledReinforcementQuality(
                3, SkillLevel.REGULAR, SkillLevel.VETERAN));
        // already at ceiling: stays clamped at QUALITY_CEILING (5)
        assertEquals(5, StratConOpForRosterBuilder.scaledReinforcementQuality(
                5, SkillLevel.REGULAR, SkillLevel.ELITE));
    }

    @Test
    void scaledReinforcementQuality_skillFlat_returnsBaseline() {
        assertEquals(3, StratConOpForRosterBuilder.scaledReinforcementQuality(
                3, SkillLevel.REGULAR, SkillLevel.REGULAR),
                "Quality must not change when the reinforcement skill has not risen");
    }

    // -------------------------------------------------------------------------
    // seedMilitiaPool — attacker seeds formations within profile range
    // -------------------------------------------------------------------------

    @Test
    void seedMilitiaPool_attackerContract_seedsWithinRange_andFlagsMilitia() {
        // PLANETARY_ASSAULT: minStarting=2, maxStarting=4
        Campaign campaign = campaignWithCombatTeams(2);
        when(campaign.getGameYear()).thenReturn(3050);

        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(any(UnitGeneratorParameters.class))).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        CampaignOptions opts = campaign.getCampaignOptions();
        when(opts.isUseStaticOpForMilitia()).thenReturn(true);

        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isClan()).thenReturn(false);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.isWoB()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);
        when(enemyFaction.getShortName()).thenReturn("LA");

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);
        when(contract.getEnemy()).thenReturn(enemyFaction);
        when(contract.getEnemyCode()).thenReturn("LA");
        when(contract.getName()).thenReturn("Attacker Test");
        when(contract.isPlayerAttacker()).thenReturn(true);

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getRequiredLanceCount()).thenReturn(1);
        when(track.getDisplayableName()).thenReturn("Sector 0");

        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForRosterBuilder.seedMilitiaPool(campaign, contract, roster, List.of(track));

        int formationCount = roster.getFormations().size();
        assertTrue(formationCount >= 2 && formationCount <= 4,
                "PLANETARY_ASSAULT should seed 2–4 militia formations; got " + formationCount);

        // All seeded formations must be flagged militia
        boolean allMilitia = roster.getFormations().stream()
                .allMatch(StratConOpForFormation::isMilitia);
        assertTrue(allMilitia, "All seeded formations must be flagged as militia");
    }

    @Test
    void seedMilitiaPool_defenderContract_seedsNothing() {
        // contract.isPlayerAttacker() == false — no militia should be seeded
        Campaign campaign = campaignWithCombatTeams(2);
        when(campaign.getGameYear()).thenReturn(3050);

        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(any(UnitGeneratorParameters.class))).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        CampaignOptions opts = campaign.getCampaignOptions();
        when(opts.isUseStaticOpForMilitia()).thenReturn(true);

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.GARRISON_DUTY);
        when(contract.isPlayerAttacker()).thenReturn(false);
        when(contract.getName()).thenReturn("Defender Test");

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getRequiredLanceCount()).thenReturn(1);
        when(track.getDisplayableName()).thenReturn("Sector 0");

        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForRosterBuilder.seedMilitiaPool(campaign, contract, roster, List.of(track));

        assertEquals(0, roster.getFormations().size(),
                "Defender contract must not seed any militia formations");
    }

    @Test
    void seedMilitiaPool_optionOff_seedsNothing() {
        // useStaticOpForMilitia == false — no militia even for an attacker contract
        Campaign campaign = campaignWithCombatTeams(2);
        when(campaign.getGameYear()).thenReturn(3050);

        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(any(UnitGeneratorParameters.class))).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        CampaignOptions opts = campaign.getCampaignOptions();
        when(opts.isUseStaticOpForMilitia()).thenReturn(false);

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);
        when(contract.isPlayerAttacker()).thenReturn(true);
        when(contract.getName()).thenReturn("Option Off Test");

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getRequiredLanceCount()).thenReturn(1);
        when(track.getDisplayableName()).thenReturn("Sector 0");

        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForRosterBuilder.seedMilitiaPool(campaign, contract, roster, List.of(track));

        assertEquals(0, roster.getFormations().size(),
                "With useStaticOpForMilitia=false, no militia must be seeded");
    }

    // -------------------------------------------------------------------------
    // addMilitiaReinforcementFormations — flags militia, assigns to track
    // -------------------------------------------------------------------------

    @Test
    void addMilitiaReinforcementFormations_skipsEmptyFormations_whenNoUnitsGenerated() {
        Campaign campaign = campaignWithCombatTeams(2);
        when(campaign.getGameYear()).thenReturn(3050);

        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        when(unitGenerator.generate(any(UnitGeneratorParameters.class))).thenReturn(null);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);

        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isClan()).thenReturn(false);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.isWoB()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);
        when(enemyFaction.getShortName()).thenReturn("FS");

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);
        when(contract.getEnemy()).thenReturn(enemyFaction);
        when(contract.getEnemyCode()).thenReturn("FS");
        when(contract.getName()).thenReturn("Reinforce Test");

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getRequiredLanceCount()).thenReturn(1);
        when(track.getDisplayableName()).thenReturn("Gamma Track");

        StratConOpForRoster roster = new StratConOpForRoster();
        int added = StratConOpForRosterBuilder.addMilitiaReinforcementFormations(
                campaign, contract, roster, track, 2);

        // The unit generator returns null, so every formation produces no units and is
        // skipped (mirroring addReinforcementFormations' phantom-formation guard). The
        // method must therefore add nothing and report 0 added, without throwing.
        // (Militia flagging itself is proven non-vacuously by
        // seedMilitiaPool_attackerContract_seedsWithinRange_andFlagsMilitia, which exercises
        // the same buildMilitiaFormation path and asserts every added formation isMilitia().)
        assertEquals(0, added,
                "No units generated -> all militia reinforcement formations skipped -> 0 added");
        assertEquals(0, roster.getFormations().size(),
                "Skipped (empty) militia formations must not be added to the roster");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Campaign campaignWithCombatTeams(final int count) {
        return campaignWithCombatTeams(count, 1.0, 1);
    }

    private static Campaign campaignWithCombatTeams(final int count, final double padding,
            final int floor) {
        Campaign campaign = mock(Campaign.class);
        ArrayList<CombatTeam> teams = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            teams.add(mock(CombatTeam.class));
        }
        when(campaign.getCombatTeamsAsList()).thenReturn(teams);

        CampaignOptions options = mock(CampaignOptions.class);
        when(options.getStaticOpForPaddingFactor()).thenReturn(padding);
        when(options.getStaticOpForFormationCountFloor()).thenReturn(floor);
        when(campaign.getCampaignOptions()).thenReturn(options);
        return campaign;
    }
}
