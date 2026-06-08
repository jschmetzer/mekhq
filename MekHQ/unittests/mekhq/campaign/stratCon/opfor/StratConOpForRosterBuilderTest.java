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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;
import mekhq.campaign.universe.Faction;

/**
 * Tests for {@link StratConOpForRosterBuilder} sizing math.
 */
class StratConOpForRosterBuilderTest {

    /**
     * For a 1-month contract with a formation floor of 3, the computed roster
     * BV must be at least {@code floor × oneFormationTargetBV}.
     */
    @Test
    void computeFinalRosterBv_oneMonthContract_respectsFormationFloor() {
        int formationFloor = 3;
        int unitsPerFormation = 4; // IS faction
        double oneFormationTargetBV = unitsPerFormation * StratConOpForRosterBuilder.BASE_SCENARIO_BV;
        double expectedFloor = formationFloor * oneFormationTargetBV;

        // Campaign mocks
        CampaignOptions opts = mock(CampaignOptions.class);
        when(opts.getSkillLevel()).thenReturn(megamek.common.enums.SkillLevel.REGULAR);
        when(opts.getStaticOpForPaddingFactor()).thenReturn(1.25);
        when(opts.getStaticOpForFormationCountFloor()).thenReturn(formationFloor);

        Campaign campaign = mock(Campaign.class);
        when(campaign.getCampaignOptions()).thenReturn(opts);

        // Enemy faction — IS (4 units per formation)
        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(unitsPerFormation);

        // Contract — 1 month
        AtBContract contract = mock(AtBContract.class);
        when(contract.getLength()).thenReturn(1);
        when(contract.getEnemy()).thenReturn(enemyFaction);

        // Campaign state with two tracks, each low-odds
        StratConTrackState track1 = mock(StratConTrackState.class);
        when(track1.getScenarioOdds()).thenReturn(10);
        when(track1.getRequiredLanceCount()).thenReturn(1);

        StratConTrackState track2 = mock(StratConTrackState.class);
        when(track2.getScenarioOdds()).thenReturn(10);
        when(track2.getRequiredLanceCount()).thenReturn(1);

        List<StratConTrackState> tracks = new ArrayList<>();
        tracks.add(track1);
        tracks.add(track2);

        StratConCampaignState campaignState = mock(StratConCampaignState.class);
        when(campaignState.getTracks()).thenReturn(tracks);

        double result = StratConOpForRosterBuilder.computeFinalRosterBv(campaign, contract, campaignState);

        assertTrue(result >= expectedFloor,
                () -> String.format("Expected result >= %.0f (floor), but got %.0f", expectedFloor, result));
    }

    /**
     * A longer contract should produce a larger roster BV than a short one
     * (assuming same options and the target exceeds the floor).
     */
    @Test
    void computeFinalRosterBv_longerContractProducesLargerTarget() {
        // Campaign with high difficulty so target BV is large
        CampaignOptions opts = mock(CampaignOptions.class);
        when(opts.getSkillLevel()).thenReturn(megamek.common.enums.SkillLevel.ELITE);
        when(opts.getStaticOpForPaddingFactor()).thenReturn(1.25);
        when(opts.getStaticOpForFormationCountFloor()).thenReturn(1);

        Campaign campaign = mock(Campaign.class);
        when(campaign.getCampaignOptions()).thenReturn(opts);

        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);

        StratConTrackState track = mock(StratConTrackState.class);
        when(track.getScenarioOdds()).thenReturn(50);
        when(track.getRequiredLanceCount()).thenReturn(3);
        List<StratConTrackState> tracks = List.of(track);

        StratConCampaignState shortState = mock(StratConCampaignState.class);
        when(shortState.getTracks()).thenReturn(tracks);
        AtBContract shortContract = mock(AtBContract.class);
        when(shortContract.getLength()).thenReturn(1);
        when(shortContract.getEnemy()).thenReturn(enemyFaction);

        StratConCampaignState longState = mock(StratConCampaignState.class);
        when(longState.getTracks()).thenReturn(tracks);
        AtBContract longContract = mock(AtBContract.class);
        when(longContract.getLength()).thenReturn(6);
        when(longContract.getEnemy()).thenReturn(enemyFaction);

        double shortBv = StratConOpForRosterBuilder.computeFinalRosterBv(campaign, shortContract, shortState);
        double longBv = StratConOpForRosterBuilder.computeFinalRosterBv(campaign, longContract, longState);

        assertTrue(longBv > shortBv,
                () -> String.format(
                        "6-month BV (%.0f) should exceed 1-month BV (%.0f)", longBv, shortBv));
    }
}
