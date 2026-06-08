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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import megamek.common.annotations.Nullable;
import megamek.common.enums.SkillLevel;
import megamek.common.loaders.MekSummaryCache;
import megamek.logging.MMLogger;

/**
 * A formation (lance, star, Level II) in a static OpFor roster.
 *
 * <p>Each formation groups several {@link StratConOpForUnit} records under a
 * human-readable name and is assigned to a specific StratCon track for the
 * duration of the contract.</p>
 */
@XmlRootElement(name = "opForFormation")
public class StratConOpForFormation {

    private static final MMLogger LOGGER = MMLogger.create(StratConOpForFormation.class);

    @XmlElement
    private UUID id = UUID.randomUUID();

    @XmlElement
    private String name;

    /**
     * {@code EntityWeightClass} constant (e.g.,
     * {@code megamek.common.units.EntityWeightClass.WEIGHT_MEDIUM}).
     */
    @XmlElement
    private int weightClass;

    @XmlElement
    private int unitQuality;

    @XmlElement
    private SkillLevel skillLevel;

    /** Nullable: formation not yet assigned to a track. */
    @XmlElement
    private String assignedTrackName;

    @XmlElementWrapper(name = "unitIds")
    @XmlElement(name = "unitId")
    private List<UUID> unitIds = new ArrayList<>();

    @XmlElement
    private IntelLevel intelLevel = IntelLevel.UNKNOWN;

    /**
     * Tracks which scenario this formation was last deployed into.
     * Used by the deployer to prefer fresher formations over recently-used ones.
     */
    @XmlElement
    private UUID lastDeployedScenarioId;

    /** No-arg constructor required by JAXB. */
    public StratConOpForFormation() {
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Returns the living (non-terminal) units in this formation from the given
     * roster.
     *
     * @param roster the roster that owns the unit records
     * @return list of living units; never null
     */
    public List<StratConOpForUnit> livingUnits(final StratConOpForRoster roster) {
        List<StratConOpForUnit> result = new ArrayList<>();
        for (UUID unitId : unitIds) {
            StratConOpForUnit unit = roster.getUnit(unitId);
            if (unit != null && !unit.getStatus().isTerminal()) {
                result.add(unit);
            }
        }
        return result;
    }

    /**
     * Returns {@code true} if every unit in this formation has a terminal status.
     *
     * @param roster the roster that owns the unit records
     * @return {@code true} if the formation is completely eliminated
     */
    public boolean isDestroyed(final StratConOpForRoster roster) {
        return livingUnits(roster).isEmpty();
    }

    /**
     * Approximates the total Battle Value of living units in this formation.
     *
     * <p>Uses {@code MekSummaryCache} for BV lookup.  Cache misses are logged at
     * WARN level and the partial sum is returned.</p>
     *
     * @param roster the roster that owns the unit records
     * @return approximate BV (≥ 0)
     */
    public double currentBV(final StratConOpForRoster roster) {
        double total = 0;
        for (StratConOpForUnit unit : livingUnits(roster)) {
            UnitTemplate template = unit.getProtoEntity();
            if (template == null) {
                continue;
            }
            String lookupKey = template.getChassis() + " " + template.getModel();
            var summary = MekSummaryCache.getInstance().getMek(lookupKey);
            if (summary == null) {
                LOGGER.warn("MekSummaryCache miss for '{}'; BV estimate incomplete.", lookupKey);
                continue;
            }
            total += summary.getBV();
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public int getWeightClass() {
        return weightClass;
    }

    public void setWeightClass(final int weightClass) {
        this.weightClass = weightClass;
    }

    public int getUnitQuality() {
        return unitQuality;
    }

    public void setUnitQuality(final int unitQuality) {
        this.unitQuality = unitQuality;
    }

    public SkillLevel getSkillLevel() {
        return skillLevel;
    }

    public void setSkillLevel(final SkillLevel skillLevel) {
        this.skillLevel = skillLevel;
    }

    public @Nullable String getAssignedTrackName() {
        return assignedTrackName;
    }

    public void setAssignedTrackName(@Nullable final String assignedTrackName) {
        this.assignedTrackName = assignedTrackName;
    }

    public List<UUID> getUnitIds() {
        return unitIds;
    }

    public void setUnitIds(final List<UUID> unitIds) {
        this.unitIds = unitIds;
    }

    public IntelLevel getIntelLevel() {
        return intelLevel;
    }

    public void setIntelLevel(final IntelLevel intelLevel) {
        this.intelLevel = intelLevel;
    }

    public @Nullable UUID getLastDeployedScenarioId() {
        return lastDeployedScenarioId;
    }

    public void setLastDeployedScenarioId(@Nullable final UUID lastDeployedScenarioId) {
        this.lastDeployedScenarioId = lastDeployedScenarioId;
    }
}
