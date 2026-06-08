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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
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
import megamek.common.units.Entity;
import megamek.logging.MMLogger;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.ResolveScenarioTracker.OppositionPersonnelStatus;
import mekhq.campaign.events.OpForRosterChangedEvent;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.stratCon.StratConScenario;
import mekhq.campaign.stratCon.StratConTrackState;
import mekhq.campaign.unit.TestUnit;

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

    private static final MMLogger LOGGER = MMLogger.create(StratConOpForRoster.class);
    private static final String RESOURCE_BUNDLE_NAME = "mekhq/resources/AtBStratCon";

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

    /** Count of reinforcement events fired this contract (capped per profile). */
    @XmlElement
    private int reinforcementEventsFired = 0;

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
     * Returns the count of reinforcement events fired this contract.
     *
     * @return non-negative integer
     */
    public int getReinforcementEventsFired() {
        return reinforcementEventsFired;
    }

    /** Sets the reinforcement counter; JAXB and tests only. */
    public void setReinforcementEventsFired(final int reinforcementEventsFired) {
        this.reinforcementEventsFired = reinforcementEventsFired;
    }

    /** Increments the reinforcement counter by one. */
    public void incrementReinforcementEventsFired() {
        this.reinforcementEventsFired++;
    }

    /**
     * Returns the track name with the highest formation-destruction ratio among
     * the supplied candidates, or {@code null} if no destroyed formations exist
     * on any candidate track. Caller should fall back to a weighted-random
     * track selection when this returns null.
     *
     * @param candidateTrackNames track names to consider; if empty, returns null
     * @return the most attrited track name, or null
     */
    public @Nullable String mostAttritedTrack(
            final List<String> candidateTrackNames) {
        if (candidateTrackNames == null || candidateTrackNames.isEmpty()) {
            return null;
        }
        // Single-pass tally: walk formations once, counting per-track totals and
        // destroyed counts in parallel maps keyed by track name.
        java.util.Map<String, long[]> tallies = new java.util.HashMap<>();
        for (String trackName : candidateTrackNames) {
            tallies.put(trackName, new long[]{0L, 0L}); // {total, destroyed}
        }
        for (StratConOpForFormation formation : formations) {
            long[] tally = tallies.get(formation.getAssignedTrackName());
            if (tally == null) {
                continue;
            }
            tally[0]++;
            if (formation.isDestroyed(this)) {
                tally[1]++;
            }
        }
        String best = null;
        double bestRatio = 0.0;
        for (String trackName : candidateTrackNames) {
            long[] tally = tallies.get(trackName);
            if (tally[0] == 0) {
                continue;
            }
            double ratio = (double) tally[1] / (double) tally[0];
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = trackName;
            }
        }
        return best;
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

    /**
     * Reads the post-battle outcome of every unit that was deployed in
     * {@code scenario} and folds the result back into the roster.
     *
     * <p>Status transitions (in priority order):</p>
     * <ol>
     *   <li><b>DESTROYED</b> — entity is destroyed or is in the devastated list.</li>
     *   <li><b>SALVAGED</b>  — entity appears on the salvage list.</li>
     *   <li><b>CAPTURED</b>  — a captured pilot's ID matches this unit's
     *       {@code pilotPersistentId} (works for multi-slot crew where
     *       {@code Person.getId()} carries the crew external-ID; solo Mek
     *       pilots are not reconciled via this path — see note in findings).</li>
     *   <li><b>Retreated</b> — no status change; entity is kept for future
     *       scenarios.</li>
     *   <li><b>Survived on field</b> — persistent damage is updated from the
     *       entity's current state.</li>
     * </ol>
     *
     * <p>After the unit loop, formations that lost ≥ 50 % of their units to
     * terminal statuses are upgraded to {@link IntelLevel#FULL_INTEL}.</p>
     *
     * @param scenario             the StratCon scenario that just resolved
     * @param entities             all entities keyed by their external UUID
     *                             (entity.externalIdAsString → entity)
     * @param actualSalvage        units claimed as salvage by the player
     * @param devastatedEnemyUnits enemy units that were devastated
     * @param oppositionPersonnel  opposition crew data, including capture flag
     * @param retreatedEntities    entities that retreated from the battle
     * @param track                the track the scenario took place on
     *                             (used by Phase 8 for event firing); may be null
     * @return list of report lines to add to the campaign daily log
     */
    public List<String> foldResolutionInto(
            final StratConScenario scenario,
            final Map<UUID, Entity> entities,
            final List<TestUnit> actualSalvage,
            final List<TestUnit> devastatedEnemyUnits,
            final Hashtable<UUID, OppositionPersonnelStatus> oppositionPersonnel,
            final Enumeration<Entity> retreatedEntities,
            final @Nullable StratConTrackState track) {

        List<String> reportLines = new ArrayList<>();

        if ((scenario == null) || (scenario.getBackingScenario() == null)) {
            LOGGER.warn("foldResolutionInto called with null scenario or backing scenario; skipping.");
            return reportLines;
        }

        // Bridge int scenario ID to UUID so we can compare against lastDeployedScenarioId
        int rawId = scenario.getBackingScenario().getId();
        UUID scenarioUuid = new UUID(rawId, 0L);

        // Drain the single-use Enumeration into a Set before we iterate
        Set<UUID> retreatedUuids = new HashSet<>();
        while (retreatedEntities.hasMoreElements()) {
            Entity e = retreatedEntities.nextElement();
            if (e != null) {
                String extId = e.getExternalIdAsString();
                if ((extId != null) && !"-1".equals(extId)) {
                    retreatedUuids.add(UUID.fromString(extId));
                }
            }
        }

        // Collect UUIDs of salvaged and devastated entities
        Set<UUID> salvageIds = new HashSet<>();
        for (TestUnit tu : actualSalvage) {
            if ((tu != null) && (tu.getEntity() != null)) {
                String extId = tu.getEntity().getExternalIdAsString();
                if ((extId != null) && !"-1".equals(extId)) {
                    salvageIds.add(UUID.fromString(extId));
                }
            }
        }

        Set<UUID> devastatedIds = new HashSet<>();
        for (TestUnit tu : devastatedEnemyUnits) {
            if ((tu != null) && (tu.getEntity() != null)) {
                String extId = tu.getEntity().getExternalIdAsString();
                if ((extId != null) && !"-1".equals(extId)) {
                    devastatedIds.add(UUID.fromString(extId));
                }
            }
        }

        // Build an index: pilotPersistentId -> unit, for capture reconciliation
        Map<UUID, StratConOpForUnit> byPilotId = new HashMap<>();
        for (StratConOpForUnit u : unitList) {
            if ((u.getPilotPersistentId() != null)
                    && Objects.equals(u.getLastDeployedScenarioId(), scenarioUuid)) {
                byPilotId.put(u.getPilotPersistentId(), u);
            }
        }

        // --- Main status loop: units deployed in this specific scenario ---
        for (StratConOpForUnit unit : unitList) {
            // Only process units that were in this scenario
            if (!Objects.equals(unit.getLastDeployedScenarioId(), scenarioUuid)) {
                continue;
            }
            if (unit.getStatus() != Status.READY) {
                continue;
            }

            Entity entity = entities.get(unit.getId());
            if (entity == null) {
                // Entity missing — treat as retreated (no roster change)
                LOGGER.warn("No entity found for OpFor unit {}; treating as retreated.", unit.getId());
                continue;
            }

            if (devastatedIds.contains(unit.getId()) || entity.isDestroyed()) {
                // --- DESTROYED ---
                unit.setStatus(Status.DESTROYED);
                unit.setRevealed(true);
                unit.setPersistentDamage(new PersistentDamageState());
                reportLines.add(buildDestroyedReportLine(unit));
            } else if (salvageIds.contains(unit.getId())) {
                // --- SALVAGED ---
                unit.setStatus(Status.SALVAGED);
                unit.setRevealed(true);
                unit.setPersistentDamage(new PersistentDamageState());
                reportLines.add(buildDestroyedReportLine(unit));
            } else if (retreatedUuids.contains(unit.getId())) {
                // --- RETREATED — no status change ---
            } else {
                // --- SURVIVED ON FIELD — persist damage ---
                unit.setPersistentDamage(OpForDamageReader.readPersistentDamageFrom(entity));
            }
        }

        // --- Captured-pilot reconciliation ---
        // Works for multi-slot crews where Person.getId() == crew.externalIdAsString
        // (set in Utilities.genRandomCrewWithCombinedSkill for multi-slot path).
        // Solo Mek pilots are NOT reconciled here — see findings note.
        for (OppositionPersonnelStatus ops : oppositionPersonnel.values()) {
            if (!ops.isCaptured()) {
                continue;
            }
            StratConOpForUnit unit = byPilotId.get(ops.getPerson().getId());
            if (unit != null) {
                // CAPTURED overrides any other status
                unit.setStatus(Status.CAPTURED);
                unit.setRevealed(true);
            }
        }

        // --- Intel upgrade: ≥ 50 % losses → FULL_INTEL ---
        for (StratConOpForFormation formation : formations) {
            long initialCount = formation.getUnitIds().size();
            if (initialCount == 0) {
                continue;
            }
            long terminated = formation.getUnitIds().stream()
                    .map(unitsById::get)
                    .filter(Objects::nonNull)
                    .filter(u -> u.getStatus() != Status.READY)
                    .count();
            if ((terminated * 2 >= initialCount)
                    && (formation.getIntelLevel() != IntelLevel.FULL_INTEL)) {
                formation.setIntelLevel(IntelLevel.FULL_INTEL);
            }
        }

        // --- Record newly destroyed formations ---
        for (StratConOpForFormation formation : formations) {
            if ((formation.getId() != null)
                    && formation.isDestroyed(this)
                    && !formationsDestroyedThisContract.contains(formation.getId())) {
                formationsDestroyedThisContract.add(formation.getId());
            }
        }

        // Fire event so the UI panel can refresh without polling
        if (track != null) {
            MekHQ.triggerEvent(new OpForRosterChangedEvent(track));
        }

        return reportLines;
    }

    /**
     * Builds a single destruction/salvage report line for the given unit.
     *
     * <p>Format: {@code "<pilotName>'s <chassis> <model> destroyed —
     * <formation> reduced to <living> / <total>"}</p>
     */
    private String buildDestroyedReportLine(final StratConOpForUnit unit) {
        String pilotName = (unit.getPilotName() != null) ? unit.getPilotName() : "Unknown pilot";
        String chassis = "";
        String model = "";
        if (unit.getProtoEntity() != null) {
            chassis = Objects.toString(unit.getProtoEntity().getChassis(), "");
            model = Objects.toString(unit.getProtoEntity().getModel(), "");
        }
        String chassisModel = (chassis + " " + model).trim();

        // Resolve formation name and living/total counts
        String formationName = "Unknown formation";
        int living = 0;
        int total = 0;
        if (unit.getFormationId() != null) {
            for (StratConOpForFormation f : formations) {
                if (unit.getFormationId().equals(f.getId())) {
                    formationName = f.getName();
                    total = f.getUnitIds().size();
                    living = (int) f.getUnitIds().stream()
                            .map(unitsById::get)
                            .filter(Objects::nonNull)
                            .filter(u -> u.getStatus() == Status.READY)
                            .count();
                    break;
                }
            }
        }

        try {
            ResourceBundle bundle = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
            return MessageFormat.format(
                    bundle.getString("opForRosterPanel.reportLine.unitDestroyed"),
                    pilotName, chassisModel, formationName, living, total);
        } catch (Exception ex) {
            LOGGER.warn("Could not load report bundle; using fallback format.", ex);
            return pilotName + "'s " + chassisModel + " destroyed — "
                    + formationName + " reduced to " + living + " / " + total;
        }
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
