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

import jakarta.xml.bind.annotation.XmlElement;

/**
 * Persistent damage record for a single entity location.
 *
 * <p>Carries enough information to reproduce the location's damage state when
 * the entity is materialised for a future scenario.</p>
 */
public class LocationDamage {

    /** MegaMek location constant (e.g., {@code Mek.LOC_LT}). */
    @XmlElement
    private int locationIndex;

    /**
     * How many internal structure points have been lost since the unit left the
     * factory.  Zero means no internal damage.
     */
    @XmlElement
    private int reducedInternals;

    /** {@code true} if the location has been completely destroyed. */
    @XmlElement
    private boolean locationDestroyed;

    /**
     * {@code true} if the location was physically blown off the hull (implies
     * {@link #locationDestroyed}).
     */
    @XmlElement
    private boolean blownOff;

    /** No-arg constructor required by JAXB. */
    public LocationDamage() {
    }

    /**
     * Constructs a {@code LocationDamage} record with all four fields.
     *
     * @param locationIndex    MegaMek location constant
     * @param reducedInternals internal structure points lost
     * @param locationDestroyed whether the location is destroyed
     * @param blownOff         whether the location is blown off
     */
    public LocationDamage(final int locationIndex, final int reducedInternals,
            final boolean locationDestroyed, final boolean blownOff) {
        this.locationIndex = locationIndex;
        this.reducedInternals = reducedInternals;
        this.locationDestroyed = locationDestroyed;
        this.blownOff = blownOff;
    }

    public int getLocationIndex() {
        return locationIndex;
    }

    public void setLocationIndex(final int locationIndex) {
        this.locationIndex = locationIndex;
    }

    public int getReducedInternals() {
        return reducedInternals;
    }

    public void setReducedInternals(final int reducedInternals) {
        this.reducedInternals = reducedInternals;
    }

    public boolean isLocationDestroyed() {
        return locationDestroyed;
    }

    public void setLocationDestroyed(final boolean locationDestroyed) {
        this.locationDestroyed = locationDestroyed;
    }

    public boolean isBlownOff() {
        return blownOff;
    }

    public void setBlownOff(final boolean blownOff) {
        this.blownOff = blownOff;
    }
}
