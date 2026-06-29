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
package mekhq.campaign.stratCon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.opfor.ChallengerStatus;
import mekhq.campaign.stratCon.opfor.StratConOpForRoster;
import org.junit.jupiter.api.Test;

class StratConCampaignStateChallengerTest {
    private static StratConOpForRoster challenger(String code, ChallengerStatus status) {
        StratConOpForRoster r = new StratConOpForRoster();
        r.setFactionCode(code);
        r.setStatus(status);
        return r;
    }

    @Test
    void getActiveChallengersExcludesTerminal() {
        StratConCampaignState state = new StratConCampaignState();
        state.addChallenger(challenger("PIR", ChallengerStatus.DEFEATED));
        StratConOpForRoster dc = challenger("DC", ChallengerStatus.ACTIVE);
        state.addChallenger(dc);

        List<StratConOpForRoster> active = state.getActiveChallengers();

        assertEquals(1, active.size());
        assertSame(dc, active.get(0));
    }

    @Test
    void primaryChallengerIsNewestActive() {
        StratConCampaignState state = new StratConCampaignState();
        state.addChallenger(challenger("PIR", ChallengerStatus.ACTIVE));
        StratConOpForRoster dc = challenger("DC", ChallengerStatus.ACTIVE);
        state.addChallenger(dc);

        assertSame(dc, state.getPrimaryChallenger());
    }

    @Test
    void getOpForRosterReturnsPrimaryActiveForBackCompat() {
        StratConCampaignState state = new StratConCampaignState();
        StratConOpForRoster pir = challenger("PIR", ChallengerStatus.ACTIVE);
        state.setOpForRoster(pir); // legacy single-set call path used at contract acceptance

        assertSame(pir, state.getOpForRoster());
        assertEquals(1, state.getActiveChallengers().size());
    }

    @Test
    void getOpForRosterNullWhenNoActive() {
        assertNull(new StratConCampaignState().getOpForRoster());
    }

    @Test
    void migrateLegacyRosterMovesSingleRosterIntoChallengerList() {
        StratConCampaignState state = new StratConCampaignState();
        StratConOpForRoster legacy = challenger("PIR", ChallengerStatus.WITHDRAWN);
        state.setLegacyOpForRoster(legacy);

        state.migrateLegacyRoster();

        assertEquals(1, state.getOpForChallengers().size());
        assertSame(legacy, state.getOpForChallengers().get(0));
        assertEquals(ChallengerStatus.ACTIVE, legacy.getStatus(), "migrated legacy roster becomes the active challenger");
        assertNull(state.getLegacyOpForRoster());
    }

    @Test
    void migrateLegacyRosterIsNoOpWhenChallengersAlreadyPresent() {
        StratConCampaignState state = new StratConCampaignState();
        state.addChallenger(challenger("DC", ChallengerStatus.ACTIVE));
        state.setLegacyOpForRoster(challenger("PIR", ChallengerStatus.ACTIVE));

        state.migrateLegacyRoster();

        assertEquals(1, state.getOpForChallengers().size());
        assertTrue(state.getOpForChallengers().stream().anyMatch(c -> "DC".equals(c.getFactionCode())));
        assertFalse(state.getOpForChallengers().stream().anyMatch(c -> "PIR".equals(c.getFactionCode())),
                "the legacy roster must not be absorbed when challengers are already present");
    }

    @Test
    void backfillChallengerIdentitiesToleratesNullContractColour() {
        // Regression: a legacy save where the contract has a null enemy colour must NOT NPE during backfill.
        StratConCampaignState state = new StratConCampaignState();
        StratConOpForRoster unstamped = new StratConOpForRoster(); // factionCode null → eligible for backfill
        state.addChallenger(unstamped);
        AtBContract contract = mock(AtBContract.class);
        when(contract.getEnemyCode()).thenReturn("PIR");
        when(contract.getEnemyBotName()).thenReturn("Pirates");
        when(contract.getEnemyColour()).thenReturn(null);

        state.backfillChallengerIdentities(contract); // must not throw

        assertEquals("PIR", unstamped.getFactionCode());
        assertEquals("Pirates", unstamped.getEnemyBotName());
        assertNull(unstamped.getEnemyColour(), "colour left unset when the contract colour is null");
    }
}
