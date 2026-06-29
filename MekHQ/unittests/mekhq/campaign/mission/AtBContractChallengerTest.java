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
package mekhq.campaign.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.stratCon.opfor.ChallengerStatus;
import mekhq.campaign.stratCon.opfor.StratConOpForRoster;
import mekhq.campaign.stratCon.opfor.StratConOpForUnit;
import mekhq.campaign.stratCon.opfor.Status;
import org.junit.jupiter.api.Test;

class AtBContractChallengerTest {
    private static StratConOpForRoster challenger(String code, ChallengerStatus status) {
        StratConOpForRoster r = new StratConOpForRoster();
        r.setFactionCode(code);
        r.setStatus(status);
        return r;
    }

    @Test
    void getOpForRosterReturnsNewestActiveFromAtbList() {
        AtBContract contract = new AtBContract();
        StratConOpForRoster pir = challenger("PIR", ChallengerStatus.ACTIVE);
        StratConOpForRoster dc = challenger("DC", ChallengerStatus.ACTIVE);
        contract.addAtbChallenger(pir);
        contract.addAtbChallenger(dc);

        assertSame(dc, contract.getOpForRoster());
        assertEquals(2, contract.getActiveOpForChallengers().size());
    }

    @Test
    void getActiveOpForChallengersExcludesTerminal() {
        AtBContract contract = new AtBContract();
        StratConOpForRoster pir = challenger("PIR", ChallengerStatus.WITHDRAWN);
        StratConOpForRoster dc = challenger("DC", ChallengerStatus.ACTIVE);
        contract.addAtbChallenger(pir);
        contract.addAtbChallenger(dc);

        assertEquals(List.of(dc), contract.getActiveOpForChallengers());
    }

    @Test
    void getOpForRosterNullWhenNoActiveChallengers() {
        AtBContract contract = new AtBContract();
        contract.addAtbChallenger(challenger("PIR", ChallengerStatus.DEFEATED));

        assertEquals(null, contract.getOpForRoster());
    }

    @Test
    void retireActiveChallengersMarksEmptyDefeatedAndMannedWithdrawn() {
        AtBContract contract = new AtBContract();
        StratConOpForRoster empty = challenger("PIR", ChallengerStatus.ACTIVE); // no units → eliminated
        StratConOpForRoster manned = challenger("DC", ChallengerStatus.ACTIVE);
        StratConOpForUnit survivor = new StratConOpForUnit();
        survivor.setStatus(Status.READY);
        manned.addUnit(survivor);
        contract.addAtbChallenger(empty);
        contract.addAtbChallenger(manned);

        contract.retireActiveChallengers(LocalDate.of(3151, 6, 1));

        assertEquals(ChallengerStatus.DEFEATED, empty.getStatus(), "no living line units → DEFEATED");
        assertEquals(ChallengerStatus.WITHDRAWN, manned.getStatus(), "survivors remain → WITHDRAWN");
        assertEquals(LocalDate.of(3151, 6, 1), empty.getEndedDate());
    }

    @Test
    void maybeSpawnChallengerNoOpWhenStaticOpForOff() {
        AtBContract contract = new AtBContract();
        StratConOpForRoster pir = challenger("PIR", ChallengerStatus.ACTIVE);
        contract.addAtbChallenger(pir);
        Campaign campaign = mock(Campaign.class);
        CampaignOptions options = mock(CampaignOptions.class);
        when(options.isUseStaticOpForRoster()).thenReturn(false);
        when(campaign.getCampaignOptions()).thenReturn(options);

        contract.maybeSpawnChallenger(campaign, LocalDate.of(3151, 6, 1));

        assertEquals(1, contract.getAtbOpForChallengers().size(), "static OpFor off → no challenger spawned");
        assertEquals(ChallengerStatus.ACTIVE, pir.getStatus(), "existing challenger untouched");
    }
}
