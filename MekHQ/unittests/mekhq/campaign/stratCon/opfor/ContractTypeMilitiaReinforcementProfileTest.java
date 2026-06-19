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

class ContractTypeMilitiaReinforcementProfileTest {

    @Test
    void getProfile_planetaryAssault_isAssault() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.PLANETARY_ASSAULT);
        assertEquals(AtBMoraleLevel.ADVANCING, p.triggerThreshold());
        assertEquals(5, p.eventCap());
        assertEquals(4, p.maxStarting());
        assertTrue(p.isReinforcementAllowed());
        assertTrue(p.hasStartingPool());
    }

    @Test
    void getProfile_cadreDuty_isNever() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.CADRE_DUTY);
        assertFalse(p.isReinforcementAllowed());
        assertFalse(p.hasStartingPool());
    }

    @Test
    void getProfile_objectiveRaid_isRaid() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.OBJECTIVE_RAID);
        assertEquals(3, p.eventCap());
        assertEquals(2, p.maxStarting());
    }

    @Test
    void getProfile_diversinaryRaid_isRaid() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.DIVERSIONARY_RAID);
        assertEquals(3, p.eventCap());
        assertEquals(2, p.maxStarting());
        assertTrue(p.isReinforcementAllowed());
    }

    @Test
    void getProfile_reconRaid_isRaid() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.RECON_RAID);
        assertEquals(3, p.eventCap());
        assertEquals(2, p.maxStarting());
    }

    @Test
    void getProfile_extractionRaid_isRaid() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.EXTRACTION_RAID);
        assertEquals(3, p.eventCap());
        assertEquals(2, p.maxStarting());
    }

    @Test
    void getProfile_observationRaid_isRaid() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.OBSERVATION_RAID);
        assertEquals(3, p.eventCap());
        assertEquals(2, p.maxStarting());
    }

    @Test
    void getProfile_pirateHunting_isIrregular() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.PIRATE_HUNTING);
        assertEquals(2, p.eventCap());
        assertEquals(1, p.maxStarting());
        assertEquals(0, p.minStarting());
        assertTrue(p.isReinforcementAllowed());
    }

    @Test
    void getProfile_guerrillaWarfare_isIrregular() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.GUERRILLA_WARFARE);
        assertEquals(2, p.eventCap());
        assertEquals(1, p.maxStarting());
    }

    @Test
    void getProfile_moleHunting_isIrregular() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.MOLE_HUNTING);
        assertEquals(2, p.eventCap());
        assertEquals(1, p.maxStarting());
    }

    @Test
    void getProfile_null_isNever() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(null);
        assertFalse(p.isReinforcementAllowed());
        assertFalse(p.hasStartingPool());
    }

    @Test
    void allContractTypes_returnNonNullProfile() {
        for (AtBContractType type : AtBContractType.values()) {
            assertNotNull(ContractTypeMilitiaReinforcementProfile.getProfile(type),
                    "Every contract type must return a non-null profile: " + type);
        }
    }

    @Test
    void getProfile_planetaryAssault_correctAllFields() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.PLANETARY_ASSAULT);
        assertEquals(AtBMoraleLevel.ADVANCING, p.triggerThreshold());
        assertEquals(0.55, p.probability(), 1e-9);
        assertEquals(1, p.minFormations());
        assertEquals(2, p.maxFormations());
        assertEquals(5, p.eventCap());
        assertEquals(2, p.minStarting());
        assertEquals(4, p.maxStarting());
    }

    @Test
    void getProfile_objectiveRaid_correctAllFields() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.OBJECTIVE_RAID);
        assertEquals(AtBMoraleLevel.ADVANCING, p.triggerThreshold());
        assertEquals(0.40, p.probability(), 1e-9);
        assertEquals(1, p.minFormations());
        assertEquals(1, p.maxFormations());
        assertEquals(3, p.eventCap());
        assertEquals(1, p.minStarting());
        assertEquals(2, p.maxStarting());
    }

    @Test
    void getProfile_pirateHunting_correctAllFields() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.PIRATE_HUNTING);
        assertEquals(AtBMoraleLevel.ADVANCING, p.triggerThreshold());
        assertEquals(0.30, p.probability(), 1e-9);
        assertEquals(1, p.minFormations());
        assertEquals(1, p.maxFormations());
        assertEquals(2, p.eventCap());
        assertEquals(0, p.minStarting());
        assertEquals(1, p.maxStarting());
    }

    @Test
    void irregularProfile_hasStartingPool_maxOne() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.PIRATE_HUNTING);
        // maxStarting == 1 means hasStartingPool() is true
        assertTrue(p.hasStartingPool());
    }

    @Test
    void neverProfile_probability_isZero() {
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.CADRE_DUTY);
        assertEquals(0.0, p.probability(), 1e-9);
        assertEquals(0, p.eventCap());
        assertEquals(0, p.maxFormations());
        assertEquals(0, p.maxStarting());
    }

    @Test
    void garrisonDuty_isNever() {
        // Garrison duty is not in the militia map — should return NEVER
        var p = ContractTypeMilitiaReinforcementProfile.getProfile(AtBContractType.GARRISON_DUTY);
        assertFalse(p.isReinforcementAllowed(),
                "Garrison Duty is a defender contract; militia do not apply");
        assertFalse(p.hasStartingPool());
    }
}
