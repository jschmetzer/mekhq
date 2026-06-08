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

import java.util.Comparator;
import java.util.List;

import megamek.logging.MMLogger;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.enums.DailyReportType;
import mekhq.campaign.events.OpForRosterChangedEvent;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConFacility;
import mekhq.campaign.stratCon.StratConTrackState;

/**
 * Applies roster mutations to the static OpFor and ally rosters when a
 * StratCon facility changes hands (Phase 2 of static OpFor).
 *
 * <p>The mutation table lives in {@link FacilityRosterEffect}. This class
 * applies the chosen effect: positive deltas reuse the reinforcement
 * machinery; negative deltas destroy N living formations on the affected
 * track, preferring formations with the most living units.</p>
 *
 * <p>Hooked into {@code StratConRulesManager.processScenarioCompletion}
 * immediately after the existing {@code switchFacilityOwner} call.</p>
 */
public final class FacilityCaptureEffects {

    private static final MMLogger LOGGER = MMLogger.create(FacilityCaptureEffects.class);

    /** Owner-state semantics for the facility transition. */
    public enum CaptureDirection {
        /** Hostile facility becomes Allied (player captures from enemy). */
        PLAYER_CAPTURE,
        /** Allied facility becomes Hostile (player loses to enemy). */
        PLAYER_LOSS
    }

    private FacilityCaptureEffects() {
    }

    /**
     * Applies the facility-driven roster effect for the given transition.
     *
     * <p>No-ops when the contract has no StratCon state, when neither roster
     * exists, or when the facility type has no effect. Posts a campaign report
     * line for any roster shrinkage or growth, and fires
     * {@link OpForRosterChangedEvent} so the UI refreshes.</p>
     *
     * @param facility  the facility that just changed hands
     * @param direction whether this was a player capture or player loss
     * @param track     the track the facility belongs to
     * @param contract  the active AtB contract
     * @param campaign  the active campaign
     */
    public static void apply(final StratConFacility facility,
            final CaptureDirection direction,
            final StratConTrackState track,
            final AtBContract contract,
            final Campaign campaign) {

        if (facility == null || track == null || contract == null) {
            return;
        }
        StratConCampaignState state = contract.getStratconCampaignState();
        if (state == null) {
            return;
        }

        FacilityRosterEffect.Effect effect = (direction == CaptureDirection.PLAYER_CAPTURE)
                ? FacilityRosterEffect.onPlayerCapture(facility.getFacilityType())
                : FacilityRosterEffect.onPlayerLoss(facility.getFacilityType());

        if (!effect.isAnyChange()) {
            return;
        }

        StratConOpForRoster opForRoster = state.getOpForRoster();
        StratConOpForRoster allyRoster = state.getAlliedRoster();

        boolean changed = false;
        if (effect.enemyDelta() != 0 && opForRoster != null) {
            changed |= applyDelta(effect.enemyDelta(), opForRoster, /*isAlly=*/false,
                    facility, direction, track, contract, campaign);
        }
        if (effect.allyDelta() != 0 && allyRoster != null) {
            changed |= applyDelta(effect.allyDelta(), allyRoster, /*isAlly=*/true,
                    facility, direction, track, contract, campaign);
        }

        if (changed) {
            MekHQ.triggerEvent(new OpForRosterChangedEvent(track));
        }
    }

    /**
     * Applies a single roster delta (positive = reinforce, negative = shrink)
     * and posts a campaign report.
     */
    private static boolean applyDelta(final int delta,
            final StratConOpForRoster roster,
            final boolean isAlly,
            final StratConFacility facility,
            final CaptureDirection direction,
            final StratConTrackState track,
            final AtBContract contract,
            final Campaign campaign) {

        // Note: this path intentionally does NOT call
        // roster.incrementReinforcementEventsFired() — that counter caps
        // morale-driven reinforcements only. Facility-driven roster changes are
        // a separate mechanism and shouldn't burn against the morale cap.

        if (delta > 0) {
            // Reinforcement — reuse the morale-driven reinforcement entry points
            int added = isAlly
                    ? StratConOpForRosterBuilder.addAllyReinforcementFormations(
                            campaign, contract, roster, track, delta)
                    : StratConOpForRosterBuilder.addReinforcementFormations(
                            campaign, contract, roster, track, delta);
            if (added > 0) {
                String facilityName = java.util.Objects.toString(
                        facility.getDisplayableName(), "facility");
                String trackName = java.util.Objects.toString(
                        track.getDisplayableName(), "the sector");
                String causeNoun = (direction == CaptureDirection.PLAYER_CAPTURE)
                        ? "your capture of"
                        : "the loss of";
                String sideNoun = isAlly ? "Allied" : "Enemy";
                campaign.addReport(DailyReportType.BATTLE, String.format(
                        "%s reinforcements arrive on %s in response to %s the %s — %d new formation%s.",
                        sideNoun, trackName, causeNoun, facilityName,
                        added, added == 1 ? "" : "s"));
                LOGGER.info("Facility-effect: {} +{} formations on '{}' from facility '{}' ({})",
                        sideNoun, added, trackName, facilityName, direction);
                return true;
            }
        } else if (delta < 0) {
            // Shrinkage — destroy living formations on this track
            int targetKillCount = -delta;
            List<StratConOpForFormation> victims = pickVictims(roster, track, targetKillCount);
            if (!victims.isEmpty()) {
                for (StratConOpForFormation victim : victims) {
                    for (java.util.UUID unitId : victim.getUnitIds()) {
                        StratConOpForUnit unit = roster.getUnit(unitId);
                        if (unit != null && unit.getStatus() == Status.READY) {
                            unit.setStatus(Status.DESTROYED);
                            unit.setRevealed(true);
                        }
                    }
                }
                String facilityName = java.util.Objects.toString(
                        facility.getDisplayableName(), "facility");
                String trackName = java.util.Objects.toString(
                        track.getDisplayableName(), "the sector");
                String causeNoun = (direction == CaptureDirection.PLAYER_CAPTURE)
                        ? "your capture of"
                        : "the loss of";
                String sideNoun = isAlly ? "Allied" : "Enemy";
                campaign.addReport(DailyReportType.BATTLE, String.format(
                        "%s forces on %s collapse in response to %s the %s — %d formation%s destroyed.",
                        sideNoun, trackName, causeNoun, facilityName,
                        victims.size(), victims.size() == 1 ? "" : "s"));
                LOGGER.info("Facility-effect: {} -{} formations on '{}' from facility '{}' ({})",
                        sideNoun, victims.size(), trackName, facilityName, direction);
                return true;
            }
        }
        return false;
    }

    /**
     * Selects up to {@code count} living formations on the given track, prefer
     * formations with the most living units (maximizes impact, more dramatic
     * narrative).
     */
    private static List<StratConOpForFormation> pickVictims(
            final StratConOpForRoster roster,
            final StratConTrackState track,
            final int count) {
        String trackName = track.getDisplayableName();
        if (trackName == null) {
            return List.of();
        }
        return roster.livingFormationsForTrack(trackName).stream()
                .sorted(Comparator.<StratConOpForFormation>comparingInt(
                        f -> f.livingUnits(roster).size()).reversed())
                .limit(count)
                .toList();
    }
}
