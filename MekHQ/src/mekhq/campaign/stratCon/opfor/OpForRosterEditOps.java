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
import java.util.UUID;

import megamek.common.annotations.Nullable;
import megamek.common.enums.SkillLevel;

/**
 * GM-mode editing operations for a {@link StratConOpForRoster}.
 *
 * <p>This helper holds the cross-object orchestration (cascading deletes,
 * formation reassignment) and field validation that back the GM roster editor
 * UI, keeping that logic out of the Swing layer so it can be unit-tested. The
 * roster's own {@code addUnit}/{@code removeUnit}/{@code addFormation}/
 * {@code removeFormation} methods are the low-level primitives that maintain the
 * unit-id index; this class composes them while also maintaining the
 * formation&rarr;unit membership links.</p>
 */
public final class OpForRosterEditOps {

    /** Inclusive minimum for a pilot's gunnery / piloting skill value. */
    public static final int MIN_SKILL = 0;
    /** Inclusive maximum for a pilot's gunnery / piloting skill value. */
    public static final int MAX_SKILL = 8;
    /** Inclusive minimum for a formation's unit-quality rating (A). */
    public static final int MIN_QUALITY = 0;
    /** Inclusive maximum for a formation's unit-quality rating (F). */
    public static final int MAX_QUALITY = 5;

    private OpForRosterEditOps() {
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Creates a new formation, populates it, and registers it on the roster.
     *
     * @param roster      the roster to add to
     * @param name        formation display name
     * @param weightClass {@code EntityWeightClass} constant
     * @param unitQuality quality rating (0&ndash;5)
     * @param skillLevel  crew skill level
     * @param trackName   assigned StratCon track, or {@code null} if unassigned
     * @param militia     whether this is a planetary-militia formation
     * @return the newly created formation (already added to the roster)
     */
    public static StratConOpForFormation addFormation(final StratConOpForRoster roster,
            final String name, final int weightClass, final int unitQuality,
            final SkillLevel skillLevel, final @Nullable String trackName, final boolean militia) {
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setName(name);
        formation.setWeightClass(weightClass);
        formation.setUnitQuality(unitQuality);
        formation.setSkillLevel(skillLevel);
        formation.setAssignedTrackName(trackName);
        formation.setMilitia(militia);
        roster.addFormation(formation);
        return formation;
    }

    /**
     * Removes a formation and all of its member units from the roster.
     *
     * @param roster      the roster to mutate
     * @param formationId the formation to delete; no-op if unknown
     */
    public static void deleteFormation(final StratConOpForRoster roster, final UUID formationId) {
        StratConOpForFormation formation = findFormation(roster, formationId);
        if (formation == null) {
            return;
        }
        // Copy the id list first: removeUnit does not touch unitIds, but we are
        // about to drop the whole formation anyway, so iterate a stable copy.
        for (UUID unitId : new ArrayList<>(formation.getUnitIds())) {
            roster.removeUnit(unitId);
        }
        roster.removeFormation(formationId);
    }

    /**
     * Creates a new unit, links it to the given formation, and registers it on
     * the roster.
     *
     * @param roster      the roster to add to
     * @param formationId the owning formation; must exist
     * @param template    the unit's chassis/model/faction template
     * @param unitType    {@code UnitType} constant for OOB-panel glyph rendering
     * @param pilotName   the crew name
     * @param gunnery     gunnery skill (0&ndash;8)
     * @param piloting    piloting skill (0&ndash;8)
     * @return the newly created unit (already added to the roster and formation)
     * @throws IllegalArgumentException if no formation with {@code formationId} exists
     */
    public static StratConOpForUnit addUnit(final StratConOpForRoster roster,
            final UUID formationId, final UnitTemplate template, final int unitType,
            final String pilotName, final int gunnery, final int piloting) {
        StratConOpForFormation formation = findFormation(roster, formationId);
        if (formation == null) {
            throw new IllegalArgumentException(
                    "Cannot add unit: no formation with id " + formationId);
        }
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setProtoEntity(template);
        unit.setUnitType(unitType);
        unit.setPilotName(pilotName);
        unit.setGunnery(gunnery);
        unit.setPiloting(piloting);
        // Capture reconciliation (foldResolutionInto) matches deployed crews back to
        // their roster record via this id, so a GM-added unit needs one too.
        unit.setPilotPersistentId(UUID.randomUUID());
        unit.setFormationId(formationId);
        roster.addUnit(unit);
        formation.getUnitIds().add(unit.getId());
        return unit;
    }

    /**
     * Removes a single unit from the roster and from its owning formation's
     * membership list.
     *
     * @param roster the roster to mutate
     * @param unitId the unit to remove; no-op if unknown
     */
    public static void removeUnit(final StratConOpForRoster roster, final UUID unitId) {
        StratConOpForUnit unit = roster.getUnit(unitId);
        if (unit == null) {
            return;
        }
        StratConOpForFormation formation = findFormation(roster, unit.getFormationId());
        if (formation != null) {
            formation.getUnitIds().remove(unitId);
        }
        roster.removeUnit(unitId);
    }

    /**
     * Moves a unit from its current formation to another formation.
     *
     * @param roster         the roster to mutate
     * @param unitId         the unit to move; no-op if unknown
     * @param newFormationId the destination formation; must exist
     * @throws IllegalArgumentException if no formation with {@code newFormationId} exists
     */
    public static void reassignUnit(final StratConOpForRoster roster, final UUID unitId,
            final UUID newFormationId) {
        StratConOpForUnit unit = roster.getUnit(unitId);
        if (unit == null) {
            return;
        }
        StratConOpForFormation destination = findFormation(roster, newFormationId);
        if (destination == null) {
            throw new IllegalArgumentException(
                    "Cannot reassign unit: no formation with id " + newFormationId);
        }
        StratConOpForFormation source = findFormation(roster, unit.getFormationId());
        if (source != null) {
            source.getUnitIds().remove(unitId);
        }
        unit.setFormationId(newFormationId);
        if (!destination.getUnitIds().contains(unitId)) {
            destination.getUnitIds().add(unitId);
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Validates the editable fields of a formation.
     *
     * @return list of human-readable error messages; empty when valid
     */
    public static List<String> validateFormation(final String name, final int unitQuality,
            final SkillLevel skillLevel) {
        List<String> errors = new ArrayList<>();
        if ((name == null) || name.isBlank()) {
            errors.add("Formation name must not be blank.");
        }
        if ((unitQuality < MIN_QUALITY) || (unitQuality > MAX_QUALITY)) {
            errors.add("Unit quality must be between " + MIN_QUALITY + " and " + MAX_QUALITY + ".");
        }
        if ((skillLevel == null) || (skillLevel == SkillLevel.NONE)) {
            errors.add("A valid skill level must be selected.");
        }
        return errors;
    }

    /**
     * Validates the editable fields of a unit.
     *
     * @return list of human-readable error messages; empty when valid
     */
    public static List<String> validateUnit(final String pilotName, final int gunnery,
            final int piloting, final @Nullable UnitTemplate template) {
        List<String> errors = new ArrayList<>();
        if ((pilotName == null) || pilotName.isBlank()) {
            errors.add("Pilot name must not be blank.");
        }
        if ((gunnery < MIN_SKILL) || (gunnery > MAX_SKILL)) {
            errors.add("Gunnery must be between " + MIN_SKILL + " and " + MAX_SKILL + ".");
        }
        if ((piloting < MIN_SKILL) || (piloting > MAX_SKILL)) {
            errors.add("Piloting must be between " + MIN_SKILL + " and " + MAX_SKILL + ".");
        }
        if ((template == null) || (template.getChassis() == null) || template.getChassis().isBlank()) {
            errors.add("A unit (chassis/model) must be selected.");
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the formation with the given id, or {@code null} if not found. */
    public static @Nullable StratConOpForFormation findFormation(final StratConOpForRoster roster,
            final @Nullable UUID formationId) {
        if (formationId == null) {
            return null;
        }
        for (StratConOpForFormation formation : roster.getFormations()) {
            if (formationId.equals(formation.getId())) {
                return formation;
            }
        }
        return null;
    }
}
