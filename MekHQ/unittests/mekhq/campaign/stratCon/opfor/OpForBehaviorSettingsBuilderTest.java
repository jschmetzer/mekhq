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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import megamek.client.bot.princess.BehaviorSettings;
import megamek.client.bot.princess.CardinalEdge;
import megamek.common.enums.SkillLevel;
import mekhq.campaign.mission.enums.AtBContractType;

import org.junit.jupiter.api.Test;

class OpForBehaviorSettingsBuilderTest {

    // -------------------------------------------------------------------------
    // Tier 1 — exact value pins per posture
    // -------------------------------------------------------------------------

    @Test
    void t1_defender_exactValues() throws Exception {
        StratConOpForRoster roster = buildRoster(4, 4); // 100 % healthy
        StratConOpForFormation formation = roster.getFormations().get(0);

        BehaviorSettings s = OpForBehaviorSettingsBuilder.forFormation(
                formation, roster, OpForBehaviorSettingsBuilder.Posture.DEFENDER);

        assertEquals(3, s.getHyperAggressionIndex(), "DEFENDER Tier 1 aggression");
        assertEquals(3, s.getBraveryIndex(), "DEFENDER Tier 1 bravery");
        assertEquals(7, s.getSelfPreservationIndex(), "DEFENDER Tier 1 selfPres");
        assertEquals(6, s.getHerdMentalityIndex(), "DEFENDER Tier 1 herd");
        assertEquals(7, s.getFallShameIndex(), "DEFENDER Tier 1 fallShame");
        assertTrue(s.isForcedWithdrawal());
        assertEquals(CardinalEdge.NEAREST, s.getRetreatEdge());
        assertEquals("STATIC_OPFOR_T1_DEFENDER", s.getDescription());
    }

    @Test
    void t1_attacker_exactValues() throws Exception {
        StratConOpForRoster roster = buildRoster(4, 4);
        StratConOpForFormation formation = roster.getFormations().get(0);

        BehaviorSettings s = OpForBehaviorSettingsBuilder.forFormation(
                formation, roster, OpForBehaviorSettingsBuilder.Posture.ATTACKER);

        assertEquals(5, s.getHyperAggressionIndex(), "ATTACKER Tier 1 aggression");
        assertEquals(5, s.getBraveryIndex(), "ATTACKER Tier 1 bravery");
        assertEquals(6, s.getSelfPreservationIndex(), "ATTACKER Tier 1 selfPres");
        assertEquals(6, s.getHerdMentalityIndex(), "ATTACKER Tier 1 herd");
        assertEquals(6, s.getFallShameIndex(), "ATTACKER Tier 1 fallShame");
        assertEquals("STATIC_OPFOR_T1_ATTACKER", s.getDescription());
    }

    @Test
    void t1_irregular_exactValues() throws Exception {
        StratConOpForRoster roster = buildRoster(4, 4);
        StratConOpForFormation formation = roster.getFormations().get(0);

        BehaviorSettings s = OpForBehaviorSettingsBuilder.forFormation(
                formation, roster, OpForBehaviorSettingsBuilder.Posture.IRREGULAR);

        assertEquals(4, s.getHyperAggressionIndex(), "IRREGULAR Tier 1 aggression");
        assertEquals(3, s.getBraveryIndex(), "IRREGULAR Tier 1 bravery");
        assertEquals(6, s.getSelfPreservationIndex(), "IRREGULAR Tier 1 selfPres");
        assertEquals(4, s.getHerdMentalityIndex(), "IRREGULAR Tier 1 herd");
        assertEquals(5, s.getFallShameIndex(), "IRREGULAR Tier 1 fallShame");
        assertEquals("STATIC_OPFOR_T1_IRREGULAR", s.getDescription());
    }

    // -------------------------------------------------------------------------
    // Tier 2 / Tier 3 — posture-independent exact values
    // -------------------------------------------------------------------------

    @Test
    void t2_formationDepleted_exactValues() throws Exception {
        // 2 living of 4 in formation = exactly 50% → triggers Tier 2 with ≤ check.
        // Roster: 6 living of 8 total = 75% — above the 30% Tier 3 threshold.
        StratConOpForRoster roster = buildRosterWithExtraFormation(2, 4, 4);
        StratConOpForFormation formation = roster.getFormations().get(0);

        BehaviorSettings s = OpForBehaviorSettingsBuilder.forFormation(
                formation, roster, OpForBehaviorSettingsBuilder.Posture.DEFENDER);

        assertEquals(2, s.getHyperAggressionIndex(), "Tier 2 aggression");
        assertEquals(2, s.getBraveryIndex(), "Tier 2 bravery");
        assertEquals(8, s.getSelfPreservationIndex(), "Tier 2 selfPres");
        assertEquals(7, s.getHerdMentalityIndex(), "Tier 2 herd");
        assertEquals(8, s.getFallShameIndex(), "Tier 2 fallShame");
        assertEquals("STATIC_OPFOR_T2_DEPLETED", s.getDescription());
    }

    @Test
    void t3_rosterCritical_exactValues() throws Exception {
        // 2 living of 8 = 25 % → ≤ 30 % triggers Tier 3
        StratConOpForRoster roster = buildRoster(2, 8);
        StratConOpForFormation formation = roster.getFormations().get(0);

        BehaviorSettings s = OpForBehaviorSettingsBuilder.forFormation(
                formation, roster, OpForBehaviorSettingsBuilder.Posture.ATTACKER);

        // Posture-independent at this tier
        assertEquals(1, s.getHyperAggressionIndex(), "Tier 3 aggression");
        assertEquals(1, s.getBraveryIndex(), "Tier 3 bravery");
        assertEquals(10, s.getSelfPreservationIndex(), "Tier 3 selfPres");
        assertEquals(8, s.getHerdMentalityIndex(), "Tier 3 herd");
        assertEquals(9, s.getFallShameIndex(), "Tier 3 fallShame");
        assertEquals("STATIC_OPFOR_T3_CRITICAL", s.getDescription());
    }

    // -------------------------------------------------------------------------
    // Threshold boundary fix — 50% formation = Tier 2 (not Tier 1)
    // -------------------------------------------------------------------------

    @Test
    void exactly50PercentFormation_triggersTier2() throws Exception {
        // 4 living of 8 = exactly 50%. Roster healthy (above 30%).
        StratConOpForRoster roster = buildRosterWithExtraFormation(4, 8, 4);
        StratConOpForFormation formation = roster.getFormations().get(0);

        BehaviorSettings s = OpForBehaviorSettingsBuilder.forFormation(
                formation, roster, OpForBehaviorSettingsBuilder.Posture.DEFENDER);

        assertEquals("STATIC_OPFOR_T2_DEPLETED", s.getDescription(),
                "≤ threshold means exactly 50% triggers Tier 2");
    }

    // -------------------------------------------------------------------------
    // getPosture mapping
    // -------------------------------------------------------------------------

    @Test
    void getPosture_garrisonDuty_isAttacker() {
        assertEquals(OpForBehaviorSettingsBuilder.Posture.ATTACKER,
                OpForBehaviorSettingsBuilder.getPosture(AtBContractType.GARRISON_DUTY));
    }

    @Test
    void getPosture_pirateHunting_isIrregular() {
        assertEquals(OpForBehaviorSettingsBuilder.Posture.IRREGULAR,
                OpForBehaviorSettingsBuilder.getPosture(AtBContractType.PIRATE_HUNTING));
    }

    @Test
    void getPosture_planetaryAssault_isDefender() {
        assertEquals(OpForBehaviorSettingsBuilder.Posture.DEFENDER,
                OpForBehaviorSettingsBuilder.getPosture(AtBContractType.PLANETARY_ASSAULT));
    }

    @Test
    void getPosture_nullType_defaultsToDefender() {
        assertEquals(OpForBehaviorSettingsBuilder.Posture.DEFENDER,
                OpForBehaviorSettingsBuilder.getPosture(null));
    }

    @Test
    void getPosture_allContractTypes_returnNonNull() {
        for (AtBContractType type : AtBContractType.values()) {
            assertNotNull(OpForBehaviorSettingsBuilder.getPosture(type),
                    "Posture must be non-null for " + type);
        }
    }

    // -------------------------------------------------------------------------
    // Backwards compat — no-posture overload still works
    // -------------------------------------------------------------------------

    @Test
    void noPostureOverload_usesDefender() throws Exception {
        StratConOpForRoster roster = buildRoster(4, 4);
        StratConOpForFormation formation = roster.getFormations().get(0);
        BehaviorSettings s = OpForBehaviorSettingsBuilder.forFormation(formation, roster);
        assertEquals("STATIC_OPFOR_T1_DEFENDER", s.getDescription(),
                "Backwards-compat overload should default to DEFENDER posture");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StratConOpForRoster buildRoster(int livingCount, int totalCount) {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setId(UUID.randomUUID());
        formation.setName("Test Formation");
        formation.setSkillLevel(SkillLevel.REGULAR);

        List<UUID> unitIds = new ArrayList<>();
        for (int i = 0; i < totalCount; i++) {
            StratConOpForUnit unit = new StratConOpForUnit();
            unit.setId(UUID.randomUUID());
            unit.setFormationId(formation.getId());
            if (i >= livingCount) {
                unit.setStatus(Status.DESTROYED);
            }
            roster.addUnit(unit);
            unitIds.add(unit.getId());
        }
        formation.setUnitIds(unitIds);
        roster.addFormation(formation);
        return roster;
    }

    /**
     * Builds a roster with two formations — the first with {@code livingCount}/{@code totalCount},
     * and a second fully-healthy formation of {@code padTotalCount} units. Used to test
     * Tier 2 boundary without simultaneously crossing the Tier 3 roster threshold.
     */
    private static StratConOpForRoster buildRosterWithExtraFormation(
            int livingCount, int totalCount, int padTotalCount) {
        StratConOpForRoster roster = buildRoster(livingCount, totalCount);

        StratConOpForFormation pad = new StratConOpForFormation();
        pad.setId(UUID.randomUUID());
        pad.setName("Pad Formation");
        pad.setSkillLevel(SkillLevel.REGULAR);
        List<UUID> padIds = new ArrayList<>();
        for (int i = 0; i < padTotalCount; i++) {
            StratConOpForUnit unit = new StratConOpForUnit();
            unit.setId(UUID.randomUUID());
            unit.setFormationId(pad.getId());
            roster.addUnit(unit);
            padIds.add(unit.getId());
        }
        pad.setUnitIds(padIds);
        roster.addFormation(pad);
        return roster;
    }
}
