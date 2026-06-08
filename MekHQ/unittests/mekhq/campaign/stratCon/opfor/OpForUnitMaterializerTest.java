/*
 * Copyright (C) 2019-2026 The MegaMek Team. All Rights Reserved.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

import megamek.common.Player;
import megamek.common.enums.Gender;
import megamek.common.game.Game;
import megamek.common.loaders.MekFileParser;
import megamek.common.loaders.MekSummary;
import megamek.common.loaders.MekSummaryCache;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.common.units.Entity;
import megamek.common.units.Mek;
import mekhq.campaign.Campaign;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class OpForUnitMaterializerTest {

    /**
     * Verifies that deploy() returns null (with a warn log, not an exception)
     * when MekSummaryCache cannot locate the requested chassis+model.
     */
    @Test
    void deploy_returnsNullWhenCacheMisses() {
        Campaign campaign = mock(Campaign.class);

        try (MockedStatic<MekSummaryCache> cacheMock = mockStatic(MekSummaryCache.class)) {
            MekSummaryCache cacheInstance = mock(MekSummaryCache.class);
            cacheMock.when(MekSummaryCache::getInstance).thenReturn(cacheInstance);
            when(cacheInstance.getMek(anyString())).thenReturn(null);

            StratConOpForUnit unit = buildUnit("Atlas", "AS7-D", 3, 4);
            Entity result = OpForUnitMaterializer.deploy(unit, campaign);
            assertNull(result, "Expected null when cache misses");
        }
    }

    /**
     * Verifies that deploy() correctly wires the pilot's persistent UUID onto
     * the Crew's external ID (slot 0) and the unit's UUID onto the Entity's
     * external ID string.  These links are required for Phase 6 captured-pilot
     * reconciliation.
     *
     * <p>The test mocks {@code MekSummaryCache} and {@code MekFileParser} so it
     * does not require real unit data files to be present.  The entity returned by
     * the mocked parser is itself a mock so that no real Mek data-loading occurs.</p>
     */
    @Test
    void deploy_setsExternalIdAndPilotId() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID pilotId = UUID.randomUUID();

        // Real crew object so getExternalIdAsString(0) works after the materializer sets it
        Crew fakeCrew = new Crew(CrewType.SINGLE, "OldPilot", 1, 4, 5,
                Gender.RANDOMIZE, false, new HashMap<>());

        // Mock entity — avoids triggering BipedMek/EquipmentType static init
        Entity fakeEntity = mock(Entity.class);
        Crew existingCrew = mock(Crew.class);
        when(existingCrew.getCrewType()).thenReturn(CrewType.SINGLE);
        when(fakeEntity.getCrew()).thenReturn(existingCrew, existingCrew, fakeCrew);
        when(fakeEntity.locations()).thenReturn(0);
        when(fakeEntity.getNumberOfCriticalSlots(anyInt())).thenReturn(0);
        // Capture the external ID string set by the materializer
        ArgumentCaptor<String> entityIdCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.doNothing().when(fakeEntity).setExternalIdAsString(entityIdCaptor.capture());

        // Capture the crew passed to setCrew
        ArgumentCaptor<Crew> crewCaptor = ArgumentCaptor.forClass(Crew.class);
        Mockito.doNothing().when(fakeEntity).setCrew(crewCaptor.capture());

        MekSummary mockSummary = mock(MekSummary.class);
        when(mockSummary.getSourceFile()).thenReturn(new File("fake.mtf"));
        when(mockSummary.getEntryName()).thenReturn(null);

        Campaign campaign = mock(Campaign.class);
        when(campaign.getPlayer()).thenReturn(mock(Player.class));
        when(campaign.getGame()).thenReturn(mock(Game.class));

        try (MockedStatic<MekSummaryCache> cacheMock = mockStatic(MekSummaryCache.class);
             MockedConstruction<MekFileParser> parserMock = Mockito.mockConstruction(
                     MekFileParser.class,
                     (parser, ctx) -> when(parser.getEntity()).thenReturn(fakeEntity))) {

            MekSummaryCache cacheInstance = mock(MekSummaryCache.class);
            cacheMock.when(MekSummaryCache::getInstance).thenReturn(cacheInstance);
            when(cacheInstance.getMek(anyString())).thenReturn(mockSummary);

            StratConOpForUnit unit = buildUnit("Atlas", "AS7-D", 3, 4);
            unit.setId(unitId);
            unit.setPilotPersistentId(pilotId);

            Entity result = OpForUnitMaterializer.deploy(unit, campaign);

            assertNotNull(result, "Expected non-null entity for mocked cache hit");

            // Verify the entity's external ID was set to the unit's UUID
            assertEquals(unitId.toString(), entityIdCaptor.getValue(),
                    "Entity external ID must equal the unit's UUID");

            // Verify the crew that was set on the entity has the pilot UUID on slot 0
            Crew capturedCrew = crewCaptor.getValue();
            assertNotNull(capturedCrew, "A crew must have been set on the entity");
            assertEquals(pilotId.toString(), capturedCrew.getExternalIdAsString(0),
                    "Crew external ID (slot 0) must equal pilotPersistentId");
        }
    }

    /**
     * Verifies that applyPersistentDamage applies a reduced-internals record to
     * the correct location on a mock entity.
     *
     * <p>We use a Mockito spy rather than a real BipedMek because constructing a
     * fully initialised Mek requires game-data loading that is not available in a
     * unit-test environment.  The spy lets us assert the exact call was made.</p>
     */
    @Test
    void applyPersistentDamage_reducesInternalsOnLocation() {
        int loc = Mek.LOC_CENTER_TORSO;
        int baselineInternal = 20;
        int reduction = 8;

        // Use a mock entity so we don't need full initialisation
        Entity entity = mock(Entity.class);
        when(entity.locations()).thenReturn(loc + 1); // enough locations
        when(entity.getInternal(loc)).thenReturn(baselineInternal);
        when(entity.getNumberOfCriticalSlots(org.mockito.ArgumentMatchers.anyInt())).thenReturn(0);

        PersistentDamageState damage = new PersistentDamageState();
        damage.setReducedInternals(loc, reduction);

        OpForUnitMaterializer.applyPersistentDamage(entity, damage);

        // Verify setInternal was called with the expected reduced value
        Mockito.verify(entity).setInternal(baselineInternal - reduction, loc);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StratConOpForUnit buildUnit(String chassis, String model,
            int gunnery, int piloting) {
        StratConOpForUnit u = new StratConOpForUnit();
        u.setId(UUID.randomUUID());
        u.setPilotPersistentId(UUID.randomUUID());
        u.setPilotName("Test Pilot");
        u.setGunnery(gunnery);
        u.setPiloting(piloting);
        UnitTemplate template = new UnitTemplate();
        template.setChassis(chassis);
        template.setModel(model);
        template.setFactionCode("IND");
        u.setProtoEntity(template);
        u.setPersistentDamage(new PersistentDamageState());
        return u;
    }

}

