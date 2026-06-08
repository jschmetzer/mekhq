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
package mekhq.campaign.campaignOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import megamek.Version;
import mekhq.utilities.MHQXMLUtility;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Tests for the static OpFor campaign options added in {@link CampaignOptions}.
 */
class CampaignOptionsTest {

    /**
     * Verifies that the three static OpFor options have the expected default values
     * immediately after construction.
     */
    @Test
    void staticOpForDefaults() {
        CampaignOptions options = new CampaignOptions();

        assertFalse(options.isUseStaticOpForRoster(),
              "useStaticOpForRoster should default to false");
        assertEquals(1.25, options.getStaticOpForPaddingFactor(), 1e-9,
              "staticOpForPaddingFactor should default to 1.25");
        assertEquals(3, options.getStaticOpForFormationCountFloor(),
              "staticOpForFormationCountFloor should default to 3");
    }

    /**
     * Verifies that {@code staticOpForPaddingFactor} survives a marshal → unmarshal round-trip.
     */
    @Test
    void staticOpForPaddingFactorRoundTrips() throws Exception {
        // Arrange
        CampaignOptions options = new CampaignOptions();
        options.setStaticOpForPaddingFactor(1.75);

        StringWriter stringWriter = new StringWriter();
        PrintWriter pw = new PrintWriter(stringWriter);
        CampaignOptionsMarshaller.writeCampaignOptionsToXML(options, pw, 0);
        pw.flush();
        String xml = stringWriter.toString();

        // Act
        Document doc = MHQXMLUtility.newSafeDocumentBuilder()
              .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Node root = doc.getDocumentElement();
        CampaignOptions restored = CampaignOptionsUnmarshaller.generateCampaignOptionsFromXml(
              root, new Version("0.50.0"));

        // Assert
        assertEquals(1.75, restored.getStaticOpForPaddingFactor(), 1e-9,
              "staticOpForPaddingFactor must survive XML round-trip");
    }

    /**
     * Verifies that {@code staticOpForFormationCountFloor} survives a marshal → unmarshal round-trip.
     */
    @Test
    void staticOpForFormationCountFloorRoundTrips() throws Exception {
        // Arrange
        CampaignOptions options = new CampaignOptions();
        options.setStaticOpForFormationCountFloor(7);

        StringWriter stringWriter = new StringWriter();
        PrintWriter pw = new PrintWriter(stringWriter);
        CampaignOptionsMarshaller.writeCampaignOptionsToXML(options, pw, 0);
        pw.flush();
        String xml = stringWriter.toString();

        // Act
        Document doc = MHQXMLUtility.newSafeDocumentBuilder()
              .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Node root = doc.getDocumentElement();
        CampaignOptions restored = CampaignOptionsUnmarshaller.generateCampaignOptionsFromXml(
              root, new Version("0.50.0"));

        // Assert
        assertEquals(7, restored.getStaticOpForFormationCountFloor(),
              "staticOpForFormationCountFloor must survive XML round-trip");
    }

    /**
     * Verifies that {@code useStaticOpForRoster} survives a marshal → unmarshal round-trip.
     */
    @Test
    void useStaticOpForRosterRoundTrips() throws Exception {
        // Arrange
        CampaignOptions options = new CampaignOptions();
        options.setUseStaticOpForRoster(true);

        StringWriter stringWriter = new StringWriter();
        PrintWriter pw = new PrintWriter(stringWriter);
        CampaignOptionsMarshaller.writeCampaignOptionsToXML(options, pw, 0);
        pw.flush();
        String xml = stringWriter.toString();

        // Act
        Document doc = MHQXMLUtility.newSafeDocumentBuilder()
              .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Node root = doc.getDocumentElement();
        CampaignOptions restored = CampaignOptionsUnmarshaller.generateCampaignOptionsFromXml(
              root, new Version("0.50.0"));

        // Assert
        assertEquals(true, restored.isUseStaticOpForRoster(),
              "useStaticOpForRoster must survive XML round-trip");
    }
}
