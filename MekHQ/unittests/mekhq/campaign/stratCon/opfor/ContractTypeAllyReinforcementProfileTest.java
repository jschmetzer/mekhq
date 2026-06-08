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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;

class ContractTypeAllyReinforcementProfileTest {

    @Test
    void getProfile_garrisonDuty_heavySupport() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeAllyReinforcementProfile.getProfile(AtBContractType.GARRISON_DUTY);
        assertEquals(AtBMoraleLevel.ADVANCING, p.triggerThreshold());
        assertTrue(p.isReinforcementAllowed());
        assertEquals(4, p.eventCap());
    }

    @Test
    void getProfile_planetaryAssault_reluctantSupport() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeAllyReinforcementProfile.getProfile(AtBContractType.PLANETARY_ASSAULT);
        assertEquals(AtBMoraleLevel.DOMINATING, p.triggerThreshold());
        assertTrue(p.isReinforcementAllowed());
        assertEquals(3, p.eventCap());
    }

    @Test
    void getProfile_pirateHunt_never() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeAllyReinforcementProfile.getProfile(AtBContractType.PIRATE_HUNTING);
        assertFalse(p.isReinforcementAllowed(),
                "Pirate Hunt should not get employer reinforcements");
    }

    @Test
    void getProfile_guerrillaWarfare_never() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeAllyReinforcementProfile.getProfile(AtBContractType.GUERRILLA_WARFARE);
        assertFalse(p.isReinforcementAllowed(),
                "Guerrilla Warfare runs alone — no employer reinforcements");
    }

    @Test
    void getProfile_assassination_never() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeAllyReinforcementProfile.getProfile(AtBContractType.ASSASSINATION);
        assertFalse(p.isReinforcementAllowed(),
                "Covert work — no employer reinforcements");
    }

    @Test
    void getProfile_nullType_never() {
        ContractTypeReinforcementProfile.Profile p =
                ContractTypeAllyReinforcementProfile.getProfile(null);
        assertFalse(p.isReinforcementAllowed());
    }

    @Test
    void allContractTypes_returnNonNullProfile() {
        for (AtBContractType type : AtBContractType.values()) {
            assertNotNull(ContractTypeAllyReinforcementProfile.getProfile(type),
                    "Every contract type must return a non-null profile: " + type);
        }
    }
}
