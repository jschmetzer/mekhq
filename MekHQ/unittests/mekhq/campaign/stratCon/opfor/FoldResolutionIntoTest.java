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
 * NOTICE: The MegaMek Organization is a non-profit group of volunteers
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import megamek.common.units.Entity;
import mekhq.campaign.ResolveScenarioTracker.OppositionPersonnelStatus;
import mekhq.campaign.mission.AtBDynamicScenario;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.stratCon.StratConScenario;
import mekhq.campaign.unit.TestUnit;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StratConOpForRoster#foldResolutionInto}.
 *
 * <p>Uses Mockito to construct lightweight scenario/entity/person mocks so
 * no full campaign fixture is needed.</p>
 */
class FoldResolutionIntoTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a roster with a single formation containing one READY unit.
     * The unit's {@code lastDeployedScenarioId} is set to the bridge UUID
     * constructed from {@code scenarioIntId}.
     *
     * @param unitId        the unit UUID
     * @param scenarioIntId the integer scenario ID (bridged to UUID)
     * @return a roster ready for testing
     */
    private StratConOpForRoster buildSingleUnitRoster(
            final UUID unitId,
            final int scenarioIntId) {
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setId(unitId);
        unit.setPilotName("Test Pilot");
        unit.setStatus(Status.READY);
        unit.setLastDeployedScenarioId(new UUID(scenarioIntId, 0L));

        StratConOpForFormation formation = new StratConOpForFormation();
        UUID formationId = UUID.randomUUID();
        formation.setId(formationId);
        formation.setName("Alpha Lance");
        formation.setUnitIds(List.of(unitId));
        unit.setFormationId(formationId);

        StratConOpForRoster roster = new StratConOpForRoster();
        roster.addUnit(unit);
        roster.addFormation(formation);
        return roster;
    }

    /**
     * Creates a mock {@link StratConScenario} whose backing scenario
     * returns {@code scenarioIntId} from {@code getId()}.
     */
    private StratConScenario mockScenario(final int scenarioIntId) {
        AtBDynamicScenario backing = mock(AtBDynamicScenario.class);
        when(backing.getId()).thenReturn(scenarioIntId);

        StratConScenario scenario = mock(StratConScenario.class);
        when(scenario.getBackingScenario()).thenReturn(backing);
        return scenario;
    }

    /**
     * Creates a mock entity whose {@code getExternalIdAsString()} returns
     * {@code unitId.toString()} and whose {@code isDestroyed()} returns
     * {@code destroyed}.
     */
    private Entity mockEntity(final UUID unitId, final boolean destroyed) {
        Entity e = mock(Entity.class);
        when(e.getExternalIdAsString()).thenReturn(unitId.toString());
        when(e.isDestroyed()).thenReturn(destroyed);
        return e;
    }

    /** Builds an entities map containing a single mocked entity. */
    private Map<UUID, Entity> singleEntityMap(final UUID unitId, final Entity entity) {
        Map<UUID, Entity> map = new HashMap<>();
        map.put(unitId, entity);
        return map;
    }

    // -------------------------------------------------------------------------
    // Destruction path
    // -------------------------------------------------------------------------

    /**
     * A unit whose entity is destroyed should transition to DESTROYED and
     * be revealed.
     */
    @Test
    void foldResolutionInto_destroyedEntity_statusBecomesDestroyed() {
        int scenarioId = 42;
        UUID unitId = UUID.randomUUID();
        StratConOpForRoster roster = buildSingleUnitRoster(unitId, scenarioId);

        Entity destroyedEntity = mockEntity(unitId, true);
        Map<UUID, Entity> entities = singleEntityMap(unitId, destroyedEntity);

        List<String> lines = roster.foldResolutionInto(
                mockScenario(scenarioId),
                entities,
                Collections.emptyList(),   // no salvage
                Collections.emptyList(),   // no devastated
                new Hashtable<>(),         // no opposition personnel
                Collections.emptyEnumeration(), // no retreated
                null);

        StratConOpForUnit unit = roster.getUnit(unitId);
        assertEquals(Status.DESTROYED, unit.getStatus());
        assertTrue(unit.isRevealed());
        assertEquals(1, lines.size());
    }

    /**
     * A unit in the devastated list should be marked DESTROYED even if its
     * entity reports {@code isDestroyed() == false}.
     */
    @Test
    void foldResolutionInto_unitInDevastatedList_statusBecomesDestroyed() {
        int scenarioId = 43;
        UUID unitId = UUID.randomUUID();
        StratConOpForRoster roster = buildSingleUnitRoster(unitId, scenarioId);

        Entity entity = mockEntity(unitId, false);
        Map<UUID, Entity> entities = singleEntityMap(unitId, entity);

        TestUnit devastatedUnit = mock(TestUnit.class);
        when(devastatedUnit.getEntity()).thenReturn(entity);

        List<String> lines = roster.foldResolutionInto(
                mockScenario(scenarioId),
                entities,
                Collections.emptyList(),
                List.of(devastatedUnit),
                new Hashtable<>(),
                Collections.emptyEnumeration(),
                null);

        assertEquals(Status.DESTROYED, roster.getUnit(unitId).getStatus());
        assertFalse(lines.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Salvage path
    // -------------------------------------------------------------------------

    /**
     * A unit on the salvage list should transition to SALVAGED.
     */
    @Test
    void foldResolutionInto_unitOnSalvageList_statusBecomesSalvaged() {
        int scenarioId = 44;
        UUID unitId = UUID.randomUUID();
        StratConOpForRoster roster = buildSingleUnitRoster(unitId, scenarioId);

        Entity entity = mockEntity(unitId, false);
        Map<UUID, Entity> entities = singleEntityMap(unitId, entity);

        TestUnit salvageUnit = mock(TestUnit.class);
        when(salvageUnit.getEntity()).thenReturn(entity);

        roster.foldResolutionInto(
                mockScenario(scenarioId),
                entities,
                List.of(salvageUnit),
                Collections.emptyList(),
                new Hashtable<>(),
                Collections.emptyEnumeration(),
                null);

        assertEquals(Status.SALVAGED, roster.getUnit(unitId).getStatus());
    }

    // -------------------------------------------------------------------------
    // Retreat path
    // -------------------------------------------------------------------------

    /**
     * A unit whose entity appears in the retreated enumeration should keep
     * status READY (no change).
     */
    @Test
    void foldResolutionInto_unitRetreated_statusRemainsReady() {
        int scenarioId = 45;
        UUID unitId = UUID.randomUUID();
        StratConOpForRoster roster = buildSingleUnitRoster(unitId, scenarioId);

        Entity entity = mockEntity(unitId, false);
        Map<UUID, Entity> entities = singleEntityMap(unitId, entity);

        Enumeration<Entity> retreated = new Vector<>(List.of(entity)).elements();

        roster.foldResolutionInto(
                mockScenario(scenarioId),
                entities,
                Collections.emptyList(),
                Collections.emptyList(),
                new Hashtable<>(),
                retreated,
                null);

        assertEquals(Status.READY, roster.getUnit(unitId).getStatus());
        assertFalse(roster.getUnit(unitId).isRevealed());
    }

    // -------------------------------------------------------------------------
    // Captured-pilot reconciliation
    // -------------------------------------------------------------------------

    /**
     * A unit should be marked CAPTURED when an opposition person is captured
     * and {@code person.getId()} matches the unit's {@code pilotPersistentId}
     * (the multi-slot-crew propagation path in
     * {@code Utilities.genRandomCrewWithCombinedSkill}).
     */
    @Test
    void foldResolutionInto_capturedPilot_statusBecomesCaptured() {
        int scenarioId = 46;
        UUID unitId = UUID.randomUUID();
        UUID pilotPersistentId = UUID.randomUUID();
        StratConOpForRoster roster = buildSingleUnitRoster(unitId, scenarioId);
        roster.getUnit(unitId).setPilotPersistentId(pilotPersistentId);

        Entity entity = mockEntity(unitId, false);
        Map<UUID, Entity> entities = singleEntityMap(unitId, entity);

        // Build a captured OppositionPersonnelStatus where person.getId() == pilotPersistentId
        Person person = mock(Person.class);
        when(person.getId()).thenReturn(pilotPersistentId);
        when(person.getFullName()).thenReturn("Test Pilot");

        OppositionPersonnelStatus ops = mock(OppositionPersonnelStatus.class);
        when(ops.isCaptured()).thenReturn(true);
        when(ops.getPerson()).thenReturn(person);

        Hashtable<UUID, OppositionPersonnelStatus> oppositionPersonnel = new Hashtable<>();
        oppositionPersonnel.put(pilotPersistentId, ops);

        roster.foldResolutionInto(
                mockScenario(scenarioId),
                entities,
                Collections.emptyList(),
                Collections.emptyList(),
                oppositionPersonnel,
                Collections.emptyEnumeration(),
                null);

        assertEquals(Status.CAPTURED, roster.getUnit(unitId).getStatus());
        assertTrue(roster.getUnit(unitId).isRevealed());
    }

    // -------------------------------------------------------------------------
    // Intel upgrade on ≥ 50 % attrition
    // -------------------------------------------------------------------------

    /**
     * A formation that loses ≥ 50 % of its units to terminal statuses should
     * be upgraded to FULL_INTEL.
     */
    @Test
    void foldResolutionInto_halfFormationDestroyed_intelUpgradesToFullIntel() {
        int scenarioId = 47;
        UUID unitId1 = UUID.randomUUID();
        UUID unitId2 = UUID.randomUUID();
        UUID formationId = UUID.randomUUID();

        StratConOpForUnit unit1 = new StratConOpForUnit();
        unit1.setId(unitId1);
        unit1.setStatus(Status.READY);
        unit1.setLastDeployedScenarioId(new UUID(scenarioId, 0L));
        unit1.setFormationId(formationId);

        StratConOpForUnit unit2 = new StratConOpForUnit();
        unit2.setId(unitId2);
        unit2.setStatus(Status.READY);
        unit2.setLastDeployedScenarioId(new UUID(scenarioId, 0L));
        unit2.setFormationId(formationId);

        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setId(formationId);
        formation.setName("Bravo Lance");
        formation.setUnitIds(List.of(unitId1, unitId2));
        formation.setIntelLevel(IntelLevel.OBSERVED);

        StratConOpForRoster roster = new StratConOpForRoster();
        roster.addUnit(unit1);
        roster.addUnit(unit2);
        roster.addFormation(formation);

        // unit1's entity is destroyed; unit2 survives
        Entity destroyedEntity = mockEntity(unitId1, true);
        Entity survivingEntity = mockEntity(unitId2, false);

        Map<UUID, Entity> entities = new HashMap<>();
        entities.put(unitId1, destroyedEntity);
        entities.put(unitId2, survivingEntity);

        roster.foldResolutionInto(
                mockScenario(scenarioId),
                entities,
                Collections.emptyList(),
                Collections.emptyList(),
                new Hashtable<>(),
                Collections.emptyEnumeration(),
                null);

        // 1 of 2 destroyed = 50 % → upgrade to FULL_INTEL
        assertEquals(IntelLevel.FULL_INTEL, roster.getFormations().get(0).getIntelLevel());
    }

    /**
     * A formation that loses fewer than 50 % of its units should NOT
     * be upgraded to FULL_INTEL.
     */
    @Test
    void foldResolutionInto_oneOfFourDestroyed_intelStaysObserved() {
        int scenarioId = 48;
        UUID fId = UUID.randomUUID();

        StratConOpForRoster roster = new StratConOpForRoster();
        Map<UUID, Entity> entities = new HashMap<>();
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setId(fId);
        formation.setName("Charlie Lance");
        formation.setIntelLevel(IntelLevel.OBSERVED);

        List<UUID> unitIds = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID uid = UUID.randomUUID();
            unitIds.add(uid);
            StratConOpForUnit u = new StratConOpForUnit();
            u.setId(uid);
            u.setFormationId(fId);
            u.setStatus(Status.READY);
            u.setLastDeployedScenarioId(new UUID(scenarioId, 0L));
            roster.addUnit(u);
            boolean isDestroyed = (i == 0); // only first is destroyed
            entities.put(uid, mockEntity(uid, isDestroyed));
        }
        formation.setUnitIds(unitIds);
        roster.addFormation(formation);

        roster.foldResolutionInto(
                mockScenario(scenarioId),
                entities,
                Collections.emptyList(),
                Collections.emptyList(),
                new Hashtable<>(),
                Collections.emptyEnumeration(),
                null);

        // 1 of 4 = 25 % < 50 % → no upgrade
        assertEquals(IntelLevel.OBSERVED, roster.getFormations().get(0).getIntelLevel());
    }

    // -------------------------------------------------------------------------
    // Units not in this scenario are not modified
    // -------------------------------------------------------------------------

    /**
     * Units whose {@code lastDeployedScenarioId} does not match the current
     * scenario should be left untouched.
     */
    @Test
    void foldResolutionInto_unitFromDifferentScenario_notModified() {
        UUID unitId = UUID.randomUUID();
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setId(unitId);
        unit.setStatus(Status.READY);
        // Different scenario
        unit.setLastDeployedScenarioId(new UUID(99, 0L));

        StratConOpForRoster roster = new StratConOpForRoster();
        roster.addUnit(unit);

        Entity destroyedEntity = mockEntity(unitId, true);
        Map<UUID, Entity> entities = singleEntityMap(unitId, destroyedEntity);

        roster.foldResolutionInto(
                mockScenario(42), // different scenario
                entities,
                Collections.emptyList(),
                Collections.emptyList(),
                new Hashtable<>(),
                Collections.emptyEnumeration(),
                null);

        // Unit should still be READY — it wasn't in this scenario
        assertEquals(Status.READY, roster.getUnit(unitId).getStatus());
    }
}
