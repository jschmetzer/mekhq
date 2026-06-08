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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mekhq.campaign.universe.Faction;

/**
 * Tests for {@link FormationNamer}.
 */
class FormationNamerTest {

    // -------------------------------------------------------------------------
    // Sequential, distinct names
    // -------------------------------------------------------------------------

    @Test
    void sequentialCalls_produceDistinctNames() {
        Faction faction = mock(Faction.class);
        when(faction.isClan()).thenReturn(false);
        when(faction.isComStarOrWoB()).thenReturn(false);

        FormationNamer namer = new FormationNamer(faction);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 26; i++) {
            names.add(namer.nextFormationName());
        }

        assertEquals(26, names.size(), "All 26 NATO-alphabet names should be distinct");
    }

    @Test
    void firstThreeNames_matchExpectedLanceSuffix() {
        Faction faction = mock(Faction.class);
        when(faction.isClan()).thenReturn(false);
        when(faction.isComStarOrWoB()).thenReturn(false);

        FormationNamer namer = new FormationNamer(faction);
        assertEquals("Alpha Lance", namer.nextFormationName());
        assertEquals("Bravo Lance", namer.nextFormationName());
        assertEquals("Charlie Lance", namer.nextFormationName());
    }

    // -------------------------------------------------------------------------
    // Faction-specific suffixes
    // -------------------------------------------------------------------------

    @Test
    void clanFaction_useStarSuffix() {
        Faction faction = mock(Faction.class);
        when(faction.isClan()).thenReturn(true);
        when(faction.isComStarOrWoB()).thenReturn(false);

        FormationNamer namer = new FormationNamer(faction);
        assertEquals("Alpha Star", namer.nextFormationName());
        assertEquals("Bravo Star", namer.nextFormationName());
    }

    @Test
    void comStarFaction_useLevelIISuffix() {
        Faction faction = mock(Faction.class);
        when(faction.isClan()).thenReturn(false);
        when(faction.isComStarOrWoB()).thenReturn(true);

        FormationNamer namer = new FormationNamer(faction);
        assertEquals("Alpha Level II", namer.nextFormationName());
        assertEquals("Bravo Level II", namer.nextFormationName());
    }

    // -------------------------------------------------------------------------
    // Beyond-alphabet fallback
    // -------------------------------------------------------------------------

    @Test
    void beyondZulu_usesNumericOrdinal() {
        Faction faction = mock(Faction.class);
        when(faction.isClan()).thenReturn(false);
        when(faction.isComStarOrWoB()).thenReturn(false);

        FormationNamer namer = new FormationNamer(faction);
        // consume all 26 NATO names
        for (int i = 0; i < 26; i++) {
            namer.nextFormationName();
        }
        String name27 = namer.nextFormationName();
        // should be "27 Lance"
        assertEquals("27 Lance", name27);
        assertNotEquals("Zulu Lance", name27);
    }

    @Test
    void nullFaction_fallsBackToLanceSuffix() {
        FormationNamer namer = new FormationNamer((Faction) null);
        assertEquals("Alpha Lance", namer.nextFormationName());
        assertEquals("Bravo Lance", namer.nextFormationName());
        assertEquals("Charlie Lance", namer.nextFormationName());
    }
}
