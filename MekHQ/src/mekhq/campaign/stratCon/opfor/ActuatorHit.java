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
 * Identifies a single hit actuator slot by its location and slot index.
 *
 * <p>Used as elements in a {@code Set} so that duplicate hits for the same slot
 * are deduplicated; therefore {@link #equals} and {@link #hashCode} cover both
 * fields.</p>
 */
public class ActuatorHit {

    /** MegaMek location constant (e.g., {@code Mek.LOC_LARM}). */
    @XmlElement
    private int locationIndex;

    /** Critical slot index within the location. */
    @XmlElement
    private int slotIndex;

    /** No-arg constructor required by JAXB. */
    public ActuatorHit() {
    }

    /**
     * Constructs an {@code ActuatorHit} for the given location and slot.
     *
     * @param locationIndex MegaMek location constant
     * @param slotIndex     critical slot index within that location
     */
    public ActuatorHit(final int locationIndex, final int slotIndex) {
        this.locationIndex = locationIndex;
        this.slotIndex = slotIndex;
    }

    public int getLocationIndex() {
        return locationIndex;
    }

    public void setLocationIndex(final int locationIndex) {
        this.locationIndex = locationIndex;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(final int slotIndex) {
        this.slotIndex = slotIndex;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ActuatorHit other)) {
            return false;
        }
        return locationIndex == other.locationIndex && slotIndex == other.slotIndex;
    }

    @Override
    public int hashCode() {
        return 31 * locationIndex + slotIndex;
    }
}
