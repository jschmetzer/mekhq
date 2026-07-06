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

import megamek.common.units.Entity;

/**
 * Tests for {@link UnitTemplate#getFullName()}, the MekSummaryCache lookup name. A trailing space here (from a blank
 * model) misses the cache, so a model-less unit fails to materialise and the whole static OpFor force falls back to
 * dynamic generation.
 */
class UnitTemplateTest {

    @Test
    void fullName_withModel_joinsChassisAndModel() {
        assertEquals("Griffin GRF-1N", new UnitTemplate("Griffin", "GRF-1N", "FS").getFullName());
    }

    @Test
    void fullName_withBlankModel_isChassisOnly_noTrailingSpace() {
        // Model-less units (e.g. 'Ryoken II', 'Griffin IIC') must not produce a trailing space, which misses the cache.
        assertEquals("Ryoken II", new UnitTemplate("Ryoken II", "", "CW").getFullName());
    }

    @Test
    void fullName_withNullModel_isChassisOnly() {
        assertEquals("Stalking Spider II", new UnitTemplate("Stalking Spider II", null, "CW").getFullName());
    }

    @Test
    void fullName_withWhitespaceModel_isChassisOnly() {
        assertEquals("Griffin IIC", new UnitTemplate("Griffin IIC", "   ", "CW").getFullName());
    }

    // -------------------------------------------------------------------------
    // fromEntity — the built template's getFullName() MUST equal the entity's
    // getShortNameRaw(), which is exactly how MekSummaryCache keys the unit.
    // Clan units carry a reporting name between chassis and model (e.g.
    // "Koshi (Mist Lynx) A"); keying off the bare chassis ("Koshi A") misses the
    // cache and the whole static OpFor force falls back to dynamic generation.
    // -------------------------------------------------------------------------

    @Test
    void fromEntity_clanUnit_keyMatchesShortNameRaw() {
        Entity entity = mock(Entity.class);
        when(entity.getFullChassis()).thenReturn("Koshi (Mist Lynx)");
        when(entity.getModel()).thenReturn("A");
        when(entity.getShortNameRaw()).thenReturn("Koshi (Mist Lynx) A");

        UnitTemplate template = UnitTemplate.fromEntity(entity, "CW");

        assertEquals("Koshi (Mist Lynx) A", template.getFullName());
        assertEquals(entity.getShortNameRaw(), template.getFullName());
        assertEquals("CW", template.getFactionCode());
    }

    @Test
    void fromEntity_innerSphereUnit_keyMatchesShortNameRaw() {
        Entity entity = mock(Entity.class);
        when(entity.getFullChassis()).thenReturn("Griffin");
        when(entity.getModel()).thenReturn("GRF-1N");
        when(entity.getShortNameRaw()).thenReturn("Griffin GRF-1N");

        UnitTemplate template = UnitTemplate.fromEntity(entity, "FS");

        assertEquals("Griffin GRF-1N", template.getFullName());
        assertEquals(entity.getShortNameRaw(), template.getFullName());
    }

    @Test
    void fromEntity_modelLessUnit_keyMatchesShortNameRaw() {
        // A Clan OmniMek with no model still has a reporting name and no trailing space.
        Entity entity = mock(Entity.class);
        when(entity.getFullChassis()).thenReturn("Ryoken II");
        when(entity.getModel()).thenReturn("");
        when(entity.getShortNameRaw()).thenReturn("Ryoken II");

        UnitTemplate template = UnitTemplate.fromEntity(entity, "CW");

        assertEquals("Ryoken II", template.getFullName());
        assertEquals(entity.getShortNameRaw(), template.getFullName());
    }
}
