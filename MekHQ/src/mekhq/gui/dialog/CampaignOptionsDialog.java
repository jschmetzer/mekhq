/*
 * Copyright (C) 2009-2021 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.gui.dialog;

import megamek.client.generator.RandomGenderGenerator;
import megamek.client.generator.RandomNameGenerator;
import megamek.client.ui.preferences.JWindowPreference;
import megamek.client.ui.preferences.PreferencesNode;
import megamek.client.ui.swing.dialog.imageChooser.CamoChooserDialog;
import megamek.client.ui.swing.util.PlayerColour;
import megamek.common.EquipmentType;
import megamek.common.ITechnology;
import megamek.common.icons.AbstractIcon;
import megamek.common.icons.Camouflage;
import megamek.common.options.IOption;
import megamek.common.options.IOptionGroup;
import megamek.common.options.OptionsConstants;
import megamek.common.options.PilotOptions;
import megamek.common.util.EncodeControl;
import megamek.common.util.sorter.NaturalOrderComparator;
import mekhq.MHQStaticDirectoryManager;
import mekhq.MekHQ;
import mekhq.Utilities;
import mekhq.campaign.Campaign;
import mekhq.campaign.CampaignOptions;
import mekhq.campaign.GamePreset;
import mekhq.campaign.RandomSkillPreferences;
import mekhq.campaign.againstTheBot.enums.AtBLanceRole;
import mekhq.campaign.event.OptionsChangedEvent;
import mekhq.campaign.finances.enums.FinancialYearDuration;
import mekhq.campaign.market.PersonnelMarketDylan;
import mekhq.campaign.market.PersonnelMarketRandom;
import mekhq.campaign.market.enums.ContractMarketMethod;
import mekhq.campaign.market.enums.UnitMarketMethod;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.parts.Part;
import mekhq.campaign.personnel.SkillType;
import mekhq.campaign.personnel.SpecialAbility;
import mekhq.campaign.personnel.enums.BabySurnameStyle;
import mekhq.campaign.personnel.enums.FamilialRelationshipDisplayLevel;
import mekhq.campaign.personnel.enums.Marriage;
import mekhq.campaign.personnel.enums.PersonnelRole;
import mekhq.campaign.personnel.enums.Phenotype;
import mekhq.campaign.personnel.enums.PrisonerCaptureStyle;
import mekhq.campaign.personnel.enums.PrisonerStatus;
import mekhq.campaign.personnel.enums.TimeInDisplayFormat;
import mekhq.campaign.rating.UnitRatingMethod;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.RATManager;
import mekhq.gui.FileDialogs;
import mekhq.gui.SpecialAbilityPanel;
import mekhq.gui.baseComponents.SortedComboBoxModel;
import mekhq.gui.panes.RankSystemsPane;
import mekhq.module.PersonnelMarketServiceManager;
import mekhq.module.api.PersonnelMarketMethod;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;

/**
 * @author Jay Lawson <jaylawson39 at yahoo.com>
 */
public class CampaignOptionsDialog extends JDialog {
    //region Variable Declarations
    private static final long serialVersionUID = 1935043247792962964L;

    //region General Variables (ones not relating to a specific tab)
    private Campaign campaign;
    private CampaignOptions options;
    private RandomSkillPreferences rSkillPrefs;
    private LocalDate date;
    private JFrame frame;
    private Camouflage camouflage;
    private PlayerColour colour;
    private String iconCategory;
    private String iconFileName;
    private Hashtable<String, JSpinner> hashSkillTargets;
    private Hashtable<String, JSpinner> hashGreenSkill;
    private Hashtable<String, JSpinner> hashRegSkill;
    private Hashtable<String, JSpinner> hashVetSkill;
    private Hashtable<String, JSpinner> hashEliteSkill;
    private boolean cancelled;
    //endregion General Variables (ones not relating to a specific tab)

    //region Shared UI Variables
    private JTabbedPane tabOptions;
    private JButton btnOkay;
    private JButton btnSave;
    private JButton btnLoad;
    private JButton btnCancel;
    //endregion Shared UI Variables

    //region General Tab
    private JPanel panGeneral;
    private JTextField txtName;
    private JComboBox<String> comboFaction;
    private SortedComboBoxModel<String> factionModel;
    private JComboBox<UnitRatingMethod> unitRatingMethodCombo;
    private JSpinner manualUnitRatingModifier;
    private JButton btnDate;
    private JButton btnCamo;
    private JButton btnIcon;
    //endregion General Tab

    //region Repair and Maintenance Tab
    private JPanel panRepair;
    //repair
    private JCheckBox useEraModsCheckBox;
    private JCheckBox assignedTechFirstCheckBox;
    private JCheckBox resetToFirstTechCheckBox;
    private JCheckBox useQuirksBox;
    private JCheckBox useAeroSystemHitsBox;
    private JCheckBox useDamageMargin;
    private JSpinner spnDamageMargin;
    private JSpinner spnDestroyPartTarget;
    //maintenance
    private JCheckBox checkMaintenance;
    private JSpinner spnMaintenanceDays;
    private JSpinner spnMaintenanceBonus;
    private JCheckBox useQualityMaintenance;
    private JCheckBox reverseQualityNames;
    private JCheckBox useUnofficialMaintenance;
    private JCheckBox logMaintenance;
    //endregion Repair and Maintenance Tab

    //region Supplies and Acquisitions Tab
    private JPanel panSupplies;
    //acquisition
    private JSpinner spnAcquireWaitingPeriod;
    private JComboBox<String> choiceAcquireSkill;
    private JCheckBox chkSupportStaffOnly;
    private JSpinner spnAcquireClanPenalty;
    private JSpinner spnAcquireIsPenalty;
    private JTextField txtMaxAcquisitions;
    //Delivery
    private JSpinner spnNDiceTransitTime;
    private JSpinner spnConstantTransitTime;
    private JComboBox<String> choiceTransitTimeUnits;
    private JSpinner spnAcquireMinimum;
    private JComboBox<String> choiceAcquireMinimumUnit;
    private JSpinner spnAcquireMosBonus;
    private JComboBox<String> choiceAcquireMosUnits;
    //planetary acquisitions
    private JCheckBox usePlanetaryAcquisitions;
    private JSpinner spnMaxJumpPlanetaryAcquisitions;
    private JComboBox<String> comboPlanetaryAcquisitionsFactionLimits;
    private JCheckBox disallowPlanetaryAcquisitionClanCrossover;
    private JCheckBox usePlanetaryAcquisitionsVerbose;
    private JSpinner[] spnPlanetAcquireTechBonus;
    private JSpinner[] spnPlanetAcquireIndustryBonus;
    private JSpinner[] spnPlanetAcquireOutputBonus;
    private JCheckBox disallowClanPartsFromIS;
    private JSpinner spnPenaltyClanPartsFromIS;
    //endregion Supplies and Acquisitions Tab

    //region Tech Limits Tab
    private JPanel panTech;
    private JCheckBox limitByYearBox;
    private JCheckBox disallowExtinctStuffBox;
    private JCheckBox allowClanPurchasesBox;
    private JCheckBox allowISPurchasesBox;
    private JCheckBox allowCanonOnlyBox;
    private JCheckBox allowCanonRefitOnlyBox;
    private JComboBox<String> choiceTechLevel;
    private JCheckBox variableTechLevelBox;
    private JCheckBox factionIntroDateBox;
    private JCheckBox useAmmoByTypeBox;
    //endregion Tech Limits Tab

    //region Personnel Tab
    // General Personnel
    private JCheckBox chkUseTactics;
    private JCheckBox chkUseInitiativeBonus;
    private JCheckBox chkUseToughness;
    private JCheckBox chkUseArtillery;
    private JCheckBox chkUseAbilities;
    private JCheckBox chkUseEdge;
    private JCheckBox chkUseSupportEdge;
    private JCheckBox chkUseImplants;
    private JCheckBox chkUseAlternativeQualityAveraging;
    private JCheckBox chkUseTransfers;

    // Expanded Personnel
    private JCheckBox chkUseTimeInService;
    private JComboBox<TimeInDisplayFormat> comboTimeInServiceDisplayFormat;
    private JCheckBox chkUseTimeInRank;
    private JComboBox<TimeInDisplayFormat> comboTimeInRankDisplayFormat;
    private JCheckBox chkUseRetirementDateTracking;
    private JCheckBox chkTrackTotalEarnings;
    private JCheckBox chkShowOriginFaction;

    // Medical
    private JCheckBox chkUseAdvancedMedical;
    private JSpinner spnHealWaitingPeriod;
    private JSpinner spnNaturalHealWaitingPeriod;
    private JSpinner spnMinimumHitsForVehicles;
    private JCheckBox chkUseRandomHitsForVehicles;
    private JCheckBox chkUseTougherHealing;

    // Prisoners
    private JComboBox<PrisonerCaptureStyle> comboPrisonerCaptureStyle;
    private JComboBox<PrisonerStatus> comboPrisonerStatus;
    private JCheckBox chkPrisonerBabyStatus;
    private JCheckBox chkAtBPrisonerDefection;
    private JCheckBox chkAtBPrisonerRansom;

    // Personnel Randomization
    private JCheckBox chkUseDylansRandomXP;
    private JCheckBox chkRandomizeOrigin;
    private JCheckBox chkRandomizeDependentsOrigin;
    private JSpinner spnOriginSearchRadius;
    private JCheckBox chkExtraRandomOrigin;
    private JSpinner spnOriginDistanceScale;

    // Family
    private JComboBox<FamilialRelationshipDisplayLevel> comboDisplayFamilyLevel;

    // Salary
    private JSpinner spnCommissionedSalary;
    private JSpinner spnEnlistedSalary;
    private JSpinner spnAntiMekSalary;
    private JSpinner[] spnSalaryExperienceMultipliers;
    private JSpinner[] spnBaseSalary;

    // Marriage
    private JCheckBox chkUseManualMarriages;
    private JSpinner spnMinimumMarriageAge;
    private JSpinner spnCheckMutualAncestorsDepth;
    private JCheckBox chkLogMarriageNameChange;
    private JCheckBox chkUseRandomMarriages;
    private JSpinner spnChanceRandomMarriages;
    private JSpinner spnMarriageAgeRange;
    private JSpinner[] spnMarriageSurnameWeights;
    private JCheckBox chkUseRandomSameSexMarriages;
    private JSpinner spnChanceRandomSameSexMarriages;

    // Procreation
    private JCheckBox chkUseProcreation;
    private JSpinner spnChanceProcreation;
    private JCheckBox chkUseProcreationNoRelationship;
    private JSpinner spnChanceProcreationNoRelationship;
    private JCheckBox chkDisplayTrueDueDate;
    private JCheckBox chkLogConception;
    private JComboBox<BabySurnameStyle> comboBabySurnameStyle;
    private JCheckBox chkDetermineFatherAtBirth;

    // Death
    private JCheckBox chkKeepMarriedNameUponSpouseDeath;
    //endregion Personnel Tab

    //region Finances Tab
    private JPanel panFinances;
    private JCheckBox payForPartsBox;
    private JCheckBox payForRepairsBox;
    private JCheckBox payForUnitsBox;
    private JCheckBox payForSalariesBox;
    private JCheckBox payForOverheadBox;
    private JCheckBox payForMaintainBox;
    private JCheckBox payForTransportBox;
    private JCheckBox sellUnitsBox;
    private JCheckBox sellPartsBox;
    private JCheckBox payForRecruitmentBox;
    private JCheckBox useLoanLimitsBox;
    private JCheckBox usePercentageMaintBox;
    private JCheckBox useInfantryDontCountBox;
    private JCheckBox usePeacetimeCostBox;
    private JCheckBox useExtendedPartsModifierBox;
    private JCheckBox showPeacetimeCostBox;
    private JCheckBox newFinancialYearFinancesToCSVExportBox;
    private JComboBox<FinancialYearDuration> comboFinancialYearDuration;
    private JSpinner spnClanPriceModifier;
    private JLabel[] partQualityLabels;
    private JSpinner[] spnUsedPartsValue;
    private JSpinner spnDamagedPartsValue;
    private JSpinner spnOrderRefund;
    //endregion Finances Tab

    //region Mercenary Tab
    private JPanel panMercenary;
    private JRadioButton btnContractEquipment;
    private JSpinner spnEquipPercent;
    private JSpinner spnDropshipPercent;
    private JSpinner spnJumpshipPercent;
    private JSpinner spnWarshipPercent;
    private JCheckBox chkEquipContractSaleValue;
    private JRadioButton btnContractPersonnel;
    private JCheckBox chkBLCSaleValue;
    private JCheckBox chkOverageRepaymentInFinalPayment;
    //endregion Mercenary Tab

    //region Experience Tab
    private JPanel panXP;
    private JSpinner spnScenarioXP;
    private JSpinner spnKillXP;
    private JSpinner spnKills;
    private JSpinner spnTaskXP;
    private JSpinner spnNTasksXP;
    private JSpinner spnSuccessXP;
    private JSpinner spnMistakeXP;
    private JSpinner spnIdleXP;
    private JSpinner spnMonthsIdleXP;
    private JSpinner spnTargetIdleXP;
    private JSpinner spnContractNegotiationXP;
    private JSpinner spnAdminWeeklyXP;
    private JSpinner spnAdminWeeklyXPPeriod;
    private JSpinner spnEdgeCost;
    private JTextArea txtInstructionsXP;
    private JScrollPane scrXP;
    private JTable tableXP;
    private static final String[] TABLE_XP_COLUMN_NAMES = {"+0", "+1", "+2", "+3", "+4", "+5", "+6", "+7", "+8", "+9", "+10"};
    //endregion Experience Tab

    //region Skills Tab
    private JPanel panSkill;
    //endregion Skills Tab

    //region Special Abilities Tab
    private JPanel panSpecialAbilities;
    private Hashtable<String, SpecialAbility> tempSPA;
    private JButton btnAddSPA;
    //endregion Special Abilities Tab

    //region Skill Randomization Tab
    private JPanel panRandomSkill;
    private JCheckBox chkExtraRandom;
    private JSpinner[] phenotypeSpinners;
    private JSpinner spnProbAntiMek;
    private JSpinner spnOverallRecruitBonus;
    private JSpinner[] spnTypeRecruitBonus;
    private JSpinner spnArtyProb;
    private JSpinner spnArtyBonus;
    private JSpinner spnTacticsGreen;
    private JSpinner spnTacticsReg;
    private JSpinner spnTacticsVet;
    private JSpinner spnTacticsElite;
    private JSpinner spnCombatSA;
    private JSpinner spnSupportSA;
    private JSpinner spnSecondProb;
    private JSpinner spnSecondBonus;
    private JSpinner spnAbilGreen;
    private JSpinner spnAbilReg;
    private JSpinner spnAbilVet;
    private JSpinner spnAbilElite;
    //endregion Skill Randomization Tab

    //region Rank System Tab
    private RankSystemsPane rankSystemsPane;
    //endregion Rank System Tab

    //region Name and Portrait Generation Tab
    private JPanel panNameGen;
    private JCheckBox chkUseOriginFactionForNames;
    private JComboBox<String> comboFactionNames;
    private JSlider sldGender;
    private JPanel panRandomPortrait;
    private JCheckBox[] chkUsePortrait;
    private JCheckBox allPortraitsBox;
    private JCheckBox noPortraitsBox;
    private JCheckBox chkAssignPortraitOnRoleChange;
    //endregion Name and Portrait Generation Tab

    //region Markets Tab
    // Personnel Market
    private JComboBox<String> comboPersonnelMarketType;
    private JCheckBox chkPersonnelMarketReportRefresh;
    private JSpinner spnPersonnelMarketRandomEliteRemoval;
    private JSpinner spnPersonnelMarketRandomVeteranRemoval;
    private JSpinner spnPersonnelMarketRandomRegularRemoval;
    private JSpinner spnPersonnelMarketRandomGreenRemoval;
    private JSpinner spnPersonnelMarketRandomUltraGreenRemoval;
    private JSpinner spnPersonnelMarketDylansWeight;

    // Unit Market
    private JComboBox<UnitMarketMethod> comboUnitMarketMethod;
    private JCheckBox chkUnitMarketRegionalMechVariations;
    private JCheckBox chkInstantUnitMarketDelivery;
    private JCheckBox chkUnitMarketReportRefresh;

    // Contract Market
    private JComboBox<ContractMarketMethod> comboContractMarketMethod;
    private JCheckBox chkContractMarketReportRefresh;
    //endregion Markets Tab

    //region Against the Bot Tab
    private JPanel panAtB;
    private JCheckBox chkUseAtB;
    private JCheckBox chkUseStratCon;
    private JComboBox<String> cbSkillLevel;

    //unit administration
    private JCheckBox chkUseShareSystem;
    private JCheckBox chkSharesExcludeLargeCraft;
    private JCheckBox chkSharesForAll;
    private JCheckBox chkAeroRecruitsHaveUnits;
    private JCheckBox chkRetirementRolls;
    private JCheckBox chkCustomRetirementMods;
    private JCheckBox chkFoundersNeverRetire;
    private JCheckBox chkAddDependents;
    private JCheckBox chkDependentsNeverLeave;
    private JCheckBox chkTrackUnitFatigue;
    private JCheckBox chkUseLeadership;
    private JCheckBox chkTrackOriginalUnit;
    private JCheckBox chkUseAero;
    private JCheckBox chkUseVehicles;
    private JCheckBox chkClanVehicles;

    //contract operations
    private JSpinner spnSearchRadius;
    private JCheckBox chkVariableContractLength;
    private JCheckBox chkMercSizeLimited;
    private JCheckBox chkRestrictPartsByMission;
    private JCheckBox chkLimitLanceWeight;
    private JCheckBox chkLimitLanceNumUnits;
    private JCheckBox chkUseStrategy;
    private JSpinner spnBaseStrategyDeployment;
    private JSpinner spnAdditionalStrategyDeployment;
    private JCheckBox chkAdjustPaymentForStrategy;
    private JSpinner spnAtBBattleIntensity;
    private JSpinner[] spnAtBBattleChance;
    private JButton btnIntensityUpdate;
    private JCheckBox chkGenerateChases;

    //RATs
    private JRadioButton btnDynamicRATs;
    private JRadioButton btnStaticRATs;
    private DefaultListModel<String> chosenRatModel;
    private JList<String> chosenRats;
    private DefaultListModel<String> availableRatModel;
    private JList<String> availableRats;
    private JButton btnAddRat;
    private JButton btnRemoveRat;
    private JButton btnMoveRatUp;
    private JButton btnMoveRatDown;
    private JCheckBox chkIgnoreRatEra;

    //scenarios
    private JCheckBox chkDoubleVehicles;
    private JSpinner spnOpforLanceTypeMechs;
    private JSpinner spnOpforLanceTypeMixed;
    private JSpinner spnOpforLanceTypeVehicles;
    private JCheckBox chkOpforUsesVTOLs;
    private JCheckBox chkOpforUsesAero;
    private JSpinner spnOpforAeroChance;
    private JCheckBox chkOpforUsesLocalForces;
    private JSpinner spnOpforLocalForceChance;
    private JCheckBox chkAdjustPlayerVehicles;
    private JCheckBox chkRegionalMechVariations;
    private JCheckBox chkAttachedPlayerCamouflage;
    private JCheckBox chkPlayerControlsAttachedUnits;
    private JCheckBox chkUseDropShips;
    private JCheckBox chkUseWeatherConditions;
    private JCheckBox chkUseLightConditions;
    private JCheckBox chkUsePlanetaryConditions;
    //endregion Against the Bot Tab

    private final ResourceBundle resources = ResourceBundle.getBundle("mekhq.resources.CampaignOptionsDialog", new EncodeControl());
    //endregion Variable Declarations

    public CampaignOptionsDialog(JFrame parent, boolean modal, Campaign c) {
        super(parent, modal);
        this.campaign = c;
        //this is a hack but I have no idea what is going on here
        this.frame = parent;
        this.date = campaign.getLocalDate();
        this.camouflage = campaign.getCamouflage();
        this.colour = campaign.getColour();
        this.iconCategory = campaign.getIconCategory();
        this.iconFileName = campaign.getIconFileName();
        hashSkillTargets = new Hashtable<>();
        hashGreenSkill = new Hashtable<>();
        hashRegSkill = new Hashtable<>();
        hashVetSkill = new Hashtable<>();
        hashEliteSkill = new Hashtable<>();
        cancelled = false;

        initComponents();
        setOptions(c.getCampaignOptions(), c.getRandomSkillPreferences());
        btnCamo.setIcon(camouflage.getImageIcon());
        setForceIcon();
        setLocationRelativeTo(parent);

        setUserPreferences();
    }

    //region Initialization
    /**
     * This method is called from within the constructor to initialize the form.
     */
    private void initComponents() {
        //region Variable Declaration and Initialisation
        tabOptions = new JTabbedPane();
        comboFactionNames = new JComboBox<>();
        sldGender = new JSlider(SwingConstants.HORIZONTAL);
        panRepair = new JPanel();
        panSupplies = new JPanel();
        panFinances = new JPanel();
        panMercenary = new JPanel();
        panNameGen = new JPanel();
        panXP = new JPanel();
        panSkill = new JPanel();
        panTech = new JPanel();
        panRandomSkill = new JPanel();
        panRandomPortrait = new JPanel();
        useEraModsCheckBox = new JCheckBox();
        assignedTechFirstCheckBox = new JCheckBox();
        resetToFirstTechCheckBox = new JCheckBox();
        JLabel clanPriceModifierLabel = new JLabel();
        JLabel usedPartsValueLabel = new JLabel();
        JLabel damagedPartsValueLabel = new JLabel();
        payForPartsBox = new JCheckBox();
        payForRepairsBox = new JCheckBox();
        payForUnitsBox = new JCheckBox();
        payForSalariesBox = new JCheckBox();
        payForRecruitmentBox = new JCheckBox();
        useLoanLimitsBox = new JCheckBox();
        payForOverheadBox = new JCheckBox();
        payForMaintainBox = new JCheckBox();
        payForTransportBox = new JCheckBox();
        usePeacetimeCostBox = new JCheckBox();
        useExtendedPartsModifierBox = new JCheckBox();
        showPeacetimeCostBox = new JCheckBox();
        chkAssignPortraitOnRoleChange = new JCheckBox();
        sellUnitsBox = new JCheckBox();
        sellPartsBox = new JCheckBox();
        useQuirksBox = new JCheckBox();
        limitByYearBox = new JCheckBox();
        disallowExtinctStuffBox = new JCheckBox();
        allowClanPurchasesBox = new JCheckBox();
        allowISPurchasesBox = new JCheckBox();
        allowCanonOnlyBox = new JCheckBox();
        allowCanonRefitOnlyBox = new JCheckBox();
        variableTechLevelBox = new JCheckBox();
        factionIntroDateBox = new JCheckBox();
        useAmmoByTypeBox = new JCheckBox();
        choiceTechLevel = new JComboBox<>();
        btnLoad = new JButton();
        btnCancel = new JButton();

        usePlanetaryAcquisitions = new JCheckBox();
        usePlanetaryAcquisitionsVerbose = new JCheckBox();
        disallowPlanetaryAcquisitionClanCrossover = new JCheckBox();
        comboPlanetaryAcquisitionsFactionLimits = new JComboBox<>();
        disallowClanPartsFromIS = new JCheckBox();
        useDamageMargin = new JCheckBox();
        useAeroSystemHitsBox = new JCheckBox();
        useQualityMaintenance = new JCheckBox();
        useUnofficialMaintenance = new JCheckBox();
        checkMaintenance = new JCheckBox();
        reverseQualityNames = new JCheckBox();

        chkSupportStaffOnly = new JCheckBox();

        GridBagConstraints gridBagConstraints;
        int gridy = 0;
        int gridx = 0;

        final PersonnelRole[] personnelRoles = PersonnelRole.values();
        //endregion Variable Declaration and Initialisation

        ResourceBundle resourceMap = ResourceBundle.getBundle("mekhq.resources.CampaignOptionsDialog", new EncodeControl());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setName("Form");
        setTitle(resourceMap.getString("title.text"));
        getContentPane().setLayout(new GridBagLayout());

        tabOptions.setName("tabOptions");

        //region General Tab
        panGeneral = new JPanel(new GridBagLayout());
        panGeneral.setName("panGeneral");

        JLabel lblName = new JLabel(resourceMap.getString("lblName.text"));
        lblName.setName("lblName");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = gridx++;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panGeneral.add(lblName, gridBagConstraints);

        txtName = new JTextField(campaign.getName());
        txtName.setName("txtName");
        txtName.setMinimumSize(new Dimension(500, 30));
        txtName.setPreferredSize(new Dimension(500, 30));
        gridBagConstraints.gridx = gridx--;
        panGeneral.add(txtName, gridBagConstraints);

        JLabel lblFaction = new JLabel(resourceMap.getString("lblFaction.text"));
        lblFaction.setName("lblFaction");
        gridBagConstraints.gridx = gridx++;
        gridBagConstraints.gridy = gridy++;
        panGeneral.add(lblFaction, gridBagConstraints);

        factionModel = new SortedComboBoxModel<>();
        for (String sName : Factions.getInstance().getChoosableFactionCodes()) {
            Faction f = Factions.getInstance().getFaction(sName);
            if (f.validIn(date.getYear())) {
                factionModel.addElement(f.getFullName(date.getYear()));
            }
        }
        factionModel.setSelectedItem(campaign.getFaction().getFullName(date.getYear()));
        comboFaction = new JComboBox<>();
        comboFaction.setName("comboFaction");
        comboFaction.setModel(factionModel);
        comboFaction.setMinimumSize(new Dimension(400, 30));
        comboFaction.setPreferredSize(new Dimension(400, 30));
        gridBagConstraints.gridx = gridx--;
        panGeneral.add(comboFaction, gridBagConstraints);

        JPanel unitRatingPanel = new JPanel(new GridBagLayout());

        JLabel unitRatingMethodLabel = new JLabel(resourceMap.getString("unitRatingMethodLabel.text"));
        unitRatingMethodLabel.setName("unitRatingMethodLabel");
        gridBagConstraints.gridx = 0;
        unitRatingPanel.add(unitRatingMethodLabel, gridBagConstraints);

        unitRatingMethodCombo = new JComboBox<>(UnitRatingMethod.values());
        unitRatingMethodCombo.setName("unitRatingMethodCombo");
        gridBagConstraints.gridx = 1;
        unitRatingPanel.add(unitRatingMethodCombo, gridBagConstraints);

        JLabel manualUnitRatingModifierLabel = new JLabel(resourceMap.getString("manualUnitRatingModifierLabel.text"));
        gridBagConstraints.gridx = 2;
        unitRatingPanel.add(manualUnitRatingModifierLabel, gridBagConstraints);

        manualUnitRatingModifier = new JSpinner(new SpinnerNumberModel(0, -100, 100, 1));
        gridBagConstraints.gridx = 3;
        unitRatingPanel.add(manualUnitRatingModifier, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = gridx;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panGeneral.add(unitRatingPanel, gridBagConstraints);

        JLabel lblDate = new JLabel(resourceMap.getString("lblDate.text"));
        lblDate.setName("lblDate");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = gridx++;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panGeneral.add(lblDate, gridBagConstraints);

        btnDate = new JButton(MekHQ.getMekHQOptions().getDisplayFormattedDate(date));
        btnDate.setName("btnDate");
        btnDate.setMinimumSize(new Dimension(400, 30));
        btnDate.setPreferredSize(new Dimension(400, 30));
        btnDate.addActionListener(this::btnDateActionPerformed);
        gridBagConstraints.gridx = gridx--;
        panGeneral.add(btnDate, gridBagConstraints);

        JLabel lblCamo = new JLabel(resourceMap.getString("lblCamo.text"));
        lblCamo.setName("lblCamo");
        gridBagConstraints.gridx = gridx++;
        gridBagConstraints.gridy = gridy++;
        panGeneral.add(lblCamo, gridBagConstraints);

        btnCamo = new JButton();
        btnCamo.setName("btnCamo");
        btnCamo.setMinimumSize(new Dimension(84, 72));
        btnCamo.setPreferredSize(new Dimension(84, 72));
        btnCamo.setMaximumSize(new Dimension(84, 72));
        btnCamo.addActionListener(this::btnCamoActionPerformed);
        gridBagConstraints.gridx = gridx--;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panGeneral.add(btnCamo, gridBagConstraints);

        JLabel lblIcon = new JLabel(resourceMap.getString("lblIcon.text"));
        gridBagConstraints.gridx = gridx++;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panGeneral.add(lblIcon, gridBagConstraints);

        btnIcon = new JButton();
        btnIcon.addActionListener(this::btnIconActionPerformed);
        btnIcon.setMinimumSize(new Dimension(84, 72));
        btnIcon.setPreferredSize(new Dimension(84, 72));
        btnIcon.setMaximumSize(new Dimension(84, 72));
        gridBagConstraints.gridx = gridx--;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panGeneral.add(btnIcon, gridBagConstraints);

        tabOptions.addTab(resourceMap.getString("panGeneral.TabConstraints.tabTitle"), panGeneral);
        //endregion General Tab

        //region Repair and Maintenance Tab
        panRepair.setName("panRepair");
        panRepair.setLayout(new java.awt.GridBagLayout());

        JPanel panSubRepair = new JPanel(new GridBagLayout());
        JPanel panSubMaintenance = new JPanel(new GridBagLayout());

        panSubRepair.setBorder(BorderFactory.createTitledBorder("Repair"));
        panSubMaintenance.setBorder(BorderFactory.createTitledBorder("Maintenance"));

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        panRepair.add(panSubRepair, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        panRepair.add(panSubMaintenance, gridBagConstraints);

        useEraModsCheckBox.setText(resourceMap.getString("useEraModsCheckBox.text")); // NOI18N
        useEraModsCheckBox.setToolTipText(resourceMap.getString("useEraModsCheckBox.toolTipText")); // NOI18N
        useEraModsCheckBox.setName("useEraModsCheckBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(useEraModsCheckBox, gridBagConstraints);

        assignedTechFirstCheckBox.setText(resourceMap.getString("assignedTechFirstCheckBox.text")); // NOI18N
        assignedTechFirstCheckBox.setToolTipText(resourceMap.getString("assignedTechFirstCheckBox.toolTipText")); // NOI18N
        assignedTechFirstCheckBox.setName("assignedTechFirstCheckBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(assignedTechFirstCheckBox, gridBagConstraints);

        resetToFirstTechCheckBox.setText(resourceMap.getString("resetToFirstTechCheckBox.text")); // NOI18N
        resetToFirstTechCheckBox.setToolTipText(resourceMap.getString("resetToFirstTechCheckBox.toolTipText")); // NOI18N
        resetToFirstTechCheckBox.setName("resetToFirstTechCheckBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(resetToFirstTechCheckBox, gridBagConstraints);

        useQuirksBox.setText(resourceMap.getString("useQuirksBox.text")); // NOI18N
        useQuirksBox.setToolTipText(resourceMap.getString("useQuirksBox.toolTipText")); // NOI18N
        useQuirksBox.setName("useQuirksBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(useQuirksBox, gridBagConstraints);

        useAeroSystemHitsBox.setText(resourceMap.getString("useAeroSystemHits.text")); // NOI18N
        useAeroSystemHitsBox.setToolTipText(resourceMap.getString("useAeroSystemHits.toolTipText")); // NOI18N
        useAeroSystemHitsBox.setName("useAeroSystemHits"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(useAeroSystemHitsBox, gridBagConstraints);

        useDamageMargin.setText(resourceMap.getString("useDamageMargin.text"));
        useDamageMargin.setToolTipText(resourceMap.getString("useDamageMargin.toolTipText"));
        useDamageMargin.addActionListener(evt -> spnDamageMargin.setEnabled(useDamageMargin.isSelected()));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(useDamageMargin, gridBagConstraints);

        spnDamageMargin = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
        ((JSpinner.DefaultEditor) spnDamageMargin.getEditor()).getTextField().setEditable(false);
        JPanel pnlDamageMargin = new JPanel();
        pnlDamageMargin.add(new JLabel("Margin:"));
        pnlDamageMargin.add(spnDamageMargin);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(pnlDamageMargin, gridBagConstraints);

        spnDestroyPartTarget = new JSpinner(new SpinnerNumberModel(2, 2, 13, 1));
        ((JSpinner.DefaultEditor) spnDestroyPartTarget.getEditor()).getTextField().setEditable(false);

        JPanel pnlDestroyPartTarget = new JPanel();
        pnlDestroyPartTarget.add(new JLabel("Equipment hit in combat survives on a roll of"));
        pnlDestroyPartTarget.add(spnDestroyPartTarget);
        pnlDestroyPartTarget.add(new JLabel("or better"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubRepair.add(pnlDestroyPartTarget, gridBagConstraints);

        checkMaintenance.setText(resourceMap.getString("checkMaintenance.text"));
        checkMaintenance.setToolTipText(resourceMap.getString("checkMaintenance.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubMaintenance.add(checkMaintenance, gridBagConstraints);

        checkMaintenance.addActionListener(evt -> {
            if (checkMaintenance.isSelected()) {
                spnMaintenanceDays.setEnabled(true);
                useQualityMaintenance.setEnabled(true);
                useUnofficialMaintenance.setEnabled(true);
                reverseQualityNames.setEnabled(true);
                spnMaintenanceBonus.setEnabled(true);
                logMaintenance.setEnabled(true);
            } else {
                spnMaintenanceDays.setEnabled(false);
                useQualityMaintenance.setEnabled(false);
                useUnofficialMaintenance.setEnabled(false);
                reverseQualityNames.setEnabled(false);
                spnMaintenanceBonus.setEnabled(false);
                logMaintenance.setEnabled(false);
            }
        });

        spnMaintenanceDays = new JSpinner(new SpinnerNumberModel(30, 1, 365, 1));
        ((JSpinner.DefaultEditor) spnMaintenanceDays.getEditor()).getTextField().setEditable(false);
        JPanel pnlMaintenanceDays = new JPanel();
        pnlMaintenanceDays.add(spnMaintenanceDays);
        pnlMaintenanceDays.add(new JLabel("Maintenance cycle length in days"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubMaintenance.add(pnlMaintenanceDays, gridBagConstraints);

        spnMaintenanceBonus = new JSpinner(new SpinnerNumberModel(0, -13, 13, 1));
        ((JSpinner.DefaultEditor) spnMaintenanceBonus.getEditor()).getTextField().setEditable(false);
        spnMaintenanceBonus.setToolTipText(resourceMap.getString("spnMaintenanceBonus.toolTipText"));

        JPanel pnlMaintenanceBonus = new JPanel();
        pnlMaintenanceBonus.add(spnMaintenanceBonus);
        pnlMaintenanceBonus.add(new JLabel("Maintenance modifier"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubMaintenance.add(pnlMaintenanceBonus, gridBagConstraints);

        useQualityMaintenance.setText(resourceMap.getString("useQualityMaintenance.text"));
        useQualityMaintenance.setToolTipText(resourceMap.getString("useQualityMaintenance.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubMaintenance.add(useQualityMaintenance, gridBagConstraints);

        reverseQualityNames.setText(resourceMap.getString("reverseQualityNames.text"));
        reverseQualityNames.setToolTipText(resourceMap.getString("reverseQualityNames.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubMaintenance.add(reverseQualityNames, gridBagConstraints);


        useUnofficialMaintenance.setText(resourceMap.getString("useUnofficialMaintenance.text"));
        useUnofficialMaintenance.setToolTipText(resourceMap.getString("useUnofficialMaintenance.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubMaintenance.add(useUnofficialMaintenance, gridBagConstraints);

        logMaintenance = new JCheckBox(resourceMap.getString("logMaintenance.text"));
        logMaintenance.setToolTipText(resourceMap.getString("logMaintenance.toolTipText"));
        logMaintenance.setName("logMaintenance");
        gridBagConstraints.gridy = 6;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        panSubMaintenance.add(logMaintenance, gridBagConstraints);

        tabOptions.addTab(resourceMap.getString("panRepair.TabConstraints.tabTitle"), panRepair);
        //endregion Repair and Maintenance Tab

        //region Supplies and Acquisition Tab
        panSupplies.setName("panSupplies");
        panSupplies.setLayout(new GridBagLayout());

        JPanel panSubAcquire = new JPanel(new GridBagLayout());
        JPanel panSubDelivery = new JPanel(new GridBagLayout());
        JPanel panSubPlanetAcquire = new JPanel(new GridBagLayout());

        panSubAcquire.setBorder(BorderFactory.createTitledBorder("Acquisition"));
        panSubDelivery.setBorder(BorderFactory.createTitledBorder("Delivery"));
        panSubPlanetAcquire.setBorder(BorderFactory.createTitledBorder("Planetary Acquisition"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = .5;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        panSupplies.add(panSubAcquire, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = .5;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        panSupplies.add(panSubDelivery, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        panSupplies.add(panSubPlanetAcquire, gridBagConstraints);

        spnAcquireWaitingPeriod = new JSpinner(new SpinnerNumberModel(1, 1, 365, 1));
        ((JSpinner.DefaultEditor) spnAcquireWaitingPeriod.getEditor()).getTextField().setEditable(false);

        JPanel pnlWaitingPeriod = new JPanel();
        pnlWaitingPeriod.add(spnAcquireWaitingPeriod);
        pnlWaitingPeriod.add(new JLabel("Waiting period (in days) between acquisition rolls"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAcquire.add(pnlWaitingPeriod, gridBagConstraints);

        DefaultComboBoxModel<String> acquireSkillModel = new DefaultComboBoxModel<>();
        acquireSkillModel.addElement(CampaignOptions.S_TECH);
        acquireSkillModel.addElement(SkillType.S_ADMIN);
        acquireSkillModel.addElement(SkillType.S_SCROUNGE);
        acquireSkillModel.addElement(SkillType.S_NEG);
        acquireSkillModel.addElement(CampaignOptions.S_AUTO);
        choiceAcquireSkill = new JComboBox<>(acquireSkillModel);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panSubAcquire.add(new JLabel("Acquisition Skill:"), gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panSubAcquire.add(choiceAcquireSkill, gridBagConstraints);

        chkSupportStaffOnly.setText("Only support personnel can make acquisition checks");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAcquire.add(chkSupportStaffOnly, gridBagConstraints);

        spnAcquireClanPenalty = new JSpinner(new SpinnerNumberModel(0, 0, 13, 1));
        ((JSpinner.DefaultEditor) spnAcquireClanPenalty.getEditor()).getTextField().setEditable(false);

        JPanel pnlClanPenalty = new JPanel();
        pnlClanPenalty.add(spnAcquireClanPenalty);
        pnlClanPenalty.add(new JLabel("Penalty for Clan equipment"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAcquire.add(pnlClanPenalty, gridBagConstraints);

        spnAcquireIsPenalty = new JSpinner(new SpinnerNumberModel(0, 0, 13, 1));
        ((JSpinner.DefaultEditor) spnAcquireIsPenalty.getEditor()).getTextField().setEditable(false);

        JPanel pnlIsPenalty = new JPanel();
        pnlIsPenalty.add(spnAcquireIsPenalty);
        pnlIsPenalty.add(new JLabel("Penalty for Inner Sphere equipment"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAcquire.add(pnlIsPenalty, gridBagConstraints);

        txtMaxAcquisitions = new JTextField(4);
        txtMaxAcquisitions.setHorizontalAlignment(JTextField.RIGHT);
        txtMaxAcquisitions.setName("txtName");

        JPanel pnlMaxAcquisitions = new JPanel();
        pnlMaxAcquisitions.add(txtMaxAcquisitions);
        pnlMaxAcquisitions.add(new JLabel("Maximum Acquisitions Per Day (0 for unlimited)"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAcquire.add(pnlMaxAcquisitions, gridBagConstraints);

        spnNDiceTransitTime = new JSpinner(new SpinnerNumberModel(0, 0, 365, 1));
        ((JSpinner.DefaultEditor) spnNDiceTransitTime.getEditor()).getTextField().setEditable(false);

        spnConstantTransitTime = new JSpinner(new SpinnerNumberModel(0, 0, 365, 1));
        ((JSpinner.DefaultEditor) spnConstantTransitTime.getEditor()).getTextField().setEditable(false);

        spnAcquireMosBonus = new JSpinner(new SpinnerNumberModel(0, 0, 365, 1));
        ((JSpinner.DefaultEditor) spnAcquireMosBonus.getEditor()).getTextField().setEditable(false);

        spnAcquireMinimum = new JSpinner(new SpinnerNumberModel(0, 0, 365, 1));
        ((JSpinner.DefaultEditor) spnAcquireMinimum.getEditor()).getTextField().setEditable(false);

        DefaultComboBoxModel<String> transitUnitModel = new DefaultComboBoxModel<>();
        for (int i = 0; i < CampaignOptions.TRANSIT_UNIT_NUM; i++) {
            transitUnitModel.addElement(CampaignOptions.getTransitUnitName(i));
        }
        choiceTransitTimeUnits = new JComboBox<>(transitUnitModel);

        DefaultComboBoxModel<String> transitMosUnitModel = new DefaultComboBoxModel<>();
        for (int i = 0; i < CampaignOptions.TRANSIT_UNIT_NUM; i++) {
            transitMosUnitModel.addElement(CampaignOptions.getTransitUnitName(i));
        }
        choiceAcquireMosUnits = new JComboBox<>(transitMosUnitModel);

        DefaultComboBoxModel<String> transitMinUnitModel = new DefaultComboBoxModel<>();
        for (int i = 0; i < CampaignOptions.TRANSIT_UNIT_NUM; i++) {
            transitMinUnitModel.addElement(CampaignOptions.getTransitUnitName(i));
        }
        choiceAcquireMinimumUnit = new JComboBox<>(transitMinUnitModel);

        JPanel pnlTransitTime = new JPanel();
        pnlTransitTime.add(new JLabel("Delivery Time:"));
        pnlTransitTime.add(spnNDiceTransitTime);
        pnlTransitTime.add(new JLabel("d6 + "));
        pnlTransitTime.add(spnConstantTransitTime);
        pnlTransitTime.add(choiceTransitTimeUnits);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubDelivery.add(pnlTransitTime, gridBagConstraints);

        JPanel pnlMinTransit = new JPanel();
        pnlMinTransit.add(new JLabel("Minimum Transit Time:"));
        pnlMinTransit.add(spnAcquireMinimum);
        pnlMinTransit.add(choiceAcquireMinimumUnit);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubDelivery.add(pnlMinTransit, gridBagConstraints);

        JPanel pnlMosBonus = new JPanel();
        pnlMosBonus.add(new JLabel("Reduce delivery time by"));
        pnlMosBonus.add(spnAcquireMosBonus);
        pnlMosBonus.add(choiceAcquireMosUnits);
        pnlMosBonus.add(new JLabel("per MoS"));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubDelivery.add(pnlMosBonus, gridBagConstraints);

        usePlanetaryAcquisitions.setText(resourceMap.getString("usePlanetaryAcquisitions.text"));
        usePlanetaryAcquisitions.setToolTipText(resourceMap.getString("usePlanetaryAcquisitions.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(usePlanetaryAcquisitions, gridBagConstraints);

        spnMaxJumpPlanetaryAcquisitions = new JSpinner(new SpinnerNumberModel(2, 0, 5, 1));
        JPanel panMaxJump = new JPanel();
        panMaxJump.add(spnMaxJumpPlanetaryAcquisitions);
        panMaxJump.add(new JLabel("Maximum number of jumps away to search for supplies"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(panMaxJump, gridBagConstraints);

        DefaultComboBoxModel<String> factionLimitComboBoxModel = new DefaultComboBoxModel<>();
        factionLimitComboBoxModel.addElement(CampaignOptions.getFactionLimitName(CampaignOptions.PLANET_ACQUISITION_ALL));
        factionLimitComboBoxModel.addElement(CampaignOptions.getFactionLimitName(CampaignOptions.PLANET_ACQUISITION_NEUTRAL));
        factionLimitComboBoxModel.addElement(CampaignOptions.getFactionLimitName(CampaignOptions.PLANET_ACQUISITION_ALLY));
        factionLimitComboBoxModel.addElement(CampaignOptions.getFactionLimitName(CampaignOptions.PLANET_ACQUISITION_SELF));
        comboPlanetaryAcquisitionsFactionLimits.setModel(factionLimitComboBoxModel);
        JPanel panFactionLimit = new JPanel();
        panFactionLimit.add(new JLabel("Faction supply limitations"));
        panFactionLimit.add(comboPlanetaryAcquisitionsFactionLimits);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(panFactionLimit, gridBagConstraints);

        disallowPlanetaryAcquisitionClanCrossover.setText(resourceMap.getString("disallowPlanetaryAcquisitionClanCrossover.text"));
        disallowPlanetaryAcquisitionClanCrossover.setToolTipText(resourceMap.getString("disallowPlanetaryAcquisitionClanCrossover.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(disallowPlanetaryAcquisitionClanCrossover, gridBagConstraints);

        disallowClanPartsFromIS.setText(resourceMap.getString("disallowClanPartsFromIS.text"));
        disallowClanPartsFromIS.setToolTipText(resourceMap.getString("disallowClanPartsFromIS.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(disallowClanPartsFromIS, gridBagConstraints);

        spnPenaltyClanPartsFromIS = new JSpinner(new SpinnerNumberModel(0, 0, 12, 1));
        JPanel panPenaltyClanPartsFromIS = new JPanel();
        panPenaltyClanPartsFromIS.add(spnPenaltyClanPartsFromIS);
        JLabel lblPenaltyClanPartsFromIS = new JLabel(resourceMap.getString("spnPenaltyClanPartsFromIS.text"));
        lblPenaltyClanPartsFromIS.setToolTipText(resourceMap.getString("spnPenaltyClanPartsFromIS.toolTipText"));
        panPenaltyClanPartsFromIS.add(lblPenaltyClanPartsFromIS);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(panPenaltyClanPartsFromIS, gridBagConstraints);

        usePlanetaryAcquisitionsVerbose.setText(resourceMap.getString("usePlanetaryAcquisitionsVerbose.text"));
        usePlanetaryAcquisitionsVerbose.setToolTipText(resourceMap.getString("usePlanetaryAcquisitionsVerbose.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(usePlanetaryAcquisitionsVerbose, gridBagConstraints);

        JPanel panSocioIndustrialBonus = new JPanel();
        panSocioIndustrialBonus.setLayout(new BoxLayout(panSocioIndustrialBonus, BoxLayout.LINE_AXIS));
        panSocioIndustrialBonus.setBorder(BorderFactory.createTitledBorder("Planet socio-industrial modifiers "));

        JPanel panTechBonus = new JPanel(new GridBagLayout());
        JPanel panIndustryBonus = new JPanel(new GridBagLayout());
        JPanel panOutputBonus = new JPanel(new GridBagLayout());

        spnPlanetAcquireTechBonus = new JSpinner[6];
        spnPlanetAcquireIndustryBonus = new JSpinner[6];
        spnPlanetAcquireOutputBonus = new JSpinner[6];

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        panTechBonus.add(new JLabel("<html><b>Tech<b></html>"), gridBagConstraints);
        panIndustryBonus.add(new JLabel("<html><b>Industry<b></html>"), gridBagConstraints);
        panOutputBonus.add(new JLabel("<html><b>Output<b></html>"), gridBagConstraints);
        for (int i = EquipmentType.RATING_A; i <= EquipmentType.RATING_F; i++) {
            gridBagConstraints.gridwidth = 1;
            gridBagConstraints.gridy++;
            gridBagConstraints.gridx = 0;
            gridBagConstraints.insets = new Insets(0, 20, 0, 0);
            panTechBonus.add(new JLabel(ITechnology.getRatingName(i) + " Level"), gridBagConstraints);
            panIndustryBonus.add(new JLabel(ITechnology.getRatingName(i) + " Level"), gridBagConstraints);
            panOutputBonus.add(new JLabel(ITechnology.getRatingName(i) + " Level"), gridBagConstraints);
            gridBagConstraints.gridx = 1;
            gridBagConstraints.insets = new Insets(0, 10, 0, 0);
            spnPlanetAcquireTechBonus[i] = new JSpinner(new SpinnerNumberModel(0, -12, 12, 1));
            spnPlanetAcquireIndustryBonus[i] = new JSpinner(new SpinnerNumberModel(0, -12, 12, 1));
            spnPlanetAcquireOutputBonus[i] = new JSpinner(new SpinnerNumberModel(0, -12, 12, 1));
            ((JSpinner.DefaultEditor) spnPlanetAcquireTechBonus[i].getEditor()).getTextField().setEditable(false);
            ((JSpinner.DefaultEditor) spnPlanetAcquireIndustryBonus[i].getEditor()).getTextField().setEditable(false);
            ((JSpinner.DefaultEditor) spnPlanetAcquireOutputBonus[i].getEditor()).getTextField().setEditable(false);

            panTechBonus.add(spnPlanetAcquireTechBonus[i], gridBagConstraints);
            panOutputBonus.add(spnPlanetAcquireOutputBonus[i], gridBagConstraints);
            panIndustryBonus.add(spnPlanetAcquireIndustryBonus[i], gridBagConstraints);

        }

        panSocioIndustrialBonus.add(panTechBonus);
        panSocioIndustrialBonus.add(panIndustryBonus);
        panSocioIndustrialBonus.add(panOutputBonus);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.gridheight = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubPlanetAcquire.add(panSocioIndustrialBonus, gridBagConstraints);

        tabOptions.addTab(resourceMap.getString("panSupplies.TabConstraints.tabTitle"), panSupplies);
        //endregion Supplies and Acquisition Tab

        //region Tech Limits Tab
        gridy = 0;

        panTech.setName("panTech");
        panTech.setLayout(new java.awt.GridBagLayout());

        limitByYearBox.setText(resourceMap.getString("limitByYearBox.text"));
        limitByYearBox.setToolTipText(resourceMap.getString("limitByYearBox.toolTipText"));
        limitByYearBox.setName("limitByYearBox");
        limitByYearBox.addActionListener(e -> variableTechLevelBox.setEnabled(limitByYearBox.isSelected()));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(limitByYearBox, gridBagConstraints);

        disallowExtinctStuffBox.setText(resourceMap.getString("disallowExtinctStuffBox.text"));
        disallowExtinctStuffBox.setToolTipText(resourceMap.getString("disallowExtinctStuffBox.toolTipText"));
        disallowExtinctStuffBox.setName("disallowExtinctStuffBox");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(disallowExtinctStuffBox, gridBagConstraints);

        allowClanPurchasesBox.setText(resourceMap.getString("allowClanPurchasesBox.text"));
        allowClanPurchasesBox.setToolTipText(resourceMap.getString("allowClanPurchasesBox.toolTipText"));
        allowClanPurchasesBox.setName("allowClanPurchasesBox");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(allowClanPurchasesBox, gridBagConstraints);

        allowISPurchasesBox.setText(resourceMap.getString("allowISPurchasesBox.text"));
        allowISPurchasesBox.setToolTipText(resourceMap.getString("allowISPurchasesBox.toolTipText"));
        allowISPurchasesBox.setName("allowISPurchasesBox");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(allowISPurchasesBox, gridBagConstraints);

        allowCanonOnlyBox.setText(resourceMap.getString("allowCanonOnlyBox.text"));
        allowCanonOnlyBox.setToolTipText(resourceMap.getString("allowCanonOnlyBox.toolTipText"));
        allowCanonOnlyBox.setName("allowCanonOnlyBox");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(allowCanonOnlyBox, gridBagConstraints);

        allowCanonRefitOnlyBox.setText(resourceMap.getString("allowCanonRefitOnlyBox.text")); // NOI18N
        allowCanonRefitOnlyBox.setToolTipText(resourceMap.getString("allowCanonRefitOnlyBox.toolTipText")); // NOI18N
        allowCanonRefitOnlyBox.setName("allowCanonRefitOnlyBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(allowCanonRefitOnlyBox, gridBagConstraints);

        JLabel lblTechLevel = new JLabel(resourceMap.getString("lblTechLevel.text"));
        lblTechLevel.setName("lblTechLevel");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panTech.add(lblTechLevel, gridBagConstraints);

        DefaultComboBoxModel<String> techLevelComboBoxModel = new DefaultComboBoxModel<>();
        techLevelComboBoxModel.addElement(CampaignOptions.getTechLevelName(CampaignOptions.TECH_INTRO));
        techLevelComboBoxModel.addElement(CampaignOptions.getTechLevelName(CampaignOptions.TECH_STANDARD));
        techLevelComboBoxModel.addElement(CampaignOptions.getTechLevelName(CampaignOptions.TECH_ADVANCED));
        techLevelComboBoxModel.addElement(CampaignOptions.getTechLevelName(CampaignOptions.TECH_EXPERIMENTAL));
        techLevelComboBoxModel.addElement(CampaignOptions.getTechLevelName(CampaignOptions.TECH_UNOFFICIAL));
        choiceTechLevel.setModel(techLevelComboBoxModel);
        //choiceTechLevel.setToolTipText(resourceMap.getString("choiceTechLevel.toolTipText")); // NOI18N
        choiceTechLevel.setName("choiceTechLevel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(choiceTechLevel, gridBagConstraints);

        variableTechLevelBox.setText(resourceMap.getString("variableTechLevelBox.text")); // NOI18N
        variableTechLevelBox.setToolTipText(resourceMap.getString("variableTechLevelBox.toolTipText")); // NOI18N
        variableTechLevelBox.setName("variableTechLevelBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(variableTechLevelBox, gridBagConstraints);

        factionIntroDateBox.setText(resourceMap.getString("factionIntroDateBox.text"));
        factionIntroDateBox.setToolTipText(resourceMap.getString("factionIntroDateBox.toolTipText"));
        factionIntroDateBox.setName("factionIntroDateBox");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(factionIntroDateBox, gridBagConstraints);

        useAmmoByTypeBox.setText(resourceMap.getString("useAmmoByTypeBox.text"));
        useAmmoByTypeBox.setToolTipText(resourceMap.getString("useAmmoByTypeBox.toolTipText"));
        useAmmoByTypeBox.setName("useAmmoByTypeBox");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panTech.add(useAmmoByTypeBox, gridBagConstraints);

        tabOptions.addTab(resourceMap.getString("panTech.TabConstraints.tabTitle"), panTech);
        //endregion Tech Limits Tab

        //region Personnel Tab
        tabOptions.addTab(resourceMap.getString("personnelPanel.title"), createPersonnelTab());
        //endregion Personnel Tab

        //region Finances Tab
        panFinances.setName("panFinances");
        panFinances.setLayout(new GridBagLayout());
        gridy = 0;

        payForPartsBox.setText(resourceMap.getString("payForPartsBox.text"));
        payForPartsBox.setToolTipText(resourceMap.getString("payForPartsBox.toolTipText"));
        payForPartsBox.setName("payForPartsBox");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForPartsBox, gridBagConstraints);

        payForRepairsBox.setText(resourceMap.getString("payForRepairsBox.text")); // NOI18N
        payForRepairsBox.setToolTipText(resourceMap.getString("payForRepairsBox.toolTipText")); // NOI18N
        payForRepairsBox.setName("payForRepairsBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForRepairsBox, gridBagConstraints);

        payForUnitsBox.setText(resourceMap.getString("payForUnitsBox.text")); // NOI18N
        payForUnitsBox.setToolTipText(resourceMap.getString("payForUnitsBox.toolTipText")); // NOI18N
        payForUnitsBox.setName("payForUnitsBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForUnitsBox, gridBagConstraints);

        payForSalariesBox.setText(resourceMap.getString("payForSalariesBox.text")); // NOI18N
        payForSalariesBox.setToolTipText(resourceMap.getString("payForSalariesBox.toolTipText")); // NOI18N
        payForSalariesBox.setName("payForSalariesBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForSalariesBox, gridBagConstraints);

        payForOverheadBox.setText(resourceMap.getString("payForOverheadBox.text")); // NOI18N
        payForOverheadBox.setToolTipText(resourceMap.getString("payForOverheadBox.toolTipText")); // NOI18N
        payForOverheadBox.setName("payForOverheadBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForOverheadBox, gridBagConstraints);

        payForMaintainBox.setText(resourceMap.getString("payForMaintainBox.text")); // NOI18N
        payForMaintainBox.setToolTipText(resourceMap.getString("payForMaintainBox.toolTipText")); // NOI18N
        payForMaintainBox.setName("payForMaintainBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForMaintainBox, gridBagConstraints);

        payForTransportBox.setText(resourceMap.getString("payForTransportBox.text")); // NOI18N
        payForTransportBox.setToolTipText(resourceMap.getString("payForTransportBox.toolTipText")); // NOI18N
        payForTransportBox.setName("payForTransportBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForTransportBox, gridBagConstraints);

        sellUnitsBox.setText(resourceMap.getString("sellUnitsBox.text")); // NOI18N
        sellUnitsBox.setToolTipText(resourceMap.getString("sellUnitsBox.toolTipText")); // NOI18N
        sellUnitsBox.setName("sellUnitsBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(sellUnitsBox, gridBagConstraints);

        sellPartsBox.setText(resourceMap.getString("sellPartsBox.text")); // NOI18N
        sellPartsBox.setToolTipText(resourceMap.getString("sellPartsBox.toolTipText")); // NOI18N
        sellPartsBox.setName("sellPartsBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(sellPartsBox, gridBagConstraints);

        payForRecruitmentBox.setText(resourceMap.getString("payForRecruitmentBox.text")); // NOI18N
        payForRecruitmentBox.setToolTipText(resourceMap.getString("payForRecruitmentBox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(payForRecruitmentBox, gridBagConstraints);

        useLoanLimitsBox.setText(resourceMap.getString("useLoanLimitsBox.text")); // NOI18N
        useLoanLimitsBox.setToolTipText(resourceMap.getString("useLoanLimitsBox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(useLoanLimitsBox, gridBagConstraints);

        // Unofficial maintenance costs
        usePercentageMaintBox = new JCheckBox(resourceMap.getString("usePercentageMaintBox.text")); // NOI18N
        usePercentageMaintBox.setToolTipText(resourceMap.getString("usePercentageMaintBox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(usePercentageMaintBox, gridBagConstraints);

        // Unofficial infantry don't count for contract pay
        useInfantryDontCountBox = new JCheckBox(resourceMap.getString("infantryDontCount.text")); // NOI18N
        useInfantryDontCountBox.setToolTipText(resourceMap.getString("infantryDontCount.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(useInfantryDontCountBox, gridBagConstraints);

        // Campaign Operations Peacetime operating costs
        usePeacetimeCostBox.setText(resourceMap.getString("usePeacetimeCostBox.text")); // NOI18N
        usePeacetimeCostBox.setToolTipText(resourceMap.getString("usePeacetimeCostBox.toolTipText")); // NOI18N
        usePeacetimeCostBox.setName("usePeacetimeCostBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(usePeacetimeCostBox, gridBagConstraints);

        useExtendedPartsModifierBox.setText(resourceMap.getString("useExtendedPartsModifierBox.text")); // NOI18N
        useExtendedPartsModifierBox.setName("useExtendedPartsModifierBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(useExtendedPartsModifierBox, gridBagConstraints);

        showPeacetimeCostBox.setText(resourceMap.getString("showPeacetimeCostBox.text")); // NOI18N
        showPeacetimeCostBox.setToolTipText(resourceMap.getString("showPeacetimeCostBox.toolTipText")); // NOI18N
        showPeacetimeCostBox.setName("showPeacetimeCostBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panFinances.add(showPeacetimeCostBox, gridBagConstraints);

        DefaultComboBoxModel<FinancialYearDuration> financialYearDurationModel = new DefaultComboBoxModel<>(FinancialYearDuration.values());
        comboFinancialYearDuration = new JComboBox<>(financialYearDurationModel);
        comboFinancialYearDuration.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof FinancialYearDuration) {
                        list.setToolTipText(((FinancialYearDuration) value).getToolTipText());
                    }
                    return this;
                }
            });
        JPanel pnlFinancialYearDuration = new JPanel();
        pnlFinancialYearDuration.add(new JLabel(resourceMap.getString("financialYearDuration.text")));
        pnlFinancialYearDuration.setToolTipText(resourceMap.getString("financialYearDuration.toolTipText"));
        pnlFinancialYearDuration.add(comboFinancialYearDuration);
        gridBagConstraints.gridy = gridy++;
        panFinances.add(pnlFinancialYearDuration, gridBagConstraints);

        newFinancialYearFinancesToCSVExportBox = new JCheckBox(resourceMap.getString("newFinancialYearFinancesToCSVExportBox.text"));
        newFinancialYearFinancesToCSVExportBox.setToolTipText(resourceMap.getString("newFinancialYearFinancesToCSVExportBox.toolTipText"));
        newFinancialYearFinancesToCSVExportBox.setName("newFinancialYearFinancesToCSVExportBox");
        gridBagConstraints.gridy = gridy++;
        panFinances.add(newFinancialYearFinancesToCSVExportBox, gridBagConstraints);

        clanPriceModifierLabel.setText(resourceMap.getString("clanPriceModifierLabel.text")); // NOI18N
        clanPriceModifierLabel.setName("clanPriceModifierLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panFinances.add(clanPriceModifierLabel, gridBagConstraints);

        spnClanPriceModifier = new JSpinner(new SpinnerNumberModel(1.0, 1.0, null, 0.1));
        spnClanPriceModifier.setEditor(new JSpinner.NumberEditor(spnClanPriceModifier, "0.00"));
        spnClanPriceModifier.setToolTipText(resourceMap.getString("clanPriceModifierJFormattedTextField.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panFinances.add(spnClanPriceModifier, gridBagConstraints);

        usedPartsValueLabel.setText(resourceMap.getString("usedPartsValueLabel.text")); // NOI18N
        usedPartsValueLabel.setName("usedPartsValueLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panFinances.add(usedPartsValueLabel, gridBagConstraints);

        spnUsedPartsValue = new JSpinner[6];
        partQualityLabels = new JLabel[spnUsedPartsValue.length];
        gridBagConstraints.gridwidth = 1;
        for (int i = Part.QUALITY_A; i <= Part.QUALITY_F; i++) {
            gridBagConstraints.gridy++;
            gridBagConstraints.gridx = 3;
            gridBagConstraints.insets = new Insets(0, 20, 0, 0);
            partQualityLabels[i] = new JLabel();
            panFinances.add(partQualityLabels[i], gridBagConstraints);
            gridBagConstraints.gridx = 2;
            gridBagConstraints.insets = new Insets(0, 10, 0, 0);
            spnUsedPartsValue[i] = new JSpinner(new SpinnerNumberModel(0.00, 0.00, 1.00, 0.05));
            spnUsedPartsValue[i].setEditor(new JSpinner.NumberEditor(spnUsedPartsValue[i], "0.00"));
            spnUsedPartsValue[i].setToolTipText(resourceMap.getString("usedPartsValueJFormattedTextField.toolTipText"));
            panFinances.add(spnUsedPartsValue[i], gridBagConstraints);
        }

        damagedPartsValueLabel.setText(resourceMap.getString("damagedPartsValueLabel.text"));
        damagedPartsValueLabel.setName("damagedPartsValueLabel");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panFinances.add(damagedPartsValueLabel, gridBagConstraints);

        spnDamagedPartsValue = new JSpinner(new SpinnerNumberModel(0.00, 0.00, 1.00, 0.05));
        spnDamagedPartsValue.setEditor(new JSpinner.NumberEditor(spnDamagedPartsValue, "0.00"));
        spnDamagedPartsValue.setToolTipText(resourceMap.getString("damagedPartsValueJFormattedTextField.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panFinances.add(spnDamagedPartsValue, gridBagConstraints);

        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panFinances.add(new JLabel("Reimbursement % (as decimal) for cancelled orders"), gridBagConstraints);

        spnOrderRefund = new JSpinner(new SpinnerNumberModel(0.00, 0.00, 1.00, 0.05));
        spnOrderRefund.setEditor(new JSpinner.NumberEditor(spnOrderRefund, "0.00"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panFinances.add(spnOrderRefund, gridBagConstraints);

        tabOptions.addTab(resourceMap.getString("panFinances.TabConstraints.tabTitle"), panFinances);
        //endregion Finances Tab

        //region Mercenary Tab
        panMercenary.setName("panMercenary");
        panMercenary.setLayout(new GridBagLayout());

        btnContractEquipment = new JRadioButton(resourceMap.getString("panMercenary.IntOpsPayment.title"));
        btnContractEquipment.setToolTipText(resourceMap.getString("panMercenary.IntOpsPayment.tooltip"));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(btnContractEquipment, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 30, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(new JLabel("Combat Percent:"), gridBagConstraints);

        spnEquipPercent = new JSpinner(new SpinnerNumberModel(0.1, 0.1, CampaignOptions.MAXIMUM_COMBAT_EQUIPMENT_PERCENT, 0.1));
        spnEquipPercent.setEditor(new JSpinner.NumberEditor(spnEquipPercent, "0.0"));
        ((JSpinner.DefaultEditor) spnEquipPercent.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(spnEquipPercent, gridBagConstraints);

        chkEquipContractSaleValue = new JCheckBox("Base on equipment sale value");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(chkEquipContractSaleValue, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 30, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(new JLabel("DropShip Percent:"), gridBagConstraints);

        spnDropshipPercent = new JSpinner(new SpinnerNumberModel(0.1, 0.0, CampaignOptions.MAXIMUM_DROPSHIP_EQUIPMENT_PERCENT, 0.1));
        spnDropshipPercent.setEditor(new JSpinner.NumberEditor(spnDropshipPercent, "0.0"));
        ((JSpinner.NumberEditor) spnDropshipPercent.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(spnDropshipPercent, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 30, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(new JLabel("JumpShip Percent:"), gridBagConstraints);

        spnJumpshipPercent = new JSpinner(new SpinnerNumberModel(0.1, 0.0, CampaignOptions.MAXIMUM_JUMPSHIP_EQUIPMENT_PERCENT, 0.1));
        spnJumpshipPercent.setEditor(new JSpinner.NumberEditor(spnJumpshipPercent, "0.0"));
        ((JSpinner.DefaultEditor) spnJumpshipPercent.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(spnJumpshipPercent, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 30, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(new JLabel("WarShip Percent:"), gridBagConstraints);

        spnWarshipPercent = new JSpinner(new SpinnerNumberModel(0.1, 0.0, CampaignOptions.MAXIMUM_WARSHIP_EQUIPMENT_PERCENT, 0.1));
        spnWarshipPercent.setEditor(new JSpinner.NumberEditor(spnWarshipPercent, "0.0"));
        ((JSpinner.DefaultEditor) spnWarshipPercent.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(spnWarshipPercent, gridBagConstraints);

        btnContractPersonnel = new JRadioButton(resourceMap.getString("panMercenary.FMMRPayment.title"));
        btnContractPersonnel.setToolTipText(resourceMap.getString("panMercenary.FMMRPayment.tooltip"));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panMercenary.add(btnContractPersonnel, gridBagConstraints);

        chkBLCSaleValue = new JCheckBox("Base battle loss compensation on equipment sale value");
        gridBagConstraints.gridy = 6;
        panMercenary.add(chkBLCSaleValue, gridBagConstraints);

        chkOverageRepaymentInFinalPayment = new JCheckBox(resourceMap.getString("chkOverageRepaymentInFinalPayment.text"));
        chkOverageRepaymentInFinalPayment.setToolTipText(resourceMap.getString("chkOverageRepaymentInFinalPayment.toolTipText"));
        gridBagConstraints.gridy = 7;
        panMercenary.add(chkOverageRepaymentInFinalPayment, gridBagConstraints);

        ButtonGroup groupContract = new ButtonGroup();
        groupContract.add(btnContractEquipment);
        groupContract.add(btnContractPersonnel);

        tabOptions.addTab(resourceMap.getString("panMercenary.TabConstraints.tabTitle"), panMercenary);
        //endregion Mercenary Tab

        //region XP Tab
        panXP.setName("panXP");
        panXP.setLayout(new java.awt.GridBagLayout());

        JLabel lblScenarioXP = new JLabel(resourceMap.getString("lblScenarioXP.text"));
        spnScenarioXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnScenarioXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnScenarioXP, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(lblScenarioXP, gridBagConstraints);

        JLabel lblKillXP = new JLabel(resourceMap.getString("lblKillXP.text"));
        spnKillXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnKillXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnKillXP, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(lblKillXP, gridBagConstraints);

        JLabel lblKills = new JLabel(resourceMap.getString("lblKills.text"));
        spnKills = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnKills.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnKills, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(lblKills, gridBagConstraints);

        JLabel lblTaskXP = new JLabel(resourceMap.getString("lblKillXP.text"));
        spnTaskXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnTaskXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnTaskXP, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(lblTaskXP, gridBagConstraints);

        JLabel lblTasks = new JLabel(resourceMap.getString("lblTasks.text"));
        spnNTasksXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnNTasksXP.getEditor()).getTextField().setEditable(false);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnNTasksXP, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(lblTasks, gridBagConstraints);

        JLabel lblSuccessXp = new JLabel(resourceMap.getString("lblSuccessXP.text"));
        spnSuccessXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnSuccessXP.getEditor()).getTextField().setEditable(false);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnSuccessXP, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(lblSuccessXp, gridBagConstraints);

        JLabel lblMistakeXP = new JLabel(resourceMap.getString("lblMistakeXP.text"));
        spnMistakeXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnMistakeXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnMistakeXP, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(lblMistakeXP, gridBagConstraints);

        spnIdleXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnIdleXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnIdleXP, gridBagConstraints);

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(new JLabel("XP for every"), gridBagConstraints);

        spnMonthsIdleXP = new JSpinner(new SpinnerNumberModel(0, 0, 36, 1));
        ((JSpinner.DefaultEditor) spnMonthsIdleXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnMonthsIdleXP, gridBagConstraints);

        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(new JLabel("active month(s) on a 2d6 roll of greater than or equal to"), gridBagConstraints);

        spnTargetIdleXP = new JSpinner(new SpinnerNumberModel(2, 2, 13, 1));
        ((JSpinner.DefaultEditor) spnTargetIdleXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnTargetIdleXP, gridBagConstraints);

        spnContractNegotiationXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnContractNegotiationXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnContractNegotiationXP, gridBagConstraints);

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(new JLabel("XP awarded to the selected negotiator for a new contract"), gridBagConstraints);

        spnAdminWeeklyXP = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnAdminWeeklyXP.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnAdminWeeklyXP, gridBagConstraints);

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(new JLabel("XP awarded to each administrator every Monday for the work of the previous"), gridBagConstraints);

        spnAdminWeeklyXPPeriod = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        ((JSpinner.DefaultEditor) spnAdminWeeklyXPPeriod.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnAdminWeeklyXPPeriod, gridBagConstraints);

        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(new JLabel("week(s)"), gridBagConstraints);

        spnEdgeCost = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
        ((JSpinner.DefaultEditor) spnEdgeCost.getEditor()).getTextField().setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(spnEdgeCost, gridBagConstraints);

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(new JLabel("XP Cost for 1 Edge Point"), gridBagConstraints);

        txtInstructionsXP = new JTextArea();
        txtInstructionsXP.setText(resourceMap.getString("txtInstructionsXP.text"));
        txtInstructionsXP.setName("txtInstructions");
        txtInstructionsXP.setEditable(false);
        txtInstructionsXP.setEditable(false);
        txtInstructionsXP.setLineWrap(true);
        txtInstructionsXP.setWrapStyleWord(true);
        txtInstructionsXP.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(resourceMap.getString("txtInstructionsXP.title")),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        txtInstructionsXP.setOpaque(false);
        txtInstructionsXP.setMinimumSize(new Dimension(550, 120));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(txtInstructionsXP, gridBagConstraints);

        tableXP = new JTable(getSkillCostsArray(SkillType.getSkillHash()), TABLE_XP_COLUMN_NAMES);
        tableXP.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableXP.setRowSelectionAllowed(false);
        tableXP.setColumnSelectionAllowed(false);
        tableXP.setCellSelectionEnabled(true);
        scrXP = new JScrollPane(tableXP);
        scrXP.setMinimumSize(new Dimension(550, 140));
        scrXP.setPreferredSize(new Dimension(550, 140));
        JTable rowTable = new RowNamesTable(tableXP);
        scrXP.setRowHeaderView(rowTable);
        scrXP.setCorner(JScrollPane.UPPER_LEFT_CORNER, rowTable.getTableHeader());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panXP.add(scrXP, gridBagConstraints);

        tabOptions.addTab(resourceMap.getString("panXP.TabConstraints.tabTitle"), panXP);
        //endregion XP Tab

        //region Skill Tab
        panSkill.setName("panSkill");
        panSkill.setLayout(new java.awt.GridBagLayout());

        JPanel skPanel;

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);

        GridBagConstraints c;
        JSpinner spnTarget;
        JSpinner spnGreen;
        JSpinner spnReg;
        JSpinner spnVet;
        JSpinner spnElite;
        SkillType type;
        JLabel lblSkill;
        for (String skillName : SkillType.getSkillList()) {
            type = SkillType.getType(skillName);
            skPanel = new JPanel();
            c = new java.awt.GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 1.0;
            c.weighty = 1.0;
            c.fill = java.awt.GridBagConstraints.BOTH;
            c.anchor = java.awt.GridBagConstraints.WEST;
            c.insets = new java.awt.Insets(5, 5, 5, 5);
            lblSkill = new JLabel(resourceMap.getString("lblSkillTarget.text"));
            skPanel.add(lblSkill, c);
            c.gridx++;
            spnTarget = new JSpinner(new SpinnerNumberModel(type.getTarget(), 0, 12, 1));
            ((JSpinner.DefaultEditor) spnTarget.getEditor()).getTextField().setEditable(false);
            hashSkillTargets.put(skillName, spnTarget);
            skPanel.add(spnTarget, c);
            c.gridx++;
            lblSkill = new JLabel(resourceMap.getString("lblSkillGreen.text"));
            skPanel.add(lblSkill, c);
            c.gridx++;
            spnGreen = new JSpinner(new SpinnerNumberModel(type.getGreenLevel(), 0, 10, 1));
            ((JSpinner.DefaultEditor) spnGreen.getEditor()).getTextField().setEditable(false);
            hashGreenSkill.put(skillName, spnGreen);
            skPanel.add(spnGreen, c);
            c.gridx++;
            lblSkill = new JLabel(resourceMap.getString("lblSkillRegular.text"));
            skPanel.add(lblSkill, c);
            c.gridx++;
            spnReg = new JSpinner(new SpinnerNumberModel(type.getRegularLevel(), 0, 10, 1));
            ((JSpinner.DefaultEditor) spnReg.getEditor()).getTextField().setEditable(false);
            hashRegSkill.put(skillName, spnReg);
            skPanel.add(spnReg, c);
            c.gridx++;
            lblSkill = new JLabel(resourceMap.getString("lblSkillVeteran.text"));
            skPanel.add(lblSkill, c);
            c.gridx++;
            spnVet = new JSpinner(new SpinnerNumberModel(type.getVeteranLevel(), 0, 10, 1));
            ((JSpinner.DefaultEditor) spnVet.getEditor()).getTextField().setEditable(false);
            hashVetSkill.put(skillName, spnVet);
            skPanel.add(spnVet, c);
            c.gridx++;
            lblSkill = new JLabel(resourceMap.getString("lblSkillElite.text"));
            skPanel.add(lblSkill, c);
            c.gridx++;
            spnElite = new JSpinner(new SpinnerNumberModel(type.getEliteLevel(), 0, 10, 1));
            ((JSpinner.DefaultEditor) spnElite.getEditor()).getTextField().setEditable(false);
            hashEliteSkill.put(skillName, spnElite);
            skPanel.add(spnElite, c);
            c.gridx++;

            skPanel.setBorder(BorderFactory.createTitledBorder(skillName));
            panSkill.add(skPanel, gridBagConstraints);
            gridBagConstraints.gridy++;
        }

        JScrollPane scrSkill = new JScrollPane(panSkill);
        scrSkill.setPreferredSize(new java.awt.Dimension(500, 400));

        tabOptions.addTab(resourceMap.getString("panSkill.TabConstraints.tabTitle"), scrSkill);
        //endregion Skills Tab

        //region Special Abilities Tab
        panSpecialAbilities = new JPanel(new GridBagLayout());

        Set<String> spaNames = SpecialAbility.getAllSpecialAbilities().keySet();
        //We need to create a temporary hash of special abilities that we can modify without
        //changing the underlying one in case the user cancels the changes
        tempSPA = new Hashtable<>();
        for (String name : spaNames) {
            getCurrentSPA().put(name, SpecialAbility.getAbility(name).clone());
        }

        btnAddSPA = new JButton("Add Another Special Ability");
        btnAddSPA.addActionListener(evt -> btnAddSPA());

        recreateSPAPanel(!getUnusedSPA().isEmpty());

        JScrollPane scrSPA = new JScrollPane(panSpecialAbilities);
        scrSPA.setPreferredSize(new java.awt.Dimension(500, 400));

        tabOptions.addTab("Special Abilities", scrSPA);
        //endregion Special Abilities Tab

        //region Skill Randomization Tab
        panRandomSkill.setName("panRandomSkill");
        panRandomSkill.setLayout(new java.awt.GridBagLayout());

        JPanel panRollTable = new JPanel(new GridLayout(6, 3, 5, 0));
        panRollTable.add(new JLabel("<html><b>Value</b></html>"));
        panRollTable.add(new JLabel("<html><b>Level</b></html>"));
        panRollTable.add(new JLabel("<html><b># Abils</b></html>"));
        panRollTable.add(new JLabel("less than 2"));
        JLabel lblUltraGreen = new JLabel("Ultra-Green/None");
        lblUltraGreen.setToolTipText(resourceMap.getString("lblUltraGreen.toolTipText"));
        panRollTable.add(lblUltraGreen);
        panRollTable.add(new JLabel("0"));
        panRollTable.add(new JLabel("2-5"));
        panRollTable.add(new JLabel(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_GREEN]));
        panRollTable.add(new JLabel("0"));
        panRollTable.add(new JLabel("6-9"));
        panRollTable.add(new JLabel(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_REGULAR]));
        panRollTable.add(new JLabel("0"));
        panRollTable.add(new JLabel("10-11"));
        panRollTable.add(new JLabel(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_VETERAN]));
        panRollTable.add(new JLabel("1"));
        panRollTable.add(new JLabel("12 or more"));
        panRollTable.add(new JLabel(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_ELITE]));
        panRollTable.add(new JLabel("2"));
        panRollTable.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("2d6 + Bonus"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        JLabel lblOverallRecruitBonus = new JLabel(resourceMap.getString("lblOverallRecruitBonus.text"));
        chkExtraRandom = new JCheckBox(resourceMap.getString("chkExtraRandom.text"));
        chkExtraRandom.setToolTipText(resourceMap.getString("chkExtraRandom.toolTipText"));
        JLabel lblProbAntiMek = new JLabel(resourceMap.getString("lblProbAntiMek.text"));
        spnProbAntiMek = new JSpinner(new SpinnerNumberModel(0, 0, 100, 5));
        ((JSpinner.DefaultEditor) spnProbAntiMek.getEditor()).getTextField().setEditable(false);
        spnOverallRecruitBonus = new JSpinner(new SpinnerNumberModel(0, -12, 12, 1));
        ((JSpinner.DefaultEditor) spnOverallRecruitBonus.getEditor()).getTextField().setEditable(false);
        spnOverallRecruitBonus.setToolTipText(resourceMap.getString("spnOverallRecruitBonus.toolTipText"));
        spnTypeRecruitBonus = new JSpinner[personnelRoles.length];
        int nRow = (int) Math.ceil(personnelRoles.length / 4.0);
        JPanel panTypeRecruitBonus = new JPanel(new GridLayout(nRow, 4));
        JSpinner spin;
        JPanel panRecruit;
        for (PersonnelRole role : personnelRoles) {
            panRecruit = new JPanel(new GridBagLayout());

            spin = new JSpinner(new SpinnerNumberModel(0, -12, 12, 1));
            ((JSpinner.DefaultEditor) spin.getEditor()).getTextField().setEditable(false);
            spnTypeRecruitBonus[role.ordinal()] = spin;
            gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.anchor = GridBagConstraints.WEST;
            gridBagConstraints.insets = new Insets(2, 5, 0, 0);
            panRecruit.add(spin, gridBagConstraints);

            gridBagConstraints.gridx = 1;
            gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
            gridBagConstraints.weightx = 1.0;
            panRecruit.add(new JLabel(role.toString()), gridBagConstraints);

            panTypeRecruitBonus.add(panRecruit);
        }

        panTypeRecruitBonus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(resourceMap.getString("panTypeRecruitBonus.title")),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(chkExtraRandom, gridBagConstraints);

        // Phenotype Percentage Generation
        List<Phenotype> phenotypes = Phenotype.getExternalPhenotypes();
        phenotypeSpinners = new JSpinner[phenotypes.size()];

        JPanel phenotypesPanel = new JPanel(new GridLayout((int) Math.ceil(phenotypes.size() / 2.0), 2));
        phenotypesPanel.setBorder(BorderFactory.createTitledBorder("Trueborn Phenotype Probabilities"));

        for (int i = 0; i < phenotypes.size(); i++) {
            JSpinner phenotypeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
            phenotypeSpinners[i] = phenotypeSpinner;
            JPanel phenotypePanel = new JPanel();
            phenotypePanel.add(phenotypeSpinner);
            phenotypePanel.add(new JLabel(phenotypes.get(i).getName()));
            phenotypePanel.setToolTipText(phenotypes.get(i).getToolTip());
            phenotypesPanel.add(phenotypePanel);
        }

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        panRandomSkill.add(phenotypesPanel, gridBagConstraints);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(panRollTable, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(spnProbAntiMek, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(lblProbAntiMek, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(spnOverallRecruitBonus, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(lblOverallRecruitBonus, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(panTypeRecruitBonus, gridBagConstraints);

        JPanel panArtillery = new JPanel();
        panArtillery.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Artillery Skill"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        spnArtyProb = new JSpinner(new SpinnerNumberModel(0, 0, 100, 5));
        ((JSpinner.DefaultEditor) spnArtyProb.getEditor()).getTextField().setEditable(false);
        spnArtyProb.setToolTipText(resourceMap.getString("spnArtyProb.toolTipText"));
        panArtillery.add(spnArtyProb);
        panArtillery.add(new JLabel("Probability"));
        spnArtyBonus = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnArtyBonus.getEditor()).getTextField().setEditable(false);
        panArtillery.add(spnArtyBonus);
        panArtillery.add(new JLabel("Bonus"));
        JPanel panSecondary = new JPanel();
        spnSecondProb = new JSpinner(new SpinnerNumberModel(0, 0, 100, 5));
        ((JSpinner.DefaultEditor) spnSecondProb.getEditor()).getTextField().setEditable(false);
        spnSecondProb.setToolTipText(resourceMap.getString("spnSecondProb.toolTipText"));
        panSecondary.add(spnSecondProb);
        panSecondary.add(new JLabel("Probability"));
        spnSecondBonus = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnSecondBonus.getEditor()).getTextField().setEditable(false);
        panSecondary.add(spnSecondBonus);
        panSecondary.add(new JLabel("Bonus"));
        panSecondary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Secondary Skills"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        JPanel panTactics = new JPanel();
        spnTacticsGreen = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnTacticsGreen.getEditor()).getTextField().setEditable(false);
        spnTacticsGreen.setToolTipText(resourceMap.getString("spnTacticsGreen.toolTipText"));
        spnTacticsReg = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnTacticsReg.getEditor()).getTextField().setEditable(false);
        spnTacticsReg.setToolTipText(resourceMap.getString("spnTacticsReg.toolTipText"));
        spnTacticsVet = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnTacticsVet.getEditor()).getTextField().setEditable(false);
        spnTacticsVet.setToolTipText(resourceMap.getString("spnTacticsVet.toolTipText"));
        spnTacticsElite = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnTacticsElite.getEditor()).getTextField().setEditable(false);
        spnTacticsElite.setToolTipText(resourceMap.getString("spnTacticsElite.toolTipText"));
        panTactics.add(spnTacticsGreen);
        panTactics.add(new JLabel("Green"));
        panTactics.add(spnTacticsReg);
        panTactics.add(new JLabel("Reg"));
        panTactics.add(spnTacticsVet);
        panTactics.add(new JLabel("Vet"));
        panTactics.add(spnTacticsElite);
        panTactics.add(new JLabel("Elite"));
        panTactics.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Tactics Skill"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        JPanel panSmallArms = new JPanel();
        spnCombatSA = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnCombatSA.getEditor()).getTextField().setEditable(false);
        spnCombatSA.setToolTipText(resourceMap.getString("spnCombatSA.toolTipText"));
        spnSupportSA = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnSupportSA.getEditor()).getTextField().setEditable(false);
        spnSupportSA.setToolTipText(resourceMap.getString("spnSupportSA.toolTipText"));
        panSmallArms.add(spnCombatSA);
        panSmallArms.add(new JLabel("Combat Personnel"));
        panSmallArms.add(spnSupportSA);
        panSmallArms.add(new JLabel("Support Personnel"));
        panSmallArms.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Small Arms Skill"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        JPanel panAbilities = new JPanel();
        spnAbilGreen = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnAbilGreen.getEditor()).getTextField().setEditable(false);
        spnAbilGreen.setToolTipText(resourceMap.getString("spnAbilGreen.toolTipText"));
        spnAbilReg = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnAbilReg.getEditor()).getTextField().setEditable(false);
        spnAbilReg.setToolTipText(resourceMap.getString("spnAbilReg.toolTipText"));
        spnAbilVet = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnAbilVet.getEditor()).getTextField().setEditable(false);
        spnAbilVet.setToolTipText(resourceMap.getString("spnAbilVet.toolTipText"));
        spnAbilElite = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1));
        ((JSpinner.DefaultEditor) spnAbilElite.getEditor()).getTextField().setEditable(false);
        spnAbilElite.setToolTipText(resourceMap.getString("spnAbilElite.toolTipText"));
        panAbilities.add(spnAbilGreen);
        panAbilities.add(new JLabel("Green"));
        panAbilities.add(spnAbilReg);
        panAbilities.add(new JLabel("Reg"));
        panAbilities.add(spnAbilVet);
        panAbilities.add(new JLabel("Vet"));
        panAbilities.add(spnAbilElite);
        panAbilities.add(new JLabel("Elite"));
        panAbilities.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Special Abilities"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        JPanel panOtherBonuses = new JPanel(new GridLayout(3, 2));
        panOtherBonuses.add(panArtillery);
        panOtherBonuses.add(panSecondary);
        panOtherBonuses.add(panTactics);
        panOtherBonuses.add(panAbilities);
        panOtherBonuses.add(panSmallArms);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        panRandomSkill.add(panOtherBonuses, gridBagConstraints);

        JScrollPane scrRandomSkill = new JScrollPane(panRandomSkill);
        scrRandomSkill.setPreferredSize(new java.awt.Dimension(500, 400));

        tabOptions.addTab(resourceMap.getString("panRandomSkill.TabConstraints.tabTitle"), scrRandomSkill);
        //endregion Skill Randomization Tab

        //region Rank Systems Tab
        tabOptions.addTab(resourceMap.getString("rankSystemsPanel.title"), createRankSystemsTab(frame, campaign));
        //endregion Rank Systems Tab

        //region Name and Portrait Generation Tab
        panNameGen.setName("panNameGen");
        panNameGen.setLayout(new GridBagLayout());

        chkUseOriginFactionForNames = new JCheckBox(resourceMap.getString("chkUseOriginFactionForNames.text"));
        chkUseOriginFactionForNames.setToolTipText(resourceMap.getString("chkUseOriginFactionForNames.toolTipText"));
        chkUseOriginFactionForNames.setName("chkUseOriginFactionForNames");
        chkUseOriginFactionForNames.addActionListener(
                evt -> comboFactionNames.setEnabled(!chkUseOriginFactionForNames.isSelected()));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridy = 0;
        gridBagConstraints.gridy = gridy;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panNameGen.add(chkUseOriginFactionForNames, gridBagConstraints);


        JLabel lblFactionNames = new JLabel(resourceMap.getString("lblFactionNames.text"));
        lblFactionNames.setName("lblFactionNames");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = ++gridy;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panNameGen.add(lblFactionNames, gridBagConstraints);

        DefaultComboBoxModel<String> factionNamesModel = new DefaultComboBoxModel<>();
        for (String faction : RandomNameGenerator.getInstance().getFactions()) {
            factionNamesModel.addElement(faction);
        }
        factionNamesModel.setSelectedItem(RandomNameGenerator.getInstance().getChosenFaction());
        comboFactionNames.setModel(factionNamesModel);
        comboFactionNames.setMinimumSize(new java.awt.Dimension(400, 30));
        comboFactionNames.setName("comboFactionNames");
        comboFactionNames.setPreferredSize(new java.awt.Dimension(400, 30));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = gridy;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panNameGen.add(comboFactionNames, gridBagConstraints);

        JLabel lblGender = new JLabel(resourceMap.getString("lblGender.text"));
        lblGender.setName("lblGender");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = ++gridy;
        gridBagConstraints.insets = new Insets(10, 0, 0, 0);
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        panNameGen.add(lblGender, gridBagConstraints);

        sldGender.setMaximum(100);
        sldGender.setMinimum(0);
        sldGender.setMajorTickSpacing(25);
        sldGender.setPaintTicks(true);
        sldGender.setPaintLabels(true);
        sldGender.setValue(RandomGenderGenerator.getPercentFemale());
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = gridy;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.insets = new Insets(10, 0, 0, 0);
        panNameGen.add(sldGender, gridBagConstraints);

        panRandomPortrait.setName("panRandomPortrait");
        panRandomPortrait.setLayout(new BorderLayout());

        // The math below is used to determine how to split the personnel role options for portraits,
        // which it does into 4 columns with rows equal to the number of roles plus two, with the
        // additional two being the all role and no role options.
        JPanel panUsePortrait = new JPanel(new GridLayout((int) Math.ceil((personnelRoles.length + 2) / 4.0), 4));
        chkUsePortrait = new JCheckBox[personnelRoles.length];
        allPortraitsBox = new JCheckBox(resourceMap.getString("panUsePortrait.all.text"));
        noPortraitsBox = new JCheckBox(resourceMap.getString("panUsePortrait.no.text"));
        allPortraitsBox.addActionListener(evt -> {
            final boolean selected = allPortraitsBox.isSelected();
            for (JCheckBox box : chkUsePortrait) {
                if (selected) {
                    box.setSelected(true);
                }
                box.setEnabled(!selected);
            }
            if (selected) {
                noPortraitsBox.setSelected(false);
            }
        });
        noPortraitsBox.addActionListener(evt -> {
            final boolean selected = noPortraitsBox.isSelected();
            for (JCheckBox box : chkUsePortrait) {
                if (selected) {
                    box.setSelected(false);
                }
                box.setEnabled(!selected);
            }
            if (selected) {
                allPortraitsBox.setSelected(false);
            }
        });
        panUsePortrait.add(allPortraitsBox);
        panUsePortrait.add(noPortraitsBox);

        JCheckBox box;
        for (PersonnelRole role : personnelRoles) {
            box = new JCheckBox(role.toString());
            panUsePortrait.add(box);
            chkUsePortrait[role.ordinal()] = box;
        }

        panRandomPortrait.add(panUsePortrait, BorderLayout.CENTER);
        JTextArea txtPortraitInst = new JTextArea(resourceMap.getString("txtPortraitInst.text"));
        txtPortraitInst.setPreferredSize(new Dimension(728, 60));
        txtPortraitInst.setEditable(false);
        txtPortraitInst.setLineWrap(true);
        txtPortraitInst.setWrapStyleWord(true);
        txtPortraitInst.setOpaque(false);
        panRandomPortrait.add(txtPortraitInst, BorderLayout.PAGE_START);

        panRandomPortrait.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(resourceMap.getString("panRandomPortrait.title")),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = ++gridy;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new Insets(10, 0, 0, 0);
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panNameGen.add(panRandomPortrait, gridBagConstraints);

        chkAssignPortraitOnRoleChange.setText(resourceMap.getString("chkAssignPortraitOnRoleChange.text"));
        chkAssignPortraitOnRoleChange.setToolTipText(resourceMap.getString("chkAssignPortraitOnRoleChange.toolTipText"));
        chkAssignPortraitOnRoleChange.setName("chkAssignPortraitOnRoleChange");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = ++gridy;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        panNameGen.add(chkAssignPortraitOnRoleChange, gridBagConstraints);

        tabOptions.addTab(resourceMap.getString("panNameGen.TabConstraints.tabTitle"), panNameGen);
        //endregion Name and Portrait Generation Tab

        //region Markets Tab
        tabOptions.addTab(resourceMap.getString("marketsPanel.title"), createMarketsTab());
        //endregion Markets Tab

        //region Against the Bot Tab
        panAtB = new JPanel();

        cbSkillLevel = new JComboBox<>();
        chkUseShareSystem = new JCheckBox();
        chkRetirementRolls = new JCheckBox();
        chkTrackUnitFatigue = new JCheckBox();
        chkCustomRetirementMods = new JCheckBox();
        chkFoundersNeverRetire = new JCheckBox();
        chkTrackOriginalUnit = new JCheckBox();
        chkLimitLanceWeight = new JCheckBox();
        chkLimitLanceNumUnits = new JCheckBox();
        chkUseLeadership = new JCheckBox();
        chkUseStrategy = new JCheckBox();
        spnBaseStrategyDeployment = new JSpinner();
        spnAdditionalStrategyDeployment = new JSpinner();

        chkUseAero = new JCheckBox();
        chkUseVehicles = new JCheckBox();
        chkClanVehicles = new JCheckBox();
        chkDoubleVehicles = new JCheckBox();
        chkAdjustPlayerVehicles = new JCheckBox();
        spnOpforLanceTypeMechs = new JSpinner();
        spnOpforLanceTypeMixed = new JSpinner();
        spnOpforLanceTypeVehicles = new JSpinner();
        chkOpforUsesVTOLs = new JCheckBox();
        chkOpforUsesAero = new JCheckBox();
        chkOpforUsesLocalForces = new JCheckBox();
        spnOpforAeroChance = new JSpinner();
        spnOpforLocalForceChance = new JSpinner();

        availableRats = new JList<>();
        chosenRats = new JList<>();
        btnAddRat = new JButton();
        btnRemoveRat = new JButton();
        btnMoveRatUp = new JButton();
        btnMoveRatDown = new JButton();
        chkIgnoreRatEra = new JCheckBox();

        spnSearchRadius = new JSpinner();
        chkVariableContractLength = new JCheckBox();
        chkMercSizeLimited = new JCheckBox();
        chkRestrictPartsByMission = new JCheckBox();
        chkUseLightConditions = new JCheckBox();
        chkUsePlanetaryConditions = new JCheckBox();
        chkAeroRecruitsHaveUnits = new JCheckBox();


        panAtB.setName("panAtB");
        panAtB.setLayout(new GridBagLayout());

        JPanel panSubAtBAdmin = new JPanel(new GridBagLayout());
        JPanel panSubAtBRat = new JPanel(new GridBagLayout());
        JPanel panSubAtBContract = new JPanel(new GridBagLayout());
        JPanel panSubAtBScenario = new JPanel(new GridBagLayout());
        panSubAtBAdmin.setBorder(BorderFactory.createTitledBorder("Unit Administration"));
        panSubAtBRat.setBorder(BorderFactory.createTitledBorder("Random Assignment Tables"));
        panSubAtBContract.setBorder(BorderFactory.createTitledBorder("Contract Operations"));
        panSubAtBScenario.setBorder(BorderFactory.createTitledBorder("Scenarios"));

        chkUseAtB = new JCheckBox(resourceMap.getString("chkUseAtB.text"));
        chkUseAtB.setToolTipText(resourceMap.getString("chkUseAtB.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(10, 10, 10, 10);
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panAtB.add(chkUseAtB, gridBagConstraints);
        chkUseAtB.addActionListener(ev -> {
            enableAtBComponents(panAtB, chkUseAtB.isSelected());
            enableAtBComponents(panSubAtBRat,
                    chkUseAtB.isSelected() && btnStaticRATs.isSelected());
        });

        JLabel lblSkillLevel = new JLabel(resourceMap.getString("lblSkillLevel.text"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 1;
        panAtB.add(lblSkillLevel, gridBagConstraints);

        // TODO : Switch me to use a modified RandomSkillsGenerator.levelNames
        cbSkillLevel.addItem(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_ULTRA_GREEN]);
        cbSkillLevel.addItem(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_GREEN]);
        cbSkillLevel.addItem(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_REGULAR]);
        cbSkillLevel.addItem(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_VETERAN]);
        cbSkillLevel.addItem(SkillType.SKILL_LEVEL_NAMES[SkillType.EXP_ELITE]);
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        panAtB.add(cbSkillLevel, gridBagConstraints);

        btnDynamicRATs = new JRadioButton(resourceMap.getString("btnDynamicRATs.text"));
        btnDynamicRATs.setToolTipText(resourceMap.getString("btnDynamicRATs.tooltip"));
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        panAtB.add(btnDynamicRATs, gridBagConstraints);

        btnStaticRATs = new JRadioButton(resourceMap.getString("btnStaticRATs.text"));
        btnStaticRATs.setToolTipText(resourceMap.getString("btnStaticRATs.tooltip"));
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        panAtB.add(btnStaticRATs, gridBagConstraints);
        btnStaticRATs.addItemListener(ev -> enableAtBComponents(panSubAtBRat, btnStaticRATs.isSelected()));

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        panAtB.add(panSubAtBAdmin, gridBagConstraints);
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        panAtB.add(panSubAtBRat, gridBagConstraints);
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        panAtB.add(panSubAtBContract, gridBagConstraints);
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        panAtB.add(panSubAtBScenario, gridBagConstraints);

        chkUseStratCon = new JCheckBox(resourceMap.getString("chkUseStratCon.text"));
        chkUseStratCon.setToolTipText(resourceMap.getString("chkUseStratCon.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        panAtB.add(chkUseStratCon, gridBagConstraints);

        // AtB options: "Unit Administration" frame controls
        chkUseShareSystem.setText(resourceMap.getString("chkUseShareSystem.text"));
        chkUseShareSystem.setToolTipText(resourceMap.getString("chkUseShareSystem.toolTipText"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAtBAdmin.add(chkUseShareSystem, gridBagConstraints);

        chkSharesExcludeLargeCraft = new JCheckBox(resourceMap.getString("chkSharesExcludeLargeCraft.text"));
        chkSharesExcludeLargeCraft.setToolTipText(resourceMap.getString("chkSharesExcludeLargeCraft.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkSharesExcludeLargeCraft, gridBagConstraints);

        chkSharesForAll = new JCheckBox(resourceMap.getString("chkSharesForAll.text"));
        chkSharesForAll.setToolTipText(resourceMap.getString("chkSharesForAll.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkSharesForAll, gridBagConstraints);

        chkAeroRecruitsHaveUnits.setText(resourceMap.getString("chkAeroRecruitsHaveUnits.text"));
        chkAeroRecruitsHaveUnits.setToolTipText(resourceMap.getString("chkAeroRecruitsHaveUnits.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkAeroRecruitsHaveUnits, gridBagConstraints);

        chkRetirementRolls.setText(resourceMap.getString("chkRetirementRolls.text"));
        chkRetirementRolls.setToolTipText(resourceMap.getString("chkRetirementRolls.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkRetirementRolls, gridBagConstraints);

        chkCustomRetirementMods.setText(resourceMap.getString("chkCustomRetirementMods.text"));
        chkCustomRetirementMods.setToolTipText(resourceMap.getString("chkCustomRetirementMods.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkCustomRetirementMods, gridBagConstraints);

        chkFoundersNeverRetire.setText(resourceMap.getString("chkFoundersNeverRetire.text"));
        chkFoundersNeverRetire.setToolTipText(resourceMap.getString("chkFoundersNeverRetire.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkFoundersNeverRetire, gridBagConstraints);

        chkAddDependents = new JCheckBox(resourceMap.getString("chkAddDependents.text"));
        chkAddDependents.setToolTipText(resourceMap.getString("chkAddDependents.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkAddDependents, gridBagConstraints);

        chkDependentsNeverLeave = new JCheckBox(resourceMap.getString("chkDependentsNeverLeave.text"));
        chkDependentsNeverLeave.setToolTipText(resourceMap.getString("chkDependentsNeverLeave.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkDependentsNeverLeave, gridBagConstraints);

        chkTrackUnitFatigue.setText(resourceMap.getString("chkTrackUnitFatigue.text"));
        chkTrackUnitFatigue.setToolTipText(resourceMap.getString("chkTrackUnitFatigue.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkTrackUnitFatigue, gridBagConstraints);

        chkUseLeadership.setText(resourceMap.getString("chkUseLeadership.text"));
        chkUseLeadership.setToolTipText(resourceMap.getString("chkUseLeadership.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkUseLeadership, gridBagConstraints);

        chkTrackOriginalUnit.setText(resourceMap.getString("chkTrackOriginalUnit.text"));
        chkTrackOriginalUnit.setToolTipText(resourceMap.getString("chkTrackOriginalUnit.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkTrackOriginalUnit, gridBagConstraints);

        chkUseAero.setText(resourceMap.getString("chkUseAero.text"));
        chkUseAero.setToolTipText(resourceMap.getString("chkUseAero.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkUseAero, gridBagConstraints);

        chkUseVehicles.setText(resourceMap.getString("chkUseVehicles.text"));
        chkUseVehicles.setToolTipText(resourceMap.getString("chkUseVehicles.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkUseVehicles, gridBagConstraints);

        chkClanVehicles.setText(resourceMap.getString("chkClanVehicles.text"));
        chkClanVehicles.setToolTipText(resourceMap.getString("chkClanVehicles.toolTipText"));
        gridBagConstraints.gridy++;
        panSubAtBAdmin.add(chkClanVehicles, gridBagConstraints);

        ButtonGroup group = new ButtonGroup();
        group.add(btnDynamicRATs);
        group.add(btnStaticRATs);

        chosenRatModel = new DefaultListModel<>();
        chosenRats.setModel(chosenRatModel);
        chosenRats.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chosenRats.addListSelectionListener(arg0 -> {
            btnRemoveRat.setEnabled(chosenRats.getSelectedIndex() >= 0);
            btnMoveRatUp.setEnabled(chosenRats.getSelectedIndex() > 0);
            btnMoveRatDown.setEnabled(chosenRatModel.size() > chosenRats.getSelectedIndex() + 1);
        });
        availableRatModel = new DefaultListModel<>();
        for (String rat : RATManager.getAllRATCollections().keySet()) {
            List<Integer> eras = RATManager.getAllRATCollections().get(rat);
            if (eras != null) {
                StringBuilder displayName = new StringBuilder(rat);
                if (eras.size() > 0) {
                    displayName.append(" (").append(eras.get(0));
                    if (eras.size() > 1) {
                        displayName.append("-").append(eras.get(eras.size() - 1));
                    }
                    displayName.append(")");
                }
                availableRatModel.addElement(displayName.toString());
            }
        }
        availableRats.setModel(availableRatModel);
        availableRats.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        availableRats.addListSelectionListener(arg0 -> btnAddRat.setEnabled(availableRats.getSelectedIndex() >= 0));

        JTextArea txtRatInstructions = new JTextArea();
        txtRatInstructions.setEditable(false);
        txtRatInstructions.setWrapStyleWord(true);
        txtRatInstructions.setLineWrap(true);
        txtRatInstructions.setText(resourceMap.getString("txtRatInstructions.text"));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAtBRat.add(txtRatInstructions, gridBagConstraints);

        JLabel lblChosenRats = new JLabel(resourceMap.getString("lblChosenRats.text"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 1;
        panSubAtBRat.add(lblChosenRats, gridBagConstraints);

        JLabel lblAvailableRats = new JLabel(resourceMap.getString("lblAvailableRats.text"));
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        panSubAtBRat.add(lblAvailableRats, gridBagConstraints);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        panSubAtBRat.add(chosenRats, gridBagConstraints);

        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        panSubAtBRat.add(availableRats, gridBagConstraints);

        JPanel panRatButtons = new JPanel();
        panRatButtons.setLayout(new javax.swing.BoxLayout(panRatButtons, javax.swing.BoxLayout.Y_AXIS));
        btnAddRat.setText(resourceMap.getString("btnAddRat.text"));
        btnAddRat.setToolTipText(resourceMap.getString("btnAddRat.toolTipText"));
        btnAddRat.addActionListener(arg0 -> {
            int selectedIndex = availableRats.getSelectedIndex();
            chosenRatModel.addElement(availableRats.getSelectedValue());
            availableRatModel.removeElementAt(availableRats.getSelectedIndex());
            availableRats.setSelectedIndex(Math.min(selectedIndex, availableRatModel.size() - 1));
        });
        btnAddRat.setEnabled(false);
        panRatButtons.add(btnAddRat);
        btnRemoveRat.setText(resourceMap.getString("btnRemoveRat.text"));
        btnRemoveRat.setToolTipText(resourceMap.getString("btnRemoveRat.toolTipText"));
        btnRemoveRat.addActionListener(arg0 -> {
            int selectedIndex = chosenRats.getSelectedIndex();
            availableRatModel.addElement(chosenRats.getSelectedValue());
            chosenRatModel.removeElementAt(chosenRats.getSelectedIndex());
            chosenRats.setSelectedIndex(Math.min(selectedIndex, chosenRatModel.size() - 1));
        });
        btnRemoveRat.setEnabled(false);
        panRatButtons.add(btnRemoveRat);
        btnMoveRatUp.setText(resourceMap.getString("btnMoveRatUp.text"));
        btnMoveRatUp.setToolTipText(resourceMap.getString("btnMoveRatUp.toolTipText"));
        btnMoveRatUp.addActionListener(arg0 -> {
            int selectedIndex = chosenRats.getSelectedIndex();
            String tmp = chosenRatModel.getElementAt(selectedIndex);
            chosenRatModel.setElementAt(chosenRatModel.getElementAt(selectedIndex - 1), selectedIndex);
            chosenRatModel.setElementAt(tmp, selectedIndex - 1);
            chosenRats.setSelectedIndex(selectedIndex - 1);
        });
        btnMoveRatUp.setEnabled(false);
        panRatButtons.add(btnMoveRatUp);
        btnMoveRatDown.setText(resourceMap.getString("btnMoveRatDown.text"));
        btnMoveRatDown.setToolTipText(resourceMap.getString("btnMoveRatDown.toolTipText"));
        btnMoveRatDown.addActionListener(arg0 -> {
            int selectedIndex = chosenRats.getSelectedIndex();
            String tmp = chosenRatModel.getElementAt(selectedIndex);
            chosenRatModel.setElementAt(chosenRatModel.getElementAt(selectedIndex + 1), selectedIndex);
            chosenRatModel.setElementAt(tmp, selectedIndex + 1);
            chosenRats.setSelectedIndex(selectedIndex + 1);
        });
        btnMoveRatDown.setEnabled(false);
        panRatButtons.add(btnMoveRatDown);
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        panSubAtBRat.add(panRatButtons, gridBagConstraints);

        chkIgnoreRatEra.setText(resourceMap.getString("chkIgnoreRatEra.text"));
        chkIgnoreRatEra.setToolTipText(resourceMap.getString("chkIgnoreRatEra.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        panSubAtBRat.add(chkIgnoreRatEra, gridBagConstraints);

        JLabel lblSearchRadius = new JLabel(resourceMap.getString("lblSearchRadius.text"));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        panSubAtBContract.add(lblSearchRadius, gridBagConstraints);

        spnSearchRadius.setModel(new SpinnerNumberModel(300, 100, 2500, 100));
        spnSearchRadius.setToolTipText(resourceMap.getString("spnSearchRadius.toolTipText"));
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        panSubAtBContract.add(spnSearchRadius, gridBagConstraints);

        chkVariableContractLength.setText(resourceMap.getString("chkVariableContractLength.text"));
        chkVariableContractLength.setToolTipText(resourceMap.getString("chkVariableContractLength.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(chkVariableContractLength, gridBagConstraints);

        chkMercSizeLimited.setText(resourceMap.getString("chkMercSizeLimited.text"));
        chkMercSizeLimited.setToolTipText(resourceMap.getString("chkMercSizeLimited.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(chkMercSizeLimited, gridBagConstraints);

        chkRestrictPartsByMission.setText(resourceMap.getString("chkRestrictPartsByMission.text"));
        chkRestrictPartsByMission.setToolTipText(resourceMap.getString("chkRestrictPartsByMission.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(chkRestrictPartsByMission, gridBagConstraints);

        chkLimitLanceWeight.setText(resourceMap.getString("chkLimitLanceWeight.text"));
        chkLimitLanceWeight.setToolTipText(resourceMap.getString("chkLimitLanceWeight.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(chkLimitLanceWeight, gridBagConstraints);

        chkLimitLanceNumUnits.setText(resourceMap.getString("chkLimitLanceNumUnits.text"));
        chkLimitLanceNumUnits.setToolTipText(resourceMap.getString("chkLimitLanceNumUnits.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(chkLimitLanceNumUnits, gridBagConstraints);

        JLabel lblLanceStructure = new JLabel(resourceMap.getString("lblLanceStructure.text"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 1;
        panSubAtBContract.add(lblLanceStructure, gridBagConstraints);

        chkUseStrategy.setText(resourceMap.getString("chkUseStrategy.text"));
        chkUseStrategy.setToolTipText(resourceMap.getString("chkUseStrategy.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(chkUseStrategy, gridBagConstraints);

        JLabel lblBaseStrategyDeployment = new JLabel(resourceMap.getString("lblBaseStrategyDeployment.text"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 1;
        panSubAtBContract.add(lblBaseStrategyDeployment, gridBagConstraints);

        spnBaseStrategyDeployment.setModel(new SpinnerNumberModel(0, 0, 10, 1));
        spnBaseStrategyDeployment.setToolTipText(resourceMap.getString("spnBaseStrategyDeployment.toolTipText"));
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        panSubAtBContract.add(spnBaseStrategyDeployment, gridBagConstraints);

        JLabel lblAdditionalStrategyDeployment = new JLabel(resourceMap.getString("lblAdditionalStrategyDeployment.text"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 1;
        panSubAtBContract.add(lblAdditionalStrategyDeployment, gridBagConstraints);

        spnAdditionalStrategyDeployment.setModel(new SpinnerNumberModel(0, 0, 10, 1));
        spnAdditionalStrategyDeployment.setToolTipText(resourceMap.getString("spnAdditionalStrategyDeployment.toolTipText"));
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        panSubAtBContract.add(spnAdditionalStrategyDeployment, gridBagConstraints);

        chkAdjustPaymentForStrategy = new JCheckBox(resourceMap.getString("chkAdjustPaymentForStrategy.text"));
        chkAdjustPaymentForStrategy.setName("chkAdjustPaymentForStrategy");
        chkAdjustPaymentForStrategy.setToolTipText(resourceMap.getString("chkAdjustPaymentForStrategy.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(chkAdjustPaymentForStrategy, gridBagConstraints);

        JLabel lblIntensity = new JLabel(resourceMap.getString("lblIntensity.text"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        panSubAtBContract.add(lblIntensity, gridBagConstraints);

        // Note that spnAtBBattleIntensity is located here visibly, however must be initialized
        // following the chance of battle by role

        JLabel lblBattleFrequency = new JLabel(resourceMap.getString("lblBattleFrequency.text"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(lblBattleFrequency, gridBagConstraints);

        spnAtBBattleChance = new JSpinner[AtBLanceRole.values().length - 1];

        JLabel lblFightChance = new JLabel(AtBLanceRole.FIGHTING.toString() + ":");
        gridBagConstraints.gridy = 13;
        gridBagConstraints.gridwidth = 1;
        panSubAtBContract.add(lblFightChance, gridBagConstraints);

        JSpinner atbBattleChance = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        spnAtBBattleChance[AtBLanceRole.FIGHTING.ordinal()] = atbBattleChance;
        gridBagConstraints.gridx = 1;
        panSubAtBContract.add(atbBattleChance, gridBagConstraints);

        JLabel lblDefendChance = new JLabel(AtBLanceRole.DEFENCE.toString() + ":");
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        panSubAtBContract.add(lblDefendChance, gridBagConstraints);

        atbBattleChance = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        spnAtBBattleChance[AtBLanceRole.DEFENCE.ordinal()] = atbBattleChance;
        gridBagConstraints.gridx = 1;
        panSubAtBContract.add(atbBattleChance, gridBagConstraints);

        JLabel lblScoutChance = new JLabel(AtBLanceRole.SCOUTING.toString() + ":");
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        panSubAtBContract.add(lblScoutChance, gridBagConstraints);

        atbBattleChance = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        spnAtBBattleChance[AtBLanceRole.SCOUTING.ordinal()] = atbBattleChance;
        gridBagConstraints.gridx = 1;
        panSubAtBContract.add(atbBattleChance, gridBagConstraints);

        JLabel lblTrainingChance = new JLabel(AtBLanceRole.TRAINING.toString() + ":");
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        panSubAtBContract.add(lblTrainingChance, gridBagConstraints);

        atbBattleChance = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        spnAtBBattleChance[AtBLanceRole.TRAINING.ordinal()] = atbBattleChance;
        gridBagConstraints.gridx = 1;
        panSubAtBContract.add(atbBattleChance, gridBagConstraints);

        btnIntensityUpdate = new JButton(resourceMap.getString("btnIntensityUpdate.text"));
        AtBBattleIntensityChangeListener atBBattleIntensityChangeListener = new AtBBattleIntensityChangeListener();
        btnIntensityUpdate.addChangeListener(evt -> {
            spnAtBBattleIntensity.removeChangeListener(atBBattleIntensityChangeListener);
            spnAtBBattleIntensity.setValue(determineAtBBattleIntensity());
            spnAtBBattleIntensity.addChangeListener(atBBattleIntensityChangeListener);
        });
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.gridwidth = 2;
        panSubAtBContract.add(btnIntensityUpdate, gridBagConstraints);

        // Note that this must be after the chance by role because it requires the chance by role
        // for the initial value to be calculated
        spnAtBBattleIntensity = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 0.1));
        spnAtBBattleIntensity.setToolTipText(resourceMap.getString("spnIntensity.toolTipText"));
        spnAtBBattleIntensity.addChangeListener(atBBattleIntensityChangeListener);
        spnAtBBattleIntensity.setMinimumSize(new Dimension(60, 25));
        spnAtBBattleIntensity.setPreferredSize(new Dimension(60, 25));
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 1;
        panSubAtBContract.add(spnAtBBattleIntensity, gridBagConstraints);

        chkGenerateChases = new JCheckBox(resourceMap.getString("chkGenerateChases.text"));
        chkGenerateChases.setName("chkGenerateChases");
        chkGenerateChases.setToolTipText(resourceMap.getString("chkGenerateChases.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 18;
        panSubAtBContract.add(chkGenerateChases, gridBagConstraints);

        int yTablePosition = 0;
        chkDoubleVehicles.setText(resourceMap.getString("chkDoubleVehicles.text"));
        chkDoubleVehicles.setToolTipText(resourceMap.getString("chkDoubleVehicles.toolTipText"));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(chkDoubleVehicles, gridBagConstraints);

        JLabel lblOpforLanceType = new JLabel(resourceMap.getString("lblOpforLanceType.text"));
        lblOpforLanceType.setToolTipText(resourceMap.getString("lblOpforLanceType.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(lblOpforLanceType, gridBagConstraints);

        spnOpforLanceTypeMechs.setModel(new SpinnerNumberModel(0, 0, 10, 1));
        spnOpforLanceTypeMechs.setToolTipText(resourceMap.getString("lblOpforLanceType.toolTipText"));
        spnOpforLanceTypeMixed.setModel(new SpinnerNumberModel(0, 0, 10, 1));
        spnOpforLanceTypeMixed.setToolTipText(resourceMap.getString("lblOpforLanceType.toolTipText"));
        spnOpforLanceTypeVehicles.setModel(new SpinnerNumberModel(0, 0, 10, 1));
        spnOpforLanceTypeVehicles.setToolTipText(resourceMap.getString("lblOpforLanceType.toolTipText"));
        JPanel panOpforLanceType = new JPanel();
        panOpforLanceType.add(new JLabel(resourceMap.getString("lblOpforLanceTypeMek.text")));
        panOpforLanceType.add(spnOpforLanceTypeMechs);
        panOpforLanceType.add(new JLabel(resourceMap.getString("lblOpforLanceTypeMixed.text")));
        panOpforLanceType.add(spnOpforLanceTypeMixed);
        panOpforLanceType.add(new JLabel(resourceMap.getString("lblOpforLanceTypeVehicle.text")));
        panOpforLanceType.add(spnOpforLanceTypeVehicles);
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(panOpforLanceType, gridBagConstraints);

        chkOpforUsesVTOLs.setText(resourceMap.getString("chkOpforUsesVTOLs.text"));
        chkOpforUsesVTOLs.setToolTipText(resourceMap.getString("chkOpforUsesVTOLs.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(chkOpforUsesVTOLs, gridBagConstraints);

        JPanel panOpforAero = new JPanel();
        chkOpforUsesAero.setText(resourceMap.getString("chkOpforUsesAero.text"));
        chkOpforUsesAero.setToolTipText(resourceMap.getString("chkOpforUsesAero.toolTipText"));
        JLabel lblOpforAeroChance = new JLabel(resourceMap.getString("lblOpforAeroLikelihood.text"));
        lblOpforAeroChance.setToolTipText(resourceMap.getString("lblOpforAeroLikelihood.toolTipText"));
        spnOpforAeroChance.setModel(new SpinnerNumberModel(0, 0, 6, 1));
        panOpforAero.add(chkOpforUsesAero);
        panOpforAero.add(spnOpforAeroChance);
        panOpforAero.add(lblOpforAeroChance);
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(panOpforAero, gridBagConstraints);

        JPanel panOpforLocal = new JPanel();
        chkOpforUsesLocalForces.setText(resourceMap.getString("chkOpforUsesLocalForces.text"));
        chkOpforUsesLocalForces.setToolTipText(resourceMap.getString("chkOpforUsesLocalForces.toolTipText"));
        JLabel lblOpforLocalForceChance = new JLabel(resourceMap.getString("lblOpforLocalForceLikelihood.text"));
        lblOpforLocalForceChance.setToolTipText(resourceMap.getString("lblOpforLocalForceLikelihood.toolTipText"));
        spnOpforLocalForceChance.setModel(new SpinnerNumberModel(0, 0, 6, 1));
        panOpforLocal.add(chkOpforUsesLocalForces);
        panOpforLocal.add(spnOpforLocalForceChance);
        panOpforLocal.add(lblOpforLocalForceChance);
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(0, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(panOpforLocal, gridBagConstraints);

        chkAdjustPlayerVehicles.setText(resourceMap.getString("chkAdjustPlayerVehicles.text"));
        chkAdjustPlayerVehicles.setToolTipText(resourceMap.getString("chkAdjustPlayerVehicles.toolTipText"));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(chkAdjustPlayerVehicles, gridBagConstraints);

        chkRegionalMechVariations = new JCheckBox(resourceMap.getString("chkRegionalMechVariations.text"));
        chkRegionalMechVariations.setToolTipText(resourceMap.getString("chkRegionalMechVariations.toolTipText"));
        gridBagConstraints.gridy = yTablePosition++;
        panSubAtBScenario.add(chkRegionalMechVariations, gridBagConstraints);

        chkAttachedPlayerCamouflage = new JCheckBox(resourceMap.getString("chkAttachedPlayerCamouflage.text"));
        gridBagConstraints.gridy = yTablePosition++;
        panSubAtBScenario.add(chkAttachedPlayerCamouflage, gridBagConstraints);

        chkPlayerControlsAttachedUnits = new JCheckBox(resourceMap.getString("chkPlayerControlsAttachedUnits.text"));
        gridBagConstraints.gridy = yTablePosition++;
        panSubAtBScenario.add(chkPlayerControlsAttachedUnits, gridBagConstraints);

        chkUseDropShips = new JCheckBox(resourceMap.getString("chkUseDropShips.text"));
        chkUseDropShips.setToolTipText(resourceMap.getString("chkUseDropShips.toolTipText"));
        gridBagConstraints.gridy = yTablePosition++;
        panSubAtBScenario.add(chkUseDropShips, gridBagConstraints);

        chkUseWeatherConditions = new JCheckBox(resourceMap.getString("chkUseWeatherConditions.text"));
        chkUseWeatherConditions.setToolTipText(resourceMap.getString("chkUseWeatherConditions.toolTipText"));
        gridBagConstraints.gridy = yTablePosition++;
        panSubAtBScenario.add(chkUseWeatherConditions, gridBagConstraints);

        chkUseLightConditions.setText(resourceMap.getString("chkUseLightConditions.text"));
        chkUseLightConditions.setToolTipText(resourceMap.getString("chkUseLightConditions.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(chkUseLightConditions, gridBagConstraints);

        chkUsePlanetaryConditions.setText(resourceMap.getString("chkUsePlanetaryConditions.text"));
        chkUsePlanetaryConditions.setToolTipText(resourceMap.getString("chkUsePlanetaryConditions.toolTipText"));
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = yTablePosition++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        panSubAtBScenario.add(chkUsePlanetaryConditions, gridBagConstraints);

        JScrollPane scrAtB = new JScrollPane(panAtB);
        scrAtB.setPreferredSize(new java.awt.Dimension(500, 400));

        tabOptions.addTab(resourceMap.getString("panAtB.TabConstraints.tabTitle"), scrAtB);
        enableAtBComponents(panAtB, chkUseAtB.isSelected());
        enableAtBComponents(panSubAtBRat, chkUseAtB.isSelected() && btnStaticRATs.isSelected());

        javax.swing.SwingUtilities.invokeLater(() -> {
            scrSPA.getVerticalScrollBar().setValue(0);
            scrAtB.getVerticalScrollBar().setValue(0);
        });
        //endregion Against the Bot Tab

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(tabOptions, gridBagConstraints);

        btnOkay = new JButton(resourceMap.getString("btnOkay.text"));
        btnOkay.setName("btnOkay");
        btnOkay.addActionListener(evt -> btnOkayActionPerformed());
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = GridBagConstraints.EAST;
        gridBagConstraints.weightx = 0.25;
        getContentPane().add(btnOkay, gridBagConstraints);

        btnSave = new JButton(resourceMap.getString("btnSave.text"));
        btnSave.setName("btnSave");
        btnSave.addActionListener(evt -> btnSaveActionPerformed());
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = GridBagConstraints.CENTER;
        gridBagConstraints.weightx = 0.25;
        getContentPane().add(btnSave, gridBagConstraints);

        btnLoad.setText(resourceMap.getString("btnLoad.text"));
        btnLoad.setName("btnLoad");
        btnLoad.addActionListener(evt -> btnLoadActionPerformed());
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = GridBagConstraints.CENTER;
        gridBagConstraints.weightx = 0.25;
        getContentPane().add(btnLoad, gridBagConstraints);

        btnCancel.setText(resourceMap.getString("btnCancel.text"));
        btnCancel.setName("btnCancel");
        btnCancel.addActionListener(this::btnCancelActionPerformed);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.25;
        getContentPane().add(btnCancel, gridBagConstraints);

        pack();
    }

    //region Personnel Tab
    private JScrollPane createPersonnelTab() {
        JPanel personnelPanel = new JPanel(new GridBagLayout());
        personnelPanel.setName("personnelPanel");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        personnelPanel.add(createGeneralPersonnelPanel(), gbc);

        gbc.gridx++;
        personnelPanel.add(createExpandedPersonnelInformationPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        personnelPanel.add(createMedicalPanel(), gbc);

        gbc.gridx++;
        personnelPanel.add(createPrisonerPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        personnelPanel.add(createPersonnelRandomizationPanel(), gbc);

        gbc.gridx++;
        personnelPanel.add(createFamilyPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        personnelPanel.add(createSalaryPanel(), gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        personnelPanel.add(createMarriagePanel(), gbc);

        gbc.gridx++;
        personnelPanel.add(createProcreationPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        personnelPanel.add(createDeathPanel(), gbc);

        JScrollPane scrollPersonnel = new JScrollPane(personnelPanel);
        scrollPersonnel.setPreferredSize(new Dimension(500, 400));

        return scrollPersonnel;
    }

    private JPanel createGeneralPersonnelPanel() {
        // Create Panel Components
        chkUseTactics = new JCheckBox(resources.getString("chkUseTactics.text"));
        chkUseTactics.setToolTipText(resources.getString("chkUseTactics.toolTipText"));
        chkUseTactics.setName("chkUseTactics");

        chkUseInitiativeBonus = new JCheckBox(resources.getString("chkUseInitiativeBonus.text"));
        chkUseInitiativeBonus.setToolTipText(resources.getString("chkUseInitiativeBonus.toolTipText"));
        chkUseInitiativeBonus.setName("chkUseInitiativeBonus");

        chkUseToughness = new JCheckBox(resources.getString("chkUseToughness.text"));
        chkUseToughness.setToolTipText(resources.getString("chkUseToughness.toolTipText"));
        chkUseToughness.setName("chkUseToughness");

        chkUseArtillery = new JCheckBox(resources.getString("chkUseArtillery.text"));
        chkUseArtillery.setToolTipText(resources.getString("chkUseArtillery.toolTipText"));
        chkUseArtillery.setName("chkUseArtillery");

        chkUseAbilities = new JCheckBox(resources.getString("chkUseAbilities.text"));
        chkUseAbilities.setToolTipText(resources.getString("chkUseAbilities.toolTipText"));
        chkUseAbilities.setName("chkUseAbilities");

        chkUseEdge = new JCheckBox(resources.getString("chkUseEdge.text"));
        chkUseEdge.setToolTipText(resources.getString("chkUseEdge.toolTipText"));
        chkUseEdge.setName("chkUseEdge");
        chkUseEdge.addActionListener(evt -> chkUseSupportEdge.setEnabled(chkUseEdge.isSelected()));

        chkUseSupportEdge = new JCheckBox(resources.getString("chkUseSupportEdge.text"));
        chkUseSupportEdge.setToolTipText(resources.getString("chkUseSupportEdge.toolTipText"));
        chkUseSupportEdge.setName("chkUseSupportEdge");

        chkUseImplants = new JCheckBox(resources.getString("chkUseImplants.text"));
        chkUseImplants.setToolTipText(resources.getString("chkUseImplants.toolTipText"));
        chkUseImplants.setName("chkUseImplants");

        chkUseAlternativeQualityAveraging = new JCheckBox(resources.getString("chkUseAlternativeQualityAveraging.text"));
        chkUseAlternativeQualityAveraging.setToolTipText(resources.getString("chkUseAlternativeQualityAveraging.toolTipText"));
        chkUseAlternativeQualityAveraging.setName("chkUseAlternativeQualityAveraging");

        chkUseTransfers = new JCheckBox(resources.getString("chkUseTransfers.text"));
        chkUseTransfers.setToolTipText(resources.getString("chkUseTransfers.toolTipText"));
        chkUseTransfers.setName("chkUseTransfers");

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(""));
        panel.setName("generalPersonnelPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkUseTactics)
                        .addComponent(chkUseInitiativeBonus)
                        .addComponent(chkUseToughness)
                        .addComponent(chkUseArtillery)
                        .addComponent(chkUseAbilities)
                        .addComponent(chkUseEdge)
                        .addComponent(chkUseSupportEdge)
                        .addComponent(chkUseImplants)
                        .addComponent(chkUseAlternativeQualityAveraging)
                        .addComponent(chkUseTransfers)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkUseTactics)
                        .addComponent(chkUseInitiativeBonus)
                        .addComponent(chkUseToughness)
                        .addComponent(chkUseArtillery)
                        .addComponent(chkUseAbilities)
                        .addComponent(chkUseEdge)
                        .addComponent(chkUseSupportEdge)
                        .addComponent(chkUseImplants)
                        .addComponent(chkUseAlternativeQualityAveraging)
                        .addComponent(chkUseTransfers)
        );

        return panel;
    }

    private JPanel createExpandedPersonnelInformationPanel() {
        // Initialize Labels Used in ActionListeners
        JLabel lblTimeInServiceDisplayFormat = new JLabel();
        JLabel lblTimeInRankDisplayFormat = new JLabel();

        // Create Panel Components
        chkUseTimeInService = new JCheckBox(resources.getString("chkUseTimeInService.text"));
        chkUseTimeInService.setToolTipText(resources.getString("chkUseTimeInService.toolTipText"));
        chkUseTimeInService.setName("chkUseTimeInService");
        chkUseTimeInService.addActionListener(evt -> {
            lblTimeInServiceDisplayFormat.setEnabled(chkUseTimeInService.isSelected());
            comboTimeInServiceDisplayFormat.setEnabled(chkUseTimeInService.isSelected());
        });

        lblTimeInServiceDisplayFormat.setText(resources.getString("lblTimeInServiceDisplayFormat.text"));
        lblTimeInServiceDisplayFormat.setToolTipText(resources.getString("lblTimeInServiceDisplayFormat.toolTipText"));
        lblTimeInServiceDisplayFormat.setName("lblTimeInServiceDisplayFormat");

        comboTimeInServiceDisplayFormat = new JComboBox<>(TimeInDisplayFormat.values());
        comboTimeInServiceDisplayFormat.setToolTipText(resources.getString("lblTimeInServiceDisplayFormat.toolTipText"));
        comboTimeInServiceDisplayFormat.setName("comboTimeInServiceDisplayFormat");

        chkUseTimeInRank = new JCheckBox(resources.getString("chkUseTimeInRank.text"));
        chkUseTimeInRank.setToolTipText(resources.getString("chkUseTimeInRank.toolTipText"));
        chkUseTimeInRank.setName("chkUseTimeInRank");
        chkUseTimeInRank.addActionListener(evt -> {
            lblTimeInRankDisplayFormat.setEnabled(chkUseTimeInRank.isSelected());
            comboTimeInRankDisplayFormat.setEnabled(chkUseTimeInRank.isSelected());
        });

        lblTimeInRankDisplayFormat.setText(resources.getString("lblTimeInRankDisplayFormat.text"));
        lblTimeInRankDisplayFormat.setToolTipText(resources.getString("lblTimeInRankDisplayFormat.toolTipText"));
        lblTimeInRankDisplayFormat.setName("lblTimeInRankDisplayFormat");

        comboTimeInRankDisplayFormat = new JComboBox<>(TimeInDisplayFormat.values());
        comboTimeInRankDisplayFormat.setToolTipText(resources.getString("lblTimeInRankDisplayFormat.toolTipText"));
        comboTimeInRankDisplayFormat.setName("comboTimeInRankDisplayFormat");

        chkUseRetirementDateTracking = new JCheckBox(resources.getString("chkUseRetirementDateTracking.text"));
        chkUseRetirementDateTracking.setToolTipText(resources.getString("chkUseRetirementDateTracking.toolTipText"));
        chkUseRetirementDateTracking.setName("chkUseRetirementDateTracking");

        chkTrackTotalEarnings = new JCheckBox(resources.getString("chkTrackTotalEarnings.text"));
        chkTrackTotalEarnings.setToolTipText(resources.getString("chkTrackTotalEarnings.toolTipText"));
        chkTrackTotalEarnings.setName("chkTrackTotalEarnings");

        chkShowOriginFaction = new JCheckBox(resources.getString("chkShowOriginFaction.text"));
        chkShowOriginFaction.setToolTipText(resources.getString("chkShowOriginFaction.toolTipText"));
        chkShowOriginFaction.setName("chkShowOriginFaction");

        // Programmatically Assign Accessibility Labels
        lblTimeInServiceDisplayFormat.setLabelFor(comboTimeInServiceDisplayFormat);
        lblTimeInRankDisplayFormat.setLabelFor(comboTimeInRankDisplayFormat);

        // Disable Panel Portions by Default
        chkUseTimeInService.setSelected(true);
        chkUseTimeInService.doClick();
        chkUseTimeInRank.setSelected(true);
        chkUseTimeInRank.doClick();

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("expandedPersonnelInformationPanel.title")));
        panel.setName("expandedPersonnelInformationPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkUseTimeInService)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblTimeInServiceDisplayFormat)
                                .addComponent(comboTimeInServiceDisplayFormat, GroupLayout.Alignment.LEADING))
                        .addComponent(chkUseTimeInRank)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblTimeInRankDisplayFormat)
                                .addComponent(comboTimeInRankDisplayFormat, GroupLayout.Alignment.LEADING))
                        .addComponent(chkUseRetirementDateTracking)
                        .addComponent(chkTrackTotalEarnings)
                        .addComponent(chkShowOriginFaction)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkUseTimeInService)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblTimeInServiceDisplayFormat)
                                .addComponent(comboTimeInServiceDisplayFormat))
                        .addComponent(chkUseTimeInRank)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblTimeInRankDisplayFormat)
                                .addComponent(comboTimeInRankDisplayFormat))
                        .addComponent(chkUseRetirementDateTracking)
                        .addComponent(chkTrackTotalEarnings)
                        .addComponent(chkShowOriginFaction)
        );

        return panel;
    }

    private JPanel createMedicalPanel() {
        // Create Panel Components
        chkUseAdvancedMedical = new JCheckBox(resources.getString("chkUseAdvancedMedical.text"));
        chkUseAdvancedMedical.setToolTipText(resources.getString("chkUseAdvancedMedical.toolTipText"));
        chkUseAdvancedMedical.setName("chkUseAdvancedMedical");

        JLabel lblHealWaitingPeriod = new JLabel(resources.getString("lblHealWaitingPeriod.text"));
        lblHealWaitingPeriod.setToolTipText(resources.getString("lblHealWaitingPeriod.toolTipText"));
        lblHealWaitingPeriod.setName("lblHealWaitingPeriod");

        spnHealWaitingPeriod = new JSpinner(new SpinnerNumberModel(1, 1, 30, 1));
        spnHealWaitingPeriod.setToolTipText(resources.getString("lblHealWaitingPeriod.toolTipText"));
        spnHealWaitingPeriod.setName("spnHealWaitingPeriod");

        JLabel lblNaturalHealWaitingPeriod = new JLabel(resources.getString("lblNaturalHealWaitingPeriod.text"));
        lblNaturalHealWaitingPeriod.setToolTipText(resources.getString("lblNaturalHealWaitingPeriod.toolTipText"));
        lblNaturalHealWaitingPeriod.setName("lblNaturalHealWaitingPeriod");

        spnNaturalHealWaitingPeriod = new JSpinner(new SpinnerNumberModel(1, 1, 365, 1));
        spnNaturalHealWaitingPeriod.setToolTipText(resources.getString("lblNaturalHealWaitingPeriod.toolTipText"));
        spnNaturalHealWaitingPeriod.setName("spnNaturalHealWaitingPeriod");

        JLabel lblMinimumHitsForVehicles = new JLabel(resources.getString("lblMinimumHitsForVehicles.text"));
        lblMinimumHitsForVehicles.setToolTipText(resources.getString("lblMinimumHitsForVehicles.toolTipText"));
        lblMinimumHitsForVehicles.setName("lblMinimumHitsForVehicles");

        spnMinimumHitsForVehicles = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
        spnMinimumHitsForVehicles.setToolTipText(resources.getString("lblMinimumHitsForVehicles.toolTipText"));
        spnMinimumHitsForVehicles.setName("spnMinimumHitsForVehicles");
        ((JSpinner.DefaultEditor) spnMinimumHitsForVehicles.getEditor()).getTextField().setEditable(false);

        chkUseRandomHitsForVehicles = new JCheckBox(resources.getString("chkUseRandomHitsForVehicles.text"));
        chkUseRandomHitsForVehicles.setToolTipText(resources.getString("chkUseRandomHitsForVehicles.toolTipText"));
        chkUseRandomHitsForVehicles.setName("chkUseRandomHitsForVehicles");

        chkUseTougherHealing = new JCheckBox(resources.getString("chkUseTougherHealing.text"));
        chkUseTougherHealing.setToolTipText(resources.getString("chkUseTougherHealing.toolTipText"));
        chkUseTougherHealing.setName("chkUseTougherHealing");

        // Programmatically Assign Accessibility Labels
        lblHealWaitingPeriod.setLabelFor(spnHealWaitingPeriod);
        lblNaturalHealWaitingPeriod.setLabelFor(spnNaturalHealWaitingPeriod);
        lblMinimumHitsForVehicles.setLabelFor(spnMinimumHitsForVehicles);

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("medicalPanel.title")));
        panel.setName("medicalPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkUseAdvancedMedical)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblHealWaitingPeriod)
                                .addComponent(spnHealWaitingPeriod, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblNaturalHealWaitingPeriod)
                                .addComponent(spnNaturalHealWaitingPeriod, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblMinimumHitsForVehicles)
                                .addComponent(spnMinimumHitsForVehicles, GroupLayout.Alignment.LEADING))
                        .addComponent(chkUseRandomHitsForVehicles)
                        .addComponent(chkUseTougherHealing)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkUseAdvancedMedical)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblHealWaitingPeriod)
                                .addComponent(spnHealWaitingPeriod))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblNaturalHealWaitingPeriod)
                                .addComponent(spnNaturalHealWaitingPeriod))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblMinimumHitsForVehicles)
                                .addComponent(spnMinimumHitsForVehicles))
                        .addComponent(chkUseRandomHitsForVehicles)
                        .addComponent(chkUseTougherHealing)
        );

        return panel;
    }

    private JPanel createPrisonerPanel() {
        // Create Panel Components
        JLabel lblPrisonerCaptureStyle = new JLabel(resources.getString("lblPrisonerCaptureStyle.text"));
        lblPrisonerCaptureStyle.setToolTipText(resources.getString("lblPrisonerCaptureStyle.toolTipText"));
        lblPrisonerCaptureStyle.setName("lblPrisonerCaptureStyle");

        comboPrisonerCaptureStyle = new JComboBox<>(PrisonerCaptureStyle.values());
        comboPrisonerCaptureStyle.setName("comboPrisonerCaptureStyle");
        comboPrisonerCaptureStyle.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value,
                                                          final int index, final boolean isSelected,
                                                          final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PrisonerCaptureStyle) {
                    list.setToolTipText(((PrisonerCaptureStyle) value).getToolTip());
                }
                return this;
            }
        });

        JLabel lblPrisonerStatus = new JLabel(resources.getString("lblPrisonerStatus.text"));
        lblPrisonerStatus.setToolTipText(resources.getString("lblPrisonerStatus.toolTipText"));
        lblPrisonerStatus.setName("lblPrisonerStatus");

        DefaultComboBoxModel<PrisonerStatus> prisonerStatusModel = new DefaultComboBoxModel<>(PrisonerStatus.values());
        prisonerStatusModel.removeElement(PrisonerStatus.FREE); // we don't want this as a standard use case for prisoners
        comboPrisonerStatus = new JComboBox<>(prisonerStatusModel);
        comboPrisonerStatus.setToolTipText(resources.getString("lblPrisonerStatus.toolTipText"));
        comboPrisonerStatus.setName("comboPrisonerStatus");

        chkPrisonerBabyStatus = new JCheckBox(resources.getString("chkPrisonerBabyStatus.text"));
        chkPrisonerBabyStatus.setToolTipText(resources.getString("chkPrisonerBabyStatus.toolTipText"));
        chkPrisonerBabyStatus.setName("chkPrisonerBabyStatus");

        chkAtBPrisonerDefection = new JCheckBox(resources.getString("chkAtBPrisonerDefection.text"));
        chkAtBPrisonerDefection.setToolTipText(resources.getString("chkAtBPrisonerDefection.toolTipText"));
        chkAtBPrisonerDefection.setName("chkAtBPrisonerDefection");

        chkAtBPrisonerRansom = new JCheckBox(resources.getString("chkAtBPrisonerRansom.text"));
        chkAtBPrisonerRansom.setToolTipText(resources.getString("chkAtBPrisonerRansom.toolTipText"));
        chkAtBPrisonerRansom.setName("chkAtBPrisonerRansom");

        // Programmatically Assign Accessibility Labels
        lblPrisonerCaptureStyle.setLabelFor(comboPrisonerCaptureStyle);
        lblPrisonerStatus.setLabelFor(comboPrisonerStatus);

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("prisonerPanel.title")));
        panel.setName("prisonerPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPrisonerCaptureStyle)
                                .addComponent(comboPrisonerCaptureStyle, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPrisonerStatus)
                                .addComponent(comboPrisonerStatus, GroupLayout.Alignment.LEADING))
                        .addComponent(chkPrisonerBabyStatus)
                        .addComponent(chkAtBPrisonerDefection)
                        .addComponent(chkAtBPrisonerRansom)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPrisonerCaptureStyle)
                                .addComponent(comboPrisonerCaptureStyle))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPrisonerStatus)
                                .addComponent(comboPrisonerStatus))
                        .addComponent(chkPrisonerBabyStatus)
                        .addComponent(chkAtBPrisonerDefection)
                        .addComponent(chkAtBPrisonerRansom)
        );

        return panel;
    }

    private JPanel createPersonnelRandomizationPanel() {
        // Create Panel Components
        chkUseDylansRandomXP = new JCheckBox(resources.getString("chkUseDylansRandomXP.text"));
        chkUseDylansRandomXP.setToolTipText(resources.getString("chkUseDylansRandomXP.toolTipText"));
        chkUseDylansRandomXP.setName("chkUseDylansRandomXP");

        JPanel randomOriginPanel = createRandomOriginPanel();

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("personnelRandomizationPanel.title")));
        panel.setName("personnelRandomizationPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkUseDylansRandomXP)
                        .addComponent(randomOriginPanel)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkUseDylansRandomXP)
                        .addComponent(randomOriginPanel)
        );

        return panel;
    }

    private JPanel createRandomOriginPanel() {
        // Initialize Labels Used in ActionListeners
        JLabel lblOriginSearchRadius = new JLabel();
        JLabel lblOriginDistanceScale = new JLabel();

        // Create Panel Components
        chkRandomizeOrigin = new JCheckBox(resources.getString("chkRandomizeOrigin.text"));
        chkRandomizeOrigin.setToolTipText(resources.getString("chkRandomizeOrigin.toolTipText"));
        chkRandomizeOrigin.setName("chkRandomizeOrigin");
        chkRandomizeOrigin.addActionListener(evt -> {
            final boolean selected = chkRandomizeOrigin.isSelected();
            chkRandomizeDependentsOrigin.setEnabled(selected);
            lblOriginSearchRadius.setEnabled(selected);
            spnOriginSearchRadius.setEnabled(selected);
            chkExtraRandomOrigin.setEnabled(selected);
            lblOriginDistanceScale.setEnabled(selected);
            spnOriginDistanceScale.setEnabled(selected);
        });

        chkRandomizeDependentsOrigin = new JCheckBox(resources.getString("chkRandomizeDependentsOrigin.text"));
        chkRandomizeDependentsOrigin.setToolTipText(resources.getString("chkRandomizeDependentsOrigin.toolTipText"));
        chkRandomizeDependentsOrigin.setName("chkRandomizeDependentsOrigin");

        lblOriginSearchRadius.setText(resources.getString("lblOriginSearchRadius.text"));
        lblOriginSearchRadius.setToolTipText(resources.getString("lblOriginSearchRadius.toolTipText"));
        lblOriginSearchRadius.setName("lblOriginSearchRadius");

        spnOriginSearchRadius = new JSpinner(new SpinnerNumberModel(50, 10, 250, 10));
        spnOriginSearchRadius.setToolTipText(resources.getString("lblOriginSearchRadius.toolTipText"));
        spnOriginSearchRadius.setName("spnOriginSearchRadius");

        chkExtraRandomOrigin = new JCheckBox(resources.getString("chkExtraRandomOrigin.text"));
        chkExtraRandomOrigin.setToolTipText(resources.getString("chkExtraRandomOrigin.toolTipText"));
        chkExtraRandomOrigin.setName("chkExtraRandomOrigin");

        lblOriginDistanceScale.setText(resources.getString("lblOriginDistanceScale.text"));
        lblOriginDistanceScale.setToolTipText(resources.getString("lblOriginDistanceScale.toolTipText"));
        lblOriginDistanceScale.setName("lblOriginDistanceScale");

        spnOriginDistanceScale = new JSpinner(new SpinnerNumberModel(0.6, 0.1, 2.0, 0.1));
        spnOriginDistanceScale.setToolTipText(resources.getString("lblOriginDistanceScale.toolTipText"));
        spnOriginDistanceScale.setName("spnOriginDistanceScale");

        // Programmatically Assign Accessibility Labels
        lblOriginSearchRadius.setLabelFor(spnOriginSearchRadius);
        lblOriginDistanceScale.setLabelFor(spnOriginDistanceScale);

        // Disable Panel by Default
        chkRandomizeOrigin.setSelected(true);
        chkRandomizeOrigin.doClick();

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("randomOriginPanel.title")));
        panel.setName("randomOriginPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkRandomizeOrigin)
                        .addComponent(chkRandomizeDependentsOrigin)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblOriginSearchRadius)
                                .addComponent(spnOriginSearchRadius, GroupLayout.Alignment.LEADING))
                        .addComponent(chkExtraRandomOrigin)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblOriginDistanceScale)
                                .addComponent(spnOriginDistanceScale, GroupLayout.Alignment.LEADING))
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkRandomizeOrigin)
                        .addComponent(chkRandomizeDependentsOrigin)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblOriginSearchRadius)
                                .addComponent(spnOriginSearchRadius))
                        .addComponent(chkExtraRandomOrigin)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblOriginDistanceScale)
                                .addComponent(spnOriginDistanceScale))
        );

        return panel;
    }

    private JPanel createFamilyPanel() {
        // Create Panel Components
        JLabel lblDisplayFamilyLevel = new JLabel(resources.getString("lblDisplayFamilyLevel.text"));
        lblDisplayFamilyLevel.setToolTipText(resources.getString("lblDisplayFamilyLevel.toolTipText"));
        lblDisplayFamilyLevel.setName("lblDisplayFamilyLevel");

        comboDisplayFamilyLevel = new JComboBox<>(FamilialRelationshipDisplayLevel.values());
        comboDisplayFamilyLevel.setToolTipText(resources.getString("lblDisplayFamilyLevel.toolTipText"));
        comboDisplayFamilyLevel.setName("comboDisplayFamilyLevel");

        // Programmatically Assign Accessibility Labels
        lblDisplayFamilyLevel.setLabelFor(comboDisplayFamilyLevel);

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("familyPanel.title")));
        panel.setName("familyPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblDisplayFamilyLevel)
                                .addComponent(comboDisplayFamilyLevel, GroupLayout.Alignment.LEADING))
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblDisplayFamilyLevel)
                                .addComponent(comboDisplayFamilyLevel))
        );

        return panel;
    }

    private JPanel createSalaryPanel() {
        // Create Panel Components
        JPanel salaryMultiplierPanel = createSalaryMultiplierPanel();

        JPanel salaryExperienceModifierPanel = createSalaryExperienceMultiplierPanel();

        JPanel baseSalaryPanel = createBaseSalaryPanel();

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("salaryPanel.title")));
        panel.setName("salaryPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(salaryMultiplierPanel)
                        .addComponent(salaryExperienceModifierPanel)
                        .addComponent(baseSalaryPanel)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(salaryMultiplierPanel)
                        .addComponent(salaryExperienceModifierPanel)
                        .addComponent(baseSalaryPanel)
        );

        return panel;
    }

    private JPanel createSalaryMultiplierPanel() {
        // Create Panel Components
        JLabel lblCommissionedSalary = new JLabel(resources.getString("lblCommissionedSalary.text"));
        lblCommissionedSalary.setToolTipText(resources.getString("lblCommissionedSalary.toolTipText"));
        lblCommissionedSalary.setName("lblCommissionedSalary");

        spnCommissionedSalary = new JSpinner(new SpinnerNumberModel(0, 0, 10, 0.05));
        spnCommissionedSalary.setToolTipText(resources.getString("lblCommissionedSalary.toolTipText"));
        spnCommissionedSalary.setName("spnCommissionedSalary");

        JLabel lblEnlistedSalary = new JLabel(resources.getString("lblEnlistedSalary.text"));
        lblEnlistedSalary.setToolTipText(resources.getString("lblEnlistedSalary.toolTipText"));
        lblEnlistedSalary.setName("lblEnlistedSalary");

        spnEnlistedSalary = new JSpinner(new SpinnerNumberModel(0, 0, 10, 0.05));
        spnEnlistedSalary.setToolTipText(resources.getString("lblEnlistedSalary.toolTipText"));
        spnEnlistedSalary.setName("spnEnlistedSalary");

        JLabel lblAntiMekSalary = new JLabel(resources.getString("lblAntiMekSalary.text"));
        lblAntiMekSalary.setToolTipText(resources.getString("lblAntiMekSalary.toolTipText"));
        lblAntiMekSalary.setName("lblAntiMekSalary");

        spnAntiMekSalary = new JSpinner(new SpinnerNumberModel(0, 0, 10, 0.05));
        spnAntiMekSalary.setToolTipText(resources.getString("lblAntiMekSalary.toolTipText"));
        spnAntiMekSalary.setName("spnAntiMekSalary");

        // Programmatically Assign Accessibility Labels
        lblCommissionedSalary.setLabelFor(spnCommissionedSalary);
        lblEnlistedSalary.setLabelFor(spnEnlistedSalary);
        lblAntiMekSalary.setLabelFor(spnAntiMekSalary);

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("salaryMultiplierPanel.title")));
        panel.setToolTipText(resources.getString("salaryMultiplierPanel.toolTipText"));
        panel.setName("salaryMultiplierPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblCommissionedSalary)
                                .addComponent(spnCommissionedSalary)
                                .addComponent(lblEnlistedSalary)
                                .addComponent(spnEnlistedSalary)
                                .addComponent(lblAntiMekSalary)
                                .addComponent(spnAntiMekSalary, GroupLayout.Alignment.LEADING))
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblCommissionedSalary)
                                .addComponent(spnCommissionedSalary)
                                .addComponent(lblEnlistedSalary)
                                .addComponent(spnEnlistedSalary)
                                .addComponent(lblAntiMekSalary)
                                .addComponent(spnAntiMekSalary))
        );

        return panel;
    }

    private JPanel createSalaryExperienceMultiplierPanel() {
        final JPanel panel = new JPanel(new GridLayout(1, 10));
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("salaryExperienceMultiplierPanel.title")));
        panel.setToolTipText(resources.getString("salaryExperienceMultiplierPanel.toolTipText"));
        panel.setName("salaryExperienceMultiplierPanel");

        spnSalaryExperienceMultipliers = new JSpinner[5];
        for (int i = 0; i < 5; i++) {
            final String skillLevel = SkillType.getExperienceLevelName(i);
            final String toolTipText = String.format(resources.getString("lblSalaryExperienceMultiplier.toolTipText"), skillLevel);

            final JLabel label = new JLabel(skillLevel);
            label.setToolTipText(toolTipText);
            label.setName("lbl" + skillLevel);
            panel.add(label);

            spnSalaryExperienceMultipliers[i] = new JSpinner(new SpinnerNumberModel(0, 0, 10, 0.05));
            spnSalaryExperienceMultipliers[i].setToolTipText(toolTipText);
            spnSalaryExperienceMultipliers[i].setName("spn" + skillLevel);
            panel.add(spnSalaryExperienceMultipliers[i]);

            label.setLabelFor(spnSalaryExperienceMultipliers[i]);
        }

        return panel;
    }

    private JPanel createBaseSalaryPanel() {
        final PersonnelRole[] personnelRoles = PersonnelRole.values();
        final JPanel panel = new JPanel(new GridLayout((int) Math.ceil((double) (personnelRoles.length - 1) / 3.0), 6));
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("baseSalaryPanel.title")));
        panel.setPreferredSize(new Dimension(200, 200));

        spnBaseSalary = new JSpinner[personnelRoles.length];
        for (final PersonnelRole personnelRole : personnelRoles) {
            // Create Reused Values
            final String toolTipText = String.format(resources.getString("lblBaseSalary.toolTipText"), personnelRole.toString());

            // Create Panel Components
            final JLabel label = new JLabel(personnelRole.toString());
            label.setToolTipText(toolTipText);
            label.setName("lbl" + personnelRole.toString());
            panel.add(label);

            final JSpinner salarySpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, null, 10.0));
            salarySpinner.setToolTipText(toolTipText);
            salarySpinner.setName("spn" + personnelRole.toString());
            panel.add(salarySpinner);

            // Programmatically Assign Accessibility Labels
            label.setLabelFor(salarySpinner);

            // Component Tracking Assignment
            spnBaseSalary[personnelRole.ordinal()] = salarySpinner;
        }

        return panel;
    }

    private JPanel createMarriagePanel() {
        // Create Panel Components
        chkUseManualMarriages = new JCheckBox(resources.getString("chkUseManualMarriages.text"));
        chkUseManualMarriages.setToolTipText(resources.getString("chkUseManualMarriages.toolTipText"));
        chkUseManualMarriages.setName("chkUseManualMarriages");

        JLabel lblMinimumMarriageAge = new JLabel(resources.getString("lblMinimumMarriageAge.text"));
        lblMinimumMarriageAge.setToolTipText(resources.getString("lblMinimumMarriageAge.toolTipText"));
        lblMinimumMarriageAge.setName("lblMinimumMarriageAge");

        spnMinimumMarriageAge = new JSpinner(new SpinnerNumberModel(16, 14, null, 1));
        spnMinimumMarriageAge.setToolTipText(resources.getString("lblMinimumMarriageAge.toolTipText"));
        spnMinimumMarriageAge.setName("spnMinimumMarriageAge");

        JLabel lblCheckMutualAncestorsDepth = new JLabel(resources.getString("lblCheckMutualAncestorsDepth.text"));
        lblCheckMutualAncestorsDepth.setToolTipText(resources.getString("lblCheckMutualAncestorsDepth.toolTipText"));
        lblCheckMutualAncestorsDepth.setName("lblCheckMutualAncestorsDepth");

        spnCheckMutualAncestorsDepth = new JSpinner(new SpinnerNumberModel(4, 0, 20, 1));
        spnCheckMutualAncestorsDepth.setToolTipText(resources.getString("lblCheckMutualAncestorsDepth.toolTipText"));
        spnCheckMutualAncestorsDepth.setName("spnCheckMutualAncestorsDepth");

        chkLogMarriageNameChange = new JCheckBox(resources.getString("chkLogMarriageNameChange.text"));
        chkLogMarriageNameChange.setToolTipText(resources.getString("chkLogMarriageNameChange.toolTipText"));
        chkLogMarriageNameChange.setName("chkLogMarriageNameChange");

        JPanel marriageSurnameWeightsPanel = createMarriageSurnameWeightsPanel();

        JPanel randomMarriagePanel = createRandomMarriagePanel();

        // Programmatically Assign Accessibility Labels
        lblMinimumMarriageAge.setLabelFor(spnMinimumMarriageAge);
        lblCheckMutualAncestorsDepth.setLabelFor(spnCheckMutualAncestorsDepth);

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("marriagePanel.title")));
        panel.setName("marriagePanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkUseManualMarriages)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblMinimumMarriageAge)
                                .addComponent(spnMinimumMarriageAge, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblCheckMutualAncestorsDepth)
                                .addComponent(spnCheckMutualAncestorsDepth, GroupLayout.Alignment.LEADING))
                        .addComponent(chkLogMarriageNameChange)
                        .addComponent(marriageSurnameWeightsPanel)
                        .addComponent(randomMarriagePanel)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkUseManualMarriages)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblMinimumMarriageAge)
                                .addComponent(spnMinimumMarriageAge))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblCheckMutualAncestorsDepth)
                                .addComponent(spnCheckMutualAncestorsDepth))
                        .addComponent(chkLogMarriageNameChange)
                        .addComponent(marriageSurnameWeightsPanel)
                        .addComponent(randomMarriagePanel)
        );

        return panel;
    }

    private JPanel createMarriageSurnameWeightsPanel() {
        final Marriage[] marriageStyles = Marriage.values();
        final int surnameWeightLength = marriageStyles.length - 1;

        final JPanel panel = new JPanel(new GridLayout((int) Math.ceil(surnameWeightLength / 3.0), 6));
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("marriageSurnameWeightsPanel.title")));
        panel.setToolTipText(resources.getString("marriageSurnameWeightsPanel.toolTipText"));
        panel.setName("marriageSurnameWeightsPanel");

        spnMarriageSurnameWeights = new JSpinner[surnameWeightLength];
        for (int i = 0; i < surnameWeightLength; i++) {
            final JLabel label = new JLabel(marriageStyles[i].toString());
            label.setToolTipText(marriageStyles[i].getToolTipText());
            label.setName("lbl" + marriageStyles[i].toString());
            panel.add(label);

            spnMarriageSurnameWeights[i] = new JSpinner(new SpinnerNumberModel(0, 0, 100, 0.1));
            spnMarriageSurnameWeights[i].setToolTipText(marriageStyles[i].getToolTipText());
            spnMarriageSurnameWeights[i].setName("spn" + marriageStyles[i].toString());
            panel.add(spnMarriageSurnameWeights[i]);

            label.setLabelFor(spnMarriageSurnameWeights[i]);
        }

        return panel;
    }

    private JPanel createRandomMarriagePanel() {
        // Initialize Labels Used in ActionListeners
        JLabel lblChanceRandomMarriages = new JLabel();
        JLabel lblMarriageAgeRange = new JLabel();
        JLabel lblChanceRandomSameSexMarriages = new JLabel();

        // Create Panel Components
        chkUseRandomMarriages = new JCheckBox(resources.getString("chkUseRandomMarriages.text"));
        chkUseRandomMarriages.setToolTipText(resources.getString("chkUseRandomMarriages.toolTipText"));
        chkUseRandomMarriages.setName("chkUseRandomMarriages");
        chkUseRandomMarriages.addActionListener(evt -> {
            final boolean selected = chkUseRandomMarriages.isSelected();
            lblChanceRandomMarriages.setEnabled(selected);
            spnChanceRandomMarriages.setEnabled(selected);
            lblMarriageAgeRange.setEnabled(selected);
            spnMarriageAgeRange.setEnabled(selected);
            chkUseRandomSameSexMarriages.setEnabled(selected);
            lblChanceRandomSameSexMarriages.setEnabled(selected && chkUseRandomSameSexMarriages.isSelected());
            spnChanceRandomSameSexMarriages.setEnabled(selected && chkUseRandomSameSexMarriages.isSelected());
        });

        lblChanceRandomMarriages.setText(resources.getString("lblChanceRandomMarriages.text"));
        lblChanceRandomMarriages.setToolTipText(resources.getString("lblChanceRandomMarriages.toolTipText"));
        lblChanceRandomMarriages.setName("lblChanceRandomMarriages");

        spnChanceRandomMarriages = new JSpinner(new SpinnerNumberModel(0, 0, 100, 0.001));
        spnChanceRandomMarriages.setToolTipText(resources.getString("lblChanceRandomMarriages.toolTipText"));
        spnChanceRandomMarriages.setName("spnChanceRandomMarriages");

        lblMarriageAgeRange.setText(resources.getString("lblMarriageAgeRange.text"));
        lblMarriageAgeRange.setToolTipText(resources.getString("lblMarriageAgeRange.toolTipText"));
        lblMarriageAgeRange.setName("lblMarriageAgeRange");

        spnMarriageAgeRange = new JSpinner(new SpinnerNumberModel(10, 0, null, 1.0));
        spnMarriageAgeRange.setToolTipText(resources.getString("lblMarriageAgeRange.toolTipText"));
        spnMarriageAgeRange.setName("spnMarriageAgeRange");

        chkUseRandomSameSexMarriages = new JCheckBox(resources.getString("chkUseRandomSameSexMarriages.text"));
        chkUseRandomSameSexMarriages.setToolTipText(resources.getString("chkUseRandomSameSexMarriages.toolTipText"));
        chkUseRandomSameSexMarriages.setName("chkUseRandomSameSexMarriages");
        chkUseRandomSameSexMarriages.addActionListener(evt -> {
            final boolean selected = chkUseRandomMarriages.isSelected() && chkUseRandomSameSexMarriages.isSelected();
            lblChanceRandomSameSexMarriages.setEnabled(selected);
            spnChanceRandomSameSexMarriages.setEnabled(selected);
        });

        lblChanceRandomSameSexMarriages.setText(resources.getString("lblChanceRandomSameSexMarriages.text"));
        lblChanceRandomSameSexMarriages.setToolTipText(resources.getString("lblChanceRandomSameSexMarriages.toolTipText"));
        lblChanceRandomSameSexMarriages.setName("lblChanceRandomSameSexMarriages");

        spnChanceRandomSameSexMarriages = new JSpinner(new SpinnerNumberModel(0, 0, 100, 0.001));
        spnChanceRandomSameSexMarriages.setToolTipText(resources.getString("lblChanceRandomSameSexMarriages.toolTipText"));
        spnChanceRandomSameSexMarriages.setName("spnChanceRandomSameSexMarriages");

        // Programmatically Assign Accessibility Labels
        lblChanceRandomMarriages.setLabelFor(spnChanceRandomMarriages);
        lblMarriageAgeRange.setLabelFor(spnMarriageAgeRange);
        lblChanceRandomSameSexMarriages.setLabelFor(spnChanceRandomSameSexMarriages);

        // Disable Panel by Default
        chkUseRandomMarriages.setSelected(true);
        chkUseRandomMarriages.doClick();

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("randomMarriagePanel.title")));
        panel.setName("randomMarriagePanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkUseRandomMarriages)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblChanceRandomMarriages)
                                .addComponent(spnChanceRandomMarriages, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblMarriageAgeRange)
                                .addComponent(spnMarriageAgeRange, GroupLayout.Alignment.LEADING))
                        .addComponent(chkUseRandomSameSexMarriages)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblChanceRandomSameSexMarriages)
                                .addComponent(spnChanceRandomSameSexMarriages, GroupLayout.Alignment.LEADING))
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkUseRandomMarriages)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblChanceRandomMarriages)
                                .addComponent(spnChanceRandomMarriages))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblMarriageAgeRange)
                                .addComponent(spnMarriageAgeRange))
                        .addComponent(chkUseRandomSameSexMarriages)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblChanceRandomSameSexMarriages)
                                .addComponent(spnChanceRandomSameSexMarriages))
        );

        return panel;
    }

    private JPanel createProcreationPanel() {
        // Initialize Labels Used in ActionListeners
        JLabel lblChanceProcreation = new JLabel();
        JLabel lblChanceProcreationNoRelationship = new JLabel();
        JLabel lblBabySurnameStyle = new JLabel();

        // Create Panel Components
        chkUseProcreation = new JCheckBox(resources.getString("chkUseProcreation.text"));
        chkUseProcreation.setToolTipText(resources.getString("chkUseProcreation.toolTipText"));
        chkUseProcreation.setName("chkUseProcreation");
        chkUseProcreation.addActionListener(evt -> {
            final boolean selected = chkUseProcreation.isSelected();
            lblChanceProcreation.setEnabled(selected);
            spnChanceProcreation.setEnabled(selected);
            chkUseProcreationNoRelationship.setEnabled(selected);
            lblChanceProcreationNoRelationship.setEnabled(selected && chkUseProcreationNoRelationship.isSelected());
            spnChanceProcreationNoRelationship.setEnabled(selected && chkUseProcreationNoRelationship.isSelected());
            chkDisplayTrueDueDate.setEnabled(selected);
            chkLogConception.setEnabled(selected);
            lblBabySurnameStyle.setEnabled(selected);
            comboBabySurnameStyle.setEnabled(selected);
            chkDetermineFatherAtBirth.setEnabled(selected);
        });

        lblChanceProcreation.setText(resources.getString("lblChanceProcreation.text"));
        lblChanceProcreation.setToolTipText(resources.getString("lblChanceProcreation.toolTipText"));
        lblChanceProcreation.setName("lblChanceProcreation");

        spnChanceProcreation = new JSpinner(new SpinnerNumberModel(0, 0, 100, 0.001));
        spnChanceProcreation.setToolTipText(resources.getString("lblChanceProcreation.toolTipText"));
        spnChanceProcreation.setName("spnChanceProcreation");

        chkUseProcreationNoRelationship = new JCheckBox(resources.getString("chkUseProcreationNoRelationship.text"));
        chkUseProcreationNoRelationship.setToolTipText(resources.getString("chkUseProcreationNoRelationship.toolTipText"));
        chkUseProcreationNoRelationship.setName("chkUseProcreationNoRelationship");
        chkUseProcreationNoRelationship.addActionListener(evt -> {
            final boolean selected = chkUseProcreation.isSelected() && chkUseProcreationNoRelationship.isSelected();
            lblChanceProcreationNoRelationship.setEnabled(selected);
            spnChanceProcreationNoRelationship.setEnabled(selected);
        });

        lblChanceProcreationNoRelationship.setText(resources.getString("lblChanceProcreationNoRelationship.text"));
        lblChanceProcreationNoRelationship.setToolTipText(resources.getString("lblChanceProcreationNoRelationship.toolTipText"));
        lblChanceProcreationNoRelationship.setName("lblChanceProcreationNoRelationship");

        spnChanceProcreationNoRelationship = new JSpinner(new SpinnerNumberModel(0, 0, 100, 0.001));
        spnChanceProcreationNoRelationship.setToolTipText(resources.getString("lblChanceProcreationNoRelationship.toolTipText"));
        spnChanceProcreationNoRelationship.setName("spnChanceProcreationNoRelationship");

        chkDisplayTrueDueDate = new JCheckBox(resources.getString("chkDisplayTrueDueDate.text"));
        chkDisplayTrueDueDate.setToolTipText(resources.getString("chkDisplayTrueDueDate.toolTipText"));
        chkDisplayTrueDueDate.setName("chkDisplayTrueDueDate");

        chkLogConception = new JCheckBox(resources.getString("chkLogConception.text"));
        chkLogConception.setToolTipText(resources.getString("chkLogConception.toolTipText"));
        chkLogConception.setName("chkLogConception");

        lblBabySurnameStyle.setText(resources.getString("lblBabySurnameStyle.text"));
        lblBabySurnameStyle.setToolTipText(resources.getString("lblBabySurnameStyle.toolTipText"));
        lblBabySurnameStyle.setName("lblBabySurnameStyle");

        comboBabySurnameStyle = new JComboBox<>(BabySurnameStyle.values());
        comboBabySurnameStyle.setName("comboBabySurnameStyle");
        comboBabySurnameStyle.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value,
                                                          final int index, final boolean isSelected,
                                                          final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BabySurnameStyle) {
                    list.setToolTipText(((BabySurnameStyle) value).getToolTipText());
                }
                return this;
            }
        });

        chkDetermineFatherAtBirth = new JCheckBox(resources.getString("chkDetermineFatherAtBirth.text"));
        chkDetermineFatherAtBirth.setToolTipText(resources.getString("chkDetermineFatherAtBirth.toolTipText"));
        chkDetermineFatherAtBirth.setName("chkDetermineFatherAtBirth");

        // Programmatically Assign Accessibility Labels
        lblChanceProcreation.setLabelFor(spnChanceProcreation);
        lblChanceProcreationNoRelationship.setLabelFor(spnChanceProcreationNoRelationship);
        lblBabySurnameStyle.setLabelFor(comboBabySurnameStyle);

        // Disable Panel by Default
        chkUseProcreation.setSelected(true);
        chkUseProcreation.doClick();

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("procreationPanel.title")));
        panel.setName("procreationPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkUseProcreation)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblChanceProcreation)
                                .addComponent(spnChanceProcreation, GroupLayout.Alignment.LEADING))
                        .addComponent(chkUseProcreationNoRelationship)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblChanceProcreationNoRelationship)
                                .addComponent(spnChanceProcreationNoRelationship, GroupLayout.Alignment.LEADING))
                        .addComponent(chkDisplayTrueDueDate)
                        .addComponent(chkLogConception)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblBabySurnameStyle)
                                .addComponent(comboBabySurnameStyle, GroupLayout.Alignment.LEADING))
                        .addComponent(chkDetermineFatherAtBirth)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkUseProcreation)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblChanceProcreation)
                                .addComponent(spnChanceProcreation))
                        .addComponent(chkUseProcreationNoRelationship)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblChanceProcreationNoRelationship)
                                .addComponent(spnChanceProcreationNoRelationship))
                        .addComponent(chkDisplayTrueDueDate)
                        .addComponent(chkLogConception)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblBabySurnameStyle)
                                .addComponent(comboBabySurnameStyle))
                        .addComponent(chkDetermineFatherAtBirth)
        );

        return panel;
    }

    private JPanel createDeathPanel() {
        // Create Panel Components
        chkKeepMarriedNameUponSpouseDeath = new JCheckBox(resources.getString("chkKeepMarriedNameUponSpouseDeath.text"));
        chkKeepMarriedNameUponSpouseDeath.setToolTipText(resources.getString("chkKeepMarriedNameUponSpouseDeath.toolTipText"));
        chkKeepMarriedNameUponSpouseDeath.setName("chkKeepMarriedNameUponSpouseDeath");

        // Layout the Panel
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("deathPanel.title")));
        panel.setName("deathPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addComponent(chkKeepMarriedNameUponSpouseDeath)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(chkKeepMarriedNameUponSpouseDeath)
        );

        return panel;
    }
    //endregion Personnel Tab

    //region Rank Systems Tab
    private JScrollPane createRankSystemsTab(final JFrame frame, final Campaign campaign) {
        rankSystemsPane = new RankSystemsPane(frame, campaign);
        return rankSystemsPane;
    }
    //endregion Rank Systems Tab

    //region Markets Tab
    private JScrollPane createMarketsTab() {
        JPanel marketsPanel = new JPanel(new GridBagLayout());
        marketsPanel.setName("marketsPanel");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        marketsPanel.add(createPersonnelMarketPanel(), gbc);

        gbc.gridx++;
        marketsPanel.add(createContractMarketPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        marketsPanel.add(createUnitMarketPanel(), gbc);

        JScrollPane scrollMarkets = new JScrollPane(marketsPanel);
        scrollMarkets.setPreferredSize(new Dimension(500, 400));

        return scrollMarkets;
    }

    private JPanel createPersonnelMarketPanel() {
        // Initialize Labels Used in ActionListeners
        JLabel lblPersonnelMarketRandomEliteRemoval = new JLabel();
        JLabel lblPersonnelMarketRandomVeteranRemoval = new JLabel();
        JLabel lblPersonnelMarketRandomRegularRemoval = new JLabel();
        JLabel lblPersonnelMarketRandomGreenRemoval = new JLabel();
        JLabel lblPersonnelMarketRandomUltraGreenRemoval = new JLabel();
        JLabel lblPersonnelMarketDylansWeight = new JLabel();

        // Create Panel Components
        JLabel lblPersonnelMarketType = new JLabel(resources.getString("lblPersonnelMarket.text"));
        lblPersonnelMarketType.setToolTipText(resources.getString("lblPersonnelMarketType.toolTipText"));
        lblPersonnelMarketType.setName("lblPersonnelMarketType");

        final DefaultComboBoxModel<String> personnelMarketTypeModel = new DefaultComboBoxModel<>();
        for (final PersonnelMarketMethod method : PersonnelMarketServiceManager.getInstance().getAllServices(true)) {
            personnelMarketTypeModel.addElement(method.getModuleName());
        }
        comboPersonnelMarketType = new JComboBox<>(personnelMarketTypeModel);
        comboPersonnelMarketType.setToolTipText(resources.getString("lblPersonnelMarketType.toolTipText"));
        comboPersonnelMarketType.setName("comboPersonnelMarketType");
        comboPersonnelMarketType.addActionListener(evt -> {
            final boolean isDylan = new PersonnelMarketDylan().getModuleName().equals(comboPersonnelMarketType.getSelectedItem());
            final boolean enabled = isDylan || new PersonnelMarketRandom().getModuleName().equals(comboPersonnelMarketType.getSelectedItem());
            lblPersonnelMarketRandomEliteRemoval.setEnabled(enabled);
            spnPersonnelMarketRandomEliteRemoval.setEnabled(enabled);
            lblPersonnelMarketRandomVeteranRemoval.setEnabled(enabled);
            spnPersonnelMarketRandomVeteranRemoval.setEnabled(enabled);
            lblPersonnelMarketRandomRegularRemoval.setEnabled(enabled);
            spnPersonnelMarketRandomRegularRemoval.setEnabled(enabled);
            lblPersonnelMarketRandomGreenRemoval.setEnabled(enabled);
            spnPersonnelMarketRandomGreenRemoval.setEnabled(enabled);
            lblPersonnelMarketRandomUltraGreenRemoval.setEnabled(enabled);
            spnPersonnelMarketRandomUltraGreenRemoval.setEnabled(enabled);
            lblPersonnelMarketDylansWeight.setEnabled(isDylan);
            spnPersonnelMarketDylansWeight.setEnabled(isDylan);
        });

        chkPersonnelMarketReportRefresh = new JCheckBox(resources.getString("chkPersonnelMarketReportRefresh.text"));
        chkPersonnelMarketReportRefresh.setToolTipText(resources.getString("chkPersonnelMarketReportRefresh.toolTipText"));
        chkPersonnelMarketReportRefresh.setName("chkPersonnelMarketReportRefresh");

        lblPersonnelMarketRandomEliteRemoval.setText(resources.getString("lblPersonnelMarketRandomEliteRemoval.text"));
        lblPersonnelMarketRandomEliteRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomEliteRemoval.toolTipText"));
        lblPersonnelMarketRandomEliteRemoval.setName("lblPersonnelMarketRandomEliteRemoval");

        spnPersonnelMarketRandomEliteRemoval = new JSpinner(new SpinnerNumberModel(0, 0, 12, 1));
        spnPersonnelMarketRandomEliteRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomEliteRemoval.toolTipText"));
        spnPersonnelMarketRandomEliteRemoval.setName("spnPersonnelMarketRandomEliteRemoval");

        lblPersonnelMarketRandomVeteranRemoval.setText(resources.getString("lblPersonnelMarketRandomVeteranRemoval.text"));
        lblPersonnelMarketRandomVeteranRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomVeteranRemoval.toolTipText"));
        lblPersonnelMarketRandomVeteranRemoval.setName("lblPersonnelMarketRandomVeteranRemoval");

        spnPersonnelMarketRandomVeteranRemoval = new JSpinner(new SpinnerNumberModel(0, 0, 12, 1));
        spnPersonnelMarketRandomVeteranRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomVeteranRemoval.toolTipText"));
        spnPersonnelMarketRandomVeteranRemoval.setName("spnPersonnelMarketRandomVeteranRemoval");

        lblPersonnelMarketRandomRegularRemoval.setText(resources.getString("lblPersonnelMarketRandomRegularRemoval.text"));
        lblPersonnelMarketRandomRegularRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomRegularRemoval.toolTipText"));
        lblPersonnelMarketRandomRegularRemoval.setName("lblPersonnelMarketRandomRegularRemoval");

        spnPersonnelMarketRandomRegularRemoval = new JSpinner(new SpinnerNumberModel(0, 0, 12, 1));
        spnPersonnelMarketRandomRegularRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomRegularRemoval.toolTipText"));
        spnPersonnelMarketRandomRegularRemoval.setName("spnPersonnelMarketRandomRegularRemoval");

        lblPersonnelMarketRandomGreenRemoval.setText(resources.getString("lblPersonnelMarketRandomGreenRemoval.text"));
        lblPersonnelMarketRandomGreenRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomGreenRemoval.toolTipText"));
        lblPersonnelMarketRandomGreenRemoval.setName("lblPersonnelMarketRandomGreenRemoval");

        spnPersonnelMarketRandomGreenRemoval = new JSpinner(new SpinnerNumberModel(0, 0, 12, 1));
        spnPersonnelMarketRandomGreenRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomGreenRemoval.toolTipText"));
        spnPersonnelMarketRandomGreenRemoval.setName("spnPersonnelMarketRandomGreenRemoval");

        lblPersonnelMarketRandomUltraGreenRemoval.setText(resources.getString("lblPersonnelMarketRandomUltraGreenRemoval.text"));
        lblPersonnelMarketRandomUltraGreenRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomUltraGreenRemoval.toolTipText"));
        lblPersonnelMarketRandomUltraGreenRemoval.setName("lblPersonnelMarketRandomUltraGreenRemoval");

        spnPersonnelMarketRandomUltraGreenRemoval = new JSpinner(new SpinnerNumberModel(0, 0, 12, 1));
        spnPersonnelMarketRandomUltraGreenRemoval.setToolTipText(resources.getString("lblPersonnelMarketRandomUltraGreenRemoval.toolTipText"));
        spnPersonnelMarketRandomUltraGreenRemoval.setName("spnPersonnelMarketRandomUltraGreenRemoval");

        lblPersonnelMarketDylansWeight.setText(resources.getString("lblPersonnelMarketDylansWeight.text"));
        lblPersonnelMarketDylansWeight.setToolTipText(resources.getString("lblPersonnelMarketDylansWeight.toolTipText"));
        lblPersonnelMarketDylansWeight.setName("lblPersonnelMarketDylansWeight");

        spnPersonnelMarketDylansWeight = new JSpinner(new SpinnerNumberModel(0.3, 0, 1, 0.1));
        spnPersonnelMarketDylansWeight.setToolTipText(resources.getString("lblPersonnelMarketDylansWeight.toolTipText"));
        spnPersonnelMarketDylansWeight.setName("spnPersonnelMarketDylansWeight");

        // Programmatically Assign Accessibility Labels
        lblPersonnelMarketType.setLabelFor(comboPersonnelMarketType);
        lblPersonnelMarketRandomEliteRemoval.setLabelFor(spnPersonnelMarketRandomEliteRemoval);
        lblPersonnelMarketRandomVeteranRemoval.setLabelFor(spnPersonnelMarketRandomVeteranRemoval);
        lblPersonnelMarketRandomRegularRemoval.setLabelFor(spnPersonnelMarketRandomRegularRemoval);
        lblPersonnelMarketRandomGreenRemoval.setLabelFor(spnPersonnelMarketRandomGreenRemoval);
        lblPersonnelMarketRandomUltraGreenRemoval.setLabelFor(spnPersonnelMarketRandomUltraGreenRemoval);
        lblPersonnelMarketDylansWeight.setLabelFor(spnPersonnelMarketDylansWeight);

        // Layout the UI
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("personnelMarketPanel.title")));
        panel.setName("personnelMarketPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPersonnelMarketType)
                                .addComponent(comboPersonnelMarketType, GroupLayout.Alignment.LEADING))
                        .addComponent(chkPersonnelMarketReportRefresh)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPersonnelMarketRandomEliteRemoval)
                                .addComponent(spnPersonnelMarketRandomEliteRemoval, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPersonnelMarketRandomVeteranRemoval)
                                .addComponent(spnPersonnelMarketRandomVeteranRemoval, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPersonnelMarketRandomRegularRemoval)
                                .addComponent(spnPersonnelMarketRandomRegularRemoval, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPersonnelMarketRandomGreenRemoval)
                                .addComponent(spnPersonnelMarketRandomGreenRemoval, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPersonnelMarketRandomUltraGreenRemoval)
                                .addComponent(spnPersonnelMarketRandomUltraGreenRemoval, GroupLayout.Alignment.LEADING))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblPersonnelMarketDylansWeight)
                                .addComponent(spnPersonnelMarketDylansWeight, GroupLayout.Alignment.LEADING))
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPersonnelMarketType)
                                .addComponent(comboPersonnelMarketType))
                        .addComponent(chkPersonnelMarketReportRefresh)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPersonnelMarketRandomEliteRemoval)
                                .addComponent(spnPersonnelMarketRandomEliteRemoval))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPersonnelMarketRandomVeteranRemoval)
                                .addComponent(spnPersonnelMarketRandomVeteranRemoval))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPersonnelMarketRandomRegularRemoval)
                                .addComponent(spnPersonnelMarketRandomRegularRemoval))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPersonnelMarketRandomGreenRemoval)
                                .addComponent(spnPersonnelMarketRandomGreenRemoval))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPersonnelMarketRandomUltraGreenRemoval)
                                .addComponent(spnPersonnelMarketRandomUltraGreenRemoval))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblPersonnelMarketDylansWeight)
                                .addComponent(spnPersonnelMarketDylansWeight))
        );

        return panel;
    }

    private JPanel createUnitMarketPanel() {
        // Create Panel Components
        JLabel lblUnitMarketMethod = new JLabel(resources.getString("lblUnitMarketMethod.text"));
        lblUnitMarketMethod.setToolTipText(resources.getString("lblUnitMarketMethod.toolTipText"));
        lblUnitMarketMethod.setName("lblUnitMarketMethod");

        comboUnitMarketMethod = new JComboBox<>(UnitMarketMethod.values());
        comboUnitMarketMethod.setToolTipText(resources.getString("lblUnitMarketMethod.toolTipText"));
        comboUnitMarketMethod.setName("comboUnitMarketMethod");
        comboUnitMarketMethod.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value,
                                                          final int index, final boolean isSelected,
                                                          final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof UnitMarketMethod) {
                    list.setToolTipText(((UnitMarketMethod) value).getToolTipText());
                }
                return this;
            }
        });
        comboUnitMarketMethod.addActionListener(evt -> {
            final boolean enabled = !((UnitMarketMethod) Objects.requireNonNull(comboUnitMarketMethod.getSelectedItem())).isNone();
            chkUnitMarketRegionalMechVariations.setEnabled(enabled);
            chkInstantUnitMarketDelivery.setEnabled(enabled);
            chkUnitMarketReportRefresh.setEnabled(enabled);
        });

        chkUnitMarketRegionalMechVariations = new JCheckBox(resources.getString("chkUnitMarketRegionalMechVariations.text"));
        chkUnitMarketRegionalMechVariations.setToolTipText(resources.getString("chkUnitMarketRegionalMechVariations.toolTipText"));
        chkUnitMarketRegionalMechVariations.setName("chkUnitMarketRegionalMechVariations");

        chkInstantUnitMarketDelivery = new JCheckBox(resources.getString("chkInstantUnitMarketDelivery.text"));
        chkInstantUnitMarketDelivery.setToolTipText(resources.getString("chkInstantUnitMarketDelivery.toolTipText"));
        chkInstantUnitMarketDelivery.setName("chkInstantUnitMarketDelivery");

        chkUnitMarketReportRefresh = new JCheckBox(resources.getString("chkUnitMarketReportRefresh.text"));
        chkUnitMarketReportRefresh.setToolTipText(resources.getString("chkUnitMarketReportRefresh.toolTipText"));
        chkUnitMarketReportRefresh.setName("chkUnitMarketReportRefresh");

        // Programmatically Assign Accessibility Labels
        lblUnitMarketMethod.setLabelFor(comboUnitMarketMethod);

        // Layout the UI
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("unitMarketPanel.title")));
        panel.setName("unitMarketPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblUnitMarketMethod)
                                .addComponent(comboUnitMarketMethod, GroupLayout.Alignment.LEADING))
                        .addComponent(chkUnitMarketRegionalMechVariations)
                        .addComponent(chkInstantUnitMarketDelivery)
                        .addComponent(chkUnitMarketReportRefresh)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblUnitMarketMethod)
                                .addComponent(comboUnitMarketMethod))
                        .addComponent(chkUnitMarketRegionalMechVariations)
                        .addComponent(chkInstantUnitMarketDelivery)
                        .addComponent(chkUnitMarketReportRefresh)
        );

        return panel;
    }

    private JPanel createContractMarketPanel() {
        // Create Panel Components
        JLabel lblContractMarketMethod = new JLabel(resources.getString("lblContractMarketMethod.text"));
        lblContractMarketMethod.setToolTipText(resources.getString("lblContractMarketMethod.toolTipText"));
        lblContractMarketMethod.setName("lblContractMarketMethod");
        lblContractMarketMethod.setVisible(false); // TODO : AbstractContractMarket : Remove

        comboContractMarketMethod = new JComboBox<>(ContractMarketMethod.values());
        comboContractMarketMethod.setToolTipText(resources.getString("lblContractMarketMethod.toolTipText"));
        comboContractMarketMethod.setName("comboContractMarketMethod");
        comboContractMarketMethod.setVisible(false); // TODO : AbstractContractMarket : Remove
        /*
        comboContractMarketMethod.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value,
                                                          final int index, final boolean isSelected,
                                                          final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ContractMarketMethod) {
                    list.setToolTipText(((ContractMarketMethod) value).getToolTipText());
                }
                return this;
            }
        });
        comboContractMarketMethod.addActionListener(evt -> {
            final boolean enabled = !((ContractMarketMethod) Objects.requireNonNull(comboContractMarketMethod.getSelectedItem())).isNone();
            chkContractMarketReportRefresh.setEnabled(enabled);
        });
         */

        chkContractMarketReportRefresh = new JCheckBox(resources.getString("chkContractMarketReportRefresh.text"));
        chkContractMarketReportRefresh.setToolTipText(resources.getString("chkContractMarketReportRefresh.toolTipText"));
        chkContractMarketReportRefresh.setName("chkContractMarketReportRefresh");

        // Programmatically Assign Accessibility Labels
        lblContractMarketMethod.setLabelFor(comboContractMarketMethod);

        // Layout the UI
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(resources.getString("contractMarketPanel.title")));
        panel.setName("contractMarketPanel");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(lblContractMarketMethod)
                                .addComponent(comboContractMarketMethod, GroupLayout.Alignment.LEADING))
                        .addComponent(chkContractMarketReportRefresh)
        );

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lblContractMarketMethod)
                                .addComponent(comboContractMarketMethod))
                        .addComponent(chkContractMarketReportRefresh)
        );

        return panel;
    }
    //endregion Markets Tab

    private void setUserPreferences() {
        PreferencesNode preferences = MekHQ.getPreferences().forClass(getClass());
        setName("CampaignOptionsDialog");
        preferences.manage(new JWindowPreference(this));
    }

    public void applyPreset(GamePreset gamePreset) {
        // Handle CampaignOptions and RandomSkillPreferences
        setOptions(gamePreset.getOptions(), gamePreset.getRandomSkillPreferences());

        // Handle SPAs
        tempSPA = (gamePreset.getSpecialAbilities() != null) ? gamePreset.getSpecialAbilities() : new Hashtable<>();
        recreateSPAPanel(!getUnusedSPA().isEmpty());

        if (gamePreset.getSkillHash() != null) {
            // Overwriting XP Table
            tableXP.setModel(new DefaultTableModel(getSkillCostsArray(gamePreset.getSkillHash()), TABLE_XP_COLUMN_NAMES));
            ((DefaultTableModel) tableXP.getModel()).fireTableDataChanged();

            // Overwriting Skill List
            for (String skillName : SkillType.getSkillList()) {
                SkillType skillType = gamePreset.getSkillHash().get(skillName);

                JSpinner spnTarget = hashSkillTargets.get(skillName);
                if (spnTarget == null) {
                    continue;
                }

                spnTarget.setValue(skillType.getTarget());
                hashGreenSkill.get(skillName).setValue(skillType.getGreenLevel());
                hashRegSkill.get(skillName).setValue(skillType.getRegularLevel());
                hashVetSkill.get(skillName).setValue(skillType.getVeteranLevel());
                hashEliteSkill.get(skillName).setValue(skillType.getEliteLevel());
            }
        }
    }

    public void setOptions(CampaignOptions options, RandomSkillPreferences randomSkillPreferences) {
        // Use the provided options and preferences when possible, but flip if they are null to be safe
        if (options != null) {
            this.options = options;
        } else {
            options = this.options;
        }

        if (randomSkillPreferences != null) {
            this.rSkillPrefs = randomSkillPreferences;
        } else {
            randomSkillPreferences = this.rSkillPrefs;
        }

        //region General Tab
        unitRatingMethodCombo.setSelectedItem(options.getUnitRatingMethod());
        manualUnitRatingModifier.setValue(options.getManualUnitRatingModifier());
        //endregion General Tab

        //region Repair and Maintenance Tab
        useEraModsCheckBox.setSelected(options.useEraMods());
        assignedTechFirstCheckBox.setSelected(options.useAssignedTechFirst());
        resetToFirstTechCheckBox.setSelected(options.useResetToFirstTech());
        useQuirksBox.setSelected(options.useQuirks());
        useAeroSystemHitsBox.setSelected(options.useAeroSystemHits());
        if (useDamageMargin.isSelected() != options.isDestroyByMargin()) {
            useDamageMargin.doClick();
        }
        spnDamageMargin.setValue(options.getDestroyMargin());
        spnDestroyPartTarget.setValue(options.getDestroyPartTarget());

        if (checkMaintenance.isSelected() != options.checkMaintenance()) {
            checkMaintenance.doClick();
        }
        spnMaintenanceDays.setValue(options.getMaintenanceCycleDays());
        spnMaintenanceBonus.setValue(options.getMaintenanceBonus());
        useQualityMaintenance.setSelected(options.useQualityMaintenance());
        reverseQualityNames.setSelected(options.reverseQualityNames());
        useUnofficialMaintenance.setSelected(options.useUnofficialMaintenance());
        logMaintenance.setSelected(options.logMaintenance());
        //endregion Repair and Maintenance Tab

        //region Supplies and Acquisitions Tab
        spnAcquireWaitingPeriod.setValue(options.getWaitingPeriod());
        choiceAcquireSkill.setSelectedItem(options.getAcquisitionSkill());
        chkSupportStaffOnly.setSelected(options.isAcquisitionSupportStaffOnly());
        spnAcquireClanPenalty.setValue(options.getClanAcquisitionPenalty());
        spnAcquireIsPenalty.setValue(options.getIsAcquisitionPenalty());
        txtMaxAcquisitions.setText(Integer.toString(options.getMaxAcquisitions()));

        spnNDiceTransitTime.setValue(options.getNDiceTransitTime());
        spnConstantTransitTime.setValue(options.getConstantTransitTime());
        choiceTransitTimeUnits.setSelectedItem(CampaignOptions.getTransitUnitName(options.getUnitTransitTime()));
        spnAcquireMinimum.setValue(options.getAcquireMinimumTime());
        choiceAcquireMinimumUnit.setSelectedItem(CampaignOptions.getTransitUnitName(options.getAcquireMinimumTimeUnit()));
        spnAcquireMosBonus.setValue(options.getAcquireMosBonus());
        choiceAcquireMosUnits.setSelectedItem(CampaignOptions.getTransitUnitName(options.getAcquireMosUnit()));

        usePlanetaryAcquisitions.setSelected(options.usesPlanetaryAcquisition());
        spnMaxJumpPlanetaryAcquisitions.setValue(options.getMaxJumpsPlanetaryAcquisition());
        comboPlanetaryAcquisitionsFactionLimits.setSelectedIndex(options.getPlanetAcquisitionFactionLimit());
        disallowPlanetaryAcquisitionClanCrossover.setSelected(options.disallowPlanetAcquisitionClanCrossover());
        disallowClanPartsFromIS.setSelected(options.disallowClanPartsFromIS());
        spnPenaltyClanPartsFromIS.setValue(options.getPenaltyClanPartsFroIS());
        usePlanetaryAcquisitionsVerbose.setSelected(options.usePlanetAcquisitionVerboseReporting());
        for (int i = EquipmentType.RATING_A; i <= EquipmentType.RATING_F; i++) {
            spnPlanetAcquireTechBonus[i].setValue(options.getPlanetTechAcquisitionBonus(i));
            spnPlanetAcquireIndustryBonus[i].setValue(options.getPlanetIndustryAcquisitionBonus(i));
            spnPlanetAcquireOutputBonus[i].setValue(options.getPlanetOutputAcquisitionBonus(i));
        }
        //endregion Supplies and Acquisitions Tab

        //region Tech Limits Tab
        if (limitByYearBox.isSelected() != options.limitByYear()) {
            limitByYearBox.doClick();
        }
        disallowExtinctStuffBox.setSelected(options.disallowExtinctStuff());
        allowClanPurchasesBox.setSelected(options.allowClanPurchases());
        allowISPurchasesBox.setSelected(options.allowISPurchases());
        allowCanonOnlyBox.setSelected(options.allowCanonOnly());
        allowCanonRefitOnlyBox.setSelected(options.allowCanonRefitOnly());
        choiceTechLevel.setSelectedIndex(options.getTechLevel());
        variableTechLevelBox.setSelected(options.useVariableTechLevel() && options.limitByYear());
        factionIntroDateBox.setSelected(options.useFactionIntroDate());
        useAmmoByTypeBox.setSelected(options.useAmmoByType());
        //endregion Tech Limits Tab

        //region Personnel Tab
        // General Personnel
        chkUseTactics.setSelected(options.useTactics());
        chkUseInitiativeBonus.setSelected(options.useInitiativeBonus());
        chkUseToughness.setSelected(options.useToughness());
        chkUseArtillery.setSelected(options.useArtillery());
        chkUseAbilities.setSelected(options.useAbilities());
        if (chkUseEdge.isSelected() != options.useEdge()) {
            chkUseEdge.doClick();
        }
        chkUseSupportEdge.setSelected(options.useSupportEdge());
        chkUseImplants.setSelected(options.useImplants());
        chkUseAlternativeQualityAveraging.setSelected(options.useAlternativeQualityAveraging());
        chkUseTransfers.setSelected(options.useTransfers());

        // Expanded Personnel Information
        if (chkUseTimeInService.isSelected() != options.getUseTimeInService()) {
            chkUseTimeInService.doClick();
        }
        comboTimeInServiceDisplayFormat.setSelectedItem(options.getTimeInServiceDisplayFormat());
        if (chkUseTimeInRank.isSelected() != options.getUseTimeInRank()) {
            chkUseTimeInRank.doClick();
        }
        comboTimeInRankDisplayFormat.setSelectedItem(options.getTimeInRankDisplayFormat());
        chkUseRetirementDateTracking.setSelected(options.useRetirementDateTracking());
        chkTrackTotalEarnings.setSelected(options.trackTotalEarnings());
        chkShowOriginFaction.setSelected(options.showOriginFaction());

        // Medical
        chkUseAdvancedMedical.setSelected(options.useAdvancedMedical());
        spnHealWaitingPeriod.setValue(options.getHealingWaitingPeriod());
        spnNaturalHealWaitingPeriod.setValue(options.getNaturalHealingWaitingPeriod());
        spnMinimumHitsForVehicles.setValue(options.getMinimumHitsForVehicles());
        chkUseRandomHitsForVehicles.setSelected(options.useRandomHitsForVehicles());
        chkUseTougherHealing.setSelected(options.useTougherHealing());

        // Prisoners
        comboPrisonerCaptureStyle.setSelectedItem(options.getPrisonerCaptureStyle());
        comboPrisonerStatus.setSelectedItem(options.getDefaultPrisonerStatus());
        chkPrisonerBabyStatus.setSelected(options.getPrisonerBabyStatus());
        chkAtBPrisonerDefection.setSelected(options.useAtBPrisonerDefection());
        chkAtBPrisonerRansom.setSelected(options.useAtBPrisonerRansom());

        // Personnel Randomization
        chkUseDylansRandomXP.setSelected(options.useDylansRandomXP());
        if (chkRandomizeOrigin.isSelected() != options.randomizeOrigin()) {
            chkRandomizeOrigin.doClick();
        }
        chkRandomizeDependentsOrigin.setSelected(options.getRandomizeDependentOrigin());
        spnOriginSearchRadius.setValue(options.getOriginSearchRadius());
        chkExtraRandomOrigin.setSelected(options.extraRandomOrigin());
        spnOriginDistanceScale.setValue(options.getOriginDistanceScale());

        // Family
        comboDisplayFamilyLevel.setSelectedItem(options.getDisplayFamilyLevel());

        // Salary
        spnCommissionedSalary.setValue(options.getSalaryCommissionMultiplier());
        spnEnlistedSalary.setValue(options.getSalaryEnlistedMultiplier());
        spnAntiMekSalary.setValue(options.getSalaryAntiMekMultiplier());
        for (int i = 0; i < spnSalaryExperienceMultipliers.length; i++) {
            spnSalaryExperienceMultipliers[i].setValue(options.getSalaryXPMultiplier(i));
        }
        for (int i = 0; i < spnBaseSalary.length; i++) {
            spnBaseSalary[i].setValue(options.getRoleBaseSalaries()[i].getAmount().doubleValue());
        }

        // Marriage
        chkUseManualMarriages.setSelected(options.useManualMarriages());
        spnMinimumMarriageAge.setValue(options.getMinimumMarriageAge());
        spnCheckMutualAncestorsDepth.setValue(options.checkMutualAncestorsDepth());
        chkLogMarriageNameChange.setSelected(options.logMarriageNameChange());
        if (chkUseRandomMarriages.isSelected() != options.useRandomMarriages()) {
            chkUseRandomMarriages.doClick();
        }
        spnChanceRandomMarriages.setValue(options.getChanceRandomMarriages() * 100.0);
        spnMarriageAgeRange.setValue(options.getMarriageAgeRange());
        for (int i = 0; i < spnMarriageSurnameWeights.length; i++) {
            spnMarriageSurnameWeights[i].setValue(options.getMarriageSurnameWeight(i) / 10.0);
        }
        if (chkUseRandomSameSexMarriages.isSelected() != options.useRandomSameSexMarriages()) {
            if (chkUseRandomSameSexMarriages.isEnabled()) {
                chkUseRandomSameSexMarriages.doClick();
            } else {
                chkUseRandomSameSexMarriages.setSelected(options.useRandomSameSexMarriages());
            }
        }
        spnChanceRandomSameSexMarriages.setValue(options.getChanceRandomSameSexMarriages() * 100.0);

        // Procreation
        if (chkUseProcreation.isSelected() != options.useProcreation()) {
            chkUseProcreation.doClick();
        }
        spnChanceProcreation.setValue(options.getChanceProcreation() * 100.0);
        if (chkUseProcreationNoRelationship.isSelected() != options.useProcreationNoRelationship()) {
            if (chkUseProcreationNoRelationship.isEnabled()) {
                chkUseProcreationNoRelationship.doClick();
            } else {
                chkUseProcreationNoRelationship.setSelected(options.useProcreationNoRelationship());
            }
        }
        spnChanceProcreationNoRelationship.setValue(options.getChanceProcreationNoRelationship() * 100.0);
        chkDisplayTrueDueDate.setSelected(options.getDisplayTrueDueDate());
        chkLogConception.setSelected(options.logConception());
        comboBabySurnameStyle.setSelectedItem(options.getBabySurnameStyle());
        chkDetermineFatherAtBirth.setSelected(options.determineFatherAtBirth());

        // Death
        chkKeepMarriedNameUponSpouseDeath.setSelected(options.getKeepMarriedNameUponSpouseDeath());
        //endregion Personnel Tab

        //region Finances Tab
        payForPartsBox.setSelected(options.payForParts());
        payForRepairsBox.setSelected(options.payForRepairs());
        payForUnitsBox.setSelected(options.payForUnits());
        payForSalariesBox.setSelected(options.payForSalaries());
        payForOverheadBox.setSelected(options.payForOverhead());
        payForMaintainBox.setSelected(options.payForMaintain());
        payForTransportBox.setSelected(options.payForTransport());
        sellUnitsBox.setSelected(options.canSellUnits());
        sellPartsBox.setSelected(options.canSellParts());
        payForRecruitmentBox.setSelected(options.payForRecruitment());
        useLoanLimitsBox.setSelected(options.useLoanLimits());
        usePercentageMaintBox.setSelected(options.usePercentageMaint());
        useInfantryDontCountBox.setSelected(options.useInfantryDontCount());
        usePeacetimeCostBox.setSelected(options.usePeacetimeCost());
        useExtendedPartsModifierBox.setSelected(options.useExtendedPartsModifier());
        showPeacetimeCostBox.setSelected(options.showPeacetimeCost());
        comboFinancialYearDuration.setSelectedItem(options.getFinancialYearDuration());
        newFinancialYearFinancesToCSVExportBox.setSelected(options.getNewFinancialYearFinancesToCSVExport());

        spnClanPriceModifier.setValue(options.getClanPriceModifier());
        for (int i = 0; i < spnUsedPartsValue.length; i++) {
            spnUsedPartsValue[i].setValue(options.getUsedPartsValue(i));
            partQualityLabels[i].setText(Part.getQualityName(i, options.reverseQualityNames()) + " Quality");
        }
        spnDamagedPartsValue.setValue(options.getDamagedPartsValue());
        spnOrderRefund.setValue(options.GetCanceledOrderReimbursement());
        //endregion Finances Tab

        //region Mercenary Tab
        if (options.useEquipmentContractBase()) {
            btnContractEquipment.setSelected(true);
        } else {
            btnContractPersonnel.setSelected(true);
        }
        spnEquipPercent.setValue(options.getEquipmentContractPercent());
        spnDropshipPercent.setValue(options.getDropshipContractPercent());
        spnJumpshipPercent.setValue(options.getJumpshipContractPercent());
        spnWarshipPercent.setValue(options.getWarshipContractPercent());
        chkEquipContractSaleValue.setSelected(options.useEquipmentContractSaleValue());
        chkBLCSaleValue.setSelected(options.useBLCSaleValue());
        chkOverageRepaymentInFinalPayment.setSelected(options.getOverageRepaymentInFinalPayment());
        //endregion Mercenary Tab

        //region Experience Tab
        spnScenarioXP.setValue(options.getScenarioXP());
        spnKillXP.setValue(options.getKillXPAward());
        spnKills.setValue(options.getKillsForXP());
        spnTaskXP.setValue(options.getTaskXP());
        spnNTasksXP.setValue(options.getNTasksXP());
        spnSuccessXP.setValue(options.getSuccessXP());
        spnMistakeXP.setValue(options.getMistakeXP());
        spnIdleXP.setValue(options.getIdleXP());
        spnMonthsIdleXP.setValue(options.getMonthsIdleXP());
        spnTargetIdleXP.setValue(options.getTargetIdleXP());
        spnContractNegotiationXP.setValue(options.getContractNegotiationXP());
        spnAdminWeeklyXP.setValue(options.getAdminXP());
        spnAdminWeeklyXPPeriod.setValue(options.getAdminXPPeriod());
        spnEdgeCost.setValue(options.getEdgeCost());
        //endregion Experience Tab

        //region Skills Tab
        //endregion Skills Tab

        //region Special Abilities Tab
        //endregion Special Abilities Tab

        //region Skill Randomization Tab
        chkExtraRandom.setSelected(randomSkillPreferences.randomizeSkill());
        final int[] phenotypeProbabilities = options.getPhenotypeProbabilities();
        for (int i = 0; i < phenotypeSpinners.length; i++) {
            phenotypeSpinners[i].setValue(phenotypeProbabilities[i]);
        }
        spnProbAntiMek.setValue(rSkillPrefs.getAntiMekProb());
        spnOverallRecruitBonus.setValue(rSkillPrefs.getOverallRecruitBonus());
        for (int i = 0; i < spnTypeRecruitBonus.length; i++) {
            spnTypeRecruitBonus[i].setValue(rSkillPrefs.getRecruitBonuses()[i]);
        }
        spnArtyProb.setValue(rSkillPrefs.getArtilleryProb());
        spnArtyBonus.setValue(rSkillPrefs.getArtilleryBonus());
        spnSecondProb.setValue(rSkillPrefs.getSecondSkillProb());
        spnSecondBonus.setValue(rSkillPrefs.getSecondSkillBonus());
        spnTacticsGreen.setValue(rSkillPrefs.getTacticsMod(SkillType.EXP_GREEN));
        spnTacticsReg.setValue(rSkillPrefs.getTacticsMod(SkillType.EXP_REGULAR));
        spnTacticsVet.setValue(rSkillPrefs.getTacticsMod(SkillType.EXP_VETERAN));
        spnTacticsElite.setValue(rSkillPrefs.getTacticsMod(SkillType.EXP_ELITE));
        spnAbilGreen.setValue(rSkillPrefs.getSpecialAbilBonus(SkillType.EXP_GREEN));
        spnAbilReg.setValue(rSkillPrefs.getSpecialAbilBonus(SkillType.EXP_REGULAR));
        spnAbilVet.setValue(rSkillPrefs.getSpecialAbilBonus(SkillType.EXP_VETERAN));
        spnAbilElite.setValue(rSkillPrefs.getSpecialAbilBonus(SkillType.EXP_ELITE));
        spnCombatSA.setValue(rSkillPrefs.getCombatSmallArmsBonus());
        spnSupportSA.setValue(rSkillPrefs.getSupportSmallArmsBonus());
        //endregion Skill Randomization Tab

        //region Rank System Tab
        //endregion Rank System Tab

        //region Name and Portrait Generation Tab
        if (chkUseOriginFactionForNames.isSelected() != options.useOriginFactionForNames()) {
            chkUseOriginFactionForNames.doClick();
        }

        boolean allSelected = true;
        boolean noneSelected = true;
        final boolean[] usePortraitForRole = options.usePortraitForRoles();
        for (int i = 0; i < chkUsePortrait.length; i++) {
            chkUsePortrait[i].setSelected(usePortraitForRole[i]);
            if (usePortraitForRole[i]) {
                noneSelected = false;
            } else {
                allSelected = false;
            }
        }
        if (allSelected != allPortraitsBox.isSelected()) {
            allPortraitsBox.doClick();
        }

        if (noneSelected != noPortraitsBox.isSelected()) {
            noPortraitsBox.doClick();
        }

        chkAssignPortraitOnRoleChange.setSelected(options.getAssignPortraitOnRoleChange());
        //endregion Name and Portrait Generation Tab

        //region Markets Tab
        comboPersonnelMarketType.setSelectedItem(options.getPersonnelMarketType());
        chkPersonnelMarketReportRefresh.setSelected(options.getPersonnelMarketReportRefresh());
        spnPersonnelMarketRandomEliteRemoval.setValue(options.getPersonnelMarketRandomEliteRemoval());
        spnPersonnelMarketRandomVeteranRemoval.setValue(options.getPersonnelMarketRandomVeteranRemoval());
        spnPersonnelMarketRandomRegularRemoval.setValue(options.getPersonnelMarketRandomRegularRemoval());
        spnPersonnelMarketRandomGreenRemoval.setValue(options.getPersonnelMarketRandomGreenRemoval());
        spnPersonnelMarketRandomUltraGreenRemoval.setValue(options.getPersonnelMarketRandomUltraGreenRemoval());
        spnPersonnelMarketDylansWeight.setValue(options.getPersonnelMarketDylansWeight());

        // Unit Market
        comboUnitMarketMethod.setSelectedItem(options.getUnitMarketMethod());
        chkUnitMarketRegionalMechVariations.setSelected(options.useUnitMarketRegionalMechVariations());
        chkInstantUnitMarketDelivery.setSelected(options.getInstantUnitMarketDelivery());
        chkUnitMarketReportRefresh.setSelected(options.getUnitMarketReportRefresh());

        // Contract Market
        comboContractMarketMethod.setSelectedItem(options.getContractMarketMethod());
        chkContractMarketReportRefresh.setSelected(options.getContractMarketReportRefresh());
        //endregion Markets Tab

        //region Against the Bot Tab
        if (chkUseAtB.isSelected() != options.getUseAtB()) {
            chkUseAtB.doClick();
        }
        chkUseStratCon.setSelected(options.getUseStratCon());
        cbSkillLevel.setSelectedIndex(options.getSkillLevel());

        chkUseShareSystem.setSelected(options.getUseShareSystem());
        chkSharesExcludeLargeCraft.setSelected(options.getSharesExcludeLargeCraft());
        chkSharesForAll.setSelected(options.getSharesForAll());
        chkAeroRecruitsHaveUnits.setSelected(options.getAeroRecruitsHaveUnits());
        chkRetirementRolls.setSelected(options.doRetirementRolls());
        chkCustomRetirementMods.setSelected(options.getCustomRetirementMods());
        chkFoundersNeverRetire.setSelected(options.getFoundersNeverRetire());
        chkAddDependents.setSelected(options.canAtBAddDependents());
        chkDependentsNeverLeave.setSelected(options.getDependentsNeverLeave());
        chkTrackUnitFatigue.setSelected(options.getTrackUnitFatigue());
        chkUseLeadership.setSelected(options.getUseLeadership());
        chkTrackOriginalUnit.setSelected(options.getTrackOriginalUnit());
        chkUseAero.setSelected(options.getUseAero());
        chkUseVehicles.setSelected(options.getUseVehicles());
        chkClanVehicles.setSelected(options.getClanVehicles());

        spnSearchRadius.setValue(options.getSearchRadius());
        chkVariableContractLength.setSelected(options.getVariableContractLength());
        chkMercSizeLimited.setSelected(options.isMercSizeLimited());
        chkRestrictPartsByMission.setSelected(options.getRestrictPartsByMission());
        chkLimitLanceWeight.setSelected(options.getLimitLanceWeight());
        chkLimitLanceNumUnits.setSelected(options.getLimitLanceNumUnits());
        chkUseStrategy.setSelected(options.getUseStrategy());
        spnBaseStrategyDeployment.setValue(options.getBaseStrategyDeployment());
        spnAdditionalStrategyDeployment.setValue(options.getAdditionalStrategyDeployment());
        chkAdjustPaymentForStrategy.setSelected(options.getAdjustPaymentForStrategy());
        spnAtBBattleChance[AtBLanceRole.FIGHTING.ordinal()].setValue(options.getAtBBattleChance(AtBLanceRole.FIGHTING));
        spnAtBBattleChance[AtBLanceRole.DEFENCE.ordinal()].setValue(options.getAtBBattleChance(AtBLanceRole.DEFENCE));
        spnAtBBattleChance[AtBLanceRole.SCOUTING.ordinal()].setValue(options.getAtBBattleChance(AtBLanceRole.SCOUTING));
        spnAtBBattleChance[AtBLanceRole.TRAINING.ordinal()].setValue(options.getAtBBattleChance(AtBLanceRole.TRAINING));
        btnIntensityUpdate.doClick();
        chkGenerateChases.setSelected(options.generateChases());

        btnDynamicRATs.setSelected(!options.useStaticRATs());
        btnStaticRATs.setSelected(options.useStaticRATs());
        for (String rat : options.getRATs()) {
            List<Integer> eras = RATManager.getAllRATCollections().get(rat);
            if (eras != null) {
                StringBuilder displayName = new StringBuilder(rat);
                if (eras.size() > 0) {
                    displayName.append(" (").append(eras.get(0));
                    if (eras.size() > 1) {
                        displayName.append("-").append(eras.get(eras.size() - 1));
                    }
                    displayName.append(")");
                }
                if (availableRatModel.contains(displayName.toString())) {
                    chosenRatModel.addElement(displayName.toString());
                    availableRatModel.removeElement(displayName.toString());
                }
            }
        }
        chkIgnoreRatEra.setSelected(options.canIgnoreRatEra());

        chkDoubleVehicles.setSelected(options.getDoubleVehicles());
        spnOpforLanceTypeMechs.setValue(options.getOpforLanceTypeMechs());
        spnOpforLanceTypeMixed.setValue(options.getOpforLanceTypeMixed());
        spnOpforLanceTypeVehicles.setValue(options.getOpforLanceTypeVehicles());
        chkOpforUsesVTOLs.setSelected(options.getOpforUsesVTOLs());
        chkOpforUsesAero.setSelected(options.getAllowOpforAeros());
        spnOpforAeroChance.setValue(options.getOpforAeroChance());
        chkOpforUsesLocalForces.setSelected(options.getAllowOpforLocalUnits());
        spnOpforLocalForceChance.setValue(options.getOpforLocalUnitChance());
        chkAdjustPlayerVehicles.setSelected(options.getAdjustPlayerVehicles());
        chkRegionalMechVariations.setSelected(options.getRegionalMechVariations());
        chkAttachedPlayerCamouflage.setSelected(options.getAttachedPlayerCamouflage());
        chkPlayerControlsAttachedUnits.setSelected(options.getPlayerControlsAttachedUnits());
        chkUseDropShips.setSelected(options.getUseDropShips());
        chkUseWeatherConditions.setSelected(options.getUseWeatherConditions());
        chkUseLightConditions.setSelected(options.getUseLightConditions());
        chkUsePlanetaryConditions.setSelected(options.getUsePlanetaryConditions());
        //endregion Against the Bot Tab
    }

    public static String[][] getSkillCostsArray(Hashtable<String, SkillType> skillHash) {
        String[][] array = new String[SkillType.getSkillList().length][11];
        int i = 0;
        for (String name : SkillType.getSkillList()) {
            SkillType type = skillHash.get(name);
            for (int j = 0; j < 11; j++) {
                array[i][j] = Integer.toString(type.getCost(j));
            }
            i++;
        }
        return array;
    }
    //endregion Initialization

    private void btnLoadActionPerformed() {
        List<GamePreset> presets = GamePreset.getGamePresetsIn();

        if (!presets.isEmpty()) {
            ChooseGamePresetDialog cgpd = new ChooseGamePresetDialog(null, true, presets);
            cgpd.setVisible(true);
            if (!cgpd.wasCancelled() && (cgpd.getSelectedPreset() != null)) {
                applyPreset(cgpd.getSelectedPreset());
            }
        }
    }

    private void btnSaveActionPerformed() {
        if (txtName.getText().length() == 0) {
            return;
        }
        GamePresetDescriptionDialog gpdd = new GamePresetDescriptionDialog(null, true,
                "Enter a title", "Enter description of preset");
        gpdd.setVisible(true);
        if (!gpdd.wasChanged()) {
            return;
        }

        MekHQ.getLogger().info("Saving campaign options...");
        // Choose a file...
        Optional<File> maybeFile = FileDialogs.saveCampaignOptions(null);

        if (!maybeFile.isPresent()) {
            return;
        }

        File file = maybeFile.get();

        String path = file.getPath();
        if (!path.endsWith(".xml")) {
            path += ".xml";
            file = new File(path);
        }

        // check for existing file and make a back-up if found
        String path2 = path + "_backup";
        File backupFile = new File(path2);
        if (file.exists()) {
            Utilities.copyfile(file, backupFile);
        }

        updateOptions();
        GamePreset preset = new GamePreset(gpdd.getTitle(), gpdd.getDesc(), options, rSkillPrefs,
                SkillType.lookupHash, SpecialAbility.getAllSpecialAbilities());

        // Then save it out to that file.
        try (FileOutputStream fos = new FileOutputStream(file);
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            preset.writeToXml(pw, 1);
            pw.flush();
            MekHQ.getLogger().info("Campaign options saved to " + file);
        } catch (Exception ex) {
            MekHQ.getLogger().error(ex);
            JOptionPane.showMessageDialog(null,
                    "Whoops, for some reason the game presets could not be saved",
                    "Could not save presets", JOptionPane.ERROR_MESSAGE);
            file.delete();
            if (backupFile.exists()) {
                Utilities.copyfile(backupFile, file);
            }
        }
        if (backupFile.exists()) {
            backupFile.delete();
        }

        this.setVisible(false);
    }

    private void updateOptions() {
        campaign.setName(txtName.getText());
        campaign.setLocalDate(date);
        // Ensure that the MegaMek year GameOption matches the campaign year
        campaign.getGameOptions().getOption(OptionsConstants.ALLOWED_YEAR).setValue(campaign.getGameYear());
        campaign.setFactionCode(Factions.getInstance().getFactionFromFullNameAndYear
                (String.valueOf(comboFaction.getSelectedItem()), date.getYear()).getShortName());
        if (null != comboFactionNames.getSelectedItem()) {
            RandomNameGenerator.getInstance().setChosenFaction((String) comboFactionNames.getSelectedItem());
        }
        RandomGenderGenerator.setPercentFemale(sldGender.getValue());
        rankSystemsPane.applyToCampaign();
        campaign.setCamouflage(camouflage);
        campaign.setColour(colour);

        campaign.setIconCategory(iconCategory);
        campaign.setIconFileName(iconFileName);

        for (int i = 0; i < chkUsePortrait.length; i++) {
            options.setUsePortraitForRole(i, chkUsePortrait[i].isSelected());
        }

        updateSkillTypes();
        updateXPCosts();

        // Rules panel
        options.setEraMods(useEraModsCheckBox.isSelected());
        options.setAssignedTechFirst(assignedTechFirstCheckBox.isSelected());
        options.setResetToFirstTech(resetToFirstTechCheckBox.isSelected());
        options.setQuirks(useQuirksBox.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.ADVANCED_STRATOPS_QUIRKS).setValue(useQuirksBox.isSelected());
        options.setClanPriceModifier((Double) spnClanPriceModifier.getValue());
        for (int i = Part.QUALITY_A; i <= Part.QUALITY_F; i++) {
            options.setUsedPartsValue((Double) spnUsedPartsValue[i].getValue(), i);
        }
        options.setDamagedPartsValue((Double) spnDamagedPartsValue.getValue());
        options.setCanceledOrderReimbursement((Double) spnOrderRefund.getValue());
        options.setUnitRatingMethod((UnitRatingMethod) unitRatingMethodCombo.getSelectedItem());
        options.setManualUnitRatingModifier((Integer) manualUnitRatingModifier.getValue());
        options.setUseOriginFactionForNames(chkUseOriginFactionForNames.isSelected());
        options.setDestroyByMargin(useDamageMargin.isSelected());
        options.setDestroyMargin((Integer) spnDamageMargin.getValue());
        options.setDestroyPartTarget((Integer) spnDestroyPartTarget.getValue());
        options.setUseAeroSystemHits(useAeroSystemHitsBox.isSelected());
        options.setCheckMaintenance(checkMaintenance.isSelected());
        options.setUseQualityMaintenance(useQualityMaintenance.isSelected());
        options.setReverseQualityNames(reverseQualityNames.isSelected());
        options.setUseUnofficialMaintenance(useUnofficialMaintenance.isSelected());
        options.setLogMaintenance(logMaintenance.isSelected());
        options.setMaintenanceBonus((Integer) spnMaintenanceBonus.getValue());
        options.setMaintenanceCycleDays((Integer) spnMaintenanceDays.getValue());
        options.setPayForParts(payForPartsBox.isSelected());
        options.setPayForRepairs(payForRepairsBox.isSelected());
        options.setPayForUnits(payForUnitsBox.isSelected());
        options.setPayForSalaries(payForSalariesBox.isSelected());
        options.setPayForOverhead(payForOverheadBox.isSelected());
        options.setPayForMaintain(payForMaintainBox.isSelected());
        options.setPayForTransport(payForTransportBox.isSelected());
        options.setPayForRecruitment(payForRecruitmentBox.isSelected());
        options.setLoanLimits(useLoanLimitsBox.isSelected());
        options.setUsePercentageMaint(usePercentageMaintBox.isSelected());
        options.setUseInfantryDontCount(useInfantryDontCountBox.isSelected());
        options.setSellUnits(sellUnitsBox.isSelected());
        options.setSellParts(sellPartsBox.isSelected());
        options.setUsePeacetimeCost(usePeacetimeCostBox.isSelected());
        options.setUseExtendedPartsModifier(useExtendedPartsModifierBox.isSelected());
        options.setShowPeacetimeCost(showPeacetimeCostBox.isSelected());
        options.setNewFinancialYearFinancesToCSVExport(newFinancialYearFinancesToCSVExportBox.isSelected());
        options.setFinancialYearDuration((FinancialYearDuration) comboFinancialYearDuration.getSelectedItem());
        options.setAssignPortraitOnRoleChange(chkAssignPortraitOnRoleChange.isSelected());

        options.setEquipmentContractBase(btnContractEquipment.isSelected());
        options.setEquipmentContractPercent((Double) spnEquipPercent.getValue());
        options.setDropshipContractPercent((Double) spnDropshipPercent.getValue());
        options.setJumpshipContractPercent((Double) spnJumpshipPercent.getValue());
        options.setWarshipContractPercent((Double) spnWarshipPercent.getValue());
        options.setEquipmentContractSaleValue(chkEquipContractSaleValue.isSelected());
        options.setBLCSaleValue(chkBLCSaleValue.isSelected());
        options.setOverageRepaymentInFinalPayment(chkOverageRepaymentInFinalPayment.isSelected());

        options.setWaitingPeriod((Integer) spnAcquireWaitingPeriod.getValue());
        options.setAcquisitionSkill((String) choiceAcquireSkill.getSelectedItem());
        options.setAcquisitionSupportStaffOnly(chkSupportStaffOnly.isSelected());
        options.setClanAcquisitionPenalty((Integer) spnAcquireClanPenalty.getValue());
        options.setIsAcquisitionPenalty((Integer) spnAcquireIsPenalty.getValue());
        options.setMaxAcquisitions(Integer.parseInt(txtMaxAcquisitions.getText()));

        options.setNDiceTransitTime((Integer) spnNDiceTransitTime.getValue());
        options.setConstantTransitTime((Integer) spnConstantTransitTime.getValue());
        options.setUnitTransitTime(choiceTransitTimeUnits.getSelectedIndex());
        options.setAcquireMosBonus((Integer) spnAcquireMosBonus.getValue());
        options.setAcquireMinimumTime((Integer) spnAcquireMinimum.getValue());
        options.setAcquireMinimumTimeUnit(choiceAcquireMinimumUnit.getSelectedIndex());
        options.setAcquireMosUnit(choiceAcquireMosUnits.getSelectedIndex());
        options.setPlanetaryAcquisition(usePlanetaryAcquisitions.isSelected());
        options.setDisallowClanPartsFromIS(disallowClanPartsFromIS.isSelected());
        options.setPlanetAcquisitionVerboseReporting(usePlanetaryAcquisitionsVerbose.isSelected());
        options.setDisallowPlanetAcquisitionClanCrossover(disallowPlanetaryAcquisitionClanCrossover.isSelected());
        options.setMaxJumpsPlanetaryAcquisition((int) spnMaxJumpPlanetaryAcquisitions.getValue());
        options.setPenaltyClanPartsFroIS((int) spnPenaltyClanPartsFromIS.getValue());
        options.setPlanetAcquisitionFactionLimit(comboPlanetaryAcquisitionsFactionLimits.getSelectedIndex());
        for (int i = ITechnology.RATING_A; i <= ITechnology.RATING_F; i++) {
            options.setPlanetTechAcquisitionBonus((int) spnPlanetAcquireTechBonus[i].getValue(), i);
            options.setPlanetIndustryAcquisitionBonus((int) spnPlanetAcquireIndustryBonus[i].getValue(), i);
            options.setPlanetOutputAcquisitionBonus((int) spnPlanetAcquireOutputBonus[i].getValue(), i);

        }

        options.setScenarioXP((Integer) spnScenarioXP.getValue());
        options.setKillsForXP((Integer) spnKills.getValue());
        options.setKillXPAward((Integer) spnKillXP.getValue());

        options.setTaskXP((Integer) spnTaskXP.getValue());
        options.setNTasksXP((Integer) spnNTasksXP.getValue());
        options.setSuccessXP((Integer) spnSuccessXP.getValue());
        options.setMistakeXP((Integer) spnMistakeXP.getValue());
        options.setIdleXP((Integer) spnIdleXP.getValue());
        options.setMonthsIdleXP((Integer) spnMonthsIdleXP.getValue());
        options.setContractNegotiationXP((Integer) spnContractNegotiationXP.getValue());
        options.setAdminXP((Integer) spnAdminWeeklyXP.getValue());
        options.setAdminXPPeriod((Integer) spnAdminWeeklyXPPeriod.getValue());
        options.setEdgeCost((Integer) spnEdgeCost.getValue());
        options.setTargetIdleXP((Integer) spnTargetIdleXP.getValue());

        options.setLimitByYear(limitByYearBox.isSelected());
        options.setDisallowExtinctStuff(disallowExtinctStuffBox.isSelected());
        options.setAllowClanPurchases(allowClanPurchasesBox.isSelected());
        options.setAllowISPurchases(allowISPurchasesBox.isSelected());
        options.setAllowCanonOnly(allowCanonOnlyBox.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.ALLOWED_CANON_ONLY).setValue(allowCanonOnlyBox.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.ALLOWED_ERA_BASED).setValue(variableTechLevelBox.isSelected());
        options.setVariableTechLevel(variableTechLevelBox.isSelected() && options.limitByYear());
        options.setFactionIntroDate(factionIntroDateBox.isSelected());
        campaign.updateTechFactionCode();
        options.setAllowCanonRefitOnly(allowCanonRefitOnlyBox.isSelected());
        options.setUseAmmoByType(useAmmoByTypeBox.isSelected());
        options.setTechLevel(choiceTechLevel.getSelectedIndex());
        campaign.getGameOptions().getOption(OptionsConstants.ALLOWED_TECHLEVEL).setValue((String) choiceTechLevel.getSelectedItem());

        rSkillPrefs.setOverallRecruitBonus((Integer) spnOverallRecruitBonus.getValue());
        for (int i = 0; i < spnTypeRecruitBonus.length; i++) {
            rSkillPrefs.setRecruitBonus(i, (Integer) spnTypeRecruitBonus[i].getValue());
        }
        rSkillPrefs.setRandomizeSkill(chkExtraRandom.isSelected());
        rSkillPrefs.setAntiMekProb((Integer) spnProbAntiMek.getValue());
        rSkillPrefs.setArtilleryProb((Integer) spnArtyProb.getValue());
        rSkillPrefs.setArtilleryBonus((Integer) spnArtyBonus.getValue());
        rSkillPrefs.setSecondSkillProb((Integer) spnSecondProb.getValue());
        rSkillPrefs.setSecondSkillBonus((Integer) spnSecondBonus.getValue());
        rSkillPrefs.setTacticsMod(SkillType.EXP_GREEN, (Integer) spnTacticsGreen.getValue());
        rSkillPrefs.setTacticsMod(SkillType.EXP_REGULAR, (Integer) spnTacticsReg.getValue());
        rSkillPrefs.setTacticsMod(SkillType.EXP_VETERAN, (Integer) spnTacticsVet.getValue());
        rSkillPrefs.setTacticsMod(SkillType.EXP_ELITE, (Integer) spnTacticsElite.getValue());
        rSkillPrefs.setCombatSmallArmsBonus((Integer) spnCombatSA.getValue());
        rSkillPrefs.setSupportSmallArmsBonus((Integer) spnSupportSA.getValue());
        rSkillPrefs.setSpecialAbilBonus(SkillType.EXP_GREEN, (Integer) spnAbilGreen.getValue());
        rSkillPrefs.setSpecialAbilBonus(SkillType.EXP_REGULAR, (Integer) spnAbilReg.getValue());
        rSkillPrefs.setSpecialAbilBonus(SkillType.EXP_VETERAN, (Integer) spnAbilVet.getValue());
        rSkillPrefs.setSpecialAbilBonus(SkillType.EXP_ELITE, (Integer) spnAbilElite.getValue());
        campaign.setRandomSkillPreferences(rSkillPrefs);

        for (int i = 0; i < phenotypeSpinners.length; i++) {
            options.setPhenotypeProbability(i, (Integer) phenotypeSpinners[i].getValue());
        }

        //region Personnel Tab
        // General Personnel
        options.setUseTactics(chkUseTactics.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.RPG_COMMAND_INIT).setValue(chkUseTactics.isSelected());
        options.setUseInitiativeBonus(chkUseInitiativeBonus.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.RPG_INDIVIDUAL_INITIATIVE).setValue(chkUseInitiativeBonus.isSelected());
        options.setUseToughness(chkUseToughness.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.RPG_TOUGHNESS).setValue(chkUseToughness.isSelected());
        options.setUseArtillery(chkUseArtillery.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.RPG_ARTILLERY_SKILL).setValue(chkUseArtillery.isSelected());
        options.setUseAbilities(chkUseAbilities.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.RPG_PILOT_ADVANTAGES).setValue(chkUseAbilities.isSelected());
        options.setUseEdge(chkUseEdge.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.EDGE).setValue(chkUseEdge.isSelected());
        options.setUseSupportEdge(chkUseEdge.isSelected() && chkUseSupportEdge.isSelected());
        options.setUseImplants(chkUseImplants.isSelected());
        campaign.getGameOptions().getOption(OptionsConstants.RPG_MANEI_DOMINI).setValue(chkUseImplants.isSelected());
        options.setAlternativeQualityAveraging(chkUseAlternativeQualityAveraging.isSelected());
        options.setUseTransfers(chkUseTransfers.isSelected());

        // Expanded Personnel Information
        options.setUseTimeInService(chkUseTimeInService.isSelected());
        options.setTimeInServiceDisplayFormat((TimeInDisplayFormat) comboTimeInServiceDisplayFormat.getSelectedItem());
        options.setUseTimeInRank(chkUseTimeInRank.isSelected());
        options.setTimeInRankDisplayFormat((TimeInDisplayFormat) comboTimeInRankDisplayFormat.getSelectedItem());
        options.setUseRetirementDateTracking(chkUseRetirementDateTracking.isSelected());
        options.setTrackTotalEarnings(chkTrackTotalEarnings.isSelected());
        options.setShowOriginFaction(chkShowOriginFaction.isSelected());

        // Medical
        options.setUseAdvancedMedical(chkUseAdvancedMedical.isSelected());
        // we need to reset healing time options through the campaign because we may need to
        // loop through personnel to make adjustments
        campaign.setHealingTimeOptions((Integer) spnHealWaitingPeriod.getValue(),
                (Integer) spnNaturalHealWaitingPeriod.getValue());
        options.setMinimumHitsForVehicles((Integer) spnMinimumHitsForVehicles.getValue());
        options.setUseRandomHitsForVehicles(chkUseRandomHitsForVehicles.isSelected());
        options.setTougherHealing(chkUseTougherHealing.isSelected());

        // Prisoners
        options.setPrisonerCaptureStyle((PrisonerCaptureStyle) comboPrisonerCaptureStyle.getSelectedItem());
        options.setDefaultPrisonerStatus((PrisonerStatus) comboPrisonerStatus.getSelectedItem());
        options.setPrisonerBabyStatus(chkPrisonerBabyStatus.isSelected());
        options.setUseAtBPrisonerDefection(chkAtBPrisonerDefection.isSelected());
        options.setUseAtBPrisonerRansom(chkAtBPrisonerRansom.isSelected());

        // Personnel Randomization
        options.setUseDylansRandomXP(chkUseDylansRandomXP.isSelected());
        options.setRandomizeOrigin(chkRandomizeOrigin.isSelected());
        options.setRandomizeDependentOrigin(chkRandomizeDependentsOrigin.isSelected());
        options.setOriginSearchRadius((Integer) spnOriginSearchRadius.getValue());
        options.setExtraRandomOrigin(chkExtraRandomOrigin.isSelected());
        options.setOriginDistanceScale((Double) spnOriginDistanceScale.getValue());

        // Family
        options.setDisplayFamilyLevel((FamilialRelationshipDisplayLevel) comboDisplayFamilyLevel.getSelectedItem());

        // Salary
        options.setSalaryCommissionMultiplier((Double) spnCommissionedSalary.getValue());
        options.setSalaryEnlistedMultiplier((Double) spnEnlistedSalary.getValue());
        options.setSalaryAntiMekMultiplier((Double) spnAntiMekSalary.getValue());
        for (int i = 0; i < spnSalaryExperienceMultipliers.length; i++) {
            options.setSalaryXPMultiplier(i, (Double) spnSalaryExperienceMultipliers[i].getValue());
        }
        for (final PersonnelRole personnelRole : PersonnelRole.values()) {
            options.setRoleBaseSalary(personnelRole, (double) spnBaseSalary[personnelRole.ordinal()].getValue());
        }

        // Marriage
        options.setUseManualMarriages(chkUseManualMarriages.isSelected());
        options.setMinimumMarriageAge((Integer) spnMinimumMarriageAge.getValue());
        options.setCheckMutualAncestorsDepth((Integer) spnCheckMutualAncestorsDepth.getValue());
        options.setLogMarriageNameChange(chkLogMarriageNameChange.isSelected());
        options.setUseRandomMarriages(chkUseRandomMarriages.isSelected());
        options.setChanceRandomMarriages((Double) spnChanceRandomMarriages.getValue() / 100.0);
        options.setMarriageAgeRange((Integer) spnMarriageAgeRange.getValue());
        for (int i = 0; i < spnMarriageSurnameWeights.length; i++) {
            int val = (int) Math.round(((Double) spnMarriageSurnameWeights[i].getValue()) * 10);
            options.setMarriageSurnameWeight(i, val);
        }
        options.setUseRandomSameSexMarriages(chkUseRandomSameSexMarriages.isSelected());
        options.setChanceRandomSameSexMarriages((Double) spnChanceRandomSameSexMarriages.getValue() / 100.0);

        // Procreation
        options.setUseProcreation(chkUseProcreation.isSelected());
        options.setChanceProcreation((Double) spnChanceProcreation.getValue() / 100.0);
        options.setUseProcreationNoRelationship(chkUseProcreationNoRelationship.isSelected());
        options.setChanceProcreationNoRelationship((Double) spnChanceProcreationNoRelationship.getValue() / 100.0);
        options.setDisplayTrueDueDate(chkDisplayTrueDueDate.isSelected());
        options.setLogConception(chkLogConception.isSelected());
        options.setBabySurnameStyle((BabySurnameStyle) comboBabySurnameStyle.getSelectedItem());
        options.setDetermineFatherAtBirth(chkDetermineFatherAtBirth.isSelected());

        // Death
        options.setKeepMarriedNameUponSpouseDeath(chkKeepMarriedNameUponSpouseDeath.isSelected());
        //endregion Personnel Tab

        //start SPA
        SpecialAbility.replaceSpecialAbilities(getCurrentSPA());
        //end SPA

        //region Markets Tab
        // Personnel Market
        options.setPersonnelMarketType((String) comboPersonnelMarketType.getSelectedItem());
        options.setPersonnelMarketReportRefresh(chkPersonnelMarketReportRefresh.isSelected());
        options.setPersonnelMarketRandomEliteRemoval((Integer) spnPersonnelMarketRandomEliteRemoval.getValue());
        options.setPersonnelMarketRandomVeteranRemoval((Integer) spnPersonnelMarketRandomVeteranRemoval.getValue());
        options.setPersonnelMarketRandomRegularRemoval((Integer) spnPersonnelMarketRandomRegularRemoval.getValue());
        options.setPersonnelMarketRandomGreenRemoval((Integer) spnPersonnelMarketRandomGreenRemoval.getValue());
        options.setPersonnelMarketRandomUltraGreenRemoval((Integer) spnPersonnelMarketRandomUltraGreenRemoval.getValue());
        options.setPersonnelMarketDylansWeight((Double) spnPersonnelMarketDylansWeight.getValue());

        // Unit Market
        options.setUnitMarketMethod((UnitMarketMethod) comboUnitMarketMethod.getSelectedItem());
        options.setUnitMarketRegionalMechVariations(chkUnitMarketRegionalMechVariations.isSelected());
        options.setInstantUnitMarketDelivery(chkInstantUnitMarketDelivery.isSelected());
        options.setUnitMarketReportRefresh(chkUnitMarketReportRefresh.isSelected());

        // Contract Market
        options.setContractMarketMethod((ContractMarketMethod) comboContractMarketMethod.getSelectedItem());
        options.setContractMarketReportRefresh(chkContractMarketReportRefresh.isSelected());
        //endregion Markets Tab

        // Start Against the Bot
        options.setUseAtB(chkUseAtB.isSelected());
        options.setUseStratCon(chkUseStratCon.isSelected());
        options.setSkillLevel(cbSkillLevel.getSelectedIndex());
        options.setUseShareSystem(chkUseShareSystem.isSelected());
        options.setSharesExcludeLargeCraft(chkSharesExcludeLargeCraft.isSelected());
        options.setSharesForAll(chkSharesForAll.isSelected());
        options.setTrackOriginalUnit(chkTrackOriginalUnit.isSelected());
        options.setRetirementRolls(chkRetirementRolls.isSelected());
        options.setCustomRetirementMods(chkCustomRetirementMods.isSelected());
        options.setFoundersNeverRetire(chkFoundersNeverRetire.isSelected());
        options.setAtBAddDependents(chkAddDependents.isSelected());
        options.setDependentsNeverLeave(chkDependentsNeverLeave.isSelected());
        options.setTrackUnitFatigue(chkTrackUnitFatigue.isSelected());
        options.setLimitLanceWeight(chkLimitLanceWeight.isSelected());
        options.setLimitLanceNumUnits(chkLimitLanceNumUnits.isSelected());
        options.setUseLeadership(chkUseLeadership.isSelected());
        options.setUseStrategy(chkUseStrategy.isSelected());
        options.setBaseStrategyDeployment((Integer) spnBaseStrategyDeployment.getValue());
        options.setAdditionalStrategyDeployment((Integer) spnAdditionalStrategyDeployment.getValue());
        options.setAdjustPaymentForStrategy(chkAdjustPaymentForStrategy.isSelected());

        options.setUseAero(chkUseAero.isSelected());
        options.setUseVehicles(chkUseVehicles.isSelected());
        options.setClanVehicles(chkClanVehicles.isSelected());
        options.setDoubleVehicles(chkDoubleVehicles.isSelected());
        options.setAdjustPlayerVehicles(chkAdjustPlayerVehicles.isSelected());
        options.setOpforLanceTypeMechs((Integer) spnOpforLanceTypeMechs.getValue());
        options.setOpforLanceTypeMixed((Integer) spnOpforLanceTypeMixed.getValue());
        options.setOpforLanceTypeVehicles((Integer) spnOpforLanceTypeVehicles.getValue());
        options.setOpforUsesVTOLs(chkOpforUsesVTOLs.isSelected());
        options.setAllowOpforAeros(chkOpforUsesAero.isSelected());
        options.setAllowOpforLocalUnits(chkOpforUsesLocalForces.isSelected());
        options.setOpforAeroChance((Integer) spnOpforAeroChance.getValue());
        options.setOpforLocalUnitChance((Integer) spnOpforLocalForceChance.getValue());
        options.setUseDropShips(chkUseDropShips.isSelected());

        options.setStaticRATs(btnStaticRATs.isSelected());
        options.setIgnoreRatEra(chkIgnoreRatEra.isSelected());
        //Strip dates used in display name
        String[] ratList = new String[chosenRatModel.size()];
        for (int i = 0; i < chosenRatModel.size(); i++) {
            ratList[i] = chosenRatModel.elementAt(i).replaceFirst(" \\(.*?\\)", "");
        }
        options.setRATs(ratList);
        options.setSearchRadius((Integer) spnSearchRadius.getValue());
        for (int i = 0; i < spnAtBBattleChance.length; i++) {
            options.setAtBBattleChance(i, (Integer) spnAtBBattleChance[i].getValue());
        }
        options.setGenerateChases(chkGenerateChases.isSelected());
        options.setVariableContractLength(chkVariableContractLength.isSelected());
        options.setMercSizeLimited(chkMercSizeLimited.isSelected());
        options.setRestrictPartsByMission(chkRestrictPartsByMission.isSelected());
        options.setRegionalMechVariations(chkRegionalMechVariations.isSelected());
        options.setAttachedPlayerCamouflage(chkAttachedPlayerCamouflage.isSelected());
        options.setPlayerControlsAttachedUnits(chkPlayerControlsAttachedUnits.isSelected());
        options.setUseWeatherConditions(chkUseWeatherConditions.isSelected());
        options.setUseLightConditions(chkUseLightConditions.isSelected());
        options.setUsePlanetaryConditions(chkUsePlanetaryConditions.isSelected());
        options.setAeroRecruitsHaveUnits(chkAeroRecruitsHaveUnits.isSelected());
        // End Against the Bot

        campaign.setCampaignOptions(options);

        MekHQ.triggerEvent(new OptionsChangedEvent(campaign, options));
    }

    private void btnOkayActionPerformed() {
        if (txtName.getText().length() > 0) {
            updateOptions();
            this.setVisible(false);
        }
    }

    private void updateXPCosts() {
        for (int i = 0; i < SkillType.skillList.length; i++) {
            for (int j = 0; j < 11; j++) {
                try {
                    int cost = Integer.parseInt((String) tableXP.getValueAt(i, j));
                    SkillType.setCost(SkillType.skillList[i], cost, j);
                } catch (NumberFormatException e) {
                    MekHQ.getLogger().error("unreadable value in skill cost table for " + SkillType.skillList[i]);
                }
            }
        }
    }

    private void updateSkillTypes() {
        for (String skillName : SkillType.getSkillList()) {
            SkillType type = SkillType.getType(skillName);
            if (null != hashSkillTargets.get(skillName)) {
                type.setTarget((Integer) hashSkillTargets.get(skillName).getValue());
            }
            if (null != hashGreenSkill.get(skillName)) {
                type.setGreenLevel((Integer) hashGreenSkill.get(skillName).getValue());
            }
            if (null != hashRegSkill.get(skillName)) {
                type.setRegularLevel((Integer) hashRegSkill.get(skillName).getValue());
            }
            if (null != hashVetSkill.get(skillName)) {
                type.setVeteranLevel((Integer) hashVetSkill.get(skillName).getValue());
            }
            if (null != hashEliteSkill.get(skillName)) {
                type.setEliteLevel((Integer) hashEliteSkill.get(skillName).getValue());
            }
        }
    }


    private void btnCancelActionPerformed(ActionEvent evt) {
        cancelled = true;
        this.setVisible(false);
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    private void btnDateActionPerformed(ActionEvent evt) {
        // show the date chooser
        DateChooser dc = new DateChooser(frame, date);
        // user can either choose a date or cancel by closing
        if (dc.showDateChooser() == DateChooser.OK_OPTION) {
            date = dc.getDate();
            btnDate.setText(MekHQ.getMekHQOptions().getDisplayFormattedDate(date));
            factionModel = new SortedComboBoxModel<>();
            for (String sname : Factions.getInstance().getChoosableFactionCodes()) {
                Faction f = Factions.getInstance().getFaction(sname);
                if (f.validIn(date.getYear())) {
                    factionModel.addElement(f.getFullName(date.getYear()));
                }
            }
            factionModel.setSelectedItem(campaign.getFaction().getFullName(date.getYear()));
            comboFaction.setModel(factionModel);
        }
    }

    private void btnIconActionPerformed(ActionEvent evt) {
        ImageChoiceDialog pcd = new ImageChoiceDialog(frame, true, iconCategory, iconFileName,
                MHQStaticDirectoryManager.getForceIcons());
        pcd.setVisible(true);
        if (pcd.isChanged()) {
            iconCategory = pcd.getCategory();
            iconFileName = pcd.getFileName();
        }
        setForceIcon();
    }

    private void btnCamoActionPerformed(ActionEvent evt) {
        CamoChooserDialog ccd = new CamoChooserDialog(frame, camouflage);
        if ((ccd.showDialog() == JOptionPane.CANCEL_OPTION) || (ccd.getSelectedItem() == null)) {
            return;
        }
        camouflage = ccd.getSelectedItem();
        btnCamo.setIcon(camouflage.getImageIcon());
    }

    private Vector<String> getUnusedSPA() {
        Vector<String> unused = new Vector<>();
        PilotOptions poptions = new PilotOptions();
        for (Enumeration<IOptionGroup> i = poptions.getGroups(); i.hasMoreElements();) {
            IOptionGroup group = i.nextElement();

            if (!group.getKey().equalsIgnoreCase(PilotOptions.LVL3_ADVANTAGES)) {
                continue;
            }

            for (Enumeration<IOption> j = group.getOptions(); j.hasMoreElements();) {
                IOption option = j.nextElement();
                if (getCurrentSPA().get(option.getName()) == null) {
                    unused.add(option.getName());
                }
            }
        }

        for (String key : SpecialAbility.getAllDefaultSpecialAbilities().keySet()) {
            if ((getCurrentSPA().get(key) == null) && !unused.contains(key)) {
                unused.add(key);
            }
        }

        return unused;
    }

    public Hashtable<String, SpecialAbility> getCurrentSPA() {
        return tempSPA;
    }

    private void btnAddSPA() {
        SelectUnusedAbilityDialog suad = new SelectUnusedAbilityDialog(this.frame, getUnusedSPA(), getCurrentSPA());
        suad.setVisible(true);

        recreateSPAPanel(!getUnusedSPA().isEmpty());
    }

    public void btnRemoveSPA(String name) {
        getCurrentSPA().remove(name);

        //we also need to cycle through the existing SPAs and remove this one from
        //any prereqs
        for (String key: getCurrentSPA().keySet()) {
            SpecialAbility otherAbil = getCurrentSPA().get(key);
            Vector<String> prereq = otherAbil.getPrereqAbilities();
            Vector<String> invalid = otherAbil.getInvalidAbilities();
            Vector<String> remove = otherAbil.getRemovedAbilities();
            if (prereq.remove(name)) {
                otherAbil.setPrereqAbilities(prereq);
            }
            if (invalid.remove(name)) {
                otherAbil.setInvalidAbilities(invalid);
            }
            if (remove.remove(name)) {
                otherAbil.setRemovedAbilities(remove);
            }
        }

        recreateSPAPanel(true);
    }

    public void recreateSPAPanel(boolean enableAddSPA) {
        panSpecialAbilities.removeAll();

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.0;
        panSpecialAbilities.add(btnAddSPA, gridBagConstraints);
        btnAddSPA.setEnabled(enableAddSPA);

        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weighty = 1.0;

        NaturalOrderComparator naturalOrderComparator = new NaturalOrderComparator();
        getCurrentSPA().values().stream().sorted((o1, o2) ->
                naturalOrderComparator.compare(o1.getDisplayName(), o2.getDisplayName())
        ).forEach(spa -> {
            panSpecialAbilities.add(new SpecialAbilityPanel(spa, this), gridBagConstraints);
            gridBagConstraints.gridy++;
        });
        panSpecialAbilities.revalidate();
        panSpecialAbilities.repaint();
    }

    public void setForceIcon() {
        if (null == iconCategory) {
            return;
        }

        if (AbstractIcon.DEFAULT_ICON_FILENAME.equals(iconFileName)) {
            btnIcon.setIcon(null);
            btnIcon.setText("None");
            return;
        }

        // Try to get the root file.
        try {
            // Translate the root icon directory name.
            if (AbstractIcon.ROOT_CATEGORY.equals(iconCategory)) {
                iconCategory = "";
            }
            Image icon = (Image) MHQStaticDirectoryManager.getForceIcons().getItem(iconCategory, iconFileName);
            icon = icon.getScaledInstance(75, -1, Image.SCALE_DEFAULT);
            btnIcon.setIcon(new ImageIcon(icon));
        } catch (Exception err) {
            iconFileName = AbstractIcon.DEFAULT_ICON_FILENAME;
            setForceIcon();
        }
    }

    private void enableAtBComponents(JPanel panel, boolean enabled) {
        for (Component c : panel.getComponents()) {
            if (c.equals(chkUseAtB)) {
                continue;
            }

            if (c instanceof JPanel) {
                enableAtBComponents((JPanel) c, enabled);
            } else if (enabled && c.equals(btnAddRat)) {
                c.setEnabled(availableRats.getSelectedIndex() >= 0);
            } else if (enabled && c.equals(btnRemoveRat)) {
                c.setEnabled(chosenRats.getSelectedIndex() >= 0);
            } else if (enabled && c.equals(btnMoveRatUp)) {
                c.setEnabled(chosenRats.getSelectedIndex() > 0);
            } else if (enabled && c.equals(btnMoveRatDown)) {
                c.setEnabled((availableRats.getSelectedIndex() >= 0)
                        && (chosenRatModel.size() > chosenRats.getSelectedIndex() + 1));
            } else {
                c.setEnabled(enabled);
            }
        }
    }

    private double determineAtBBattleIntensity() {
        double intensity = 0.0;
        int x;

        x = (Integer) spnAtBBattleChance[AtBLanceRole.FIGHTING.ordinal()].getValue();
        intensity += ((-3.0 / 2.0) * (2.0 * x - 1.0)) / (2.0 * x - 201.0);

        x = (Integer) spnAtBBattleChance[AtBLanceRole.DEFENCE.ordinal()].getValue();
        intensity += ((-4.0) * (2.0 * x - 1.0)) / (2.0 * x - 201.0);

        x = (Integer) spnAtBBattleChance[AtBLanceRole.SCOUTING.ordinal()].getValue();
        intensity += ((-2.0 / 3.0) * (2.0 * x - 1.0)) / (2.0 * x - 201.0);

        x = (Integer) spnAtBBattleChance[AtBLanceRole.TRAINING.ordinal()].getValue();
        intensity += ((-9.0) * (2.0 * x - 1.0)) / (2.0 * x - 201.0);

        intensity = intensity / 4.0;

        if (intensity > 100.0) {
            intensity = 100.0;
        }

        return Math.round(intensity * 10.0) / 10.0;
    }

    private class AtBBattleIntensityChangeListener implements ChangeListener  {
        @Override
        public void stateChanged(ChangeEvent e) {
            double intensity = (Double) spnAtBBattleIntensity.getValue();

            if (intensity >= AtBContract.MINIMUM_INTENSITY) {
                int value = (int) Math.min(Math.round(400.0 * intensity / (4.0 * intensity + 6.0) + 0.05), 100);
                spnAtBBattleChance[AtBLanceRole.FIGHTING.ordinal()].setValue(value);
                value = (int) Math.min(Math.round(200.0 * intensity / (2.0 * intensity + 8.0) + 0.05), 100);
                spnAtBBattleChance[AtBLanceRole.DEFENCE.ordinal()].setValue(value);
                value = (int) Math.min(Math.round(600.0 * intensity / (6.0 * intensity + 4.0) + 0.05), 100);
                spnAtBBattleChance[AtBLanceRole.SCOUTING.ordinal()].setValue(value);
                value = (int) Math.min(Math.round(100.0 * intensity / (intensity + 9.0) + 0.05), 100);
                spnAtBBattleChance[AtBLanceRole.TRAINING.ordinal()].setValue(value);
            } else {
                spnAtBBattleChance[AtBLanceRole.FIGHTING.ordinal()].setValue(0);
                spnAtBBattleChance[AtBLanceRole.DEFENCE.ordinal()].setValue(0);
                spnAtBBattleChance[AtBLanceRole.SCOUTING.ordinal()].setValue(0);
                spnAtBBattleChance[AtBLanceRole.TRAINING.ordinal()].setValue(0);
            }
        }
    }

    /*
     * Taken from:
     *  http://tips4java.wordpress.com/2008/11/18/row-number-table/
     *	Use a JTable as a renderer for row numbers of a given main table.
     *  This table must be added to the row header of the scrollpane that
     *  contains the main table.
     */
    public static class RowNamesTable extends JTable implements ChangeListener, PropertyChangeListener {
        private static final long serialVersionUID = 3151119498072423302L;
        private JTable main;

        public RowNamesTable(JTable table) {
            main = table;
            main.addPropertyChangeListener(this);

            setFocusable(false);
            setAutoCreateColumnsFromModel(false);
            setModel(main.getModel());
            setSelectionModel(main.getSelectionModel());

            TableColumn column = new TableColumn();
            column.setHeaderValue(" ");
            addColumn(column);
            column.setCellRenderer(new RowNumberRenderer());

            getColumnModel().getColumn(0).setPreferredWidth(120);
            setPreferredScrollableViewportSize(getPreferredSize());
        }

        @Override
        public void addNotify() {
            super.addNotify();
            Component c = getParent();
            //  Keep scrolling of the row table in sync with the main table.
            if (c instanceof JViewport) {
                JViewport viewport = (JViewport) c;
                viewport.addChangeListener(this);
            }
        }

        /*
         *  Delegate method to main table
         */
        @Override
        public int getRowCount() {
            return main.getRowCount();
        }

        @Override
        public int getRowHeight(int row) {
            return main.getRowHeight(row);
        }

        /*
         *  This table does not use any data from the main TableModel,
         *  so just return a value based on the row parameter.
         */
        @Override
        public Object getValueAt(int row, int column) {
            return SkillType.skillList[row];
        }

        /*
         *  Don't edit data in the main TableModel by mistake
         */
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        //
        //  Implement the ChangeListener
        //
        @Override
        public void stateChanged(ChangeEvent e) {
            //  Keep the scrolling of the row table in sync with main table
            JViewport viewport = (JViewport) e.getSource();
            JScrollPane scrollPane = (JScrollPane) viewport.getParent();
            scrollPane.getVerticalScrollBar().setValue(viewport.getViewPosition().y);
        }

        //
        //  Implement the PropertyChangeListener
        //
        @Override
        public void propertyChange(PropertyChangeEvent e) {
            //  Keep the row table in sync with the main table

            if ("selectionModel".equals(e.getPropertyName())) {
                setSelectionModel(main.getSelectionModel());
            }

            if ("model".equals(e.getPropertyName())) {
                setModel(main.getModel());
            }
        }

        /*
         *  Borrow the renderer from JDK1.4.2 table header
         */
        private static class RowNumberRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = -5430873664301394767L;

            public RowNumberRenderer() {
                setHorizontalAlignment(JLabel.LEFT);
            }

            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                if (table != null) {
                    JTableHeader header = table.getTableHeader();

                    if (header != null) {
                        setForeground(header.getForeground());
                        setBackground(header.getBackground());
                        setFont(header.getFont());
                    }
                }

                if (isSelected) {
                    setFont(getFont().deriveFont(Font.BOLD));
                }

                setText((value == null) ? "" : value.toString());
                setBorder(UIManager.getBorder("TableHeader.cellBorder"));

                return this;
            }
        }
    }
}
