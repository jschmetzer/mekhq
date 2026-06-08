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

import mekhq.campaign.stratCon.StratConFacility.FacilityType;

class FacilityRosterEffectTest {

    @Test
    void onPlayerCapture_commandCenter_largeShrinkPlusAlly() {
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerCapture(FacilityType.CommandCenter);
        assertEquals(-3, e.enemyDelta());
        assertEquals(+1, e.allyDelta());
        assertTrue(e.isAnyChange());
    }

    @Test
    void onPlayerCapture_orbitalDefense_noEffect() {
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerCapture(FacilityType.OrbitalDefense);
        assertFalse(e.isAnyChange(), "OrbitalDefense should have no ground-roster effect");
    }

    @Test
    void onPlayerLoss_commandCenter_largeAllyShrinkPlusEnemyBoost() {
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerLoss(FacilityType.CommandCenter);
        assertEquals(+2, e.enemyDelta());
        assertEquals(-3, e.allyDelta());
        assertTrue(e.isAnyChange());
    }

    @Test
    void onPlayerLoss_mekBase_enemyBoost() {
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerLoss(FacilityType.MekBase);
        assertEquals(+2, e.enemyDelta(),
                "Lost MekBase should produce captured Meks redeployed against player");
        assertEquals(0, e.allyDelta());
    }

    @Test
    void onPlayerCapture_nullType_noEffect() {
        assertFalse(FacilityRosterEffect.onPlayerCapture(null).isAnyChange());
    }

    @Test
    void onPlayerLoss_nullType_noEffect() {
        assertFalse(FacilityRosterEffect.onPlayerLoss(null).isAnyChange());
    }

    @Test
    void allFacilityTypes_returnNonNullEffect() {
        for (FacilityType type : FacilityType.values()) {
            assertNotNull(FacilityRosterEffect.onPlayerCapture(type),
                    "PlayerCapture must return non-null for " + type);
            assertNotNull(FacilityRosterEffect.onPlayerLoss(type),
                    "PlayerLoss must return non-null for " + type);
        }
    }
}
