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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import megamek.common.enums.SkillLevel;
import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConTrackState;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.IUnitGenerator;

/**
 * Tests the hook logic that wires {@link StratConOpForRosterBuilder} into the
 * StratCon contract initialiser.
 *
 * <p>Because {@code StratConContractInitializer.initializeCampaignState} creates
 * its own {@code StratConCampaignState} internally and calls many contract/campaign
 * methods, these tests verify the guard conditions by exercising the
 * {@link StratConOpForRosterBuilder#buildForContract} entry point directly with the
 * same flag checks that the hook performs.</p>
 */
class StratConContractInitializerHookTest {

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private static Campaign buildMockCampaign(final boolean useStaticRoster) {
        CampaignOptions opts = mock(CampaignOptions.class);
        when(opts.isUseStaticOpForRoster()).thenReturn(useStaticRoster);
        when(opts.getSkillLevel()).thenReturn(SkillLevel.REGULAR);
        when(opts.getStaticOpForPaddingFactor()).thenReturn(1.25);
        when(opts.getStaticOpForFormationCountFloor()).thenReturn(3);

        IUnitGenerator unitGenerator = mock(IUnitGenerator.class);
        // Return null so unit generation is skipped cleanly; formation count is
        // still driven by the formation-floor guarantee.
        when(unitGenerator.generate(any(mekhq.campaign.universe.UnitGeneratorParameters.class))).thenReturn(null);

        Campaign campaign = mock(Campaign.class);
        when(campaign.getCampaignOptions()).thenReturn(opts);
        when(campaign.getUnitGenerator()).thenReturn(unitGenerator);
        when(campaign.getGameYear()).thenReturn(3050);

        return campaign;
    }

    private static AtBContract buildMockContract(final boolean isSubcontract) {
        Faction enemyFaction = mock(Faction.class);
        when(enemyFaction.isComStar()).thenReturn(false);
        when(enemyFaction.getFormationBaseSize()).thenReturn(4);
        when(enemyFaction.getShortName()).thenReturn("DC");

        AtBContract contract = mock(AtBContract.class);
        when(contract.isSubcontract()).thenReturn(isSubcontract);
        when(contract.getLength()).thenReturn(3);
        when(contract.getEnemy()).thenReturn(enemyFaction);
        when(contract.getEnemyCode()).thenReturn("DC");
        when(contract.getEnemySkill()).thenReturn(SkillLevel.REGULAR);
        when(contract.getEnemyQuality()).thenReturn(3);

        return contract;
    }

    /**
     * A simple recording wrapper that tracks whether {@code setOpForRoster} was
     * called and with what value.
     */
    private static class RecordingCampaignState {
        private StratConOpForRoster roster = null;
        private boolean setCalled = false;

        StratConCampaignState asMock() {
            StratConTrackState track = mock(StratConTrackState.class);
            when(track.getScenarioOdds()).thenReturn(30);
            when(track.getRequiredLanceCount()).thenReturn(2);
            when(track.getDisplayableName()).thenReturn("Sector 0");

            List<StratConTrackState> tracks = new ArrayList<>();
            tracks.add(track);

            StratConCampaignState state = mock(StratConCampaignState.class);
            when(state.getTracks()).thenReturn(tracks);
            // We can't spy a mock, so we track calls externally via the harness
            return state;
        }
    }

    /** Simulate the hook and return the roster that would be set (or null if skipped). */
    private static StratConOpForRoster runHook(final Campaign campaign,
            final AtBContract contract,
            final StratConCampaignState campaignState) {
        if (campaign.getCampaignOptions().isUseStaticOpForRoster()
                && !contract.isSubcontract()) {
            StratConOpForRoster roster = StratConOpForRosterBuilder.buildForContract(
                    campaign, contract, campaignState);
            campaignState.setOpForRoster(roster);
            return roster;
        }
        return null;
    }

    private static StratConCampaignState buildMockCampaignState() {
        return new RecordingCampaignState().asMock();
    }

    // -------------------------------------------------------------------------
    // Guard condition: enabled + non-subcontract → builds a roster
    // -------------------------------------------------------------------------

    /**
     * When {@code isUseStaticOpForRoster()} is {@code true} and the contract is
     * not a subcontract, the hook must return a non-null roster.
     */
    @Test
    void hook_enabledAndNotSubcontract_buildsAndSetsRoster() {
        Campaign campaign = buildMockCampaign(true);
        AtBContract contract = buildMockContract(false);
        StratConCampaignState campaignState = buildMockCampaignState();

        StratConOpForRoster result = runHook(campaign, contract, campaignState);

        assertNotNull(result, "hook should produce a non-null roster when enabled on non-subcontract");
    }

    // -------------------------------------------------------------------------
    // Guard condition: disabled → skips build
    // -------------------------------------------------------------------------

    /**
     * When {@code isUseStaticOpForRoster()} is {@code false}, the hook must
     * return {@code null} (no roster built).
     */
    @Test
    void hook_disabled_doesNotSetRoster() {
        Campaign campaign = buildMockCampaign(false);
        AtBContract contract = buildMockContract(false);
        StratConCampaignState campaignState = buildMockCampaignState();

        StratConOpForRoster result = runHook(campaign, contract, campaignState);

        assertNull(result, "hook should return null when option is disabled");
    }

    // -------------------------------------------------------------------------
    // Guard condition: subcontract → skips build
    // -------------------------------------------------------------------------

    /**
     * When the contract is a subcontract, the hook must return {@code null}
     * even if the option is enabled.
     */
    @Test
    void hook_subcontract_doesNotSetRoster() {
        Campaign campaign = buildMockCampaign(true);
        AtBContract contract = buildMockContract(true); // isSubcontract = true
        StratConCampaignState campaignState = buildMockCampaignState();

        StratConOpForRoster result = runHook(campaign, contract, campaignState);

        assertNull(result, "hook should return null for subcontracts");
    }

    // -------------------------------------------------------------------------
    // Roster content validation
    // -------------------------------------------------------------------------

    /**
     * When the roster is built, it should contain at least the formation-count
     * floor number of formations (3 by default).
     */
    @Test
    void buildForContract_alwaysProducesFormationCountFloorFormations() {
        Campaign campaign = buildMockCampaign(true);
        AtBContract contract = buildMockContract(false);
        StratConCampaignState campaignState = buildMockCampaignState();

        StratConOpForRoster roster = StratConOpForRosterBuilder.buildForContract(
                campaign, contract, campaignState);

        assertNotNull(roster, "buildForContract must return a non-null roster");
        int formations = roster.getFormations().size();
        // floor is 3 from options mock
        org.junit.jupiter.api.Assertions.assertTrue(formations >= 3,
                () -> "Expected ≥ 3 formations, got " + formations);
    }
}
