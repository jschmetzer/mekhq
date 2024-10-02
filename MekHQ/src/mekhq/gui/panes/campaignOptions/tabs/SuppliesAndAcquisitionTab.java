package mekhq.gui.panes.campaignOptions.tabs;

import megamek.client.ui.baseComponents.MMComboBox;
import megamek.common.EquipmentType;
import mekhq.MekHQ;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.enums.PlanetaryAcquisitionFactionLimit;
import mekhq.campaign.personnel.SkillType;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import java.awt.*;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

import static mekhq.gui.panes.campaignOptions.tabs.CampaignOptionsUtilities.*;

public class SuppliesAndAcquisitionTab {
    // region Variable Declarations
    private static String RESOURCE_PACKAGE = "mekhq/resources/NEWCampaignOptionsDialog";
    private static final ResourceBundle resources = ResourceBundle.getBundle(RESOURCE_PACKAGE,
        MekHQ.getMHQOptions().getLocale());

    JFrame frame;
    String name;

    //start Acquisition Tab
    private JLabel lblChoiceAcquireSkill;
    private MMComboBox<String> choiceAcquireSkill;
    private JCheckBox chkSupportStaffOnly;
    private JLabel lblAcquireClanPenalty;
    private JSpinner spnAcquireClanPenalty;
    private JLabel lblAcquireIsPenalty;
    private JSpinner spnAcquireIsPenalty;
    private JLabel lblAcquireWaitingPeriod;
    private JSpinner spnAcquireWaitingPeriod;
    private JLabel lblMaxAcquisitions;
    private JSpinner spnMaxAcquisitions;
    //end Acquisition Tab

    //start Delivery Tab
    private JLabel lblNDiceTransitTime;
    private JSpinner spnNDiceTransitTime;
    private JLabel lblConstantTransitTime;
    private JSpinner spnConstantTransitTime;
    private JLabel lblAcquireMosBonus;
    private JSpinner spnAcquireMosBonus;
    private JLabel lblAcquireMinimum;
    private JSpinner spnAcquireMinimum;
    private MMComboBox<String> choiceTransitTimeUnits;
    private MMComboBox<String> choiceAcquireMosUnits;
    private MMComboBox<String> choiceAcquireMinimumUnit;
    private static final int TRANSIT_UNIT_DAY = 0;
    private static final int TRANSIT_UNIT_WEEK = 1;
    private static final int TRANSIT_UNIT_MONTH = 2;
    private static final int TRANSIT_UNIT_NUM = 3;
    //end Delivery Tab

    //start Planetary Acquisition Tab
    private JCheckBox usePlanetaryAcquisitions;
    private JLabel lblMaxJumpPlanetaryAcquisitions;
    private JSpinner spnMaxJumpPlanetaryAcquisitions;
    private JLabel lblPlanetaryAcquisitionsFactionLimits;
    private MMComboBox<PlanetaryAcquisitionFactionLimit> comboPlanetaryAcquisitionsFactionLimits;
    private JCheckBox disallowClanPartsFromIS;
    private JCheckBox disallowPlanetaryAcquisitionClanCrossover;
    private JLabel lblPenaltyClanPartsFromIS;
    private JSpinner spnPenaltyClanPartsFromIS;
    private JCheckBox usePlanetaryAcquisitionsVerbose;
    private JLabel[] lblPlanetAcquireTechBonus;
    private JSpinner[] spnPlanetAcquireTechBonus;
    private JLabel[] lblPlanetAcquireIndustryBonus;
    private JSpinner[] spnPlanetAcquireIndustryBonus;
    private JLabel[] lblPlanetAcquireOutputBonus;
    private JSpinner[] spnPlanetAcquireOutputBonus;
    //end Planetary Acquisition Tab

    public SuppliesAndAcquisitionTab(JFrame frame, String name) {
        this.frame = frame;
        this.name = name;

        initialize();
    }

    /**
     * Creates the acquisition tab panel.
     *
     * @return the created tab panel as a {@link JPanel}
     */
    public JPanel createAcquisitionTab() {
        // Header
        JPanel headerPanel = createHeaderPanel("AcquisitionTab",
            getImageDirectory() + "logo_calderon_protectorate.png", false,
            "", true);

        // Acquisitions skill
        lblChoiceAcquireSkill = createLabel("ChoiceAcquireSkill", null);
        int choiceAcquireSkillWidth = getDimensionWidthForComboBox(choiceAcquireSkill);
        choiceAcquireSkill.setMinimumSize(new Dimension(choiceAcquireSkillWidth, 30));
        choiceAcquireSkill.setMaximumSize(new Dimension(choiceAcquireSkillWidth , 30));

        // Support personnel only
        chkSupportStaffOnly = createCheckBox("SupportStaffOnly", null);

        // Clan Acquisition penalty
        Map<JLabel, JSpinner> acquireClanPenaltyFields = createLabeledSpinner("AcquireClanPenalty",
            null, 0, 0, 13, 1);
        for (Entry<JLabel, JSpinner> entry : acquireClanPenaltyFields.entrySet()) {
            lblAcquireClanPenalty = entry.getKey();
            spnAcquireClanPenalty = entry.getValue();
        }

        // IS Acquisition penalty
        Map<JLabel, JSpinner> acquireIsPenaltyFields = createLabeledSpinner("AcquireISPenalty",
            null, 0, 0, 13, 1);
        for (Entry<JLabel, JSpinner> entry : acquireIsPenaltyFields.entrySet()) {
            lblAcquireIsPenalty = entry.getKey();
            spnAcquireIsPenalty = entry.getValue();
        }

        // Waiting Period
        Map<JLabel, JSpinner> acquireWaitingPeriodFields = createLabeledSpinner("AcquireWaitingPeriod",
            null, 1, 1, 365, 1);
        for (Entry<JLabel, JSpinner> entry : acquireWaitingPeriodFields.entrySet()) {
            lblAcquireWaitingPeriod = entry.getKey();
            spnAcquireWaitingPeriod = entry.getValue();
        }

        // Maximum Acquisitions
        Map<JLabel, JSpinner> maxAcquisitionsFields = createLabeledSpinner("MaxAcquisitions",
            null, 0,0, 100, 1);
        for (Entry<JLabel, JSpinner> entry : maxAcquisitionsFields.entrySet()) {
            lblMaxAcquisitions = entry.getKey();
            spnMaxAcquisitions = entry.getValue();
        }

        // Layout the Panel
        final JPanel panel = createStandardPanel("acquisitionTab", true, "");
        final GroupLayout layout = createStandardLayout(panel);
        panel.setLayout(layout);

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addComponent(headerPanel)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblChoiceAcquireSkill)
                    .addComponent(choiceAcquireSkill))
                .addComponent(chkSupportStaffOnly)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblAcquireClanPenalty)
                    .addComponent(spnAcquireClanPenalty))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblAcquireIsPenalty)
                    .addComponent(spnAcquireIsPenalty))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblAcquireWaitingPeriod)
                    .addComponent(spnAcquireWaitingPeriod))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblMaxAcquisitions)
                    .addComponent(spnMaxAcquisitions)));

        layout.setHorizontalGroup(
            layout.createParallelGroup(Alignment.LEADING)
                .addComponent(headerPanel)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblChoiceAcquireSkill)
                    .addComponent(choiceAcquireSkill)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addComponent(chkSupportStaffOnly)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblAcquireClanPenalty)
                    .addComponent(spnAcquireClanPenalty)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblAcquireIsPenalty)
                    .addComponent(spnAcquireIsPenalty)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblAcquireWaitingPeriod)
                    .addComponent(spnAcquireWaitingPeriod)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblMaxAcquisitions)
                    .addComponent(spnMaxAcquisitions)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE)));

        // Create Parent Panel and return
        return createParentPanel(panel, "acquisitionsTab", 500);
    }

    public JPanel createPlanetaryAcquisitionTab() {
        // Header
        JPanel headerPanel = createHeaderPanel("PlanetaryAcquisitionTab",
            getImageDirectory() + "logo_capellan_confederation.png", false,
            "", true);

        // Sub-Panels
        JPanel options = createOptionsPanel();
        JPanel modifiers = createModifiersPanel();

        // Layout the Panel
        final JPanel panel = createStandardPanel("PlanetaryAcquisitionTab", true, "");
        final GroupLayout layout = createStandardLayout(panel);
        panel.setLayout(layout);

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addComponent(headerPanel)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(options)
                    .addComponent(modifiers)));


        layout.setHorizontalGroup(
            layout.createParallelGroup(Alignment.LEADING)
                .addComponent(headerPanel)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(options)
                    .addComponent(modifiers)));

        // Create Parent Panel and return
        return createParentPanel(panel, "PlanetaryAcquisitionTab", 800);
    }


    /**
     * Creates the planetary acquisition options panel.
     *
     * @return the created tab panel as a {@link JPanel}
     */
    private JPanel createOptionsPanel() {
        // Use Planetary Acquisitions
        usePlanetaryAcquisitions = createCheckBox("UsePlanetaryAcquisitions", null);

        // Max Jump Distance
        Map<JLabel, JSpinner> maxJumpPlanetaryAcquisitionsFields = createLabeledSpinner(
            "MaxJumpPlanetaryAcquisitions", null, 2, 0,
            5, 1);
        for (Entry<JLabel, JSpinner> entry : maxJumpPlanetaryAcquisitionsFields.entrySet()) {
            lblMaxJumpPlanetaryAcquisitions = entry.getKey();
            spnMaxJumpPlanetaryAcquisitions = entry.getValue();
        }

        // Faction Limits
        lblPlanetaryAcquisitionsFactionLimits = createLabel("PlanetaryAcquisitionsFactionLimits", null);
        int comboPlanetaryAcquisitionsFactionLimitsWidth = getDimensionWidthForComboBox(comboPlanetaryAcquisitionsFactionLimits);
        comboPlanetaryAcquisitionsFactionLimits.setMinimumSize(new Dimension(comboPlanetaryAcquisitionsFactionLimitsWidth,
            30));
        comboPlanetaryAcquisitionsFactionLimits.setMaximumSize(new Dimension(comboPlanetaryAcquisitionsFactionLimitsWidth,
            30));

        // Disallow Resource Sharing (Inner Sphere)
        disallowPlanetaryAcquisitionClanCrossover = createCheckBox("DisallowPlanetaryAcquisitionClanCrossover", null);

        // Disallow Resource Sharing (Clans)
        disallowClanPartsFromIS = createCheckBox("DisallowClanPartsFromIS", null);

        // Acquisition Penalty
        Map<JLabel, JSpinner> penaltyClanPartsFromISFields = createLabeledSpinner(
            "PenaltyClanPartsFromIS", null, 0, 0, 12,
            1);
        for (Entry<JLabel, JSpinner> entry : penaltyClanPartsFromISFields.entrySet()) {
            lblPenaltyClanPartsFromIS = entry.getKey();
            spnPenaltyClanPartsFromIS = entry.getValue();
        }

        // Verbose Reporting
        usePlanetaryAcquisitionsVerbose = createCheckBox("UsePlanetaryAcquisitionsVerbose", null);

        // Layout the Panel
        final JPanel panel = new JPanel();
        final GroupLayout layout = createStandardLayout(panel);
        panel.setLayout(layout);

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addComponent(usePlanetaryAcquisitions)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(spnMaxJumpPlanetaryAcquisitions)
                    .addComponent(lblMaxJumpPlanetaryAcquisitions))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(comboPlanetaryAcquisitionsFactionLimits)
                    .addComponent(lblPlanetaryAcquisitionsFactionLimits))
                .addComponent(disallowPlanetaryAcquisitionClanCrossover)
                .addComponent(disallowClanPartsFromIS)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(spnPenaltyClanPartsFromIS)
                    .addComponent(lblPenaltyClanPartsFromIS))
                .addComponent(usePlanetaryAcquisitionsVerbose));


        layout.setHorizontalGroup(
            layout.createParallelGroup(Alignment.LEADING)
                .addComponent(usePlanetaryAcquisitions)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblMaxJumpPlanetaryAcquisitions)
                    .addComponent(spnMaxJumpPlanetaryAcquisitions)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(comboPlanetaryAcquisitionsFactionLimits)
                    .addComponent(lblPlanetaryAcquisitionsFactionLimits)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addComponent(disallowPlanetaryAcquisitionClanCrossover)
                .addComponent(disallowClanPartsFromIS)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblPenaltyClanPartsFromIS)
                    .addComponent(spnPenaltyClanPartsFromIS)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addComponent(usePlanetaryAcquisitionsVerbose));

        return panel;
    }

    /**
     * Creates the planetary acquisition modifiers panel.
     *
     * @return the created tab panel as a {@link JPanel}
     */
    private JPanel createModifiersPanel() {
        JLabel techLabel = createLabel("TechLabel", null);
        JLabel industryLabel = createLabel("IndustryLabel", null);
        JLabel outputLabel = createLabel("OutputLabel", null);

        // Modifier Spinners
        for (int i = EquipmentType.RATING_A; i <= EquipmentType.RATING_F; i++) {
            String modifierLabel = getModifierLabel(i);

            lblPlanetAcquireTechBonus[i] = new JLabel(String.format("<html><b>%s</b></html>",
                modifierLabel));
            spnPlanetAcquireTechBonus[i] = new JSpinner(new SpinnerNumberModel(
                0, -12, 12, 1));
            setSpinnerWidth(spnPlanetAcquireTechBonus[i]);

            lblPlanetAcquireIndustryBonus[i] = new JLabel(String.format("<html><b>%s</b></html>",
                modifierLabel));
            spnPlanetAcquireIndustryBonus[i] = new JSpinner(new SpinnerNumberModel(
                0, -12, 12, 1));
            setSpinnerWidth(spnPlanetAcquireIndustryBonus[i]);

            lblPlanetAcquireOutputBonus[i] = new JLabel(String.format("<html><b>%s</b></html>",
                modifierLabel));
            spnPlanetAcquireOutputBonus[i] = new JSpinner(new SpinnerNumberModel(
                0, -12, 12, 1));
            setSpinnerWidth(spnPlanetAcquireOutputBonus[i]);
        }

        // Layout the Panel
        final JPanel panel = createStandardPanel("PlanetaryAcquisitionTabModifiers",
            true, "ModifiersPanel");
        final GroupLayout layout = createStandardLayout(panel);
        panel.setLayout(layout);

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(techLabel)
                    .addComponent(industryLabel)
                    .addComponent(outputLabel))
                // There has to be a better way of doing this, but I couldn't fathom it
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblPlanetAcquireTechBonus[0])
                    .addComponent(spnPlanetAcquireTechBonus[0])
                    .addComponent(lblPlanetAcquireIndustryBonus[0])
                    .addComponent(spnPlanetAcquireIndustryBonus[0])
                    .addComponent(lblPlanetAcquireOutputBonus[0])
                    .addComponent(spnPlanetAcquireOutputBonus[0]))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblPlanetAcquireTechBonus[1])
                    .addComponent(spnPlanetAcquireTechBonus[1])
                    .addComponent(lblPlanetAcquireIndustryBonus[1])
                    .addComponent(spnPlanetAcquireIndustryBonus[1])
                    .addComponent(lblPlanetAcquireOutputBonus[1])
                    .addComponent(spnPlanetAcquireOutputBonus[1]))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblPlanetAcquireTechBonus[2])
                    .addComponent(spnPlanetAcquireTechBonus[2])
                    .addComponent(lblPlanetAcquireIndustryBonus[2])
                    .addComponent(spnPlanetAcquireIndustryBonus[2])
                    .addComponent(lblPlanetAcquireOutputBonus[2])
                    .addComponent(spnPlanetAcquireOutputBonus[2]))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblPlanetAcquireTechBonus[3])
                    .addComponent(spnPlanetAcquireTechBonus[3])
                    .addComponent(lblPlanetAcquireIndustryBonus[3])
                    .addComponent(spnPlanetAcquireIndustryBonus[3])
                    .addComponent(lblPlanetAcquireOutputBonus[3])
                    .addComponent(spnPlanetAcquireOutputBonus[3]))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblPlanetAcquireTechBonus[4])
                    .addComponent(spnPlanetAcquireTechBonus[4])
                    .addComponent(lblPlanetAcquireIndustryBonus[4])
                    .addComponent(spnPlanetAcquireIndustryBonus[4])
                    .addComponent(lblPlanetAcquireOutputBonus[4])
                    .addComponent(spnPlanetAcquireOutputBonus[4]))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblPlanetAcquireTechBonus[5])
                    .addComponent(spnPlanetAcquireTechBonus[5])
                    .addComponent(lblPlanetAcquireIndustryBonus[5])
                    .addComponent(spnPlanetAcquireIndustryBonus[5])
                    .addComponent(lblPlanetAcquireOutputBonus[5])
                    .addComponent(spnPlanetAcquireOutputBonus[5])));


        layout.setHorizontalGroup(
            layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addGap(35)
                    .addComponent(techLabel)
                    .addGap(40)
                    .addComponent(industryLabel)
                    .addGap(40)
                    .addComponent(outputLabel))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblPlanetAcquireTechBonus[0])
                    .addComponent(spnPlanetAcquireTechBonus[0])
                    .addComponent(lblPlanetAcquireIndustryBonus[0])
                    .addComponent(spnPlanetAcquireIndustryBonus[0])
                    .addComponent(lblPlanetAcquireOutputBonus[0])
                    .addComponent(spnPlanetAcquireOutputBonus[0]))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblPlanetAcquireTechBonus[1])
                    .addComponent(spnPlanetAcquireTechBonus[1])
                    .addComponent(lblPlanetAcquireIndustryBonus[1])
                    .addComponent(spnPlanetAcquireIndustryBonus[1])
                    .addComponent(lblPlanetAcquireOutputBonus[1])
                    .addComponent(spnPlanetAcquireOutputBonus[1]))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblPlanetAcquireTechBonus[2])
                    .addComponent(spnPlanetAcquireTechBonus[2])
                    .addComponent(lblPlanetAcquireIndustryBonus[2])
                    .addComponent(spnPlanetAcquireIndustryBonus[2])
                    .addComponent(lblPlanetAcquireOutputBonus[2])
                    .addComponent(spnPlanetAcquireOutputBonus[2]))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblPlanetAcquireTechBonus[3])
                    .addComponent(spnPlanetAcquireTechBonus[3])
                    .addComponent(lblPlanetAcquireIndustryBonus[3])
                    .addComponent(spnPlanetAcquireIndustryBonus[3])
                    .addComponent(lblPlanetAcquireOutputBonus[3])
                    .addComponent(spnPlanetAcquireOutputBonus[3]))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblPlanetAcquireTechBonus[4])
                    .addComponent(spnPlanetAcquireTechBonus[4])
                    .addComponent(lblPlanetAcquireIndustryBonus[4])
                    .addComponent(spnPlanetAcquireIndustryBonus[4])
                    .addComponent(lblPlanetAcquireOutputBonus[4])
                    .addComponent(spnPlanetAcquireOutputBonus[4]))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblPlanetAcquireTechBonus[5])
                    .addComponent(spnPlanetAcquireTechBonus[5])
                    .addComponent(lblPlanetAcquireIndustryBonus[5])
                    .addComponent(spnPlanetAcquireIndustryBonus[5])
                    .addComponent(lblPlanetAcquireOutputBonus[5])
                    .addComponent(spnPlanetAcquireOutputBonus[5])));

        return panel;
    }

    /**
     * Sets the width of a {@link JSpinner} component based on the width of the text that would be
     * displayed in it.
     *
     * @param spinner the {@link JSpinner} to set the width for
     */
    private void setSpinnerWidth(JSpinner spinner) {
        FontMetrics fontMetrics = spinner.getFontMetrics(spinner.getFont());
        int width = fontMetrics.stringWidth(Double.toString(12));
        width = width * WIDTH_MULTIPLIER_NUMBER;

        spinner.setMaximumSize(new Dimension(width, 30));
        spinner.setMinimumSize(new Dimension(width, 30));
    }

    /**
     * Retrieves the label for a given quality modifier.
     *
     * @param quality The quality modifier represented by an integer value.
     * @return The label corresponding to the quality modifier.
     */
    private String getModifierLabel(int quality) {
        return switch (quality) {
            case EquipmentType.RATING_A -> "A";
            case EquipmentType.RATING_B -> "B";
            case EquipmentType.RATING_C -> "C";
            case EquipmentType.RATING_D -> "D";
            case EquipmentType.RATING_E -> "E";
            case EquipmentType.RATING_F -> "F";
            default -> "ERROR";
        };
    }

    /**
     * Creates the delivery tab panel.
     *
     * @return the created tab panel as a {@link JPanel}
     */
    public JPanel createDeliveryTab() {
        // Header
        JPanel headerPanel = createHeaderPanel("DeliveryTab",
            getImageDirectory() + "logo_clan_burrock.png", false,
            "", true);

        // Delivery Time
        Map<JLabel, JSpinner> nDiceTransitTimeFields = createLabeledSpinner("NDiceTransitTime",
            null, 0, 0, 365, 1);
        for (Entry<JLabel, JSpinner> entry : nDiceTransitTimeFields.entrySet()) {
            lblNDiceTransitTime = entry.getKey();
            spnNDiceTransitTime = entry.getValue();
        }

        Map<JLabel, JSpinner> constantTransitTimeFields = createLabeledSpinner("ConstantTransitTime",
            null, 0, 0, 365, 1);
        for (Entry<JLabel, JSpinner> entry : constantTransitTimeFields.entrySet()) {
            lblConstantTransitTime = entry.getKey();
            spnConstantTransitTime = entry.getValue();
        }

        int choiceTimeUnitsWidth = getDimensionWidthForComboBox(choiceTransitTimeUnits);
        choiceTransitTimeUnits.setMinimumSize(new Dimension(choiceTimeUnitsWidth, 30));
        choiceTransitTimeUnits.setMaximumSize(new Dimension(choiceTimeUnitsWidth, 30));

        // Margin of Success Reductions
        Map<JLabel, JSpinner> acquireMosBonusFields = createLabeledSpinner("AcquireMosBonus",
            null, 0, 0, 365, 1);
        for (Entry<JLabel, JSpinner> entry : acquireMosBonusFields.entrySet()) {
            lblAcquireMosBonus = entry.getKey();
            spnAcquireMosBonus = entry.getValue();
        }

        choiceAcquireMosUnits.setMinimumSize(new Dimension(choiceTimeUnitsWidth, 30));
        choiceAcquireMosUnits.setMaximumSize(new Dimension(choiceTimeUnitsWidth, 30));

        // Minimum Transit Time
        Map<JLabel, JSpinner> acquireMinimumFields = createLabeledSpinner("AcquireMinimum",
            null, 0, 0, 365, 1);
        for (Entry<JLabel, JSpinner> entry : acquireMinimumFields.entrySet()) {
            lblAcquireMinimum = entry.getKey();
            spnAcquireMinimum = entry.getValue();
        }

        choiceAcquireMinimumUnit.setMinimumSize(new Dimension(choiceTimeUnitsWidth, 30));
        choiceAcquireMinimumUnit.setMaximumSize(new Dimension(choiceTimeUnitsWidth, 30));

        // Layout the Panel
        final JPanel panel = createStandardPanel("deliveryTab", true, "");
        final GroupLayout layout = createStandardLayout(panel);
        panel.setLayout(layout);

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addComponent(headerPanel)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblNDiceTransitTime)
                    .addComponent(spnNDiceTransitTime)
                    .addComponent(lblConstantTransitTime)
                    .addComponent(spnConstantTransitTime)
                    .addComponent(choiceTransitTimeUnits))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblAcquireMosBonus)
                    .addComponent(spnAcquireMosBonus)
                    .addComponent(choiceAcquireMosUnits))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(lblAcquireMinimum)
                    .addComponent(spnAcquireMinimum)
                    .addComponent(choiceAcquireMinimumUnit)));

        layout.setHorizontalGroup(
            layout.createParallelGroup(Alignment.LEADING)
                .addComponent(headerPanel)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblNDiceTransitTime)
                    .addComponent(spnNDiceTransitTime)
                    .addComponent(lblConstantTransitTime)
                    .addComponent(spnConstantTransitTime)
                    .addComponent(choiceTransitTimeUnits)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblAcquireMosBonus)
                    .addComponent(spnAcquireMosBonus)
                    .addComponent(choiceAcquireMosUnits)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE))
                .addGroup(layout.createSequentialGroup()
                    .addComponent(lblAcquireMinimum)
                    .addComponent(spnAcquireMinimum)
                    .addComponent(choiceAcquireMinimumUnit)
                    .addContainerGap(Short.MAX_VALUE, Short.MAX_VALUE)));

        // Create Parent Panel and return
        return createParentPanel(panel, "deliveryTab", 600);
    }

    /**
     * Retrieves the transit unit options as a {@link DefaultComboBoxModel}.
     *
     * @return The {@link DefaultComboBoxModel} containing the transit unit options with labels fetched
     * from {@code getTransitUnitName()}.
     */
    private static DefaultComboBoxModel<String> getTransitUnitOptions() {
        DefaultComboBoxModel<String> transitUnitModel = new DefaultComboBoxModel<>();

        for (int i = 0; i < TRANSIT_UNIT_NUM; i++) {
            transitUnitModel.addElement(getTransitUnitName(i));
        }
        return transitUnitModel;
    }

    /**
     * Returns the name of the transit unit based on the given unit value.
     *
     * @param unit the unit value representing the transit unit
     * @return the name of the transit unit as a {@link String}
     */
    private static String getTransitUnitName(final int unit) {
        return switch (unit) {
            case TRANSIT_UNIT_DAY -> resources.getString("transitUnitNamesDays.text");
            case TRANSIT_UNIT_WEEK -> resources.getString("transitUnitNamesWeeks.text");
            case TRANSIT_UNIT_MONTH -> resources.getString("transitUnitNamesMonths.text");
            default -> "ERROR";
        };
    }

    /**
     * Builds the options for the acquisition skill combo box.
     *
     * @return the default combo box model containing the acquisition skill options
     */
    private static DefaultComboBoxModel<String> buildAcquireSkillComboOptions() {
        DefaultComboBoxModel<String> acquireSkillModel = new DefaultComboBoxModel<>();

        acquireSkillModel.addElement(CampaignOptions.S_TECH);
        acquireSkillModel.addElement(SkillType.S_ADMIN);
        acquireSkillModel.addElement(SkillType.S_SCROUNGE);
        acquireSkillModel.addElement(SkillType.S_NEG);
        acquireSkillModel.addElement(CampaignOptions.S_AUTO);

        return acquireSkillModel;
    }

    protected void initialize() {
        // Acquisition Tab
        lblChoiceAcquireSkill = new JLabel();
        choiceAcquireSkill = new MMComboBox<>("choiceAcquireSkill", buildAcquireSkillComboOptions());

        chkSupportStaffOnly = new JCheckBox();

        lblAcquireClanPenalty = new JLabel();
        spnAcquireClanPenalty = new JSpinner();

        lblAcquireIsPenalty = new JLabel();
        spnAcquireIsPenalty = new JSpinner();

        lblAcquireWaitingPeriod = new JLabel();
        spnAcquireWaitingPeriod = new JSpinner();

        lblMaxAcquisitions = new JLabel();
        spnMaxAcquisitions = new JSpinner();

        // Delivery Tab
        lblNDiceTransitTime = new JLabel();
        spnNDiceTransitTime = new JSpinner();
        lblConstantTransitTime = new JLabel();
        spnConstantTransitTime = new JSpinner();
        choiceTransitTimeUnits = new MMComboBox<>("choiceTransitTimeUnits", getTransitUnitOptions());

        lblAcquireMosBonus = new JLabel();
        spnAcquireMosBonus = new JSpinner();
        choiceAcquireMosUnits = new MMComboBox<>("choiceAcquireMosUnits", getTransitUnitOptions());

        lblAcquireMinimum = new JLabel();
        spnAcquireMinimum = new JSpinner();
        choiceAcquireMinimumUnit = new MMComboBox<>("choiceAcquireMinimumUnit", getTransitUnitOptions());

        // Planetary Acquisition Tab
        // Options
        usePlanetaryAcquisitions = new JCheckBox();

        lblMaxJumpPlanetaryAcquisitions = new JLabel();
        spnMaxJumpPlanetaryAcquisitions = new JSpinner();

        lblPlanetaryAcquisitionsFactionLimits = new JLabel();
        comboPlanetaryAcquisitionsFactionLimits = new MMComboBox<>("comboPlanetaryAcquisitionsFactionLimits",
            PlanetaryAcquisitionFactionLimit.values());

        disallowPlanetaryAcquisitionClanCrossover = new JCheckBox();

        disallowClanPartsFromIS = new JCheckBox();

        lblPenaltyClanPartsFromIS = new JLabel();
        spnPenaltyClanPartsFromIS = new JSpinner();

        usePlanetaryAcquisitionsVerbose = new JCheckBox();

        // Modifiers
        lblPlanetAcquireTechBonus = new JLabel[6];
        spnPlanetAcquireTechBonus = new JSpinner[6];

        lblPlanetAcquireIndustryBonus = new JLabel[6];
        spnPlanetAcquireIndustryBonus = new JSpinner[6];

        lblPlanetAcquireOutputBonus = new JLabel[6];
        spnPlanetAcquireOutputBonus = new JSpinner[6];
    }
}
