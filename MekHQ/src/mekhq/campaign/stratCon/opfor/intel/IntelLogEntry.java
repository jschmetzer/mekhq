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
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.campaign.stratCon.opfor.intel;

import java.time.LocalDate;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import megamek.common.annotations.Nullable;

/**
 * A single accumulated-intelligence record produced when an enemy unit is
 * killed, captured, salvaged, or otherwise observed during contract play.
 *
 * <p>Intel log entries are persisted in the campaign save and survive across
 * contract boundaries — that's the whole point. Records are append-only;
 * the intel log doesn't allow editing or deletion.</p>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "intelLogEntry")
public class IntelLogEntry {

    /** What the player observed about this unit. */
    public enum Outcome {
        /** Enemy unit destroyed in combat. */
        KILLED,
        /** Enemy unit captured intact (Mek salvaged + pilot prisoner). */
        CAPTURED,
        /** Enemy unit salvaged (Mek recovered; pilot status separate). */
        SALVAGED,
        /** Enemy unit observed at FULL_INTEL but not killed/captured. */
        OBSERVED
    }

    @XmlElement
    private String factionCode;

    @XmlElement
    private String contractName;

    /** ISO-8601 string ({@link LocalDate#toString()}) to keep JAXB happy. */
    @XmlElement(name = "date")
    private String dateIso;

    @XmlElement
    private String pilotName;

    @XmlElement
    private String chassis;

    @XmlElement
    private String model;

    @XmlElement
    private Outcome outcome;

    /** No-arg constructor required by JAXB. */
    public IntelLogEntry() {
    }

    public IntelLogEntry(final String factionCode,
            final String contractName,
            final LocalDate date,
            final @Nullable String pilotName,
            final String chassis,
            final @Nullable String model,
            final Outcome outcome) {
        this.factionCode = factionCode;
        this.contractName = contractName;
        this.dateIso = (date == null) ? null : date.toString();
        this.pilotName = pilotName;
        this.chassis = chassis;
        this.model = model;
        this.outcome = outcome;
    }

    public String getFactionCode() {
        return factionCode;
    }

    public String getContractName() {
        return contractName;
    }

    public @Nullable LocalDate getDate() {
        if (dateIso == null || dateIso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateIso);
        } catch (java.time.format.DateTimeParseException ex) {
            // Malformed date in a hand-edited / corrupted save — log and degrade.
            return null;
        }
    }

    @XmlTransient
    public @Nullable String getDateIso() {
        return dateIso;
    }

    public @Nullable String getPilotName() {
        return pilotName;
    }

    public String getChassis() {
        return chassis;
    }

    public @Nullable String getModel() {
        return model;
    }

    public Outcome getOutcome() {
        return outcome;
    }
}
