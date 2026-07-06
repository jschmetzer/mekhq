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
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekhq.campaign.mission.enums.AtBContractType;

import org.junit.jupiter.api.Test;

class ContractTypeBreakProfileTest {

    @Test
    void planetaryAssault_holdsFast() {
        assertEquals(ContractTypeBreakProfile.HOLD_FAST,
                ContractTypeBreakProfile.getBreakThreshold(AtBContractType.PLANETARY_ASSAULT),
                "A planetary defender should hold nearly to annihilation");
    }

    @Test
    void pirateHunting_isBrittle() {
        assertEquals(ContractTypeBreakProfile.BRITTLE,
                ContractTypeBreakProfile.getBreakThreshold(AtBContractType.PIRATE_HUNTING),
                "Raiders/pirates should break early");
    }

    @Test
    void garrisonDuty_isStandard() {
        assertEquals(ContractTypeBreakProfile.STANDARD,
                ContractTypeBreakProfile.getBreakThreshold(AtBContractType.GARRISON_DUTY));
    }

    @Test
    void nullType_defaultsToStandard() {
        assertEquals(ContractTypeBreakProfile.STANDARD,
                ContractTypeBreakProfile.getBreakThreshold(null));
    }

    @Test
    void everyContractType_returnsFractionInOpenUnitInterval() {
        for (AtBContractType type : AtBContractType.values()) {
            double threshold = ContractTypeBreakProfile.getBreakThreshold(type);
            assertTrue((threshold > 0.0) && (threshold < 1.0),
                    "Break threshold must be a fraction in (0,1) for " + type + "; got " + threshold);
        }
    }
}
