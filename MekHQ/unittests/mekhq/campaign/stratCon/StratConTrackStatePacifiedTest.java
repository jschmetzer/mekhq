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
package mekhq.campaign.stratCon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * Tests for the {@code pacified} flag on {@link StratConTrackState}.
 *
 * <p>Verifies the default value, setter behaviour, and JAXB round-trip preservation.</p>
 */
class StratConTrackStatePacifiedTest {

    private static StratConTrackState roundTrip(final StratConTrackState track) throws Exception {
        JAXBContext context = JAXBContext.newInstance(StratConTrackState.class);
        JAXBElement<StratConTrackState> element = new JAXBElement<>(
                new QName(StratConTrackState.ROOT_XML_ELEMENT_NAME),
                StratConTrackState.class,
                track);
        Marshaller m = context.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter sw = new StringWriter();
        m.marshal(element, sw);
        String xml = sw.toString();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Unmarshaller u = context.createUnmarshaller();
        JAXBElement<StratConTrackState> restored = u.unmarshal(
                doc.getDocumentElement(), StratConTrackState.class);
        return restored.getValue();
    }

    @Test
    void pacifiedDefaultsFalse() {
        StratConTrackState track = new StratConTrackState();
        assertFalse(track.isPacified(),
                "A freshly created StratConTrackState must have pacified == false by default");
    }

    @Test
    void setterFlipsValue() {
        StratConTrackState track = new StratConTrackState();
        track.setPacified(true);
        assertTrue(track.isPacified(),
                "setPacified(true) must result in isPacified() returning true");
        track.setPacified(false);
        assertFalse(track.isPacified(),
                "setPacified(false) must result in isPacified() returning false");
    }

    @Test
    void pacifiedTrueRoundTripsViaJaxb() throws Exception {
        StratConTrackState track = new StratConTrackState();
        track.setDisplayableName("Alpha");
        track.setPacified(true);

        StratConTrackState restored = roundTrip(track);

        assertNotNull(restored, "Round-tripped track must not be null");
        assertTrue(restored.isPacified(),
                "pacified=true must survive a JAXB marshal/unmarshal round-trip");
    }
}
