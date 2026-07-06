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

import java.util.UUID;

import org.junit.jupiter.api.Test;

import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConScenario;
import mekhq.campaign.stratCon.StratConTrackState;

/**
 * Tests for {@link StratConOpForRoster#checkEliminationStatus}.
 *
 * <p>Three scenarios per the design:
 * <ol>
 *   <li>STILL_ACTIVE — living units remain on other tracks.</li>
 *   <li>TRACK_PACIFIED — the resolved track is empty but others have living units.</li>
 *   <li>CONTRACT_WON — every unit in the entire roster is terminal.</li>
 * </ol>
 */
class CheckEliminationStatusTest {

    private static final String TRACK_A = "Alpha Track";
    private static final String TRACK_B = "Bravo Track";

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static StratConOpForUnit readyUnit(final UUID formationId) {
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setStatus(Status.READY);
        unit.setFormationId(formationId);
        return unit;
    }

    private static StratConOpForUnit destroyedUnit(final UUID formationId) {
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setStatus(Status.DESTROYED);
        unit.setFormationId(formationId);
        return unit;
    }

    /**
     * Builds a formation assigned to the given track, containing the given units,
     * and registers everything in the roster.
     */
    private static StratConOpForFormation addFormation(
            final StratConOpForRoster roster,
            final String trackName,
            final StratConOpForUnit... units) {
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setAssignedTrackName(trackName);
        for (StratConOpForUnit unit : units) {
            unit.setFormationId(formation.getId());
            roster.addUnit(unit);
            formation.getUnitIds().add(unit.getId());
        }
        roster.addFormation(formation);
        return formation;
    }

    /**
     * Creates a mock {@link StratConScenario} that reports the given track when
     * {@code getTrackForScenario} is called.
     */
    private static StratConScenario scenarioOnTrack(final StratConTrackState track) {
        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(contract.getStratConCampaignState()).thenReturn(state);

        StratConScenario scenario = mock(StratConScenario.class);
        when(scenario.getTrackForScenario(campaign, state)).thenReturn(track);
        return scenario;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void stillActive_whenLivingUnitsRemainOnOtherTracks() {
        // Arrange: two tracks, both have living units; the resolved track's
        // formation is partially destroyed but not empty.
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit liveA = readyUnit(null);
        StratConOpForUnit deadA = destroyedUnit(null);
        addFormation(roster, TRACK_A, liveA, deadA); // track A: still has a living unit

        StratConOpForUnit liveB = readyUnit(null);
        addFormation(roster, TRACK_B, liveB);        // track B: healthy

        StratConTrackState trackA = new StratConTrackState();
        trackA.setDisplayableName(TRACK_A);

        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(contract.getStratConCampaignState()).thenReturn(state);

        StratConScenario scenario = mock(StratConScenario.class);
        when(scenario.getTrackForScenario(campaign, state)).thenReturn(trackA);

        // Act
        EliminationResult result = roster.checkEliminationStatus(campaign, contract, scenario);

        // Assert
        assertEquals(EliminationResult.STILL_ACTIVE, result,
                "Expected STILL_ACTIVE when living units remain on the resolved track");
    }

    @Test
    void trackPacified_whenResolvedTrackIsEmptyButOtherTracksHaveLivingUnits() {
        // Arrange: track A is wiped out; track B still has a living unit.
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit deadA = destroyedUnit(null);
        addFormation(roster, TRACK_A, deadA); // all terminal on track A

        StratConOpForUnit liveB = readyUnit(null);
        addFormation(roster, TRACK_B, liveB); // track B still alive

        StratConTrackState trackA = new StratConTrackState();
        trackA.setDisplayableName(TRACK_A);

        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(contract.getStratConCampaignState()).thenReturn(state);

        StratConScenario scenario = mock(StratConScenario.class);
        when(scenario.getTrackForScenario(campaign, state)).thenReturn(trackA);

        // Act
        EliminationResult result = roster.checkEliminationStatus(campaign, contract, scenario);

        // Assert
        assertEquals(EliminationResult.TRACK_PACIFIED, result,
                "Expected TRACK_PACIFIED when only the resolved track is empty");
    }

    @Test
    void stillActive_whenResolvedTrackNeverHadLineUnits_militiaOnlyTrack() {
        // Track A holds a living LINE unit (so the roster-wide check is non-empty
        // and we reach the track-scoped branch). Track B holds ONLY a militia
        // formation — no line units were ever assigned there, so its line-unit set
        // is empty from creation. Resolving a scenario on Track B must NOT pacify
        // it (nothing was cleared).
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForUnit liveLineA = readyUnit(null);
        addFormation(roster, TRACK_A, liveLineA);

        StratConOpForFormation militiaB = addFormation(roster, TRACK_B, readyUnit(null));
        militiaB.setMilitia(true);

        StratConTrackState trackB = new StratConTrackState();
        trackB.setDisplayableName(TRACK_B);

        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(contract.getStratConCampaignState()).thenReturn(state);

        StratConScenario scenario = mock(StratConScenario.class);
        when(scenario.getTrackForScenario(campaign, state)).thenReturn(trackB);

        EliminationResult result = roster.checkEliminationStatus(campaign, contract, scenario);

        assertEquals(EliminationResult.STILL_ACTIVE, result,
                "A track that never had line units (militia-only) must not report TRACK_PACIFIED");
    }

    @Test
    void contractWon_whenAllRosterUnitsAreTerminal() {
        // Arrange: every unit across all tracks is terminal.
        StratConOpForRoster roster = new StratConOpForRoster();

        addFormation(roster, TRACK_A, destroyedUnit(null), destroyedUnit(null));
        addFormation(roster, TRACK_B, destroyedUnit(null));

        StratConTrackState trackA = new StratConTrackState();
        trackA.setDisplayableName(TRACK_A);

        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(contract.getStratConCampaignState()).thenReturn(state);

        StratConScenario scenario = mock(StratConScenario.class);
        when(scenario.getTrackForScenario(campaign, state)).thenReturn(trackA);

        // Act
        EliminationResult result = roster.checkEliminationStatus(campaign, contract, scenario);

        // Assert
        assertEquals(EliminationResult.CONTRACT_WON, result,
                "Expected CONTRACT_WON when every unit in the roster is terminal");
    }

    @Test
    void contractWon_whenOnlyMilitiaRemain_lineUnitsAllDestroyed() {
        // Arrange: one line formation (all DESTROYED) + one militia formation (READY).
        // With the militia exclusion, no LINE units remain -> CONTRACT_WON.
        StratConOpForRoster roster = new StratConOpForRoster();

        // Line formation — all terminal
        addFormation(roster, TRACK_A, destroyedUnit(null), destroyedUnit(null));

        // Militia formation — still alive
        StratConOpForFormation militiaFormation = addFormation(roster, TRACK_B, readyUnit(null));
        militiaFormation.setMilitia(true);

        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(contract.getStratConCampaignState()).thenReturn(state);

        // Act
        EliminationResult result = roster.checkEliminationStatus(campaign, contract, null);

        // Assert
        assertEquals(EliminationResult.CONTRACT_WON, result,
                "Expected CONTRACT_WON when no line units remain, even if militia are still alive");
    }

    @Test
    void stillActive_whenRosterIsMilitiaOnly_noLineUnitsEverExisted() {
        // Arrange: a roster with ONLY a militia formation (all alive) and no line
        // units at all. livingLineUnits() is empty from creation, but that is
        // structural — the roster must not be declared CONTRACT_WON on the first
        // resolution just because it never had line units.
        StratConOpForRoster roster = new StratConOpForRoster();

        StratConOpForFormation militiaFormation = addFormation(roster, TRACK_A, readyUnit(null));
        militiaFormation.setMilitia(true);

        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);

        // Act
        EliminationResult result = roster.checkEliminationStatus(campaign, contract, null);

        // Assert
        assertEquals(EliminationResult.STILL_ACTIVE, result,
                "A militia-only roster (no line units ever) must not be instantly CONTRACT_WON");
    }

    @Test
    void stillActive_whenLineUnitsAlive_regardlessOfMilitia() {
        // Arrange: one line formation (READY) + one militia formation (READY).
        // Line units are still alive -> STILL_ACTIVE.
        StratConOpForRoster roster = new StratConOpForRoster();

        // Line formation — alive
        addFormation(roster, TRACK_A, readyUnit(null));

        // Militia formation — alive
        StratConOpForFormation militiaFormation = addFormation(roster, TRACK_B, readyUnit(null));
        militiaFormation.setMilitia(true);

        Campaign campaign = mock(Campaign.class);
        AtBContract contract = mock(AtBContract.class);
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(contract.getStratConCampaignState()).thenReturn(state);

        // Act
        EliminationResult result = roster.checkEliminationStatus(campaign, contract, null);

        // Assert
        assertEquals(EliminationResult.STILL_ACTIVE, result,
                "Expected STILL_ACTIVE when line units are alive, regardless of militia");
    }
}
