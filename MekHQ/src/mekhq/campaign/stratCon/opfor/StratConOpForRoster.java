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

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import org.w3c.dom.Node;
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

    /** Count of militia reinforcement events fired this contract (separate cap from line OpFor). */
    @XmlElement
    private int militiaReinforcementEventsFired = 0;

    /** Challenger faction code, captured at build time. Drives bot-force label + RAT. */
    @XmlElement
    private String factionCode;

    /** Display name captured at build time (e.g. "Draconis Combine" or a pirate band name). */
    @XmlElement
    private String enemyBotName;

    /** Player colour name for this challenger's bot forces. */
    @XmlElement
    private String enemyColour;

    /** Lifecycle status. Defaults ACTIVE so legacy single-roster saves load as the active challenger. */
    @XmlElement
    private ChallengerStatus status = ChallengerStatus.ACTIVE;

    /** Arrival date (ISO-8601 string for JAXB friendliness). */
    @XmlElement(name = "arrivedDate")
    private String arrivedDateIso;

    /** End (withdraw/defeat) date (ISO-8601 string). */
    @XmlElement(name = "endedDate")
    private String endedDateIso;

    /** No-arg constructor required by JAXB. */
    public StratConOpForRoster() {
    }

    // -------------------------------------------------------------------------
    // Standalone serialization (for AtB contracts that don't have a full
    // StratConCampaignState carrier — see AtBContract.atbOpForRoster /
    // atbAlliedRoster fields).
    // -------------------------------------------------------------------------

    /**
     * Marshals this roster as a JAXB fragment under {@code elementName}.
     *
     * @param pw          destination
     * @param elementName XML element name to wrap the roster (e.g. {@code "atbOpForRoster"})
     */
    public void serializeAs(final PrintWriter pw, final String elementName) {
        try {
            JAXBContext context = JAXBContext.newInstance(StratConOpForRoster.class);
            JAXBElement<StratConOpForRoster> element = new JAXBElement<>(
                    new QName(elementName), StratConOpForRoster.class, this);
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FRAGMENT, true);
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.marshal(element, pw);
        } catch (Exception e) {
            LOGGER.error("Failed to serialize StratConOpForRoster as {}", elementName, e);
        }
    }

    /**
     * Returns a deep, independent copy of this roster via an in-memory JAXB
     * round-trip. Mutating the copy never affects the original. Used by the GM
     * editor so edits can be discarded on Cancel.
     *
     * <p>Throws on round-trip failure rather than returning an empty roster: a
     * silent empty copy would let the GM editor's apply-on-OK overwrite a
     * populated live roster with nothing. Callers must handle the failure (the
     * editor aborts and warns).</p>
     *
     * @return a deep copy of this roster; never {@code null}
     * @throws IllegalStateException if the JAXB round-trip fails
     */
    public StratConOpForRoster copy() {
        try {
            JAXBContext context = JAXBContext.newInstance(StratConOpForRoster.class);
            JAXBElement<StratConOpForRoster> element = new JAXBElement<>(
                    new QName("opForRoster"), StratConOpForRoster.class, this);
            Marshaller m = context.createMarshaller();
            java.io.StringWriter sw = new java.io.StringWriter();
            m.marshal(element, sw);

            // Unmarshal straight from the marshalled string — no intermediate DOM,
            // and no unhardened DocumentBuilderFactory in the path. (The round-trip
            // also escapes all field content, so marshalled output cannot carry a
            // DOCTYPE.) afterUnmarshal still fires, rebuilding the unit-id index.
            Unmarshaller um = context.createUnmarshaller();
            JAXBElement<StratConOpForRoster> root = um.unmarshal(
                    new javax.xml.transform.stream.StreamSource(
                            new java.io.StringReader(sw.toString())),
                    StratConOpForRoster.class);
            StratConOpForRoster copy = (root != null) ? root.getValue() : null;
            if (copy == null) {
                throw new IllegalStateException("unmarshal returned null");
            }
            return copy;
        } catch (Exception e) {
            LOGGER.error("Failed to copy StratConOpForRoster.", e);
            throw new IllegalStateException("Failed to copy StratConOpForRoster", e);
        }
    }

    /**
     * Unmarshals a roster from a JAXB-serialized XML node. Returns {@code null} on failure.
     */
    public static @Nullable StratConOpForRoster deserialize(final Node xmlNode) {
        try {
            JAXBContext context = JAXBContext.newInstance(StratConOpForRoster.class);
            Unmarshaller um = context.createUnmarshaller();
            JAXBElement<StratConOpForRoster> element = um.unmarshal(xmlNode, StratConOpForRoster.class);
            return element.getValue();
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize StratConOpForRoster", e);
            return null;
        }
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
        rebuildIndex();
    }

    /**
     * Rebuilds the transient {@code unitsById} look-up map from the current
     * {@link #unitList}. Call after any wholesale replacement of the unit list.
     */
    private void rebuildIndex() {
        unitsById = new HashMap<>();
        if (unitList == null) {
            return;
        }
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
     * Removes the unit with the given ID from both the list and the fast-lookup
     * map. No-op if the ID is unknown. Does not touch any formation's
     * {@code unitIds} — callers that maintain formation membership (e.g. the GM
     * editor) are responsible for that link.
     *
     * @param id the unit ID to remove
     */
    public void removeUnit(final UUID id) {
        if (id == null) {
            return;
        }
        unitList.removeIf(u -> id.equals(u.getId()));
        unitsById.remove(id);
    }

    /**
     * Adds a formation to the roster.
     *
     * @param formation the formation to add
     */
    public void addFormation(final StratConOpForFormation formation) {
        formations.add(formation);
    }

    /**
     * Removes the formation with the given ID from the roster. No-op if the ID
     * is unknown. Does not remove the formation's member units — callers that
     * want a cascading delete (e.g. the GM editor) remove the units first.
     *
     * @param id the formation ID to remove
     */
    public void removeFormation(final UUID id) {
        if (id == null) {
            return;
        }
        formations.removeIf(f -> id.equals(f.getId()));
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
     * Returns {@code true} when the unit's owning formation is flagged as militia.
     *
     * @param unit the unit to check; {@code null} returns {@code false}
     * @return {@code true} if the unit belongs to a militia formation
     */
    private boolean isMilitiaUnit(final StratConOpForUnit unit) {
        if (unit == null) {
            return false;
        }
        for (StratConOpForFormation formation : formations) {
            if ((formation.getId() != null)
                    && formation.getId().equals(unit.getFormationId())) {
                return formation.isMilitia();
            }
        }
        return false;
    }

    /**
     * Returns all living (non-terminal) units belonging to non-militia (line) formations.
     *
     * <p>Militia formations are excluded from the contract-win condition; this method
     * provides the filtered view that {@link #checkEliminationStatus} uses.</p>
     *
     * @return mutable list of living line units; never null
     */
    public List<StratConOpForUnit> livingLineUnits() {
        return unitList.stream()
                .filter(u -> !u.getStatus().isTerminal())
                .filter(u -> !isMilitiaUnit(u))
                .collect(Collectors.toList());
    }

    /**
     * Returns all living non-militia units assigned to the given track.
     *
     * @param trackName the track's display name
     * @return list of living line units on the track; never null
     */
    public List<StratConOpForUnit> livingLineUnitsForTrack(final String trackName) {
        return livingUnitsForTrack(trackName).stream()
                .filter(u -> !isMilitiaUnit(u))
                .toList();
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
     * Returns every formation in the roster that still has at least one living
     * unit, regardless of its assigned track.
     *
     * <p>Used as the global deploy fallback: when a scenario's own track has been
     * cleared, the deployer draws stragglers from this pool so formations parked
     * on quiet tracks can still be brought to battle and destroyed. Without it,
     * the global {@link #checkEliminationStatus} win condition is unreachable.</p>
     *
     * @return mutable list of living formations across all tracks; never null
     */
    public List<StratConOpForFormation> livingFormations() {
        return formations.stream()
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
     * Returns the count of militia reinforcement events fired this contract.
     *
     * @return non-negative integer
     */
    public int getMilitiaReinforcementEventsFired() {
        return militiaReinforcementEventsFired;
    }

    /** Sets the militia reinforcement counter; JAXB and tests only. */
    public void setMilitiaReinforcementEventsFired(final int value) {
        this.militiaReinforcementEventsFired = value;
    }

    /** Increments the militia reinforcement counter by one. */
    public void incrementMilitiaReinforcementEventsFired() {
        this.militiaReinforcementEventsFired++;
    }

    public String getFactionCode() {
        return factionCode;
    }

    public void setFactionCode(final String factionCode) {
        this.factionCode = factionCode;
    }

    public String getEnemyBotName() {
        return enemyBotName;
    }

    public void setEnemyBotName(final String enemyBotName) {
        this.enemyBotName = enemyBotName;
    }

    public String getEnemyColour() {
        return enemyColour;
    }

    public void setEnemyColour(final String enemyColour) {
        this.enemyColour = enemyColour;
    }

    public ChallengerStatus getStatus() {
        return (status == null) ? ChallengerStatus.ACTIVE : status;
    }

    public void setStatus(final ChallengerStatus status) {
        this.status = status;
    }

    public @Nullable LocalDate getArrivedDate() {
        return ((arrivedDateIso == null) || arrivedDateIso.isBlank()) ? null : LocalDate.parse(arrivedDateIso);
    }

    public void setArrivedDate(final @Nullable LocalDate date) {
        this.arrivedDateIso = (date == null) ? null : date.toString();
    }

    public @Nullable LocalDate getEndedDate() {
        return ((endedDateIso == null) || endedDateIso.isBlank()) ? null : LocalDate.parse(endedDateIso);
    }

    public void setEndedDate(final @Nullable LocalDate date) {
        this.endedDateIso = (date == null) ? null : date.toString();
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
            final @Nullable StratConScenario justResolvedScenario) {
        if (livingLineUnits().isEmpty()) {
            // For garrison-type contracts, clearing a challenger is a milestone, not a contract win: the garrison
            // defends for its term and another challenger may arrive. Other contract types still win by attrition.
            if ((contract.getContractType() != null) && contract.getContractType().isGarrisonType()) {
                if (getStatus() == ChallengerStatus.ACTIVE) {
                    setStatus(ChallengerStatus.DEFEATED);
                    recordDefeatMilestone(campaign, contract);
                }
                return EliminationResult.STILL_ACTIVE;
            }
            return EliminationResult.CONTRACT_WON;
        }
        // v1.6: pure-AtB callers pass null for the scenario (no StratCon track to pacify).
        // The CONTRACT_WON check above still fires for AtB; TRACK_PACIFIED is StratCon-only.
        if (justResolvedScenario == null) {
            return EliminationResult.STILL_ACTIVE;
        }
        StratConTrackState track = justResolvedScenario.getTrackForScenario(
                campaign, contract.getStratConCampaignState());
        if ((track != null) && livingLineUnitsForTrack(track.getDisplayableName()).isEmpty()) {
            return EliminationResult.TRACK_PACIFIED;
        }
        return EliminationResult.STILL_ACTIVE;
    }

    /**
     * Records a challenger-defeated milestone in the campaign Intelligence Log when a garrison challenger is wiped
     * out. Best-effort: stamps the end date and writes an OBSERVED entry tagged with the challenger faction; never
     * throws if the intel log is unavailable.
     *
     * @param campaign the current campaign
     * @param contract the contract this challenger belongs to
     */
    private void recordDefeatMilestone(final Campaign campaign, final AtBContract contract) {
        if (getEndedDate() == null) {
            setEndedDate(campaign.getLocalDate());
        }
        mekhq.campaign.stratCon.opfor.intel.IntelLog log = campaign.getIntelLog();
        if (log != null) {
            log.addEntry(new mekhq.campaign.stratCon.opfor.intel.IntelLogEntry(
                    getFactionCode(), contract.getName(), campaign.getLocalDate(), null,
                    getEnemyBotName(), null,
                    mekhq.campaign.stratCon.opfor.intel.IntelLogEntry.Outcome.OBSERVED));
        }
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
        // Convenience overload: no intel-log writes (used by ally-roster path and tests).
        return foldResolutionInto(scenario, entities, actualSalvage, devastatedEnemyUnits,
                oppositionPersonnel, retreatedEntities, track, null, null);
    }

    /**
     * Variant of {@link #foldResolutionInto} that also writes status transitions
     * to the campaign's {@link mekhq.campaign.stratCon.opfor.intel.IntelLog}
     * (v2). When {@code campaignForIntel} or {@code contractForIntel} is null
     * (e.g. the ally-roster fold path), no intel entries are written.
     *
     * @param campaignForIntel the active campaign (for intel log writes); null
     *                         to skip intel logging
     * @param contractForIntel the active contract (provides faction code,
     *                         contract name); null to skip intel logging
     */
    public List<String> foldResolutionInto(
            final StratConScenario scenario,
            final Map<UUID, Entity> entities,
            final List<TestUnit> actualSalvage,
            final List<TestUnit> devastatedEnemyUnits,
            final Hashtable<UUID, OppositionPersonnelStatus> oppositionPersonnel,
            final Enumeration<Entity> retreatedEntities,
            final @Nullable StratConTrackState track,
            final @Nullable mekhq.campaign.Campaign campaignForIntel,
            final @Nullable mekhq.campaign.mission.AtBContract contractForIntel) {

        if ((scenario == null) || (scenario.getBackingScenario() == null)) {
            LOGGER.warn("foldResolutionInto called with null scenario or backing scenario; skipping.");
            return new ArrayList<>();
        }
        UUID scenarioUuid = new UUID(scenario.getBackingScenario().getId(), 0L);
        return foldResolutionInto(scenarioUuid, entities, actualSalvage, devastatedEnemyUnits,
                oppositionPersonnel, retreatedEntities, track, campaignForIntel, contractForIntel);
    }

    /**
     * v1.6: UUID-based overload of {@link #foldResolutionInto}, for callers without
     * a {@link StratConScenario} wrapper (pure-AtB scenario resolution). All other
     * arguments and semantics match the scenario-based overload.
     */
    public List<String> foldResolutionInto(
            final UUID scenarioUuid,
            final Map<UUID, Entity> entities,
            final List<TestUnit> actualSalvage,
            final List<TestUnit> devastatedEnemyUnits,
            final Hashtable<UUID, OppositionPersonnelStatus> oppositionPersonnel,
            final Enumeration<Entity> retreatedEntities,
            final @Nullable StratConTrackState track,
            final @Nullable mekhq.campaign.Campaign campaignForIntel,
            final @Nullable mekhq.campaign.mission.AtBContract contractForIntel) {

        List<String> reportLines = new ArrayList<>();

        if (scenarioUuid == null) {
            LOGGER.warn("foldResolutionInto called with null scenarioUuid; skipping.");
            return reportLines;
        }

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
                logIntel(unit, mekhq.campaign.stratCon.opfor.intel.IntelLogEntry.Outcome.KILLED,
                        campaignForIntel, contractForIntel);
            } else if (salvageIds.contains(unit.getId())) {
                // --- SALVAGED ---
                unit.setStatus(Status.SALVAGED);
                unit.setRevealed(true);
                unit.setPersistentDamage(new PersistentDamageState());
                reportLines.add(buildDestroyedReportLine(unit));
                logIntel(unit, mekhq.campaign.stratCon.opfor.intel.IntelLogEntry.Outcome.SALVAGED,
                        campaignForIntel, contractForIntel);
            } else if (retreatedUuids.contains(unit.getId())) {
                // --- RETREATED — no status change ---
            } else if ((entity.getCrew() != null) && entity.getCrew().isDead()) {
                // --- KILLED (crew dead, e.g. head or center-torso destruction) ---
                // MegaMek may not have flagged the entity isDestroyed() yet — that
                // happens at the next phase boundary, which need not occur — so a
                // head-destroyed Mek would otherwise be treated as a survivor and
                // persist a blown-off head, re-spawning next scenario as an
                // undeployable headless wreck. A dead crew is a permanent kill.
                // (Ejected/captured crew report isDead() == false, and torso-cockpit
                // units that survive headless keep a live crew, so both are excluded.)
                unit.setStatus(Status.DESTROYED);
                unit.setRevealed(true);
                unit.setPersistentDamage(new PersistentDamageState());
                reportLines.add(buildDestroyedReportLine(unit));
                logIntel(unit, mekhq.campaign.stratCon.opfor.intel.IntelLogEntry.Outcome.KILLED,
                        campaignForIntel, contractForIntel);
            } else if (OpForUnitMaterializer.isNonViable(entity)) {
                // --- KILLED (non-redeployable wreck) ---
                // The unit lost a fatal location (CT/head/leg), engine, or is otherwise
                // unable to redeploy, but MegaMek has not flagged isDestroyed() and the
                // crew survived (typically an ejected pilot). Without this branch it would
                // fall through to "survived", persist catastrophic damage, leave the
                // formation un-eliminated, and re-spawn as a wreck MegaMek cannot load.
                unit.setStatus(Status.DESTROYED);
                unit.setRevealed(true);
                unit.setPersistentDamage(new PersistentDamageState());
                reportLines.add(buildDestroyedReportLine(unit));
                logIntel(unit, mekhq.campaign.stratCon.opfor.intel.IntelLogEntry.Outcome.KILLED,
                        campaignForIntel, contractForIntel);
            } else {
                // --- SURVIVED ON FIELD — persist damage ---
                unit.setPersistentDamage(OpForDamageReader.readPersistentDamageFrom(entity));
            }
        }

        // --- Captured-pilot reconciliation ---
        // Primary match: pilotPersistentId == captured Person.getId() (multi-slot crews).
        // Fallback: sourceUnitExternalId == StratConOpForUnit id (solo Mek pilots, whose
        // pilot id is lost when the EjectedCrew entity is generated on ejection).

        // Index this-scenario units by their own id, for the capture fallback below.
        Map<UUID, StratConOpForUnit> byUnitId = new HashMap<>();
        for (StratConOpForUnit u : unitList) {
            if (Objects.equals(u.getLastDeployedScenarioId(), scenarioUuid)) {
                byUnitId.put(u.getId(), u);
            }
        }

        for (OppositionPersonnelStatus ops : oppositionPersonnel.values()) {
            if (!ops.isCaptured()) {
                continue;
            }
            StratConOpForUnit unit = byPilotId.get(ops.getPerson().getId());
            if (unit == null) {
                // Solo Mek pilots lose their pilotPersistentId linkage on ejection
                // (the EjectedCrew entity does not inherit the parent Mek's crew
                // external id). Fall back to matching the captured person's source
                // unit id, which equals the StratConOpForUnit id.
                UUID sourceUnitId = ops.getSourceUnitExternalId();
                if (sourceUnitId != null) {
                    unit = byUnitId.get(sourceUnitId);
                }
            }
            if (unit != null) {
                // CAPTURED overrides any other status
                unit.setStatus(Status.CAPTURED);
                unit.setRevealed(true);
                logIntel(unit, mekhq.campaign.stratCon.opfor.intel.IntelLogEntry.Outcome.CAPTURED,
                        campaignForIntel, contractForIntel);
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
     * Writes an IntelLogEntry for the unit's status transition into the
     * campaign's intelligence log, if context is available. v2 slice 2 hook.
     */
    private void logIntel(final StratConOpForUnit unit,
            final mekhq.campaign.stratCon.opfor.intel.IntelLogEntry.Outcome outcome,
            final @Nullable mekhq.campaign.Campaign campaign,
            final @Nullable mekhq.campaign.mission.AtBContract contract) {
        if (campaign == null || contract == null || unit == null) {
            return;
        }
        UnitTemplate proto = unit.getProtoEntity();
        String chassis = proto != null ? proto.getChassis() : null;
        String model = proto != null ? proto.getModel() : null;
        if (chassis == null) {
            return;
        }
        campaign.getIntelLog().addEntry(new mekhq.campaign.stratCon.opfor.intel.IntelLogEntry(
                contract.getEnemyCode(),
                contract.getName(),
                campaign.getLocalDate(),
                unit.getPilotName(),
                chassis,
                model,
                outcome));
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
        rebuildIndex();
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
