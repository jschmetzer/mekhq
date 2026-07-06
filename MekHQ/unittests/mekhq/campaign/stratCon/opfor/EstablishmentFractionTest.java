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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the core-battalion establishment metric on {@link StratConOpForRoster}
 * — {@code captureEstablishment}, {@code currentCoreLivingUnits},
 * {@code establishmentFraction}, and {@code coreFormations} — that drives the
 * morale-break mechanic. Count-based, so no {@code MekSummaryCache} is needed.
 */
class EstablishmentFractionTest {

    private static StratConOpForUnit unit(final Status status) {
        StratConOpForUnit unit = new StratConOpForUnit();
        unit.setStatus(status);
        return unit;
    }

    /** Adds a formation with the given militia/attachment flags and units to the roster. */
    private static StratConOpForFormation addFormation(final StratConOpForRoster roster,
            final boolean militia, final boolean attachment, final StratConOpForUnit... units) {
        StratConOpForFormation formation = new StratConOpForFormation();
        formation.setMilitia(militia);
        formation.setAttachment(attachment);
        for (StratConOpForUnit unit : units) {
            unit.setFormationId(formation.getId());
            roster.addUnit(unit);
            formation.getUnitIds().add(unit.getId());
        }
        roster.addFormation(formation);
        return formation;
    }

    @Test
    void legacyRoster_noEstablishmentRecorded_fractionIsOne() {
        StratConOpForRoster roster = new StratConOpForRoster();
        addFormation(roster, false, false, unit(Status.READY), unit(Status.READY));
        // captureEstablishment deliberately NOT called (legacy save)
        assertEquals(1.0, roster.establishmentFraction(),
                "A roster with no recorded establishment must never read as broken");
    }

    @Test
    void fullStrength_afterCapture_fractionIsOne() {
        StratConOpForRoster roster = new StratConOpForRoster();
        addFormation(roster, false, false,
                unit(Status.READY), unit(Status.READY), unit(Status.READY), unit(Status.READY));
        roster.captureEstablishment();
        assertEquals(4, roster.getEstablishmentLineUnits());
        assertEquals(1.0, roster.establishmentFraction());
    }

    @Test
    void halfCoreDestroyed_fractionIsOneHalf() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForUnit a = unit(Status.READY);
        StratConOpForUnit b = unit(Status.READY);
        addFormation(roster, false, false, a, b, unit(Status.READY), unit(Status.READY));
        roster.captureEstablishment();
        a.setStatus(Status.DESTROYED);
        b.setStatus(Status.DESTROYED);
        assertEquals(0.5, roster.establishmentFraction());
    }

    @Test
    void attachments_excludedFromEstablishmentAndFraction() {
        StratConOpForRoster roster = new StratConOpForRoster();
        StratConOpForUnit core1 = unit(Status.READY);
        StratConOpForUnit core2 = unit(Status.READY);
        addFormation(roster, false, false, core1, core2);
        roster.captureEstablishment(); // establishment = 2 core units
        assertEquals(2, roster.getEstablishmentLineUnits());

        // A reinforcing lance attaches later
        StratConOpForUnit att1 = unit(Status.READY);
        StratConOpForUnit att2 = unit(Status.READY);
        addFormation(roster, false, true, att1, att2);
        assertEquals(1.0, roster.establishmentFraction(),
                "Attachments must not raise the core battalion's strength");

        att1.setStatus(Status.DESTROYED);
        att2.setStatus(Status.DESTROYED);
        assertEquals(1.0, roster.establishmentFraction(),
                "Losing an attachment must not move the core battalion's break metric");

        core1.setStatus(Status.DESTROYED);
        assertEquals(0.5, roster.establishmentFraction(),
                "Losing a core unit must move the fraction");
    }

    @Test
    void militia_excludedFromEstablishment() {
        StratConOpForRoster roster = new StratConOpForRoster();
        addFormation(roster, false, false, unit(Status.READY), unit(Status.READY)); // core
        addFormation(roster, true, false, unit(Status.READY), unit(Status.READY));  // militia
        roster.captureEstablishment();
        assertEquals(2, roster.getEstablishmentLineUnits(),
                "Militia are not part of the core battalion's establishment");
    }

    @Test
    void establishmentAndWavering_surviveJaxbRoundTrip() {
        // A save/load that dropped these fields would silently disable the break
        // mechanic (establishment 0 -> never breaks) or lose the wavering state.
        StratConOpForRoster roster = new StratConOpForRoster();
        addFormation(roster, false, false,
                unit(Status.READY), unit(Status.READY), unit(Status.READY));
        roster.captureEstablishment(); // establishment = 3
        roster.setWavering(true);

        StratConOpForRoster restored = roster.copy(); // marshal -> unmarshal round-trip

        assertEquals(3, restored.getEstablishmentLineUnits(),
                "establishmentLineUnits must survive save/load");
        assertTrue(restored.isWavering(), "wavering must survive save/load");
    }

    @Test
    void coreFormations_excludesMilitiaAndAttachments() {
        StratConOpForRoster roster = new StratConOpForRoster();
        addFormation(roster, false, false, unit(Status.READY)); // core
        addFormation(roster, true, false, unit(Status.READY));  // militia
        addFormation(roster, false, true, unit(Status.READY));  // attachment
        assertEquals(1, roster.coreFormations().size());
    }
}
