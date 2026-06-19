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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;
import mekhq.campaign.stratCon.opfor.ContractTypeMilitiaReinforcementProfile.MilitiaProfile;

class MilitiaReinforcementServiceTest {

    // -------------------------------------------------------------------------
    // Deterministic eligibility gate — shouldAttemptReinforcement
    // -------------------------------------------------------------------------

    /** Probability 1.0, threshold ADVANCING, cap 3 — for gate tests. */
    private static final MilitiaProfile GATE_PROFILE =
            new MilitiaProfile(AtBMoraleLevel.ADVANCING, 1.0, 1, 2, 3, 0, 0);

    @Test
    void shouldAttempt_upwardIntoHighMorale_true() {
        assertTrue(MilitiaReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING, 0),
                "Militia reinforce when enemy ascendant (morale rose to threshold)");
    }

    @Test
    void shouldAttempt_downwardShift_false() {
        assertFalse(MilitiaReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.ADVANCING, AtBMoraleLevel.STALEMATE, 0),
                "Falling morale must not trigger militia reinforcement");
    }

    @Test
    void shouldAttempt_noShift_false() {
        assertFalse(MilitiaReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.ADVANCING, AtBMoraleLevel.ADVANCING, 0),
                "Flat morale must not trigger militia reinforcement");
    }

    @Test
    void shouldAttempt_upwardButBelowThreshold_false() {
        assertFalse(MilitiaReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.CRITICAL, AtBMoraleLevel.WEAKENED, 0),
                "Rose, but still below threshold — not eligible");
    }

    @Test
    void shouldAttempt_capReached_false() {
        assertFalse(MilitiaReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING, 3),
                "At the event cap — not eligible");
    }

    @Test
    void shouldAttempt_neverProfile_false() {
        MilitiaProfile never = new MilitiaProfile(AtBMoraleLevel.STALEMATE, 0.0, 0, 0, 0, 0, 0);
        assertFalse(MilitiaReinforcementService.shouldAttemptReinforcement(
                never, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.OVERWHELMING, 0),
                "NEVER-equivalent profile must never be eligible");
    }

    // -------------------------------------------------------------------------
    // maybeReinforce — no-op paths
    // -------------------------------------------------------------------------

    @Test
    void maybeReinforce_notAttacker_doesNothing() {
        // contract.isAttacker() == false → early return, militia cap stays 0
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PLANETARY_ASSAULT,
                /*hasRoster=*/true, roster, /*isAttacker=*/false, /*useStaticOpForMilitia=*/true);
        Campaign campaign = campaignWith(contract, /*useStaticOpForMilitia=*/true);

        MilitiaReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING);

        assertEquals(0, roster.getMilitiaReinforcementEventsFired(),
                "Non-attacker contract must not fire militia reinforcement");
    }

    @Test
    void maybeReinforce_noStratConState_doesNothing() {
        // contract.getStratconCampaignState() == null → early return
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);
        when(contract.getName()).thenReturn("Test Contract");
        when(contract.isAttacker()).thenReturn(true);
        when(contract.getStratconCampaignState()).thenReturn(null);

        Campaign campaign = mock(Campaign.class);
        CampaignOptions opts = mock(CampaignOptions.class);
        when(opts.isUseStaticOpForMilitia()).thenReturn(true);
        when(campaign.getCampaignOptions()).thenReturn(opts);

        // Must not throw; nothing to assert since there's no roster
        MilitiaReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING);
    }

    @Test
    void maybeReinforce_militiaOptionOff_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PLANETARY_ASSAULT,
                /*hasRoster=*/true, roster, /*isAttacker=*/true, /*useStaticOpForMilitia=*/false);
        Campaign campaign = campaignWith(contract, /*useStaticOpForMilitia=*/false);

        MilitiaReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING);

        assertEquals(0, roster.getMilitiaReinforcementEventsFired(),
                "Militia option off must suppress reinforcement");
    }

    @Test
    void maybeReinforce_cadreDuty_doesNothing() {
        // Cadre Duty maps to NEVER profile — use an upward-into-threshold morale shift
        // so the NEVER profile is the only reason for the no-op (not the morale direction).
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.CADRE_DUTY,
                /*hasRoster=*/true, roster, /*isAttacker=*/true, /*useStaticOpForMilitia=*/true);
        Campaign campaign = campaignWith(contract, /*useStaticOpForMilitia=*/true);

        MilitiaReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.OVERWHELMING);

        assertEquals(0, roster.getMilitiaReinforcementEventsFired(),
                "Cadre Duty has NEVER profile — no militia reinforcement even when morale rises");
    }

    @Test
    void maybeReinforce_moraleNotShifted_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PLANETARY_ASSAULT,
                /*hasRoster=*/true, roster, /*isAttacker=*/true, /*useStaticOpForMilitia=*/true);
        Campaign campaign = campaignWith(contract, /*useStaticOpForMilitia=*/true);

        MilitiaReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.CRITICAL, AtBMoraleLevel.CRITICAL);

        assertEquals(0, roster.getMilitiaReinforcementEventsFired(),
                "Flat morale must not trigger militia reinforcement");
    }

    @Test
    void maybeReinforce_moraleShiftedDownward_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PLANETARY_ASSAULT,
                /*hasRoster=*/true, roster, /*isAttacker=*/true, /*useStaticOpForMilitia=*/true);
        Campaign campaign = campaignWith(contract, /*useStaticOpForMilitia=*/true);

        MilitiaReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.CRITICAL);

        assertEquals(0, roster.getMilitiaReinforcementEventsFired(),
                "Downward morale (enemy losing) must not trigger militia reinforcement");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Campaign campaignWith(final AtBContract contract,
            final boolean useStaticOpForMilitia) {
        Campaign campaign = mock(Campaign.class);
        CampaignOptions opts = mock(CampaignOptions.class);
        when(opts.isUseStaticOpForMilitia()).thenReturn(useStaticOpForMilitia);
        when(campaign.getCampaignOptions()).thenReturn(opts);
        return campaign;
    }

    private static AtBContract contractOf(final AtBContractType type,
            final boolean hasRoster,
            final StratConOpForRoster roster,
            final boolean isAttacker,
            final boolean useStaticOpForMilitia) {
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(type);
        when(contract.getName()).thenReturn("Test Contract");
        when(contract.isAttacker()).thenReturn(isAttacker);

        if (hasRoster) {
            StratConTrackState track = mock(StratConTrackState.class);
            when(track.getDisplayableName()).thenReturn("Sector 0");
            when(track.isPacified()).thenReturn(false);
            when(track.getRequiredLanceCount()).thenReturn(1);

            StratConCampaignState state = mock(StratConCampaignState.class);
            when(state.getOpForRoster()).thenReturn(roster);
            when(state.getTracks()).thenReturn(List.of(track));
            when(contract.getStratconCampaignState()).thenReturn(state);
        } else {
            when(contract.getStratconCampaignState()).thenReturn(null);
        }
        return contract;
    }
}
