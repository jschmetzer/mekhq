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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import mekhq.campaign.stratCon.StratConCampaignState;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Verifies that {@link StratConCampaignState} round-trips correctly through
 * JAXB serialisation / deserialisation when a {@link StratConOpForRoster} is
 * attached — and that the legacy null-roster path is preserved.
 */
class StratConCampaignStateJaxbTest {

    /**
     * Serialises the given state to an XML string and then deserialises it back.
     * Uses direct JAXB calls (not the production Serialize method) so that any
     * exceptions surface clearly during tests.
     */
    private static StratConCampaignState roundTrip(final StratConCampaignState state)
            throws Exception {
        JAXBContext context = JAXBContext.newInstance(StratConCampaignState.class);
        JAXBElement<StratConCampaignState> element = new JAXBElement<>(
                new QName(StratConCampaignState.ROOT_XML_ELEMENT_NAME),
                StratConCampaignState.class,
                state);
        Marshaller m = context.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter sw = new StringWriter();
        m.marshal(element, sw);
        String xml = sw.toString();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Node root = doc.getDocumentElement();
        return StratConCampaignState.Deserialize(root);
    }

    @Test
    void emptyRosterRoundTripsToEqualState() throws Exception {
        StratConCampaignState state = new StratConCampaignState();
        state.setOpForRoster(new StratConOpForRoster());

        StratConCampaignState restored = roundTrip(state);

        assertNotNull(restored, "Deserialised state must not be null");
        assertNotNull(restored.getOpForRoster(),
                "Deserialised state should carry a non-null roster");
        assertFalse(restored.getOpForRoster().isEliminated(),
                "Empty roster should not report isEliminated()");
    }

    @Test
    void nullRosterDeserializesToNull() throws Exception {
        StratConCampaignState state = new StratConCampaignState();
        // deliberately do NOT set a roster

        StratConCampaignState restored = roundTrip(state);

        assertNotNull(restored, "Deserialised state must not be null");
        assertNull(restored.getOpForRoster(),
                "Legacy save with no roster element should deserialise to null");
    }
}
