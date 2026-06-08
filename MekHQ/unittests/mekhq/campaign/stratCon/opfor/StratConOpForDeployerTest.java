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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import mekhq.campaign.mission.ScenarioForceTemplate.ForceAlignment;
import org.junit.jupiter.api.Test;

class StratConOpForDeployerTest {

    // -------------------------------------------------------------------------
    // Test 3: shouldUseStaticPath routing decision
    // -------------------------------------------------------------------------

    /**
     * Opposing alignment + non-null roster → use static path.
     */
    @Test
    void shouldUseStaticPath_opposingWithRoster_returnsTrue() {
        StratConOpForRoster roster = makeNonEmptyRoster();
        assertTrue(StratConOpForDeployer.shouldUseStaticPath(ForceAlignment.Opposing, roster),
                "Opposing + non-null roster should use the static path");
    }

    /**
     * Opposing alignment + null roster → use dynamic path.
     */
    @Test
    void shouldUseStaticPath_opposingNullRoster_returnsFalse() {
        assertFalse(StratConOpForDeployer.shouldUseStaticPath(ForceAlignment.Opposing, null),
                "Opposing + null roster should NOT use the static path");
    }

    /**
     * Allied alignment + non-null roster → never intercept allied forces.
     */
    @Test
    void shouldUseStaticPath_alliedWithRoster_returnsFalse() {
        StratConOpForRoster roster = makeNonEmptyRoster();
        assertFalse(StratConOpForDeployer.shouldUseStaticPath(ForceAlignment.Allied, roster),
                "Allied alignment should never use the static path regardless of roster");
    }

    /**
     * Third-party alignment + non-null roster → never intercept third-party forces.
     */
    @Test
    void shouldUseStaticPath_thirdWithRoster_returnsFalse() {
        StratConOpForRoster roster = makeNonEmptyRoster();
        assertFalse(StratConOpForDeployer.shouldUseStaticPath(ForceAlignment.Third, roster),
                "Third alignment should never use the static path regardless of roster");
    }

    // -------------------------------------------------------------------------
    // Test 1: single-formation selection + intel advance
    // -------------------------------------------------------------------------

    /**
     * When one formation is on the matching track, it should be selected and
     * its intel level advanced from UNKNOWN to OBSERVED.
     */
    @Test
    void selectFormations_singleCandidate_advancesIntelFromUnknownToObserved() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation formation = makeReadyFormation("Alpha Track", IntelLevel.UNKNOWN, 4);
        roster.addFormation(formation);
        for (UUID id : formation.getUnitIds()) {
            StratConOpForUnit unit = new StratConOpForUnit();
            unit.setId(id);
            unit.setStatus(Status.READY);
            roster.addUnit(unit);
        }

        List<StratConOpForFormation> selected = StratConOpForDeployer.selectFormations(
                roster, "Alpha Track", 0, 50_000.0);

        assertFalse(selected.isEmpty(), "Should have selected at least one formation");
        assertTrue(selected.contains(formation), "The only formation should be selected");

        // advance intel
        StratConOpForDeployer.advanceIntelForSelected(selected, null);
        assertTrue(formation.getIntelLevel().isAtLeast(IntelLevel.OBSERVED),
                "Intel should advance to OBSERVED after first deployment");
    }

    // -------------------------------------------------------------------------
    // Test 2: weight-class preference
    // -------------------------------------------------------------------------

    /**
     * When the template wants HEAVY (EntityWeightClass.WEIGHT_HEAVY) and we have
     * one MEDIUM formation and one HEAVY formation on the track, the HEAVY one
     * should appear first in the sorted candidate list.
     */
    @Test
    void selectFormations_weightClassPreference_heavyBeforeMedium() {
        // EntityWeightClass constants: LIGHT=1, MEDIUM=2, HEAVY=3, ASSAULT=4
        int weightMedium = 2;
        int weightHeavy = 3;

        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation mediumFormation = makeReadyFormationWithWeight(
                "Beta Track", IntelLevel.UNKNOWN, 4, weightMedium);
        StratConOpForFormation heavyFormation = makeReadyFormationWithWeight(
                "Beta Track", IntelLevel.UNKNOWN, 4, weightHeavy);

        for (UUID id : mediumFormation.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        for (UUID id : heavyFormation.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        roster.addFormation(mediumFormation);
        roster.addFormation(heavyFormation);

        // Request heavy formations, with enough BV budget to pick both (but we
        // just care about ordering — first selected should be heavy).
        List<StratConOpForFormation> selected = StratConOpForDeployer.selectFormations(
                roster, "Beta Track", weightHeavy, 500_000.0);

        assertFalse(selected.isEmpty(), "Should select at least one formation");
        assertTrue(selected.get(0).getWeightClass() == weightHeavy,
                "First selected formation should be the heavy one when template requests heavy");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StratConOpForRoster makeNonEmptyRoster() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setStatus(Status.READY);
        roster.addUnit(unit);
        return roster;
    }

    private static StratConOpForFormation makeReadyFormation(
            final String trackName,
            final IntelLevel intelLevel,
            final int unitCount) {
        return makeReadyFormationWithWeight(trackName, intelLevel, unitCount, 2 /* MEDIUM */);
    }

    private static StratConOpForFormation makeReadyFormationWithWeight(
            final String trackName,
            final IntelLevel intelLevel,
            final int unitCount,
            final int weightClass) {
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setAssignedTrackName(trackName);
        formation.setIntelLevel(intelLevel);
        formation.setWeightClass(weightClass);
        for (int i = 0; i < unitCount; i++) {
            formation.getUnitIds().add(UUID.randomUUID());
        }
        return formation;
    }

    private static StratConOpForUnit makeReadyUnit(final UUID id) {
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setId(id);
        unit.setStatus(Status.READY);
        return unit;
    }
}
