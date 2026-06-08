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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import megamek.client.bot.princess.BehaviorSettings;
import megamek.client.bot.princess.CardinalEdge;

import org.junit.jupiter.api.Test;

class AllyBehaviorSettingsBuilderTest {

    @Test
    void buildSettings_exactValues() throws Exception {
        BehaviorSettings s = AllyBehaviorSettingsBuilder.buildSettings();

        assertNotNull(s);
        // Engaging-support profile
        assertEquals(6, s.getHyperAggressionIndex(), "Ally aggression");
        assertEquals(5, s.getBraveryIndex(), "Ally bravery");
        assertEquals(5, s.getSelfPreservationIndex(), "Ally selfPres");
        assertEquals(7, s.getHerdMentalityIndex(), "Ally herd");
        assertEquals(5, s.getFallShameIndex(), "Ally fallShame");
        assertTrue(s.isForcedWithdrawal(),
                "Ally forced withdrawal must be enabled");
        assertEquals(CardinalEdge.NEAREST, s.getRetreatEdge());
        assertEquals("STATIC_ALLY_SUPPORT", s.getDescription());
    }

    @Test
    void buildSettings_pushesAggressionAboveNeutral() throws Exception {
        // Princess default aggression is 5. Allies should engage actively.
        BehaviorSettings s = AllyBehaviorSettingsBuilder.buildSettings();
        assertTrue(s.getHyperAggressionIndex() > 5,
                "Ally aggression must exceed neutral Princess default of 5");
    }

    @Test
    void buildSettings_keepsAlliesGrouped() throws Exception {
        // Herd mentality above 5 means allies stick together for mutual support.
        BehaviorSettings s = AllyBehaviorSettingsBuilder.buildSettings();
        assertTrue(s.getHerdMentalityIndex() > 5,
                "Ally herd mentality must exceed neutral Princess default of 5");
    }
}
