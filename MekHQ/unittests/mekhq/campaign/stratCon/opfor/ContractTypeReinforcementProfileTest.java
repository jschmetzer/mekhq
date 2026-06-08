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

import org.junit.jupiter.api.Test;

import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;

class ContractTypeReinforcementProfileTest {

    @Test
    void getProfile_planetaryAssault_isHeavyDefender() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeReinforcementProfile.getProfile(AtBContractType.PLANETARY_ASSAULT);
        assertEquals(AtBMoraleLevel.WEAKENED, p.triggerThreshold());
        assertEquals(0.60, p.probability(), 1e-9);
        assertEquals(2, p.minFormations());
        assertEquals(4, p.maxFormations());
        assertEquals(6, p.eventCap());
        assertTrue(p.isReinforcementAllowed());
    }

    @Test
    void getProfile_pirateHunt_isIrregular() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeReinforcementProfile.getProfile(AtBContractType.PIRATE_HUNTING);
        assertEquals(AtBMoraleLevel.CRITICAL, p.triggerThreshold());
        assertEquals(0.30, p.probability(), 1e-9);
        assertEquals(1, p.minFormations());
        assertEquals(1, p.maxFormations());
        assertEquals(2, p.eventCap());
        assertTrue(p.isReinforcementAllowed());
    }

    @Test
    void getProfile_cadreDuty_isNever() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeReinforcementProfile.getProfile(AtBContractType.CADRE_DUTY);
        assertFalse(p.isReinforcementAllowed(),
                "Cadre Duty should not permit reinforcements");
    }

    @Test
    void getProfile_assassination_isNever() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeReinforcementProfile.getProfile(AtBContractType.ASSASSINATION);
        assertFalse(p.isReinforcementAllowed(),
                "Assassination should not permit reinforcements");
    }

    @Test
    void getProfile_nullType_isNever() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeReinforcementProfile.getProfile(null);
        assertFalse(p.isReinforcementAllowed(),
                "Null type should default to NEVER");
    }

    @Test
    void getProfile_garrisonDuty_andReliefDuty_shareProfile() {
        ContractTypeReinforcementProfile.Profile garrison =
                ContractTypeReinforcementProfile.getProfile(AtBContractType.GARRISON_DUTY);
        ContractTypeReinforcementProfile.Profile relief =
                ContractTypeReinforcementProfile.getProfile(AtBContractType.RELIEF_DUTY);
        assertEquals(garrison, relief, "Garrison and Relief Duty should share the same profile");
    }

    @Test
    void allContractTypes_returnNonNullProfile() {
        for (AtBContractType type : AtBContractType.values()) {
            assertNotNull(ContractTypeReinforcementProfile.getProfile(type),
                    "Every contract type must return a non-null profile: " + type);
        }
    }
}
