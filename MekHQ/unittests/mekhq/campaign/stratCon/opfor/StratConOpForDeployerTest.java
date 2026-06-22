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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import megamek.common.units.UnitType;
import mekhq.campaign.mission.ScenarioForceTemplate;
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
    // shouldUseStaticAllyPath — Ally-only mirror of the routing predicate
    // -------------------------------------------------------------------------

    @Test
    void shouldUseStaticAllyPath_alliedWithRoster_returnsTrue() {
        StratConOpForRoster roster = makeNonEmptyRoster();
        assertTrue(StratConOpForDeployer.shouldUseStaticAllyPath(ForceAlignment.Allied, roster),
                "Allied + non-null roster should use the static ally path");
    }

    @Test
    void shouldUseStaticAllyPath_alliedNullRoster_returnsFalse() {
        assertFalse(StratConOpForDeployer.shouldUseStaticAllyPath(ForceAlignment.Allied, null),
                "Allied + null roster should NOT use the static ally path");
    }

    @Test
    void shouldUseStaticAllyPath_opposingWithRoster_returnsFalse() {
        StratConOpForRoster roster = makeNonEmptyRoster();
        assertFalse(StratConOpForDeployer.shouldUseStaticAllyPath(ForceAlignment.Opposing, roster),
                "Opposing alignment should never use the static ally path");
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
    // Test for recency sort: never-deployed formations must be preferred
    // -------------------------------------------------------------------------

    /**
     * When two formations of equal weight class compete, the never-deployed one
     * (null lastDeployedScenarioId) must be selected before the one that was
     * previously deployed (non-null lastDeployedScenarioId).
     */
    @Test
    void selectFormations_recencySort_neverDeployedPickedBeforeDeployed() {
        int weightHeavy = 3;

        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForFormation neverDeployed = makeReadyFormationWithWeight(
                "Gamma Track", IntelLevel.UNKNOWN, 2, weightHeavy);
        // Mark this formation as having been deployed in some prior scenario
        StratConOpForFormation prevDeployed = makeReadyFormationWithWeight(
                "Gamma Track", IntelLevel.UNKNOWN, 2, weightHeavy);
        prevDeployed.setLastDeployedScenarioId(UUID.randomUUID());

        // Deliberately add prevDeployed first so its position in the list is
        // earlier — the sort must override insertion order.
        for (UUID id : prevDeployed.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        roster.addFormation(prevDeployed);
        for (UUID id : neverDeployed.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        roster.addFormation(neverDeployed);

        // Use a generous BV budget so both formations can be selected — we verify
        // ordering, not exclusion.  Both have BV=0 (no proto-entity), so both fit.
        List<StratConOpForFormation> selected = StratConOpForDeployer.selectFormations(
                roster, "Gamma Track", weightHeavy, 100_000.0);

        assertFalse(selected.isEmpty(), "Should select at least one formation");
        assertEquals(neverDeployed, selected.get(0),
                "Never-deployed formation must sort before previously-deployed one");
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
    // Global deploy fallback — a cleared track must pull stragglers from others
    // -------------------------------------------------------------------------

    /**
     * When the scenario's own track has no living formations, the deployer must
     * fall back to the global pool of living formations (drawn from other tracks)
     * so that stranded formations can still be engaged and destroyed. Without
     * this, formations parked on quiet tracks never deploy and the
     * eliminate-the-roster win condition is unreachable.
     */
    @Test
    void selectFormations_emptyTrack_fallsBackToGlobalLivingFormations() {
        StratConOpForRoster roster = new StratConOpForRoster();
        // The only living formation sits on a DIFFERENT track than the one we query.
        StratConOpForFormation other = makeReadyFormation("Track B", IntelLevel.UNKNOWN, 4);
        for (UUID id : other.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        roster.addFormation(other);

        List<StratConOpForFormation> selected = StratConOpForDeployer.selectFormations(
                roster, "Track A", 0, 50_000.0);

        assertFalse(selected.isEmpty(),
                "A cleared track should fall back to living formations on other tracks");
        assertTrue(selected.contains(other),
                "The straggler on Track B should be pulled in once Track A is empty");
    }

    /**
     * The fallback is a last resort: while the scenario's own track still has
     * living formations, the deployer must use only those and must NOT pull
     * formations from other tracks.
     */
    @Test
    void selectFormations_trackHasLivingFormations_doesNotPullFromOtherTracks() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation local = makeReadyFormation("Track A", IntelLevel.UNKNOWN, 4);
        StratConOpForFormation other = makeReadyFormation("Track B", IntelLevel.UNKNOWN, 4);
        for (UUID id : local.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        for (UUID id : other.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        roster.addFormation(local);
        roster.addFormation(other);

        List<StratConOpForFormation> selected = StratConOpForDeployer.selectFormations(
                roster, "Track A", 0, 500_000.0);

        assertTrue(selected.contains(local), "The on-track formation should be selected");
        assertFalse(selected.contains(other),
                "Formations on other tracks must not be pulled while the track has its own");
    }

    /**
     * The global fallback exists to keep the (line-only) win condition reachable,
     * so it must pull only line formations — never militia. Militia are excluded
     * from the contract-win condition and deploy solely on their own assigned
     * track via the normal per-track path; pulling them into the cleared-track
     * fallback would deploy militia as standard line OpFor.
     */
    @Test
    void selectFormations_emptyTrackFallback_excludesMilitia() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForFormation line = makeReadyFormation("Track B", IntelLevel.UNKNOWN, 4);
        StratConOpForFormation militia = makeReadyFormation("Track C", IntelLevel.UNKNOWN, 4);
        militia.setMilitia(true);
        for (UUID id : line.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        for (UUID id : militia.getUnitIds()) {
            roster.addUnit(makeReadyUnit(id));
        }
        roster.addFormation(line);
        roster.addFormation(militia);

        List<StratConOpForFormation> selected = StratConOpForDeployer.selectFormations(
                roster, "Track A", 0, 500_000.0);

        assertTrue(selected.contains(line), "Line straggler should be pulled via the fallback");
        assertFalse(selected.contains(militia),
                "Militia must not be pulled into the line-OpFor fallback");
    }

    // -------------------------------------------------------------------------
    // isStaticEligible — only standard ground slots may be filled from the roster
    // -------------------------------------------------------------------------

    /**
     * The static roster is a ground Mek force, so it may only satisfy the
     * standard mixed-ground slot and the pure-Mek slot. Slots that require
     * DropShips, infantry, aerospace, civilians, or an aero mix must defer to
     * dynamic generation (which produces the correct unit types) rather than
     * deploy roster Meks under a mismatched force label.
     */
    @Test
    void isStaticEligible_acceptsGroundMix_rejectsSpecialTypes() {
        assertTrue(StratConOpForDeployer.isStaticEligible(
                        forceTemplateOfType(ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_MIX)),
                "Standard AtB mixed force should be roster-eligible");
        assertTrue(StratConOpForDeployer.isStaticEligible(forceTemplateOfType(UnitType.MEK)),
                "Pure-'Mech force should be roster-eligible");

        int[] unsatisfiable = {
                UnitType.DROPSHIP,
                UnitType.INFANTRY,
                UnitType.TANK,
                ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_AERO_MIX,
                ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_CIVILIANS,
        };
        for (int unitType : unsatisfiable) {
            assertFalse(StratConOpForDeployer.isStaticEligible(forceTemplateOfType(unitType)),
                    "Special unit type " + unitType + " must defer to dynamic generation");
        }
    }

    private static ScenarioForceTemplate forceTemplateOfType(final int allowedUnitType) {
        ScenarioForceTemplate template = new ScenarioForceTemplate();
        template.setAllowedUnitType(allowedUnitType);
        return template;
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
