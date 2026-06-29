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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.enums.AtBContractType;
import org.junit.jupiter.api.Test;

class ChallengerDefeatTest {
    @Test
    void garrisonContractDoesNotWinByEliminatingOneChallenger() {
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.GARRISON_DUTY);
        Campaign campaign = mock(Campaign.class); // getIntelLog()/getLocalDate() return null → milestone skipped
        StratConOpForRoster roster = new StratConOpForRoster(); // empty → no living line units
        roster.setStatus(ChallengerStatus.ACTIVE);

        EliminationResult result = roster.checkEliminationStatus(campaign, contract, null);

        assertEquals(EliminationResult.STILL_ACTIVE, result,
                "garrison contracts defend the term; clearing one challenger must not win");
        assertEquals(ChallengerStatus.DEFEATED, roster.getStatus(),
                "the cleared challenger is marked DEFEATED");
    }

    @Test
    void nonGarrisonContractStillWinsByElimination() {
        AtBContract contract = mock(AtBContract.class);
        when(contract.getContractType()).thenReturn(AtBContractType.PLANETARY_ASSAULT);
        Campaign campaign = mock(Campaign.class);
        StratConOpForRoster roster = new StratConOpForRoster();

        assertEquals(EliminationResult.CONTRACT_WON,
                roster.checkEliminationStatus(campaign, contract, null));
    }
}
