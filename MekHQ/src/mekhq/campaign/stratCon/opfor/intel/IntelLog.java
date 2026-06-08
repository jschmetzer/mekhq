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

import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import megamek.logging.MMLogger;
import org.w3c.dom.Node;

/**
 * Campaign-level, cross-contract intelligence log. Accumulates records of
 * enemy units killed, captured, salvaged, or observed during contracts.
 *
 * <p>The log is append-only at runtime — entries are added by static-OpFor
 * fold-resolution hooks (slice 2) and never edited or removed. Persisted in
 * the campaign save via JAXB.</p>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "intelLog")
public class IntelLog {

    public static final String ROOT_XML_ELEMENT_NAME = "intelLog";

    private static final MMLogger LOGGER = MMLogger.create(IntelLog.class);

    @XmlElementWrapper(name = "entries")
    @XmlElement(name = "intelLogEntry")
    private List<IntelLogEntry> entries = new ArrayList<>();

    /** No-arg constructor required by JAXB. */
    public IntelLog() {
    }

    /**
     * Appends a new entry to the log. Caller is responsible for not creating
     * duplicates — this method does not deduplicate.
     */
    public void addEntry(final IntelLogEntry entry) {
        if (entry != null) {
            entries.add(entry);
        }
    }

    /**
     * Returns an unmodifiable view of all entries in insertion order.
     */
    public List<IntelLogEntry> getEntries() {
        return java.util.Collections.unmodifiableList(entries);
    }

    /**
     * Returns the count of entries by faction code.
     */
    public Map<String, Long> countByFaction() {
        // groupingBy rejects null keys; coalesce missing/blank to "UNKNOWN"
        // so a malformed or hand-edited save can't NPE the UI.
        return entries.stream()
                .collect(Collectors.groupingBy(
                        e -> (e.getFactionCode() == null || e.getFactionCode().isBlank())
                                ? "UNKNOWN" : e.getFactionCode(),
                        Collectors.counting()));
    }

    /**
     * Returns entries filtered by faction code.
     */
    public List<IntelLogEntry> entriesForFaction(final String factionCode) {
        if (factionCode == null) {
            return List.of();
        }
        return entries.stream()
                .filter(e -> factionCode.equals(e.getFactionCode()))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // JAXB round-trip via static helpers, matching the StratConCampaignState
    // pattern so the Campaign writer/reader can deal with us symmetrically.
    // -------------------------------------------------------------------------

    /**
     * Serializes this log to XML on the supplied writer.
     */
    public void Serialize(final PrintWriter pw) {
        try {
            JAXBContext ctx = JAXBContext.newInstance(IntelLog.class);
            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            marshaller.marshal(this, pw);
        } catch (Exception ex) {
            LOGGER.error("Failed to serialize IntelLog: {}", ex.getMessage());
        }
    }

    /**
     * Deserializes an {@link IntelLog} from the given XML node. Returns an
     * empty (but non-null) log if deserialisation fails.
     *
     * <p>Uses the two-argument {@code unmarshal(Source, Class)} overload so the
     * call is safe against future XML element renames — the result is always
     * a {@link JAXBElement} regardless of the element name in the document.</p>
     */
    public static IntelLog Deserialize(final Node xmlNode) {
        try {
            JAXBContext ctx = JAXBContext.newInstance(IntelLog.class);
            Unmarshaller unmarshaller = ctx.createUnmarshaller();
            return unmarshaller.unmarshal(xmlNode, IntelLog.class).getValue();
        } catch (Exception ex) {
            LOGGER.error("Failed to deserialize IntelLog: {}", ex.getMessage());
        }
        return new IntelLog();
    }

    /**
     * Deserializes an {@link IntelLog} from a raw XML string (used in tests).
     */
    public static IntelLog DeserializeFromString(final String xml) {
        try {
            JAXBContext ctx = JAXBContext.newInstance(IntelLog.class);
            Unmarshaller unmarshaller = ctx.createUnmarshaller();
            javax.xml.transform.stream.StreamSource source =
                    new javax.xml.transform.stream.StreamSource(new StringReader(xml));
            return unmarshaller.unmarshal(source, IntelLog.class).getValue();
        } catch (Exception ex) {
            LOGGER.error("Failed to deserialize IntelLog string: {}", ex.getMessage());
        }
        return new IntelLog();
    }
}
