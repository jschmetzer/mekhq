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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import megamek.common.annotations.Nullable;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.StratConScenario;
import mekhq.campaign.stratCon.StratConTrackState;

/**
 * The complete static order-of-battle for one StratCon contract.
 *
 * <p>Unit records are stored in a flat {@code List} for JAXB serialisation and
 * in a {@code Map} for O(1) look-up at runtime.  The map is rebuilt automatically
 * after JAXB unmarshalling via {@link #afterUnmarshal(Unmarshaller, Object)}.</p>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "opForRoster")
public class StratConOpForRoster {

    @XmlElementWrapper(name = "units")
    @XmlElement(name = "opForUnit")
    private List<StratConOpForUnit> unitList = new ArrayList<>();

    /** Rebuilt by {@link #afterUnmarshal} — not serialised. */
    @XmlTransient
    private Map<UUID, StratConOpForUnit> unitsById = new HashMap<>();

    @XmlElementWrapper(name = "formations")
    @XmlElement(name = "opForFormation")
    private List<StratConOpForFormation> formations = new ArrayList<>();

    @XmlElementWrapper(name = "formationsDestroyedThisContract")
    @XmlElement(name = "formationId")
    private List<UUID> formationsDestroyedThisContract = new ArrayList<>();

    /** No-arg constructor required by JAXB. */
    public StratConOpForRoster() {
    }

    // -------------------------------------------------------------------------
    // JAXB lifecycle callback
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the {@code unitsById} map after JAXB deserialisation.
     *
     * @param u      the unmarshaller (unused)
     * @param parent the parent object (unused)
     */
    public void afterUnmarshal(final Unmarshaller u, final Object parent) {
        unitsById = new HashMap<>();
        for (StratConOpForUnit unit : unitList) {
            if (unit.getId() != null) {
                unitsById.put(unit.getId(), unit);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mutation helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a unit to the roster and registers it in the fast-lookup map.
     *
     * @param unit the unit to add
     */
    public void addUnit(final StratConOpForUnit unit) {
        unitList.add(unit);
        if (unit.getId() != null) {
            unitsById.put(unit.getId(), unit);
        }
    }

    /**
     * Adds a formation to the roster.
     *
     * @param formation the formation to add
     */
    public void addFormation(final StratConOpForFormation formation) {
        formations.add(formation);
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the unit with the given ID, or {@code null} if not found.
     *
     * @param id the unit ID to look up
     * @return the unit, or {@code null}
     */
    public @Nullable StratConOpForUnit getUnit(final UUID id) {
        return unitsById.get(id);
    }

    /**
     * Returns all units whose {@link Status} is not terminal.
     *
     * @return mutable list of living units; never null
     */
    public List<StratConOpForUnit> livingUnits() {
        return unitList.stream()
                .filter(u -> !u.getStatus().isTerminal())
                .collect(Collectors.toList());
    }

    /**
     * Returns {@code true} when the roster was non-empty at some point and every
     * unit now has a terminal status.
     *
     * @return {@code true} if the entire OpFor has been eliminated
     */
    public boolean isEliminated() {
        return !unitList.isEmpty() && livingUnits().isEmpty();
    }

    /**
     * Returns all units assigned to the track with the given display name whose
     * status is not terminal.
     *
     * @param trackName the track's display name
     * @return list of matching units; never null
     */
    public List<StratConOpForUnit> unitsByTrack(final String trackName) {
        List<StratConOpForUnit> result = new ArrayList<>();
        for (StratConOpForFormation formation : formations) {
            if (!trackName.equals(formation.getAssignedTrackName())) {
                continue;
            }
            for (UUID unitId : formation.getUnitIds()) {
                StratConOpForUnit unit = unitsById.get(unitId);
                if (unit != null && !unit.getStatus().isTerminal()) {
                    result.add(unit);
                }
            }
        }
        return result;
    }

    /**
     * Returns all formations assigned to the given track that still have at
     * least one living unit.
     *
     * @param trackName the track's display name
     * @return list of living formations on that track; never null
     */
    public List<StratConOpForFormation> livingFormationsForTrack(final String trackName) {
        return formations.stream()
                .filter(f -> trackName.equals(f.getAssignedTrackName()))
                .filter(f -> !f.isDestroyed(this))
                .collect(Collectors.toList());
    }

    /**
     * Returns all living (non-terminal) units assigned to the given track,
     * across all formations on that track.
     *
     * @param trackName the track's display name
     * @return list of living units on the track; never null
     */
    public List<StratConOpForUnit> livingUnitsForTrack(final String trackName) {
        return formations.stream()
                .filter(f -> trackName.equals(f.getAssignedTrackName()))
                .flatMap(f -> f.getUnitIds().stream())
                .map(unitsById::get)
                .filter(Objects::nonNull)
                .filter(u -> u.getStatus() == Status.READY)
                .toList();
    }

    /**
     * Checks whether the contract's OpFor has been eliminated — either in full
     * (contract won) or on the track that just resolved (track pacified).
     *
     * <p>CONTRACT_WON is checked first so the last unit on the last track
     * triggers the correct result.</p>
     *
     * @param campaign               the current campaign
     * @param contract               the contract whose OpFor roster this is
     * @param justResolvedScenario   the scenario that just finished
     * @return the elimination result
     */
    public EliminationResult checkEliminationStatus(final Campaign campaign,
            final AtBContract contract,
            final StratConScenario justResolvedScenario) {
        if (livingUnits().isEmpty()) {
            return EliminationResult.CONTRACT_WON;
        }
        StratConTrackState track = justResolvedScenario.getTrackForScenario(
                campaign, contract.getStratconCampaignState());
        if ((track != null) && livingUnitsForTrack(track.getDisplayableName()).isEmpty()) {
            return EliminationResult.TRACK_PACIFIED;
        }
        return EliminationResult.STILL_ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public List<StratConOpForUnit> getUnitList() {
        return unitList;
    }

    public void setUnitList(final List<StratConOpForUnit> unitList) {
        this.unitList = unitList;
    }

    public List<StratConOpForFormation> getFormations() {
        return formations;
    }

    public void setFormations(final List<StratConOpForFormation> formations) {
        this.formations = formations;
    }

    public List<UUID> getFormationsDestroyedThisContract() {
        return formationsDestroyedThisContract;
    }

    public void setFormationsDestroyedThisContract(final List<UUID> formationsDestroyedThisContract) {
        this.formationsDestroyedThisContract = formationsDestroyedThisContract;
    }
}
