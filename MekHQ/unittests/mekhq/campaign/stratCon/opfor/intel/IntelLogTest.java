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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IntelLogTest {

    // ------------------------------------------------------------------
    // 1. Empty log has no entries
    // ------------------------------------------------------------------
    @Test
    void emptyLog_hasNoEntries() {
        IntelLog log = new IntelLog();
        assertTrue(log.getEntries().isEmpty());
    }

    // ------------------------------------------------------------------
    // 2. addEntry appends in insertion order
    // ------------------------------------------------------------------
    @Test
    void addEntry_appendsInOrder() {
        IntelLog log = new IntelLog();
        LocalDate date = LocalDate.of(3025, 6, 1);

        IntelLogEntry first = new IntelLogEntry(
                "PIR", "Contract A", date, "Pilot One", "Atlas", "AS7-D",
                IntelLogEntry.Outcome.KILLED);
        IntelLogEntry second = new IntelLogEntry(
                "DC", "Contract B", date, "Pilot Two", "Marauder", "MAD-3R",
                IntelLogEntry.Outcome.OBSERVED);
        IntelLogEntry third = new IntelLogEntry(
                "FS", "Contract C", date, "Pilot Three", "Hunchback", "HBK-4G",
                IntelLogEntry.Outcome.SALVAGED);

        log.addEntry(first);
        log.addEntry(second);
        log.addEntry(third);

        List<IntelLogEntry> entries = log.getEntries();
        assertEquals(3, entries.size());
        assertEquals("Atlas", entries.get(0).getChassis());
        assertEquals("Marauder", entries.get(1).getChassis());
        assertEquals("Hunchback", entries.get(2).getChassis());
    }

    // ------------------------------------------------------------------
    // 3. addEntry(null) is ignored safely
    // ------------------------------------------------------------------
    @Test
    void addEntry_nullEntry_ignoredSafely() {
        IntelLog log = new IntelLog();
        log.addEntry(null);
        assertTrue(log.getEntries().isEmpty());
    }

    // ------------------------------------------------------------------
    // 4. countByFaction groups correctly
    // ------------------------------------------------------------------
    @Test
    void countByFaction_groupsCorrectly() {
        IntelLog log = new IntelLog();
        LocalDate date = LocalDate.of(3028, 3, 10);

        for (int i = 0; i < 3; i++) {
            log.addEntry(new IntelLogEntry(
                    "PIR", "Raid " + i, date, null, "Locust", "LCT-1V",
                    IntelLogEntry.Outcome.KILLED));
        }
        for (int i = 0; i < 2; i++) {
            log.addEntry(new IntelLogEntry(
                    "DC", "Skirmish " + i, date, null, "Jenner", "JR7-D",
                    IntelLogEntry.Outcome.OBSERVED));
        }

        Map<String, Long> counts = log.countByFaction();
        assertEquals(2, counts.size());
        assertEquals(Long.valueOf(3), counts.get("PIR"));
        assertEquals(Long.valueOf(2), counts.get("DC"));
    }

    // ------------------------------------------------------------------
    // 5. entriesForFaction filters correctly
    // ------------------------------------------------------------------
    @Test
    void entriesForFaction_filtersCorrectly() {
        IntelLog log = new IntelLog();
        LocalDate date = LocalDate.of(3035, 7, 4);

        log.addEntry(new IntelLogEntry("PIR", "Op Alpha", date, "Ace", "Vulture", "Mad Cat",
                IntelLogEntry.Outcome.KILLED));
        log.addEntry(new IntelLogEntry("DC", "Op Beta", date, "Bravo", "Catapult", "CPLT-C1",
                IntelLogEntry.Outcome.SALVAGED));
        log.addEntry(new IntelLogEntry("PIR", "Op Gamma", date, "Charlie", "Rifleman", "RFL-3N",
                IntelLogEntry.Outcome.CAPTURED));
        log.addEntry(new IntelLogEntry("FS", "Op Delta", date, "Delta", "Zeus", "ZEU-6S",
                IntelLogEntry.Outcome.OBSERVED));

        List<IntelLogEntry> pirEntries = log.entriesForFaction("PIR");
        assertEquals(2, pirEntries.size());
        assertTrue(pirEntries.stream().allMatch(e -> "PIR".equals(e.getFactionCode())));
    }

    // ------------------------------------------------------------------
    // 6. entriesForFaction(null) returns empty list
    // ------------------------------------------------------------------
    @Test
    void entriesForFaction_nullFaction_returnsEmptyList() {
        IntelLog log = new IntelLog();
        log.addEntry(new IntelLogEntry(
                "PIR", "Contract", LocalDate.of(3025, 1, 1), null, "Locust", "LCT-1V",
                IntelLogEntry.Outcome.OBSERVED));

        List<IntelLogEntry> result = log.entriesForFaction(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // 7. Serialize then deserialize round-trips all fields
    // ------------------------------------------------------------------
    @Test
    void serialize_thenDeserialize_roundTripsAllFields() {
        IntelLog original = new IntelLog();
        LocalDate when = LocalDate.of(3030, 1, 15);
        IntelLogEntry entry = new IntelLogEntry(
                "PIR", "The Phantoms", when,
                "Captain Vraal", "Marauder", "MAD-3R",
                IntelLogEntry.Outcome.KILLED);
        original.addEntry(entry);

        StringWriter sw = new StringWriter();
        original.Serialize(new PrintWriter(sw));
        IntelLog restored = IntelLog.DeserializeFromString(sw.toString());

        assertNotNull(restored);
        assertEquals(1, restored.getEntries().size());
        IntelLogEntry restoredEntry = restored.getEntries().get(0);
        assertEquals("PIR", restoredEntry.getFactionCode());
        assertEquals("The Phantoms", restoredEntry.getContractName());
        assertEquals(when, restoredEntry.getDate());
        assertEquals("Captain Vraal", restoredEntry.getPilotName());
        assertEquals("Marauder", restoredEntry.getChassis());
        assertEquals("MAD-3R", restoredEntry.getModel());
        assertEquals(IntelLogEntry.Outcome.KILLED, restoredEntry.getOutcome());
    }

    // ------------------------------------------------------------------
    // 8. Malformed XML returns empty log, does not throw
    // ------------------------------------------------------------------
    @Test
    void deserializeMalformedXml_returnsEmptyLog() {
        IntelLog result = IntelLog.DeserializeFromString("not valid xml");
        assertNotNull(result);
        assertTrue(result.getEntries().isEmpty());
    }

    // ------------------------------------------------------------------
    // 9. Empty log round-trips to empty log
    // ------------------------------------------------------------------
    @Test
    void emptyLog_serialize_thenDeserialize_isEmpty() {
        IntelLog original = new IntelLog();

        StringWriter sw = new StringWriter();
        original.Serialize(new PrintWriter(sw));
        IntelLog restored = IntelLog.DeserializeFromString(sw.toString());

        assertNotNull(restored);
        assertTrue(restored.getEntries().isEmpty());
    }
}
