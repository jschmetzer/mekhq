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

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StratConOpForRoster#wasDeployedTo(UUID)}, the participant selector the resolver uses to fold a
 * scenario's result into exactly the challenger(s) that fought it.
 */
class StratConOpForRosterDeployedToTest {

    private static final UUID SCENARIO_A = new UUID(101L, 0L);
    private static final UUID SCENARIO_B = new UUID(202L, 0L);

    private static StratConOpForRoster rosterWithFormationDeployedTo(final UUID scenarioId) {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setLastDeployedScenarioId(scenarioId);
        roster.addFormation(formation);
        return roster;
    }

    @Test
    void trueOnlyForTheScenarioItsFormationWasDeployedTo() {
        StratConOpForRoster roster = rosterWithFormationDeployedTo(SCENARIO_A);

        assertTrue(roster.wasDeployedTo(SCENARIO_A), "matches the formation's deploy stamp");
        assertFalse(roster.wasDeployedTo(SCENARIO_B), "must not match a different scenario");
    }

    @Test
    void matchesWhenAnyOfSeveralFormationsWasDeployedThere() {
        // Three formations, only the MIDDLE one deployed into the queried scenario. Guards against both a "check
        // only the first" and a "check only the last" regression: the method must scan every formation.
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation first = new StratConOpForFormation();
        first.setLastDeployedScenarioId(SCENARIO_B);
        roster.addFormation(first);
        StratConOpForFormation deployed = new StratConOpForFormation();
        deployed.setLastDeployedScenarioId(SCENARIO_A);
        roster.addFormation(deployed);
        StratConOpForFormation last = new StratConOpForFormation(); // never deployed
        roster.addFormation(last);

        assertTrue(roster.wasDeployedTo(SCENARIO_A), "must match a stamp on any formation, not just the first or last");
    }

    @Test
    void falseForNullScenarioId() {
        StratConOpForRoster roster = rosterWithFormationDeployedTo(SCENARIO_A);

        assertFalse(roster.wasDeployedTo(null), "a null scenario id never matches");
    }

    @Test
    void falseWhenNoFormationHasBeenDeployed() {
        StratConOpForRoster roster = new StratConOpForRoster();
        roster.addFormation(new StratConOpForFormation()); // lastDeployedScenarioId defaults to null

        assertFalse(roster.wasDeployedTo(SCENARIO_A), "an undeployed roster matches nothing");
    }
}
