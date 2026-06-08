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
import megamek.common.annotations.Nullable;

/**
 * Identifies the MegaMek unit type for a static OpFor unit record.
 *
 * <p>The chassis + model pair is used to look up the unit in {@code MekSummaryCache}.
 * {@code customLoadoutXml} is reserved for future variants that diverge from the
 * default load-out baked into the cache.</p>
 */
public class UnitTemplate {

    @XmlElement
    private String chassis;

    @XmlElement
    private String model;

    @XmlElement
    private String factionCode;

    @XmlElement
    private String customLoadoutXml;

    /** No-arg constructor required by JAXB. */
    public UnitTemplate() {
    }

    /**
     * Constructs a {@code UnitTemplate} with the three required fields.
     *
     * @param chassis     the chassis name (e.g., "Atlas")
     * @param model       the model variant (e.g., "AS7-D")
     * @param factionCode the faction short code (e.g., "DC")
     */
    public UnitTemplate(final String chassis, final String model, final String factionCode) {
        this.chassis = chassis;
        this.model = model;
        this.factionCode = factionCode;
    }

    public String getChassis() {
        return chassis;
    }

    public void setChassis(final String chassis) {
        this.chassis = chassis;
    }

    public String getModel() {
        return model;
    }

    public void setModel(final String model) {
        this.model = model;
    }

    public String getFactionCode() {
        return factionCode;
    }

    public void setFactionCode(final String factionCode) {
        this.factionCode = factionCode;
    }

    public @Nullable String getCustomLoadoutXml() {
        return customLoadoutXml;
    }

    public void setCustomLoadoutXml(@Nullable final String customLoadoutXml) {
        this.customLoadoutXml = customLoadoutXml;
    }
}
