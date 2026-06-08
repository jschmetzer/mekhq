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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import megamek.common.annotations.Nullable;
import megamek.common.units.Mek;

/**
 * Captures the inter-scenario damage state of a single static OpFor unit.
 *
 * <p>Because JAXB cannot natively serialise {@code EnumMap}, system-critical and
 * aero-system hit counts are wrapped in small helper inner classes
 * ({@link SystemCriticalCount}, {@link AeroSystemHit}) that hold both the enum
 * key and the count as plain Java beans.</p>
 *
 * <p>Callers <em>must</em> use the typed accessor methods
 * ({@link #getCount(SystemCritical)}, {@link #setCount(SystemCritical, int)},
 * etc.) rather than manipulating the backing lists directly.  Doing so keeps the
 * EnumMap-equivalent behaviour intact regardless of JAXB round-trips.</p>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "persistentDamage")
public class PersistentDamageState {

    // -------------------------------------------------------------------------
    // Nested enums
    // -------------------------------------------------------------------------

    /**
     * Mek critical systems that can accumulate persistent hits across scenarios.
     *
     * <p>Each constant stores the {@code Mek.SYSTEM_*} index so that
     * {@link mekhq.campaign.stratCon.opfor.OpForUnitMaterializer} and
     * {@link mekhq.campaign.stratCon.opfor.OpForDamageReader} can map directly to
     * {@code CriticalSlot} indices without a secondary lookup table.</p>
     */
    public enum SystemCritical {
        ENGINE(Mek.SYSTEM_ENGINE),
        GYRO(Mek.SYSTEM_GYRO),
        SENSOR(Mek.SYSTEM_SENSORS),
        LIFE_SUPPORT(Mek.SYSTEM_LIFE_SUPPORT);

        private final int mekSystemIndex;

        SystemCritical(final int mekSystemIndex) {
            this.mekSystemIndex = mekSystemIndex;
        }

        /**
         * Returns the {@code Mek.SYSTEM_*} constant corresponding to this critical.
         *
         * @return the MegaMek system index
         */
        public int getMekSystemIndex() {
            return mekSystemIndex;
        }
    }

    /**
     * Aerospace-specific systems that can accumulate persistent damage.
     */
    public enum AeroSystem {
        SENSOR,
        FCS,
        AVIONICS,
        CIC,
        ENGINE,
        STRUCTURAL_INTEGRITY
    }

    // -------------------------------------------------------------------------
    // Helper inner classes (JAXB-friendly wrappers for EnumMap entries)
    // -------------------------------------------------------------------------

    /**
     * JAXB-serialisable container for a {@link SystemCritical} hit count.
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SystemCriticalCount {

        @XmlElement
        private SystemCritical system;

        @XmlElement
        private int count;

        /** No-arg constructor required by JAXB. */
        public SystemCriticalCount() {
        }

        public SystemCriticalCount(final SystemCritical system, final int count) {
            this.system = system;
            this.count = count;
        }

        public SystemCritical getSystem() {
            return system;
        }

        public void setSystem(final SystemCritical system) {
            this.system = system;
        }

        public int getCount() {
            return count;
        }

        public void setCount(final int count) {
            this.count = count;
        }
    }

    /**
     * JAXB-serialisable container for an {@link AeroSystem} hit count.
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AeroSystemHit {

        @XmlElement
        private AeroSystem system;

        @XmlElement
        private int count;

        /** No-arg constructor required by JAXB. */
        public AeroSystemHit() {
        }

        public AeroSystemHit(final AeroSystem system, final int count) {
            this.system = system;
            this.count = count;
        }

        public AeroSystem getSystem() {
            return system;
        }

        public void setSystem(final AeroSystem system) {
            this.system = system;
        }

        public int getCount() {
            return count;
        }

        public void setCount(final int count) {
            this.count = count;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @XmlElementWrapper(name = "locationDamages")
    @XmlElement(name = "locationDamage")
    private List<LocationDamage> locationDamageList = new ArrayList<>();

    @XmlElementWrapper(name = "systemCriticalCounts")
    @XmlElement(name = "systemCriticalCount")
    private List<SystemCriticalCount> systemCriticalCounts = new ArrayList<>();

    @XmlElementWrapper(name = "actuatorHits")
    @XmlElement(name = "actuatorHit")
    private Set<ActuatorHit> actuatorHits = new HashSet<>();

    @XmlElementWrapper(name = "aeroSystemHits")
    @XmlElement(name = "aeroSystemHit")
    private List<AeroSystemHit> aeroSystemHits = new ArrayList<>();

    @XmlElement
    private Integer infantryActiveTroopers;

    @XmlElementWrapper(name = "baTroopersLost")
    @XmlElement(name = "trooperIndex")
    private Set<Integer> baTrooperLost = new HashSet<>();

    // -------------------------------------------------------------------------
    // Typed accessors — callers must use these rather than the backing lists
    // -------------------------------------------------------------------------

    /**
     * Returns the number of persistent hits recorded for the given
     * {@link SystemCritical}, or {@code 0} if none have been recorded.
     *
     * @param system the system to query
     * @return the hit count (≥ 0)
     */
    public int getCount(final SystemCritical system) {
        for (SystemCriticalCount entry : systemCriticalCounts) {
            if (entry.getSystem() == system) {
                return entry.getCount();
            }
        }
        return 0;
    }

    /**
     * Sets the persistent hit count for the given {@link SystemCritical}.
     *
     * <p>If {@code n} is zero the entry is removed to keep the serialised form
     * compact.  Otherwise the existing entry is updated or a new one appended.</p>
     *
     * @param system the system to update
     * @param n      the new count (≥ 0)
     */
    public void setCount(final SystemCritical system, final int n) {
        for (SystemCriticalCount entry : systemCriticalCounts) {
            if (entry.getSystem() == system) {
                if (n == 0) {
                    systemCriticalCounts.remove(entry);
                } else {
                    entry.setCount(n);
                }
                return;
            }
        }
        if (n > 0) {
            systemCriticalCounts.add(new SystemCriticalCount(system, n));
        }
    }

    /**
     * Returns the number of persistent hits recorded for the given
     * {@link AeroSystem}, or {@code 0} if none.
     *
     * @param system the aero system to query
     * @return the hit count (≥ 0)
     */
    public int getAeroHit(final AeroSystem system) {
        for (AeroSystemHit entry : aeroSystemHits) {
            if (entry.getSystem() == system) {
                return entry.getCount();
            }
        }
        return 0;
    }

    /**
     * Sets the persistent hit count for the given {@link AeroSystem}.
     *
     * @param system the aero system to update
     * @param n      the new count (≥ 0)
     */
    public void setAeroHit(final AeroSystem system, final int n) {
        for (AeroSystemHit entry : aeroSystemHits) {
            if (entry.getSystem() == system) {
                if (n == 0) {
                    aeroSystemHits.remove(entry);
                } else {
                    entry.setCount(n);
                }
                return;
            }
        }
        if (n > 0) {
            aeroSystemHits.add(new AeroSystemHit(system, n));
        }
    }

    /**
     * Records that the given location has reduced internal structure.
     *
     * <p>If a {@link LocationDamage} record for {@code loc} already exists it is
     * updated in place; otherwise a new record is appended.</p>
     *
     * @param loc    MegaMek location constant
     * @param amount how many internal-structure points have been lost
     */
    public void setReducedInternals(final int loc, final int amount) {
        for (LocationDamage ld : locationDamageList) {
            if (ld.getLocationIndex() == loc) {
                ld.setReducedInternals(amount);
                return;
            }
        }
        locationDamageList.add(new LocationDamage(loc, amount, false, false));
    }

    /**
     * Marks the given location as blown off (or clears that flag).
     *
     * @param loc  MegaMek location constant
     * @param flag {@code true} to mark as blown off
     */
    public void setLocationBlownOff(final int loc, final boolean flag) {
        for (LocationDamage ld : locationDamageList) {
            if (ld.getLocationIndex() == loc) {
                ld.setBlownOff(flag);
                if (flag) {
                    ld.setLocationDestroyed(true);
                }
                return;
            }
        }
        locationDamageList.add(new LocationDamage(loc, 0, flag, flag));
    }

    // -------------------------------------------------------------------------
    // Plain getters/setters for direct iteration
    // -------------------------------------------------------------------------

    public List<LocationDamage> getLocationDamageList() {
        return locationDamageList;
    }

    public void setLocationDamageList(final List<LocationDamage> locationDamageList) {
        this.locationDamageList = locationDamageList;
    }

    public List<SystemCriticalCount> getSystemCriticalCounts() {
        return systemCriticalCounts;
    }

    public void setSystemCriticalCounts(final List<SystemCriticalCount> systemCriticalCounts) {
        this.systemCriticalCounts = systemCriticalCounts;
    }

    public Set<ActuatorHit> getActuatorHits() {
        return actuatorHits;
    }

    public void setActuatorHits(final Set<ActuatorHit> actuatorHits) {
        this.actuatorHits = actuatorHits;
    }

    public List<AeroSystemHit> getAeroSystemHits() {
        return aeroSystemHits;
    }

    public void setAeroSystemHits(final List<AeroSystemHit> aeroSystemHits) {
        this.aeroSystemHits = aeroSystemHits;
    }

    public @Nullable Integer getInfantryActiveTroopers() {
        return infantryActiveTroopers;
    }

    public void setInfantryActiveTroopers(@Nullable final Integer infantryActiveTroopers) {
        this.infantryActiveTroopers = infantryActiveTroopers;
    }

    public Set<Integer> getBaTrooperLost() {
        return baTrooperLost;
    }

    public void setBaTrooperLost(final Set<Integer> baTrooperLost) {
        this.baTrooperLost = baTrooperLost;
    }
}
