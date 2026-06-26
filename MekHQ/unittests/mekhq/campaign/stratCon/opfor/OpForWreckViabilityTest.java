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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import megamek.common.interfaces.IEntityRemovalConditions;
import megamek.common.units.Entity;
import megamek.common.units.Mek;
import megamek.common.units.UnitType;

import org.junit.jupiter.api.Test;

/**
 * Tests for the "too damaged to redeploy" predicates used to keep catastrophically
 * damaged static-OpFor units from being mis-classified as survivors:
 * {@link OpForUnitMaterializer#isNonViable(Entity)} (live post-battle entity) and
 * {@link StratConOpForUnit#isUnredeployableWreck()} (persisted save state).
 */
class OpForWreckViabilityTest {

    // ---- OpForUnitMaterializer.isNonViable(Entity) ----

    @Test
    void isNonViable_nullEntity_false() {
        assertFalse(OpForUnitMaterializer.isNonViable(null));
    }

    @Test
    void isNonViable_destroyed_true() {
        Entity e = mock(Entity.class);
        when(e.isDestroyed()).thenReturn(true);
        assertTrue(OpForUnitMaterializer.isNonViable(e));
    }

    @Test
    void isNonViable_doomed_true() {
        Entity e = mock(Entity.class);
        when(e.isDoomed()).thenReturn(true);
        assertTrue(OpForUnitMaterializer.isNonViable(e));
    }

    @Test
    void isNonViable_removalDevastated_true() {
        Entity e = mock(Entity.class);
        when(e.getRemovalCondition()).thenReturn(IEntityRemovalConditions.REMOVE_DEVASTATED);
        assertTrue(OpForUnitMaterializer.isNonViable(e));
    }

    @Test
    void isNonViable_mekLostLeg_true() {
        Mek m = mock(Mek.class);
        when(m.isLocationBad(Mek.LOC_LEFT_LEG)).thenReturn(true);
        assertTrue(OpForUnitMaterializer.isNonViable(m));
    }

    @Test
    void isNonViable_mekCenterTorsoDestroyed_true() {
        Mek m = mock(Mek.class);
        when(m.isLocationBad(Mek.LOC_CENTER_TORSO)).thenReturn(true);
        assertTrue(OpForUnitMaterializer.isNonViable(m));
    }

    @Test
    void isNonViable_healthyMek_false() {
        Mek m = mock(Mek.class); // all checks default false / 0
        assertFalse(OpForUnitMaterializer.isNonViable(m));
    }

    @Test
    void isNonViable_healthyNonMek_false() {
        Entity e = mock(Entity.class);
        assertFalse(OpForUnitMaterializer.isNonViable(e));
    }

    // ---- StratConOpForUnit.isUnredeployableWreck() ----

    private StratConOpForUnit mekUnitWith(final PersistentDamageState damage) {
        StratConOpForUnit u = new StratConOpForUnit();
        u.setUnitType(UnitType.MEK);
        u.setPersistentDamage(damage);
        return u;
    }

    @Test
    void isUnredeployableWreck_mekLegBlownOff_true() {
        PersistentDamageState d = new PersistentDamageState();
        d.setLocationBlownOff(Mek.LOC_RIGHT_LEG, true);
        assertTrue(mekUnitWith(d).isUnredeployableWreck());
    }

    @Test
    void isUnredeployableWreck_mekCenterTorsoBlownOff_true() {
        PersistentDamageState d = new PersistentDamageState();
        d.setLocationBlownOff(Mek.LOC_CENTER_TORSO, true);
        assertTrue(mekUnitWith(d).isUnredeployableWreck());
    }

    @Test
    void isUnredeployableWreck_mekLightDamage_false() {
        PersistentDamageState d = new PersistentDamageState();
        d.setReducedInternals(Mek.LOC_RIGHT_ARM, 3); // arm chewed up but not fatal
        assertFalse(mekUnitWith(d).isUnredeployableWreck());
    }

    @Test
    void isUnredeployableWreck_mekNoDamage_false() {
        assertFalse(mekUnitWith(new PersistentDamageState()).isUnredeployableWreck());
    }

    @Test
    void isUnredeployableWreck_nullDamage_false() {
        StratConOpForUnit u = new StratConOpForUnit();
        u.setUnitType(UnitType.MEK);
        u.setPersistentDamage(null);
        assertFalse(u.isUnredeployableWreck());
    }

    @Test
    void isUnredeployableWreck_nonMekWithBlownLocation_false() {
        // Scoped to Meks; other unit types use type-specific damage semantics and
        // did not exhibit the unloadable-wreck bug.
        PersistentDamageState d = new PersistentDamageState();
        d.setLocationBlownOff(1, true);
        StratConOpForUnit u = new StratConOpForUnit();
        u.setUnitType(UnitType.TANK);
        u.setPersistentDamage(d);
        assertFalse(u.isUnredeployableWreck());
    }
}
