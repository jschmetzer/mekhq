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
package mekhq.campaign.stratCon.opfor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StratConOpForFormation}, focused on the {@code attachment}
 * flag that separates reinforcing lances from the original core battalion.
 */
class StratConOpForFormationTest {

    @Test
    void attachment_defaultsFalse() {
        assertFalse(new StratConOpForFormation().isAttachment(),
                "A freshly-built formation is part of the core battalion, not an attachment");
    }

    @Test
    void attachment_setterRoundTrips() {
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setAttachment(true);
        assertTrue(formation.isAttachment());
    }

    @Test
    void attachment_survivesJaxbRoundTrip() throws Exception {
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setName("Bravo Lance");
        formation.setAttachment(true);

        JAXBContext context = JAXBContext.newInstance(StratConOpForFormation.class);
        StringWriter writer = new StringWriter();
        Marshaller marshaller = context.createMarshaller();
        marshaller.marshal(formation, writer);

        StratConOpForFormation restored = (StratConOpForFormation) context.createUnmarshaller()
                .unmarshal(new StringReader(writer.toString()));

        assertTrue(restored.isAttachment(),
                "The attachment flag must survive save/load, or the establishment math corrupts on reload");
    }
}
