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

import java.util.HashMap;

import megamek.common.CriticalSlot;
import megamek.common.annotations.Nullable;
import megamek.common.compute.Compute;
import megamek.common.enums.Gender;
import megamek.common.loaders.MekFileParser;
import megamek.common.loaders.MekSummary;
import megamek.common.loaders.MekSummaryCache;
import megamek.common.battleArmor.BattleArmor;
import megamek.common.equipment.IArmorState;
import megamek.common.units.Aero;
import megamek.common.units.ConvInfantry;
import megamek.common.units.Crew;
import megamek.common.units.Entity;
import megamek.common.units.Mek;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.stratCon.opfor.PersistentDamageState.AeroSystem;
import mekhq.campaign.stratCon.opfor.PersistentDamageState.SystemCritical;

/**
 * Converts a persisted {@link StratConOpForUnit} record into a fully-configured
 * {@link Entity} ready for use in a StratCon scenario.
 *
 * <p>The materializer applies three layers of configuration:</p>
 * <ol>
 *   <li>Load the entity from the MegaMek unit cache via {@code MekSummaryCache}.
 *   <li>Build a named {@link Crew} and wire the pilot's persistent UUID onto
 *       {@code crew.externalIdAsString(0)} so that Phase 6 captured-pilot
 *       reconciliation can locate the roster record.</li>
 *   <li>Apply any inter-scenario {@link PersistentDamageState} — internal
 *       structure reductions, critical-slot hits, and unit-type-specific
 *       damage (Aero systems, BattleArmor troopers, ConvInfantry strength).</li>
 * </ol>
 *
 * <p>All methods are {@code static}; this class is not intended to be
 * instantiated.</p>
 */
public class OpForUnitMaterializer {

    private static final MMLogger LOGGER = MMLogger.create(OpForUnitMaterializer.class);

    /** Utility class — no instantiation. */
    private OpForUnitMaterializer() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Materialises a {@link StratConOpForUnit} into a deployment-ready
     * {@link Entity}.
     *
     * <p>Returns {@code null} and logs a warning when:</p>
     * <ul>
     *   <li>The chassis+model key is absent from {@code MekSummaryCache}.</li>
     *   <li>The entity file cannot be loaded (entity-loading exception).</li>
     *   <li>The unit's {@link UnitTemplate} is {@code null}.</li>
     * </ul>
     *
     * @param unit     the roster unit to materialise
     * @param campaign the active campaign (provides player and game references)
     * @return the configured entity, or {@code null} on any load failure
     */
    public static @Nullable Entity deploy(final StratConOpForUnit unit,
            final Campaign campaign) {
        UnitTemplate template = unit.getProtoEntity();
        if (template == null) {
            LOGGER.warn("Cannot materialise unit {} — protoEntity is null.", unit.getId());
            return null;
        }

        String lookupKey = template.getChassis() + " " + template.getModel();
        MekSummary summary = MekSummaryCache.getInstance().getMek(lookupKey);
        if (summary == null) {
            LOGGER.warn("MekSummaryCache miss for '{}'; unit {} cannot be materialised.",
                    lookupKey, unit.getId());
            return null;
        }

        Entity entity;
        try {
            entity = new MekFileParser(summary.getSourceFile(), summary.getEntryName())
                    .getEntity();
        } catch (Exception ex) {
            LOGGER.warn("Failed to load entity for '{}': {}", lookupKey, ex.getMessage());
            return null;
        }

        if (entity == null) {
            LOGGER.warn("MekFileParser returned null entity for '{}'.", lookupKey);
            return null;
        }

        // Attach to campaign context
        entity.setOwner(campaign.getPlayer());
        entity.setGame(campaign.getGame());

        // Build crew using the wide constructor (the only multi-arg form available)
        Crew crew = new Crew(
                entity.getCrew().getCrewType(),
                unit.getPilotName() != null ? unit.getPilotName() : "Unknown Pilot",
                Compute.getFullCrewSize(entity),
                unit.getGunnery(),
                unit.getPiloting(),
                Gender.RANDOMIZE,
                false,
                new HashMap<>());

        // Critical wiring: Phase 6 captured-pilot reconciliation matches on this
        if (unit.getPilotPersistentId() != null) {
            crew.setExternalIdAsString(unit.getPilotPersistentId().toString(), 0);
        }
        entity.setCrew(crew);

        // Wire unit UUID as the entity's external ID (used by Phase 6 fold logic)
        entity.setExternalIdAsString(unit.getId().toString());

        // Apply any persistent damage accumulated across prior scenarios
        PersistentDamageState damage = unit.getPersistentDamage();
        if (damage != null) {
            applyPersistentDamage(entity, damage);
        }

        return entity;
    }

    // -------------------------------------------------------------------------
    // Damage application
    // -------------------------------------------------------------------------

    /**
     * Applies a {@link PersistentDamageState} to an already-loaded entity.
     *
     * <p>Processing order (mirrors the design spec):</p>
     * <ol>
     *   <li>Per-location structural damage (reduced internals / blow-offs).</li>
     *   <li>System-critical hits ({@link SystemCritical}) via slot-walk — matches
     *       the pattern used in {@code Unit.damageSystem} / {@code destroySystem}.</li>
     *   <li>Actuator hits by explicit (location, slot) index.</li>
     *   <li>Aero-specific system hits (if entity is an {@link Aero}).</li>
     *   <li>BattleArmor trooper losses or ConvInfantry strength reduction.</li>
     * </ol>
     *
     * @param entity the entity to modify in place
     * @param damage the damage state to apply
     */
    public static void applyPersistentDamage(final Entity entity,
            final PersistentDamageState damage) {

        // 1. Per-location structural damage
        for (LocationDamage ld : damage.getLocationDamageList()) {
            int loc = ld.getLocationIndex();
            if (loc < 0 || loc >= entity.locations()) {
                continue;
            }
            if (ld.isLocationDestroyed() || ld.isBlownOff()) {
                entity.destroyLocation(loc, ld.isBlownOff());
            } else if (ld.getReducedInternals() > 0) {
                int current = entity.getInternal(loc);
                int reduced = current - ld.getReducedInternals();
                entity.setInternal(Math.max(reduced, 0), loc);
            }
        }

        // 2. System-critical hits (slot-walk, same pattern as Unit.damageSystem)
        for (SystemCritical sc : SystemCritical.values()) {
            int hitsToApply = damage.getCount(sc);
            if (hitsToApply <= 0) {
                continue;
            }
            int applied = 0;
            outer:
            for (int loc = 0; loc < entity.locations() && applied < hitsToApply; loc++) {
                for (int slot = 0;
                        slot < entity.getNumberOfCriticalSlots(loc) && applied < hitsToApply;
                        slot++) {
                    CriticalSlot cs = entity.getCritical(loc, slot);
                    if (cs == null) {
                        continue;
                    }
                    if ((cs.getType() == CriticalSlot.TYPE_SYSTEM)
                            && (cs.getIndex() == sc.getMekSystemIndex())
                            && !cs.isHit()) {
                        cs.setHit(true);
                        cs.setDestroyed(true);
                        cs.setRepairable(true);
                        applied++;
                    }
                }
            }
            if (applied < hitsToApply) {
                LOGGER.warn("Could only apply {}/{} hits for system {} — fewer slots than expected.",
                        applied, hitsToApply, sc);
            }
        }

        // 3. Actuator hits by explicit (location, slot) index
        for (ActuatorHit ah : damage.getActuatorHits()) {
            int loc = ah.getLocationIndex();
            int slot = ah.getSlotIndex();
            if (loc < 0 || loc >= entity.locations()) {
                continue;
            }
            if (slot < 0 || slot >= entity.getNumberOfCriticalSlots(loc)) {
                continue;
            }
            CriticalSlot cs = entity.getCritical(loc, slot);
            if (cs != null) {
                cs.setHit(true);
                cs.setDestroyed(true);
                cs.setRepairable(true);
            }
        }

        // 4. Aero-specific system hits
        if (entity instanceof Aero aero) {
            aero.setSensorHits(damage.getAeroHit(AeroSystem.SENSOR));
            aero.setFCSHits(damage.getAeroHit(AeroSystem.FCS));
            aero.setAvionicsHits(damage.getAeroHit(AeroSystem.AVIONICS));
            aero.setCICHits(damage.getAeroHit(AeroSystem.CIC));
            aero.setEngineHits(damage.getAeroHit(AeroSystem.ENGINE));
            int siReduction = damage.getAeroHit(AeroSystem.STRUCTURAL_INTEGRITY);
            if (siReduction > 0) {
                aero.setSI(Math.max(0, aero.getOSI() - siReduction));
            }
        }

        // 5. Infantry/BattleArmor (BattleArmor extends Infantry — check BA first)
        if (entity instanceof BattleArmor ba) {
            for (int idx : damage.getBaTrooperLost()) {
                if (idx >= BattleArmor.LOC_TROOPER_1 && idx < ba.locations()) {
                    // Kill a trooper by setting internal to ARMOR_DESTROYED (-3); setInternal(0)
                    // is incorrect and does not register as a trooper kill in MegaMek's hit-routing.
                    ba.setInternal(IArmorState.ARMOR_DESTROYED, idx);
                }
            }
        } else if (entity instanceof ConvInfantry inf) {
            Integer troopers = damage.getInfantryActiveTroopers();
            if (troopers != null) {
                inf.setInternal(Math.max(0, troopers), ConvInfantry.LOC_INFANTRY);
            }
        }
    }
}
