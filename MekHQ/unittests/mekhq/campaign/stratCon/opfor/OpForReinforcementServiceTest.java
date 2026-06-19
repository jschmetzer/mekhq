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
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;

class OpForReinforcementServiceTest {

    // -------------------------------------------------------------------------
    // No-op paths — verified by checking the counter doesn't move
    // -------------------------------------------------------------------------

    @Test
    void maybeReinforce_noRoster_doesNothing() {
        Campaign campaign = mock(Campaign.class);
        AtBContract contract = contractOf(AtBContractType.PIRATE_HUNTING, /*hasRoster=*/false);

        OpForReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.WEAKENED);
        // No assertions needed — the only side-effect would be on the roster,
        // and there is no roster. We're checking the method doesn't throw NPE.
    }

    @Test
    void maybeReinforce_cadreDuty_doesNothing() {
        // Cadre Duty has NEVER profile
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.CADRE_DUTY, /*hasRoster=*/true, roster);
        Campaign campaign = mock(Campaign.class);

        OpForReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.CRITICAL);

        assertEquals(0, roster.getReinforcementEventsFired(),
                "Cadre Duty should never fire reinforcements");
    }

    @Test
    void maybeReinforce_moraleNotShifted_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PIRATE_HUNTING, /*hasRoster=*/true, roster);
        Campaign campaign = mock(Campaign.class);

        // Same morale before and after → no shift, no reinforcement
        OpForReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.CRITICAL, AtBMoraleLevel.CRITICAL);

        assertEquals(0, roster.getReinforcementEventsFired(),
                "Reinforcements should not fire when morale didn't shift");
    }

    @Test
    void maybeReinforce_moraleShiftedDownward_doesNothing() {
        StratConOpForRoster roster = new StratConOpForRoster();
        AtBContract contract = contractOf(AtBContractType.PIRATE_HUNTING, /*hasRoster=*/true, roster);
        Campaign campaign = mock(Campaign.class);

        // Regression: enemy morale dropping (enemy losing) must NOT trigger enemy reinforcement.
        OpForReinforcementService.maybeReinforce(
                campaign, contract, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.CRITICAL);

        assertEquals(0, roster.getReinforcementEventsFired(),
                "Reinforcements must not fire when enemy morale falls (enemy losing)");
    }

    // -------------------------------------------------------------------------
    // Deterministic eligibility gate (everything except the probability roll)
    // OpFor reinforces when morale RISES to >= the profile's trigger threshold.
    // -------------------------------------------------------------------------

    /** Probability 1.0, threshold ADVANCING, cap 3 — for gate tests. */
    private static final ContractTypeReinforcementProfile.Profile GATE_PROFILE =
            new ContractTypeReinforcementProfile.Profile(AtBMoraleLevel.ADVANCING, 1.0, 1, 2, 3);

    @Test
    void shouldAttempt_upwardIntoHighMorale_true() {
        assertTrue(OpForReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING, 0),
                "Enemy ascendant (morale rose to threshold) should be eligible");
    }

    @Test
    void shouldAttempt_downwardShift_false() {
        assertFalse(OpForReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.ADVANCING, AtBMoraleLevel.STALEMATE, 0),
                "Enemy losing (morale fell) must not be eligible");
    }

    @Test
    void shouldAttempt_upwardButBelowThreshold_false() {
        assertFalse(OpForReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.CRITICAL, AtBMoraleLevel.WEAKENED, 0),
                "Rose, but still below the high threshold — not eligible");
    }

    @Test
    void shouldAttempt_noShift_false() {
        assertFalse(OpForReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.ADVANCING, AtBMoraleLevel.ADVANCING, 0),
                "No morale change — not eligible");
    }

    @Test
    void shouldAttempt_capReached_false() {
        assertFalse(OpForReinforcementService.shouldAttemptReinforcement(
                GATE_PROFILE, AtBMoraleLevel.STALEMATE, AtBMoraleLevel.ADVANCING, 3),
                "At the event cap — not eligible");
    }

    @Test
    void shouldAttempt_neverProfile_false() {
        assertFalse(OpForReinforcementService.shouldAttemptReinforcement(
                ContractTypeReinforcementProfile.NEVER,
                AtBMoraleLevel.STALEMATE, AtBMoraleLevel.OVERWHELMING, 0),
                "NEVER profile is never eligible");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AtBContract contractOf(final AtBContractType type, final boolean hasRoster) {
        return contractOf(type, hasRoster, hasRoster ? new StratConOpForRoster() : null);
    }

    private static AtBContract contractOf(final AtBContractType type,
            final boolean hasRoster,
            final StratConOpForRoster roster) {
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(type);
        when(contract.getName()).thenReturn("Test Contract");

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
