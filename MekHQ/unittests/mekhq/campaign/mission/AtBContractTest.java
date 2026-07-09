/*
 * Copyright (C) 2020-2026 The MegaMek Team. All Rights Reserved.
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
package mekhq.campaign.mission;

import static mekhq.campaign.universe.Faction.MERCENARY_FACTION_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;
import java.util.Vector;
import java.util.stream.Stream;

import megamek.client.generator.RandomCallsignGenerator;
import megamek.common.enums.Gender;
import megamek.common.equipment.EquipmentType;
import megamek.common.units.Entity;
import megamek.common.units.UnitType;
import mekhq.campaign.Campaign;
import mekhq.campaign.Hangar;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.force.CombatTeam;
import mekhq.campaign.force.Formation;
import mekhq.campaign.force.FormationLevel;
import mekhq.campaign.force.FormationType;
import mekhq.campaign.mission.AtBContract.AtBContractRef;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.CombatRole;
import mekhq.campaign.mission.utilities.ContractUtilities;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.backgrounds.RandomCompanyNameGenerator;
import mekhq.campaign.personnel.enums.PersonnelRole;
import mekhq.campaign.personnel.ranks.RankSystem;
import mekhq.campaign.personnel.ranks.RankValidator;
import mekhq.campaign.personnel.ranks.Ranks;
import mekhq.campaign.unit.Unit;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.Systems;
import mekhq.campaign.universe.TestSystems;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AtBContractTest {
    private AtBContract contract;
    private Campaign campaign;
    private CampaignOptions options;

    @BeforeAll
    public static void initSingletons() {
        EquipmentType.initializeTypes();
        // TODO: fix this in the production code
        RandomCallsignGenerator.getInstance(true); // Required in this code path to generate a random merc company name
        RandomCompanyNameGenerator.getInstance(); // Required in this code path to generate a random merc company name
        Ranks.initializeRankSystems(); // Faction.getRankSystem() reads this static registry
        try {
            Factions.setInstance(Factions.loadDefault(true));
            Systems.setInstance(TestSystems.loadDefault());
        } catch (Exception ex) {
            LogManager.getLogger().error("", ex);
        }
    }

    @BeforeEach
    void setup() {
        campaign = mock(Campaign.class);
        options = mock(CampaignOptions.class);
        when(campaign.getCampaignOptions()).thenReturn(options);
        contract = new AtBContract();
    }

    @Test
    public void atbContractRestoreDoesNothingWithoutParent() {
        Campaign mockCampaign = mock(Campaign.class);

        int childId = 2;
        AtBContract child = new AtBContract();
        child.setId(childId);
        child.setParentContract(null);

        // Restore the AtBContract
        child.restore(mockCampaign);

        verify(mockCampaign, times(0)).getMission(anyInt());

        // Ensure the parent is not set
        assertNull(child.getParentContract());
    }

    @Test
    public void atbContractRestoresRefs() {
        Campaign mockCampaign = mock(Campaign.class);

        int parentId = 1;
        AtBContract parent = mock(AtBContract.class);
        when(parent.getId()).thenReturn(parentId);
        doReturn(parent).when(mockCampaign).getMission(eq(parentId));

        int childId = 2;
        AtBContract child = new AtBContract();
        child.setId(childId);
        child.setParentContract(new AtBContractRef(parentId));
        doReturn(child).when(mockCampaign).getMission(eq(childId));

        int otherId = 3;
        AtBContract other = mock(AtBContract.class);
        when(other.getId()).thenReturn(otherId);
        doReturn(other).when(mockCampaign).getMission(eq(otherId));

        // Restore the AtBContract
        child.restore(mockCampaign);

        // Ensure the parent is set properly
        assertEquals(parent, child.getParentContract());
    }

    @Test
    public void atbContractRestoreClearsParentIfMissing() {
        Campaign mockCampaign = mock(Campaign.class);

        int parentId = 1;
        doReturn(null).when(mockCampaign).getMission(eq(parentId));

        int childId = 2;
        AtBContract child = new AtBContract();
        child.setId(childId);
        child.setParentContract(new AtBContractRef(parentId));
        doReturn(child).when(mockCampaign).getMission(eq(childId));

        int otherId = 3;
        AtBContract other = mock(AtBContract.class);
        when(other.getId()).thenReturn(otherId);
        doReturn(other).when(mockCampaign).getMission(eq(otherId));

        // Restore the AtBContract
        child.restore(mockCampaign);

        // Ensure the parent is null because it is missing
        assertNull(child.getParentContract());
    }

    @Test
    public void atbContractRestoreClearsParentIfWrongType() {
        Campaign mockCampaign = mock(Campaign.class);

        int parentId = 1;
        Contract parent = mock(Contract.class);
        when(parent.getId()).thenReturn(parentId);
        doReturn(parent).when(mockCampaign).getMission(eq(parentId));

        int childId = 2;
        AtBContract child = new AtBContract();
        child.setId(childId);
        child.setParentContract(new AtBContractRef(parentId));
        doReturn(child).when(mockCampaign).getMission(eq(childId));

        int otherId = 3;
        AtBContract other = mock(AtBContract.class);
        when(other.getId()).thenReturn(otherId);
        doReturn(other).when(mockCampaign).getMission(eq(otherId));

        // Restore the AtBContract
        child.restore(mockCampaign);

        // Ensure the parent is null because it is not the correct type of contract
        assertNull(child.getParentContract());
    }

    @Test
    public void atbContractSharesPercentMatchesPreviousSetting() {
        AtBContract contract = new AtBContract("Test");
        contract.setSharesPercent(50);
        assertEquals(50, contract.getSharesPercent());
    }

    @Test
    public void setContractTypeUpdatesParentMissionType() {
        contract.setContractTypeAndName(AtBContractType.CADRE_DUTY);
        assertEquals(AtBContractType.CADRE_DUTY, contract.getContractType());
        assertEquals("Cadre Duty", contract.getContractTypeName());
    }

    private static Stream<Arguments> provideEnemyFactionAndYear() {
        return Stream.of(Arguments.of(3025, "LA", "Lyran Commonwealth"),
              Arguments.of(3059, "LA", "Lyran Alliance"),
              Arguments.of(-1, "LA", "Lyran Commonwealth"),
              Arguments.of(3025, "??", "Unknown"));
    }

    @ParameterizedTest
    @MethodSource("provideEnemyFactionAndYear")
    public void generateEnemyNameReturnsCorrectValueInYear(int year, String enemyCode, String fullName) {
        contract.setEnemyCode(enemyCode);
        assertEquals(fullName, contract.generateEnemyName(year));
    }

    @Test
    public void generateEnemyNameReturnsCorrectValueWhenMerc() {
        String name = "Testing Merc";
        contract.setEnemyCode(MERCENARY_FACTION_CODE);
        contract.setEnemyBotName(name);
        assertEquals(name, contract.generateEnemyName(3025));
    }

    @Test
    public void generateEnemyNameReturnsNonNullWhenMercAndBotNameNotSet() {
        contract.setEnemyCode("MERC");
        assertNotEquals("", contract.generateEnemyName(3025));
    }

    private static Stream<Arguments> provideEmployerNamesAndMercStatus() {
        return Stream.of(Arguments.of(3025, false, "LA", "Lyran Commonwealth"),
              Arguments.of(3059, false, "LA", "Lyran Alliance"),
              Arguments.of(3025, true, "LA", "Mercenary (Lyran Commonwealth)"),
              Arguments.of(3059, true, "LA", "Mercenary (Lyran Alliance)"),
              Arguments.of(-1, true, "LA", "Mercenary (Lyran Commonwealth)"),
              Arguments.of(3025, true, "??", "Mercenary (Unknown)"));
    }

    @ParameterizedTest
    @MethodSource("provideEmployerNamesAndMercStatus")
    public void getEmployerNameReturnsCorrectName(int year, boolean isMercSubcontract, String employerCode,
          String fullName) {
        contract.updateEmployer(employerCode, year);
        contract.setMercSubcontract(isMercSubcontract);
        assertEquals(fullName, contract.getEmployerName(year));
    }

    @Nested
    class AtBContractCalculateRequiredLancesTests {
        int nextForceId;

        Faction mockFaction;
        Campaign mockCampaign;

        Hangar hangar;

        public static Stream<Arguments> getFormationSizesForTests() {
            return Stream.of(
                  //Arguments.of(3), //Society?
                  Arguments.of(CombatTeam.LANCE_SIZE),
                  Arguments.of(CombatTeam.STAR_SIZE),
                  Arguments.of(CombatTeam.LEVEL_II_SIZE)
            );
        }

        @BeforeEach
        void beforeEach() {
            nextForceId = 0;
            hangar = new Hangar();

            mockFaction = mock(Faction.class);
            mockCampaign = mock(Campaign.class);

            when(mockCampaign.getFaction()).thenReturn(mockFaction);
            when(mockCampaign.getHangar()).thenReturn(hangar);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testNoForces(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(1, teams);
            assertEquals(1, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testOneLance(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            mockedCombatTeams.add(getMockLanceCombatTeam(formationSize));

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(1, teams);
            assertEquals(formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testThreeLances(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                mockedCombatTeams.add(getMockLanceCombatTeam(formationSize));
            }

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(3, teams);
            assertEquals(3 * formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testNineLances(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                mockedCombatTeams.add(getMockLanceCombatTeam(formationSize));
            }

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(9, teams);
            assertEquals(9 * formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testOneCompany(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            mockedCombatTeams.add(getMockCompanyCombatTeam(formationSize));

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(1, teams);
            assertEquals(3 * formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testThreeCompanies(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                mockedCombatTeams.add(getMockCompanyCombatTeam(formationSize));
            }

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(3, teams);
            assertEquals(3 * 3 * formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testNineCompanies(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                mockedCombatTeams.add(getMockCompanyCombatTeam(formationSize));
            }

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(9, teams);
            assertEquals(9 * 3 * formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testOneLanceAndOneCompany(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            mockedCombatTeams.add(getMockLanceCombatTeam(formationSize));
            mockedCombatTeams.add(getMockCompanyCombatTeam(formationSize));

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(2, teams);
            assertEquals(4 * formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testThreeLanceAndOneCompany(int formationSize) {
            // Arrange
            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                mockedCombatTeams.add(getMockLanceCombatTeam(formationSize));
            }
            mockedCombatTeams.add(getMockCompanyCombatTeam(formationSize));

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(4, teams);
            assertEquals(6 * formationSize, requiredUnits);
        }

        @ParameterizedTest
        @MethodSource(value = "getFormationSizesForTests")
        void testLancesWithTeams(int formationSize) {
            // Arrange
            int forceId = getNextForceId();

            Vector<Object> mockUnits = new Vector<>();
            Vector<UUID> mockUUIDs = new Vector<>();
            for (int i = 0; i < 2; i++) {
                Unit mockUnit = getMockUnit(UnitType.MEK);
                mockUnits.add(mockUnit);
                mockUUIDs.add(mockUnit.getId());
            }

            Formation mockFormation = mock(Formation.class);
            when(mockFormation.getId()).thenReturn(forceId);
            when(mockFormation.isFormationType(FormationType.STANDARD)).thenReturn(true);
            when(mockFormation.getFormationLevel()).thenReturn(FormationLevel.INVALID);
            when(mockFormation.getAllChildren(mockCampaign)).thenReturn(mockUnits);
            when(mockFormation.getAllUnits(anyBoolean())).thenReturn(mockUUIDs);
            when(mockFormation.getCombatRoleInMemory()).thenReturn(CombatRole.FRONTLINE);

            forceId = getNextForceId();

            Vector<Object> mockUnits2 = new Vector<>();
            Vector<UUID> mockUUIDs2 = new Vector<>();
            for (int i = 0; i < 2; i++) {
                Unit mockUnit = getMockUnit(UnitType.MEK);
                mockUnits2.add(mockUnit);
                mockUUIDs2.add(mockUnit.getId());
            }

            Formation mockFormation2 = mock(Formation.class);
            when(mockFormation2.getId()).thenReturn(forceId);
            when(mockFormation2.isFormationType(FormationType.STANDARD)).thenReturn(true);
            when(mockFormation2.getFormationLevel()).thenReturn(FormationLevel.INVALID);
            when(mockFormation2.getAllChildren(mockCampaign)).thenReturn(mockUnits2);
            when(mockFormation2.getAllUnits(anyBoolean())).thenReturn(mockUUIDs2);
            when(mockFormation2.getCombatRoleInMemory()).thenReturn(CombatRole.FRONTLINE);

            forceId = getNextForceId();

            Vector<UUID> allMockUUIDs = new Vector<>();
            allMockUUIDs.addAll(mockUUIDs);
            allMockUUIDs.addAll(mockUUIDs2);

            Vector<Object> allForces = new Vector<>();
            allForces.add(mockFormation);
            allForces.add(mockFormation2);

            Formation finalFormation = mock(Formation.class);
            when(finalFormation.getId()).thenReturn(forceId);
            when(finalFormation.isFormationType(FormationType.STANDARD)).thenReturn(true);
            when(finalFormation.getFormationLevel()).thenReturn(FormationLevel.LANCE);
            when(finalFormation.getAllChildren(mockCampaign)).thenReturn(allForces);
            when(finalFormation.getAllUnits(anyBoolean())).thenReturn(allMockUUIDs);
            when(finalFormation.getCombatRoleInMemory()).thenReturn(CombatRole.FRONTLINE);

            forceId = getNextForceId();

            CombatTeam mockLanceCombatTeam = mock(CombatTeam.class);
            when(mockLanceCombatTeam.getSize(mockCampaign)).thenReturn(4);
            when(mockLanceCombatTeam.getFormation(mockCampaign)).thenReturn(finalFormation);
            when(mockLanceCombatTeam.getFormationId()).thenReturn(forceId);

            ArrayList<CombatTeam> mockedCombatTeams = new ArrayList<>();
            mockedCombatTeams.add(mockLanceCombatTeam);

            when(mockCampaign.getCombatTeamsAsList()).thenReturn(mockedCombatTeams);

            // Act
            int teams = ContractUtilities.calculateBaseNumberOfRequiredLances(mockCampaign, false, true, 1.0);
            int requiredUnits = ContractUtilities.calculateBaseNumberOfUnitsRequiredInCombatTeams(mockCampaign);
            // Assert
            assertEquals(1, teams);
            assertEquals(4, requiredUnits);
        }


        /**
         * Lance-level formation, not necessarily a lance
         *
         * @param formationSize number of units in the formation
         *
         * @return A mocked CombatTeam of the desired size
         */
        private CombatTeam getMockLanceCombatTeam(int formationSize) {
            Formation mockFormation = getMockLanceForce(formationSize);
            int forceId = mockFormation.getId();

            when(mockFormation.getCombatRoleInMemory()).thenReturn(CombatRole.FRONTLINE);

            CombatTeam mockLance = mock(CombatTeam.class);
            when(mockLance.getSize(mockCampaign)).thenReturn(formationSize);
            when(mockLance.getFormation(mockCampaign)).thenReturn(mockFormation);
            when(mockLance.getFormationId()).thenReturn(forceId);
            return mockLance;
        }

        /**
         * Lance-level formation, not necessarily a lance
         *
         * @param formationSize number of units in the formation
         *
         * @return A mocked Force of the desired size
         */
        private Formation getMockLanceForce(int formationSize) {
            int forceId = getNextForceId();

            Vector<Object> mockUnits = new Vector<>();
            Vector<UUID> mockUUIDs = new Vector<>();
            for (int i = 0; i < formationSize; i++) {
                Unit mockUnit = getMockUnit(UnitType.MEK);
                mockUnits.add(mockUnit);
                mockUUIDs.add(mockUnit.getId());
            }

            Formation mockFormation = mock(Formation.class);
            when(mockFormation.getId()).thenReturn(forceId);
            when(mockFormation.isFormationType(FormationType.STANDARD)).thenReturn(true);
            when(mockFormation.getFormationLevel()).thenReturn(FormationLevel.LANCE);
            when(mockFormation.getAllChildren(mockCampaign)).thenReturn(mockUnits);
            when(mockFormation.getAllUnits(anyBoolean())).thenReturn(mockUUIDs);
            return mockFormation;
        }

        private CombatTeam getMockCompanyCombatTeam(int formationSize) {
            Formation mockFormation = getMockCompanyForce(formationSize);
            int forceId = mockFormation.getId();
            CombatTeam mockCompany = mock(CombatTeam.class);

            when(mockCompany.getSize(mockCampaign)).thenReturn(formationSize * 3);
            when(mockCompany.getFormation(mockCampaign)).thenReturn(mockFormation);
            when(mockFormation.getCombatRoleInMemory()).thenReturn(CombatRole.FRONTLINE);
            when(mockCompany.getFormationId()).thenReturn(forceId);

            return mockCompany;
        }

        private Formation getMockCompanyForce(int formationSize) {
            int forceId = getNextForceId();
            Formation mockCompany = mock(Formation.class);

            Vector<Object> subForces = new Vector<>();
            subForces.add(getMockLanceForce(formationSize));
            subForces.add(getMockLanceForce(formationSize));
            subForces.add(getMockLanceForce(formationSize));

            Vector<UUID> mockUUIDs = new Vector<>();
            for (Object subForce : subForces) {
                if (subForce instanceof Formation formation) {
                    mockUUIDs.addAll(formation.getAllUnits(true));
                }
            }

            when(mockCompany.getId()).thenReturn(forceId);
            when(mockCompany.isFormationType(FormationType.STANDARD)).thenReturn(true);
            when(mockCompany.getFormationLevel()).thenReturn(FormationLevel.COMPANY);
            when(mockCompany.getAllChildren(mockCampaign)).thenReturn(subForces);
            when(mockCompany.getAllUnits(anyBoolean())).thenReturn(mockUUIDs);
            when(mockCompany.getCombatRoleInMemory()).thenReturn(CombatRole.FRONTLINE);

            return mockCompany;
        }

        private Unit getMockUnit(int unitType) {
            Entity mockEntity = getMockEntity(unitType);

            UUID uuid = UUID.randomUUID();

            Unit mockUnit = mock(Unit.class);
            when(mockUnit.getEntity()).thenReturn(mockEntity);
            when(mockUnit.getId()).thenReturn(uuid);

            hangar.addUnit(mockUnit);

            return mockUnit;
        }

        private Entity getMockEntity(int unitType) {
            Entity mockEntity = mock(Entity.class);
            when(mockEntity.getUnitType()).thenReturn(unitType);

            return mockEntity;
        }

        private int getNextForceId() {
            return ++nextForceId;
        }
    }

    @org.junit.jupiter.api.Nested
    class EmployerLiaisonTests {

        @Test
        void createEmployerLiaison_appliesEmployerFactionRankSystem_notTheCampaignDefault() {
            // The liaison speaks for the employer, so their rank must render in the employer's
            // rank system. Without this a Kurita employer's liaison shows the player's ranks.
            Person liaison = mock(Person.class);
            Campaign campaign = mock(Campaign.class);
            when(campaign.newPerson(any(PersonnelRole.class), anyString(), any(Gender.class))).thenReturn(liaison);

            AtBContract contract = new AtBContract();
            contract.setEmployerCode("DC");
            contract.createEmployerLiaison(campaign);

            RankSystem employerRanks = Factions.getInstance().getFaction("DC").getRankSystem();
            verify(liaison).setRankSystem(any(RankValidator.class), eq(employerRanks));
        }
    }

    @org.junit.jupiter.api.Nested
    class RosterAccessorTests {

        /**
         * AtBContract.loadFieldsFromXmlNode creates the employer liaison, which dereferences the Person returned by
         * Campaign.newPerson. A bare mock returns null there, which the real campaign never does.
         */
        private Campaign campaignThatCanCreateALiaison() {
            Campaign campaign = mock(Campaign.class);
            when(campaign.newPerson(any(PersonnelRole.class), anyString(), any(Gender.class)))
                    .thenReturn(mock(Person.class));
            return campaign;
        }

        @Test
        void getOpForRoster_returnsNull_whenNothingSet() {
            AtBContract contract = new AtBContract();
            org.junit.jupiter.api.Assertions.assertNull(contract.getOpForRoster());
            org.junit.jupiter.api.Assertions.assertNull(contract.getAlliedRoster());
        }

        @Test
        void getOpForRoster_returnsAtbField_whenCampaignStateNull() {
            AtBContract contract = new AtBContract();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster roster =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            contract.setAtbOpForRoster(roster);

            org.junit.jupiter.api.Assertions.assertSame(roster, contract.getOpForRoster());
            org.junit.jupiter.api.Assertions.assertNull(contract.getAlliedRoster());
        }

        @Test
        void getAlliedRoster_returnsAtbField_whenCampaignStateNull() {
            AtBContract contract = new AtBContract();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster ally =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            contract.setAtbAlliedRoster(ally);

            org.junit.jupiter.api.Assertions.assertSame(ally, contract.getAlliedRoster());
            org.junit.jupiter.api.Assertions.assertNull(contract.getOpForRoster());
        }

        @Test
        void getOpForRoster_prefersCampaignState_whenBothSet() {
            AtBContract contract = new AtBContract();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster atbField =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster stateField =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            contract.setAtbOpForRoster(atbField);

            mekhq.campaign.stratCon.StratConCampaignState state =
                    new mekhq.campaign.stratCon.StratConCampaignState(contract);
            state.setOpForRoster(stateField);
            contract.setStratConCampaignState(state);

            // Campaign-state roster wins over the direct field.
            org.junit.jupiter.api.Assertions.assertSame(stateField, contract.getOpForRoster());
        }

        @Test
        void getAlliedRoster_prefersCampaignState_whenBothSet() {
            AtBContract contract = new AtBContract();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster atbField =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster stateField =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            contract.setAtbAlliedRoster(atbField);

            mekhq.campaign.stratCon.StratConCampaignState state =
                    new mekhq.campaign.stratCon.StratConCampaignState(contract);
            state.setAlliedRoster(stateField);
            contract.setStratConCampaignState(state);

            org.junit.jupiter.api.Assertions.assertSame(stateField, contract.getAlliedRoster());
        }

        @Test
        void serializationRoundTrip_preservesAtbRosters() throws Exception {
            AtBContract contract = new AtBContract();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster opfor =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            mekhq.campaign.stratCon.opfor.StratConOpForRoster ally =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            contract.setAtbOpForRoster(opfor);
            contract.setAtbAlliedRoster(ally);

            // Serialize via the public roster API directly — a full Contract XML round-trip
            // requires a Campaign + Faction + Planet harness that's out of scope here. The
            // accessor + serializer pair is what matters for slice a.
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            opfor.serializeAs(pw, "atbOpForRoster");
            pw.flush();

            String xml = sw.toString();
            org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<atbOpForRoster"),
                    "expected wrapper element, got: " + xml.substring(0, Math.min(200, xml.length())));

            // Round trip via DOM
            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            mekhq.campaign.stratCon.opfor.StratConOpForRoster restored =
                    mekhq.campaign.stratCon.opfor.StratConOpForRoster.deserialize(doc.getDocumentElement());
            org.junit.jupiter.api.Assertions.assertNotNull(restored);
        }

        @Test
        void loadFieldsFromXmlNode_relinksStratConCampaignStateContract_withoutAlliedRoster() throws Exception {
            // Regression: the StratConCampaignState -> contract back-reference is an
            // @XmlTransient field, so it must be re-linked on load. It was previously
            // stranded in the atbAlliedRoster parse branch, so a saved contract that
            // carries a StratConCampaignState but no <atbAlliedRoster> element loaded
            // the state with a null contract, NPEing in StratConTab on campaign load.
            String xml = "<contract>"
                    + "<StratConCampaignState></StratConCampaignState>"
                    + "</contract>";
            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));

            AtBContract contract = new AtBContract();
            contract.loadFieldsFromXmlNode(campaignThatCanCreateALiaison(),
                    new megamek.Version(),
                    doc.getDocumentElement());

            mekhq.campaign.stratCon.StratConCampaignState state = contract.getStratConCampaignState();
            org.junit.jupiter.api.Assertions.assertNotNull(state,
                    "StratConCampaignState should be deserialized");
            org.junit.jupiter.api.Assertions.assertSame(contract, state.getContract(),
                    "contract back-reference must be re-linked on load even without an allied roster");
        }

        @Test
        void loadFieldsFromXmlNode_parsesAtbOpForAndAlliedRosters() throws Exception {
            // Guards the AtBContract persistence wiring preserved across the
            // upstream/main merge: a pure-AtB contract's <atbOpForRoster> /
            // <atbAlliedRoster> elements must round-trip back through
            // loadFieldsFromXmlNode into getOpForRoster() / getAlliedRoster().
            // (Unlike serializationRoundTrip_preservesAtbRosters, this exercises
            // AtBContract's own parse branches, not just the roster serializer.)
            mekhq.campaign.stratCon.opfor.StratConOpForRoster opfor =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            mekhq.campaign.stratCon.opfor.StratConOpForFormation opforFormation =
                    new mekhq.campaign.stratCon.opfor.StratConOpForFormation();
            opforFormation.setName("Hostile Lance");
            opfor.addFormation(opforFormation);

            mekhq.campaign.stratCon.opfor.StratConOpForRoster ally =
                    new mekhq.campaign.stratCon.opfor.StratConOpForRoster();
            mekhq.campaign.stratCon.opfor.StratConOpForFormation allyFormation =
                    new mekhq.campaign.stratCon.opfor.StratConOpForFormation();
            allyFormation.setName("Allied Lance");
            ally.addFormation(allyFormation);

            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            opfor.serializeAs(pw, "atbOpForRoster");
            ally.serializeAs(pw, "atbAlliedRoster");
            pw.flush();
            String xml = "<contract>" + sw + "</contract>";

            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));

            AtBContract contract = new AtBContract();
            contract.loadFieldsFromXmlNode(campaignThatCanCreateALiaison(),
                    new megamek.Version(),
                    doc.getDocumentElement());

            // No StratConCampaignState present, so getOpForRoster()/getAlliedRoster()
            // fall back to the parsed atb* fields.
            org.junit.jupiter.api.Assertions.assertNotNull(contract.getOpForRoster(),
                    "atbOpForRoster must parse back via loadFieldsFromXmlNode");
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    contract.getOpForRoster().getFormations().size());
            org.junit.jupiter.api.Assertions.assertEquals("Hostile Lance",
                    contract.getOpForRoster().getFormations().get(0).getName());

            org.junit.jupiter.api.Assertions.assertNotNull(contract.getAlliedRoster(),
                    "atbAlliedRoster must parse back via loadFieldsFromXmlNode");
            org.junit.jupiter.api.Assertions.assertEquals("Allied Lance",
                    contract.getAlliedRoster().getFormations().get(0).getName());
        }
    }
}
