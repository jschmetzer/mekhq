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
    void onPlayerCapture_commandCenter_singleShrinkPlusAlly() {
        // Rebalanced to -1 per-capture (was -3). See FacilityRosterEffect.onPlayerCapture
        // for rationale — single GM-cheat captures shouldn't wipe small contracts.
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerCapture(FacilityType.CommandCenter);
        assertEquals(-1, e.enemyDelta());
        assertEquals(+1, e.allyDelta());
        assertTrue(e.isAnyChange());
    }

    @Test
    void onPlayerCapture_orbitalDefense_noEffect() {
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerCapture(FacilityType.OrbitalDefense);
        assertFalse(e.isAnyChange(), "OrbitalDefense should have no ground-roster effect");
    }

    @Test
    void onPlayerLoss_commandCenter_boundedInverseOfCapture() {
        // Loss is the exact inverse of capture (-1,+1) -> (+1,-1): enemy grows and
        // ally shrinks, but bounded to magnitude 1 so an oscillating facility can't
        // drift the rosters without bound.
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerLoss(FacilityType.CommandCenter);
        assertEquals(+1, e.enemyDelta());
        assertEquals(-1, e.allyDelta());
        assertTrue(e.isAnyChange());
    }

    @Test
    void onPlayerLoss_mekBase_enemyBoost() {
        FacilityRosterEffect.Effect e = FacilityRosterEffect.onPlayerLoss(FacilityType.MekBase);
        assertEquals(+1, e.enemyDelta(),
                "Lost MekBase should produce captured Meks redeployed against player");
        assertEquals(0, e.allyDelta());
    }

    @Test
    void onPlayerLoss_isExactInverseOfCapture_flipCycleNetsZero() {
        // The core guard against unbounded roster drift on a contested (oscillating)
        // facility: for every type, loss must negate capture so a capture+loss cycle
        // sums to zero on both rosters.
        for (FacilityType type : FacilityType.values()) {
            FacilityRosterEffect.Effect capture = FacilityRosterEffect.onPlayerCapture(type);
            FacilityRosterEffect.Effect loss = FacilityRosterEffect.onPlayerLoss(type);
            assertEquals(0, capture.enemyDelta() + loss.enemyDelta(),
                    "capture+loss enemy delta must net zero for " + type);
            assertEquals(0, capture.allyDelta() + loss.allyDelta(),
                    "capture+loss ally delta must net zero for " + type);
        }
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
