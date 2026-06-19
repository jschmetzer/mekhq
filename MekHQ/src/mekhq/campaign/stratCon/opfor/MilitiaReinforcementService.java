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
import mekhq.campaign.stratCon.opfor.ContractTypeMilitiaReinforcementProfile.MilitiaProfile;

/**
 * Adds planetary-militia formations to the static OpFor roster when the contract's
 * morale shifts upward (enemy ascendant) to at least a contract-type-specific
 * threshold — the local defenders mobilize in greater numbers while winning.
 *
 * <p>Militia formations are flagged via {@link StratConOpForFormation#isMilitia()} and
 * are excluded from the contract win-condition check ({@code livingLineUnits}). They use
 * their own event counter ({@code militiaReinforcementEventsFired}) so they do not
 * compete with the line-OpFor reinforcement budget.</p>
 *
 * <p>Only fires for attacker contracts in StratCon mode when the
 * {@code useStaticOpForMilitia} campaign option is enabled.</p>
 *
 * <p>Couples to {@code AtBContract.checkMorale} via a per-monthly hook fired from
 * {@code CampaignNewDayManager}.</p>
 */
public final class MilitiaReinforcementService {

    private static final MMLogger LOGGER = MMLogger.create(MilitiaReinforcementService.class);

    private MilitiaReinforcementService() {
    }

    /**
     * Deterministic eligibility gate for a militia reinforcement attempt — everything
     * except the probability roll. Militia reinforce while the defender is
     * <em>ascendant</em>: contract morale must shift <strong>upward</strong> this month
     * and reach at least the profile's trigger threshold.
     *
     * @param profile     the militia reinforcement profile for this contract type
     * @param oldMorale   morale before this month's check
     * @param newMorale   morale after this month's check
     * @param eventsFired militia reinforcement events already fired this contract
     * @return {@code true} iff a militia reinforcement roll should be attempted
     */
    static boolean shouldAttemptReinforcement(final MilitiaProfile profile,
            final AtBMoraleLevel oldMorale, final AtBMoraleLevel newMorale,
            final int eventsFired) {
        if (!profile.isReinforcementAllowed()) {
            return false;
        }
        if (eventsFired >= profile.eventCap()) {
            return false;
        }
        if ((oldMorale == null) || (newMorale == null)) {
            return false;
        }
        // Use getLevel() (the explicit -3..+3 semantic field) rather than ordinal()
        // so the directional comparisons survive any future enum-value insertion.
        if (newMorale.getLevel() <= oldMorale.getLevel()) {
            return false;  // morale must rise (defender winning) to commit militia
        }
        // Current morale must be at or above the profile's (high) threshold
        return newMorale.getLevel() >= profile.triggerThreshold().getLevel();
    }

    /**
     * Considers firing a militia reinforcement event for the given contract.
     *
     * <p>No-ops when:</p>
     * <ul>
     *   <li>the contract has no StratCon campaign state (legacy / dynamic mode),</li>
     *   <li>the contract has no static OpFor roster,</li>
     *   <li>the {@code useStaticOpForMilitia} campaign option is disabled,</li>
     *   <li>the contract is not an attacker contract,</li>
     *   <li>the contract type has no militia profile,</li>
     *   <li>the militia event cap has been reached,</li>
     *   <li>morale has not shifted upward this month, or</li>
     *   <li>current morale is below the trigger threshold.</li>
     * </ul>
     *
     * <p>When eligible, rolls against the profile probability and on success
     * generates the appropriate number of militia formations, attaches them to
     * the most attrited track (or a weighted-random track as fallback), and posts
     * a campaign report describing the mobilization.</p>
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

        if (!campaign.getCampaignOptions().isUseStaticOpForMilitia()) {
            return;
        }

        if (!contract.isAttacker()) {
            return;
        }

        StratConOpForRoster roster = campaignState.getOpForRoster();
        if (roster == null) {
            return;
        }

        MilitiaProfile profile =
                ContractTypeMilitiaReinforcementProfile.getProfile(contract.getContractType());
        if (!shouldAttemptReinforcement(profile, oldMorale, newMorale,
                roster.getMilitiaReinforcementEventsFired())) {
            return;
        }

        // Roll
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll >= profile.probability()) {
            LOGGER.info("Militia reinforcement check for contract '{}': morale {} (was {}), "
                    + "threshold {} met, but roll {} >= prob {}, no reinforcement.",
                    contract.getName(), newMorale, oldMorale,
                    profile.triggerThreshold(), roll, profile.probability());
            return;
        }

        int formationCount = ThreadLocalRandom.current().nextInt(
                profile.minFormations(), profile.maxFormations() + 1);
        StratConTrackState targetTrack = pickTargetTrack(roster, campaignState);
        if (targetTrack == null) {
            LOGGER.warn("Militia reinforcement triggered for contract '{}' but no tracks available; "
                    + "skipping.", contract.getName());
            return;
        }

        int formationsAdded = StratConOpForRosterBuilder.addMilitiaReinforcementFormations(
                campaign, contract, roster, targetTrack, formationCount);

        if (formationsAdded > 0) {
            roster.incrementMilitiaReinforcementEventsFired();
            String report = String.format(
                    "Planetary militia mobilizing in %s — %d additional militia formation%s "
                            + "engaged on %s.",
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
     * Picks a track to receive militia reinforcements: the most-attrited track among
     * the contract's tracks, falling back to a weighted-random pick when no
     * destruction has happened yet.
     */
    private static StratConTrackState pickTargetTrack(final StratConOpForRoster roster,
            final StratConCampaignState campaignState) {
        List<StratConTrackState> tracks = campaignState.getTracks();
        if (tracks == null || tracks.isEmpty()) {
            return null;
        }
        // Build candidate list (skip pacified tracks — no point reinforcing them)
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
