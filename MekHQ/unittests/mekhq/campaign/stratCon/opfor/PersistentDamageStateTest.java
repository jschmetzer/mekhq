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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mekhq.campaign.stratCon.opfor.PersistentDamageState.AeroSystem;
import mekhq.campaign.stratCon.opfor.PersistentDamageState.Condition;
import mekhq.campaign.stratCon.opfor.PersistentDamageState.SystemCritical;

/**
 * Tests for {@link PersistentDamageState#getCondition()} and
 * {@link PersistentDamageState#hasAnyDamage()}, which drive the order-of-battle
 * condition label.
 */
class PersistentDamageStateTest {

    @Test
    void pristine_whenNoDamage() {
        PersistentDamageState state = new PersistentDamageState();
        assertFalse(state.hasAnyDamage());
        assertEquals(Condition.PRISTINE, state.getCondition());
    }

    @Test
    void battleWorn_whenReducedInternalsOnly() {
        PersistentDamageState state = new PersistentDamageState();
        state.setReducedInternals(1, 3);
        assertTrue(state.hasAnyDamage());
        assertEquals(Condition.BATTLE_WORN, state.getCondition());
    }

    @Test
    void battleWorn_whenNonDriveTrainCriticalOnly() {
        PersistentDamageState state = new PersistentDamageState();
        state.setCount(SystemCritical.SENSOR, 1);
        assertEquals(Condition.BATTLE_WORN, state.getCondition(),
                "A sensor hit is real damage but not crippling");
    }

    @Test
    void crippled_whenLocationBlownOff() {
        PersistentDamageState state = new PersistentDamageState();
        state.setLocationBlownOff(2, true);
        assertEquals(Condition.CRIPPLED, state.getCondition());
    }

    @Test
    void crippled_whenEngineCritical() {
        PersistentDamageState state = new PersistentDamageState();
        state.setCount(SystemCritical.ENGINE, 1);
        assertEquals(Condition.CRIPPLED, state.getCondition());
    }

    @Test
    void crippled_whenGyroCritical() {
        PersistentDamageState state = new PersistentDamageState();
        state.setCount(SystemCritical.GYRO, 1);
        assertEquals(Condition.CRIPPLED, state.getCondition());
    }

    @Test
    void crippled_whenAeroStructuralIntegrityHit() {
        PersistentDamageState state = new PersistentDamageState();
        state.setAeroHit(AeroSystem.STRUCTURAL_INTEGRITY, 1);
        assertEquals(Condition.CRIPPLED, state.getCondition());
    }

    @Test
    void clearedBlownOffFlag_isNotCountedAsDamage() {
        // setLocationBlownOff(loc, false) may append an empty location record; an
        // empty record must not read as damage.
        PersistentDamageState state = new PersistentDamageState();
        state.setLocationBlownOff(3, false);
        assertFalse(state.hasAnyDamage());
        assertEquals(Condition.PRISTINE, state.getCondition());
    }
}
