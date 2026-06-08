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

import org.junit.jupiter.api.Test;

import mekhq.campaign.universe.Faction;

/**
 * Tests for {@link FormationSchema}.
 *
 * <p>These tests verify that {@code FormationSchema.formationSize} returns the
 * correct standard formation size for each faction category by mocking the
 * {@link Faction} object.</p>
 */
class FormationSchemaTest {

    /**
     * IS/Periphery factions should yield a base formation size of 4
     * (1 lance = 4 units).
     */
    @Test
    void formationSize_innerSphereFaction_returnsFour() {
        Faction faction = mock(Faction.class);
        when(faction.isComStar()).thenReturn(false);
        when(faction.getFormationBaseSize()).thenReturn(4);

        assertEquals(4, FormationSchema.formationSize(faction));
    }

    /**
     * Clan factions should yield a base formation size of 5
     * (1 star = 5 units).
     */
    @Test
    void formationSize_clanFaction_returnsFive() {
        Faction faction = mock(Faction.class);
        when(faction.isComStar()).thenReturn(false);
        when(faction.getFormationBaseSize()).thenReturn(5);

        assertEquals(5, FormationSchema.formationSize(faction));
    }

    /**
     * ComStar factions should yield a base formation size of 6
     * (1 Level II = 6 units), driven by {@code isComStar()} == true.
     */
    @Test
    void formationSize_comStarFaction_returnsSix() {
        Faction faction = mock(Faction.class);
        when(faction.isComStar()).thenReturn(true);
        when(faction.getFormationBaseSize()).thenReturn(6);

        assertEquals(6, FormationSchema.formationSize(faction));
    }

    /**
     * Any faction with a non-standard base size should have that size returned.
     */
    @Test
    void formationSize_customBaseSize_returnsCustomSize() {
        Faction faction = mock(Faction.class);
        when(faction.isComStar()).thenReturn(false);
        when(faction.getFormationBaseSize()).thenReturn(3);

        assertEquals(3, FormationSchema.formationSize(faction));
    }
}
