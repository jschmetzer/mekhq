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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import megamek.logging.MMLogger;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.enums.DailyReportType;
import mekhq.campaign.events.OpForRosterChangedEvent;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.enums.AtBMoraleLevel;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;

/**
 * Adds reinforcement formations to the static OpFor roster when the contract's
 * morale shifts downward (enemy is losing) past a contract-type-specific
 * threshold. Couples to {@code AtBContract.checkMorale} via a per-monthly hook
 * fired from {@code CampaignNewDayManager}.
 *
 * <p>This is the v1.1 morale-driven reinforcement engine. v1.5 will extend the
 * same machinery to the allied roster; Phase 2 will add facility-driven roster
 * <em>shrinkage</em>; v2 will surface incoming reinforcements via the
 * Intelligence TOE before they arrive.</p>
 */
public final class OpForReinforcementService {

    private static final MMLogger LOGGER = MMLogger.create(OpForReinforcementService.class);

    private OpForReinforcementService() {
    }

    /**
     * Considers firing a reinforcement event for the given contract.
     *
     * <p>No-ops when:</p>
     * <ul>
     *   <li>the contract has no static OpFor roster (legacy / dynamic mode),</li>
     *   <li>the contract type has no reinforcement profile,</li>
     *   <li>the event cap has been reached,</li>
     *   <li>morale has not shifted downward this month, or</li>
     *   <li>current morale is above the trigger threshold.</li>
     * </ul>
     *
     * <p>When eligible, rolls against the profile probability and on success
     * generates the appropriate number of formations, attaches them to the
     * most attrited track (or a weighted-random track as fallback), and posts
     * a campaign report describing the event.</p>
     *
     * @param campaign   the active campaign
     * @param contract   the AtB contract to consider
     * @param oldMorale  the morale level before this month's check
     * @param newMorale  the morale level after this month's check
     */
    public static void maybeReinforce(final Campaign campaign,
            final AtBContract contract,
            final AtBMoraleLevel oldMorale,
            final AtBMoraleLevel newMorale) {

        StratConCampaignState campaignState = contract.getStratconCampaignState();
        if (campaignState == null) {
            return;
        }
        StratConOpForRoster roster = campaignState.getOpForRoster();
        if (roster == null) {
            return;
        }

        ContractTypeReinforcementProfile.Profile profile =
                ContractTypeReinforcementProfile.getProfile(contract.getContractType());
        if (!profile.isReinforcementAllowed()) {
            return;
        }
        if (roster.getReinforcementEventsFired() >= profile.eventCap()) {
            return;
        }

        // Trigger requires morale to have shifted downward (enemy losing more)
        if (oldMorale == null || newMorale == null) {
            return;
        }
        if (newMorale.ordinal() >= oldMorale.ordinal()) {
            return;
        }
        // Current morale must be at or below the profile's threshold
        if (newMorale.ordinal() > profile.triggerThreshold().ordinal()) {
            return;
        }

        // Roll
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll >= profile.probability()) {
            LOGGER.info("Reinforcement check for contract '{}': morale {} (was {}), "
                    + "below threshold {}, roll {} >= prob {}, no reinforcement.",
                    contract.getName(), newMorale, oldMorale,
                    profile.triggerThreshold(), roll, profile.probability());
            return;
        }

        int formationCount = ThreadLocalRandom.current().nextInt(
                profile.minFormations(), profile.maxFormations() + 1);
        StratConTrackState targetTrack = pickTargetTrack(roster, campaignState);
        if (targetTrack == null) {
            LOGGER.warn("Reinforcement triggered for contract '{}' but no tracks available; "
                    + "skipping.", contract.getName());
            return;
        }

        int formationsAdded = StratConOpForRosterBuilder.addReinforcementFormations(
                campaign, contract, roster, targetTrack, formationCount);

        if (formationsAdded > 0) {
            roster.incrementReinforcementEventsFired();
            String report = String.format(
                    "Intel reports enemy reinforcements landing in %s — %d additional formation%s engaged on %s.",
                    targetTrack.getDisplayableName(),
                    formationsAdded,
                    formationsAdded == 1 ? "" : "s",
                    contract.getName());
            campaign.addReport(DailyReportType.BATTLE, report);
            LOGGER.info(report);
            MekHQ.triggerEvent(new OpForRosterChangedEvent(targetTrack));
        }
    }

    /**
     * Picks a track to receive the reinforcement: the most-attrited track among
     * the contract's tracks, falling back to a weighted-random pick when no
     * destruction has happened yet.
     */
    private static StratConTrackState pickTargetTrack(final StratConOpForRoster roster,
            final StratConCampaignState campaignState) {
        List<StratConTrackState> tracks = campaignState.getTracks();
        if (tracks == null || tracks.isEmpty()) {
            return null;
        }
        // Build candidate name list (skip pacified tracks — no point reinforcing them)
        List<String> candidateNames = new ArrayList<>();
        List<StratConTrackState> candidates = new ArrayList<>();
        for (StratConTrackState track : tracks) {
            if (!track.isPacified()) {
                candidateNames.add(track.getDisplayableName());
                candidates.add(track);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        String mostAttrited = roster.mostAttritedTrack(candidateNames);
        if (mostAttrited != null) {
            for (StratConTrackState track : candidates) {
                if (mostAttrited.equals(track.getDisplayableName())) {
                    return track;
                }
            }
        }
        // Fallback: weighted random by required lance count
        int totalWeight = 0;
        for (StratConTrackState track : candidates) {
            totalWeight += Math.max(1, track.getRequiredLanceCount());
        }
        int rollWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int accumulated = 0;
        for (StratConTrackState track : candidates) {
            accumulated += Math.max(1, track.getRequiredLanceCount());
            if (rollWeight < accumulated) {
                return track;
            }
        }
        return candidates.get(candidates.size() - 1);
    }
}
