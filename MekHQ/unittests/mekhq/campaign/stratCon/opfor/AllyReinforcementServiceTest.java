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
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.campaign.stratCon.opfor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;

class AllyReinforcementServiceTest {

    @Test
    void maybeReinforce_nullCampaignState_doesNothing() {
        Campaign campaign = mock(Campaign.class);
        AtBContract contract = contractOf(AtBContractType.GARRISON_DUTY, null);

        // Should not crash — null StratConCampaignState early-exit
        AllyReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING);
    }

    @Test
    void maybeReinforce_nullAllyRosterButHasCampaignState_doesNothing() {
        // Cover the roster == null guard distinct from the campaignState == null guard.
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(state.getAlliedRoster()).thenReturn(null);

        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.GARRISON_DUTY);
        when(contract.getName()).thenReturn("Test Contract");
        when(contract.getStratconCampaignState()).thenReturn(state);

        Campaign campaign = mock(Campaign.class);

        // Should not crash — null alliedRoster early-exit
        AllyReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING);
    }

    @Test
    void maybeReinforce_neverProfile_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PIRATE_HUNTING, roster);
        Campaign campaign = mock(Campaign.class);

        AllyReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.DOMINATING);

        assertEquals(0, roster.getReinforcementEventsFired(),
                "PIRATE_HUNTING never gets ally reinforcements");
    }

    @Test
    void maybeReinforce_moraleShiftedDown_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.GARRISON_DUTY, roster);
        Campaign campaign = mock(Campaign.class);

        // Player winning → morale DROPPED — ally reinforcements should NOT fire
        AllyReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.DOMINATING, AtBMoraleLevel.WEAKENED);

        assertEquals(0, roster.getReinforcementEventsFired(),
                "Downward morale shift should not trigger ally reinforcements");
    }

    @Test
    void maybeReinforce_moraleBelowThreshold_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PLANETARY_ASSAULT, roster);
        Campaign campaign = mock(Campaign.class);

        // Morale rose to ADVANCING — below PLANETARY_ASSAULT's DOMINATING threshold
        AllyReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING);

        assertEquals(0, roster.getReinforcementEventsFired(),
                "Below trigger threshold — no reinforcement");
    }

    @Test
    void maybeReinforce_moraleStable_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.GARRISON_DUTY, roster);
        Campaign campaign = mock(Campaign.class);

        // Morale unchanged — no shift, no trigger
        AllyReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.ADVANCING, AtBMoraleLevel.ADVANCING);

        assertEquals(0, roster.getReinforcementEventsFired(),
                "Flat morale — no shift, no reinforcement");
    }

    @Test
    void maybeReinforce_capReached_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        // GARRISON_DUTY ally cap is 4
        roster.setReinforcementEventsFired(4);
        AtBContract contract = contractOf(AtBContractType.GARRISON_DUTY, roster);
        Campaign campaign = mock(Campaign.class);

        AllyReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.DOMINATING);

        assertEquals(4, roster.getReinforcementEventsFired(),
                "Counter should not increment past the cap");
    }

    private static AtBContract contractOf(final AtBContractType type,
            final StratConOpForRoster roster) {
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(type);
        when(contract.getName()).thenReturn("Test Contract");

        if (roster != null) {
            StratConTrackState track = mock(StratConTrackState.class);
            when(track.getDisplayableName()).thenReturn("Sector 0");
            when(track.isPacified()).thenReturn(false);
            when(track.getRequiredLanceCount()).thenReturn(1);

            StratConCampaignState state = mock(StratConCampaignState.class);
            when(state.getAlliedRoster()).thenReturn(roster);
            when(state.getTracks()).thenReturn(List.of(track));
            when(contract.getStratconCampaignState()).thenReturn(state);
        } else {
            when(contract.getStratconCampaignState()).thenReturn(null);
        }
        return contract;
    }
}
