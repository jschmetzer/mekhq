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
 * Adds reinforcement formations to the static allied roster when contract
 * morale shifts upward (the enemy is winning, the player is losing) past a
 * contract-type-specific threshold. Couples to {@code AtBContract.checkMorale}
 * via the same monthly hook in {@code CampaignNewDayManager}.
 *
 * <p>Symmetric to {@link OpForReinforcementService} but mirrored across the
 * morale axis: ally reinforcements fire on <em>upward</em> shifts and require
 * {@code newMorale >= triggerThreshold} (not below).</p>
 *
 * <p>New ally formations are stamped at {@link IntelLevel#FULL_INTEL} the same
 * way the initial ally roster is — the employer's monthly briefing tells the
 * player about incoming support before they arrive.</p>
 */
public final class AllyReinforcementService {

    private static final MMLogger LOGGER = MMLogger.create(AllyReinforcementService.class);

    private AllyReinforcementService() {
    }

    /**
     * Considers firing an allied reinforcement event for the given contract.
     *
     * <p>No-ops when the contract has no allied roster, the contract type has
     * no profile, the cap is reached, morale has not shifted upward, or
     * current morale is below the trigger threshold. When eligible, rolls
     * against the profile probability and on success generates formations,
     * attaches them to a track, and posts a campaign report.</p>
     *
     * @param campaign  the active campaign
     * @param contract  the AtB contract to consider
     * @param oldMorale the morale level before this month's check
     * @param newMorale the morale level after this month's check
     */
    public static void maybeReinforce(final Campaign campaign,
            final AtBContract contract,
            final AtBMoraleLevel oldMorale,
            final AtBMoraleLevel newMorale) {

        StratConCampaignState campaignState = contract.getStratconCampaignState();
        if (campaignState == null) {
            return;
        }
        StratConOpForRoster roster = campaignState.getAlliedRoster();
        if (roster == null) {
            return;
        }

        ContractTypeReinforcementProfile.Profile profile =
                ContractTypeAllyReinforcementProfile.getProfile(contract.getContractType());
        if (!profile.isReinforcementAllowed()) {
            return;
        }
        if (roster.getReinforcementEventsFired() >= profile.eventCap()) {
            return;
        }

        // Trigger requires morale to have shifted UPWARD (enemy winning more)
        if (oldMorale == null || newMorale == null) {
            return;
        }
        // Use getLevel() (the explicit -3..+3 semantic field) rather than ordinal()
        // so the directional comparisons survive any future enum-value insertion.
        if (newMorale.getLevel() <= oldMorale.getLevel()) {
            return;
        }
        // Current morale must be at or above the profile's threshold
        if (newMorale.getLevel() < profile.triggerThreshold().getLevel()) {
            return;
        }

        // Roll
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll >= profile.probability()) {
            LOGGER.info("Ally reinforcement check for contract '{}': morale {} (was {}), "
                    + "above threshold {}, roll {} >= prob {}, no reinforcement.",
                    contract.getName(), newMorale, oldMorale,
                    profile.triggerThreshold(), roll, profile.probability());
            return;
        }

        int formationCount = ThreadLocalRandom.current().nextInt(
                profile.minFormations(), profile.maxFormations() + 1);
        StratConTrackState targetTrack = pickTargetTrack(roster, campaignState);
        if (targetTrack == null) {
            LOGGER.warn("Ally reinforcement triggered for contract '{}' but no tracks available; "
                    + "skipping.", contract.getName());
            return;
        }

        // Use the same builder entry point as OpFor reinforcements but with the
        // EMPLOYER faction and ally skill/quality. We materialise this through a
        // small ally-specific helper so the bot identity is right downstream.
        int formationsAdded = StratConOpForRosterBuilder.addAllyReinforcementFormations(
                campaign, contract, roster, targetTrack, formationCount);

        if (formationsAdded > 0) {
            roster.incrementReinforcementEventsFired();
            String report = String.format(
                    "Employer reports allied reinforcements landing in %s — %d additional friendly formation%s on %s.",
                    targetTrack.getDisplayableName(),
                    formationsAdded,
                    formationsAdded == 1 ? "" : "s",
                    contract.getName());
            campaign.addReport(DailyReportType.BATTLE, report);
            LOGGER.info(report);
            MekHQ.triggerEvent(new OpForRosterChangedEvent(targetTrack));
        }
    }

    /** Picks a track for the ally reinforcement: weighted random by track size. */
    private static StratConTrackState pickTargetTrack(final StratConOpForRoster roster,
            final StratConCampaignState campaignState) {
        List<StratConTrackState> tracks = campaignState.getTracks();
        if (tracks == null || tracks.isEmpty()) {
            return null;
        }
        List<StratConTrackState> candidates = new ArrayList<>();
        for (StratConTrackState track : tracks) {
            if (!track.isPacified()) {
                candidates.add(track);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
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
