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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import mekhq.campaign.stratCon.StratConCampaignState;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FacilityCaptureEffects#enemyDeltaTarget}, the multi-challenger selection that decides which
 * single active challenger a facility's enemy roster delta applies to.
 */
class FacilityCaptureEffectsTest {
    private static final String TRACK_A = "Track A";
    private static final String TRACK_B = "Track B";

    /** A challenger with one living (READY) formation assigned to the given track. */
    /** A challenger with {@code count} living (READY) formations assigned to the given track. */
    private static StratConOpForRoster rosterWithLivingFormationsOn(final String trackName, final int count) {
        StratConOpForRoster roster = new StratConOpForRoster();
        for (int i = 0; i < count; i++) {
            StratConOpForFormation formation = new StratConOpForFormation();
            formation.setAssignedTrackName(trackName);
            StratConOpForUnit unit = new StratConOpForUnit();
            unit.setStatus(Status.READY);
            unit.setFormationId(formation.getId());
            roster.addUnit(unit);
            formation.getUnitIds().add(unit.getId());
            roster.addFormation(formation);
        }
        return roster;
    }

    private static StratConOpForRoster rosterWithLivingFormationOn(final String trackName) {
        return rosterWithLivingFormationsOn(trackName, 1);
    }

    @Test
    void shrink_targetsTheChallengerContestingTheAffectedTrack() {
        StratConOpForRoster onTrackA = rosterWithLivingFormationOn(TRACK_A);
        StratConOpForRoster onTrackB = rosterWithLivingFormationOn(TRACK_B);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(state.getActiveChallengers()).thenReturn(List.of(onTrackA, onTrackB));

        StratConOpForRoster target = FacilityCaptureEffects.enemyDeltaTarget(state, TRACK_A, -1);

        assertSame(onTrackA, target,
                "a shrink must target the challenger with living formations on the affected track, not the other");
    }

    @Test
    void sharedTrack_appliesToASingleChallenger_theMostInvested() {
        // Both challengers contest TRACK_A. The delta is a single bounded facility effect, so it must land on ONE
        // roster (the most invested — the one with the most formations on the track), never be applied to both
        // (which would double the capped effect).
        StratConOpForRoster heavier = rosterWithLivingFormationsOn(TRACK_A, 2);
        StratConOpForRoster lighter = rosterWithLivingFormationsOn(TRACK_A, 1);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(state.getActiveChallengers()).thenReturn(List.of(lighter, heavier));

        assertSame(heavier, FacilityCaptureEffects.enemyDeltaTarget(state, TRACK_A, -1),
                "a shrink on a shared track hits the single most-invested challenger");
        assertSame(heavier, FacilityCaptureEffects.enemyDeltaTarget(state, TRACK_A, +1),
                "a reinforcement on a shared track reinforces the single most-invested challenger");
    }

    @Test
    void reinforce_withNoChallengerOnTrack_fallsBackToTheDistinctPrimary() {
        // Two off-track active challengers plus a DISTINCT primary — proves the fallback returns the primary
        // specifically, not just "some challenger."
        StratConOpForRoster offTrack1 = rosterWithLivingFormationOn(TRACK_B);
        StratConOpForRoster offTrack2 = rosterWithLivingFormationOn(TRACK_B);
        StratConOpForRoster primary = rosterWithLivingFormationOn(TRACK_B);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(state.getActiveChallengers()).thenReturn(List.of(offTrack1, offTrack2));
        when(state.getOpForRoster()).thenReturn(primary);

        assertSame(primary, FacilityCaptureEffects.enemyDeltaTarget(state, TRACK_A, +1),
                "a reinforcement on an uncontested track falls back to the primary challenger specifically");
    }

    @Test
    void shrink_withNoChallengerOnTrack_isNoOp_evenWhenAPrimaryExists() {
        // Primary is stubbed non-null so the no-op is proven by the enemyDelta<0 sign guard, not by a null default.
        StratConOpForRoster offTrack = rosterWithLivingFormationOn(TRACK_B);
        StratConOpForRoster primary = rosterWithLivingFormationOn(TRACK_B);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(state.getActiveChallengers()).thenReturn(List.of(offTrack));
        when(state.getOpForRoster()).thenReturn(primary);

        assertNull(FacilityCaptureEffects.enemyDeltaTarget(state, TRACK_A, -1),
                "a shrink with no on-track challenger must NOT fall back to the primary");
    }
}
