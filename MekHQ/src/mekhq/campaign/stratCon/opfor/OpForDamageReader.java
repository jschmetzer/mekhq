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

import megamek.common.CriticalSlot;
import megamek.common.battleArmor.BattleArmor;
import megamek.common.equipment.IArmorState;
import megamek.common.units.Aero;
import megamek.common.units.ConvInfantry;
import megamek.common.units.Entity;
import megamek.logging.MMLogger;
import mekhq.campaign.stratCon.opfor.PersistentDamageState.AeroSystem;
import mekhq.campaign.stratCon.opfor.PersistentDamageState.SystemCritical;

/**
 * Reads the post-battle damage state of an OpFor {@link Entity} and
 * converts it into a {@link PersistentDamageState} suitable for storage
 * in a {@link StratConOpForUnit}.
 *
 * <p>All methods are static; this class is not instantiated.</p>
 *
 * <p><strong>Entity-type dispatch order:</strong> {@link BattleArmor} is checked
 * before {@link ConvInfantry} because {@code BattleArmor} extends
 * {@code Infantry} — the reverse order would incorrectly capture BA units
 * in the infantry branch.</p>
 */
public final class OpForDamageReader {

    private static final MMLogger LOGGER = MMLogger.create(OpForDamageReader.class);

    /** Utility class — do not instantiate. */
    private OpForDamageReader() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads all persistent damage from {@code entity} and returns a fresh
     * {@link PersistentDamageState}.
     *
     * <p>Structural damage (reduced internals, blown-off locations) and
     * critical-slot hits are captured for all entity types.  Aero-specific
     * system hits are captured for {@link Aero} instances.  Infantry
     * strength is captured for {@link ConvInfantry}; BA trooper loss is
     * captured for {@link BattleArmor}.</p>
     *
     * @param entity the post-battle entity to read; must not be {@code null}
     * @return a fully populated {@link PersistentDamageState}; never null
     */
    public static PersistentDamageState readPersistentDamageFrom(final Entity entity) {
        PersistentDamageState state = new PersistentDamageState();

        readStructuralDamage(entity, state);
        readAeroDamage(entity, state);
        readInfantryDamage(entity, state);

        return state;
    }

    // -------------------------------------------------------------------------
    // Private workers
    // -------------------------------------------------------------------------

    /**
     * Populates structural damage (internals, blown-off flags) and
     * critical-slot hits (system criticals and actuator hits) from all
     * locations on {@code entity}.
     */
    private static void readStructuralDamage(final Entity entity,
            final PersistentDamageState state) {
        for (int loc = 0; loc < entity.locations(); loc++) {
            int baseline = entity.getOInternal(loc);
            int current = entity.getInternal(loc);
            boolean blownOff = entity.isLocationBlownOff(loc);

            // Skip non-entity locations (NA internal)
            if (baseline == IArmorState.ARMOR_NA) {
                continue;
            }

            if (blownOff || (current == IArmorState.ARMOR_DESTROYED)) {
                state.setLocationBlownOff(loc, true);
            } else if (current < baseline) {
                state.setReducedInternals(loc, baseline - current);
            }

            readCriticalSlots(entity, loc, state);
        }
    }

    /**
     * Reads critical-slot damage for the given location and records system
     * criticals and actuator hits in {@code state}.
     */
    private static void readCriticalSlots(final Entity entity,
            final int loc,
            final PersistentDamageState state) {
        for (CriticalSlot cs : entity.getCriticalSlots(loc)) {
            if (cs == null) {
                continue;
            }
            if (!cs.isHit() && !cs.isDestroyed()) {
                continue;
            }
            if (cs.getType() == CriticalSlot.TYPE_SYSTEM) {
                SystemCritical sc = systemCriticalForIndex(cs.getIndex());
                if (sc != null) {
                    state.setCount(sc, state.getCount(sc) + 1);
                }
            } else {
                // Actuator / equipment hit
                state.getActuatorHits().add(new ActuatorHit(loc, cs.getIndex()));
            }
        }
    }

    /**
     * Maps a {@code Mek.SYSTEM_*} index to the corresponding
     * {@link SystemCritical} constant, or {@code null} if the index is not
     * one we track persistently.
     */
    private static SystemCritical systemCriticalForIndex(final int index) {
        for (SystemCritical sc : SystemCritical.values()) {
            if (sc.getMekSystemIndex() == index) {
                return sc;
            }
        }
        return null;
    }

    /**
     * Populates aero-specific system hits for {@link Aero} entities.
     * No-ops for all other entity types.
     */
    private static void readAeroDamage(final Entity entity,
            final PersistentDamageState state) {
        if (!(entity instanceof Aero aero)) {
            return;
        }

        state.setAeroHit(AeroSystem.SENSOR, aero.getSensorHits());
        state.setAeroHit(AeroSystem.FCS, aero.getFCSHits());
        state.setAeroHit(AeroSystem.AVIONICS, aero.getAvionicsHits());
        state.setAeroHit(AeroSystem.CIC, aero.getCICHits());
        state.setAeroHit(AeroSystem.ENGINE, aero.getEngineHits());

        int siLoss = aero.getOSI() - aero.getSI();
        if (siLoss > 0) {
            state.setAeroHit(AeroSystem.STRUCTURAL_INTEGRITY, siLoss);
        }
    }

    /**
     * Populates infantry-specific damage.
     *
     * <p>BattleArmor is checked first (it extends Infantry) so that BA units
     * are handled by the trooper-loss branch, not the conventional-infantry
     * branch.</p>
     */
    private static void readInfantryDamage(final Entity entity,
            final PersistentDamageState state) {
        if (entity instanceof BattleArmor ba) {
            for (int i = BattleArmor.LOC_TROOPER_1; i < ba.locations(); i++) {
                // BA troopers are killed by setting their internal to ARMOR_DESTROYED (-3),
                // NOT by isLocationBlownOff — using the wrong API caused losses to never persist.
                if (ba.getInternal(i) == IArmorState.ARMOR_DESTROYED) {
                    state.getBaTrooperLost().add(i);
                }
            }
        } else if (entity instanceof ConvInfantry inf) {
            int current = inf.getInternal(ConvInfantry.LOC_INFANTRY);
            state.setInfantryActiveTroopers(Math.max(0, current));
        }
    }
}
