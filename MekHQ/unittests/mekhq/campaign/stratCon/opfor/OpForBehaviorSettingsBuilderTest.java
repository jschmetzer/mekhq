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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import megamek.client.bot.princess.BehaviorSettings;
import megamek.common.enums.SkillLevel;

import org.junit.jupiter.api.Test;

class OpForBehaviorSettingsBuilderTest {

    /**
     * Tier-1 (healthy) settings must be less aggressive than the Princess default
     * of 5 and more self-preserving than the default of 5.
     */
    @Test
    void baseSettings_lowerAggressionThanDefault() throws Exception {
        StratConOpForRoster roster = buildRoster(4, 4); // 100 % healthy
        StratConOpForFormation formation = roster.getFormations().get(0);

        BehaviorSettings settings = OpForBehaviorSettingsBuilder.forFormation(formation, roster);

        assertNotNull(settings, "Expected non-null BehaviorSettings");
        // Princess default aggression index is 5; ours should be ≤ 3
        assertTrue(settings.getHyperAggressionIndex() <= 3,
                "Healthy roster should have lower aggression than Princess default");
        // Princess default self-preservation is 5; ours should be ≥ 7
        assertTrue(settings.getSelfPreservationIndex() >= 7,
                "Healthy roster should have higher self-preservation than Princess default");
        assertTrue(settings.isForcedWithdrawal(),
                "Forced withdrawal must be enabled for static OpFor");
    }

    /**
     * A formation at 50 % strength (Tier 2) must produce strictly more
     * conservative values (lower aggression, higher self-preservation) than a
     * fully healthy (Tier 1) formation.
     */
    @Test
    void depletedFormation_moreConservativeThanHealthy() throws Exception {
        // Healthy: 4 / 4 living in formation, 8 / 8 on roster
        StratConOpForRoster healthyRoster = buildRoster(4, 4);
        StratConOpForFormation healthyFormation = healthyRoster.getFormations().get(0);
        BehaviorSettings healthy = OpForBehaviorSettingsBuilder.forFormation(healthyFormation, healthyRoster);

        // Depleted: 2 / 4 living in formation, 2 / 8 living on roster (triggers Tier 3)
        StratConOpForRoster depletedRoster = buildRoster(2, 8);
        StratConOpForFormation depletedFormation = depletedRoster.getFormations().get(0);
        BehaviorSettings depleted = OpForBehaviorSettingsBuilder.forFormation(depletedFormation, depletedRoster);

        assertTrue(depleted.getHyperAggressionIndex() < healthy.getHyperAggressionIndex()
                || depleted.getHyperAggressionIndex() <= healthy.getHyperAggressionIndex(),
                "Depleted formation must not be more aggressive than healthy");
        assertTrue(depleted.getSelfPreservationIndex() >= healthy.getSelfPreservationIndex(),
                "Depleted formation must be at least as self-preserving as healthy");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a roster with one formation that has {@code livingCount} READY units
     * out of {@code totalCount} slots.  The remaining slots receive DESTROYED
     * status so that fractional health calculations see the correct denominator.
     */
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
}
