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
package mekhq.campaign.stratCon.opfor;

import java.util.List;

import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;

/**
 * Generates sequential formation names using the NATO phonetic alphabet.
 *
 * <p>Names are produced in the order Alpha, Bravo, Charlie … Zulu.  Beyond
 * Zulu the generator falls back to numeric suffixes (27, 28, …).  The faction
 * category determines the suffix: "Star" for Clan, "Level II" for
 * ComStar/WoB, "Lance" for all others.</p>
 */
public class FormationNamer {

    private static final List<String> NATO_ALPHABET = List.of(
            "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot",
            "Golf", "Hotel", "India", "Juliet", "Kilo", "Lima",
            "Mike", "November", "Oscar", "Papa", "Quebec", "Romeo",
            "Sierra", "Tango", "Uniform", "Victor", "Whiskey",
            "X-ray", "Yankee", "Zulu"
    );

    private final Faction faction;
    private int counter = 0;

    /**
     * Public constructor that looks up the faction by faction code.
     *
     * <p>If the faction code is unrecognised the namer falls back to
     * Inner-Sphere Lance naming.</p>
     *
     * @param factionCode short faction code, e.g. "DC" or "CGB"
     */
    public FormationNamer(final String factionCode) {
        this(Factions.getInstance().getFaction(factionCode));
    }

    /**
     * Package-private constructor for tests — accepts a pre-built
     * {@link Faction} directly.
     *
     * @param faction the faction to use for suffix selection
     */
    FormationNamer(final Faction faction) {
        this.faction = faction;
    }

    /**
     * Returns the next formation name and advances the internal counter.
     *
     * <p>Within the NATO alphabet range (1–26) the ordinal word is used;
     * beyond that the numeric ordinal is used instead.</p>
     *
     * @return a unique formation name such as "Alpha Lance" or "Bravo Star"
     */
    public String nextFormationName() {
        String ordinal;
        if (counter < NATO_ALPHABET.size()) {
            ordinal = NATO_ALPHABET.get(counter);
        } else {
            ordinal = String.valueOf(counter + 1);
        }
        counter++;
        return ordinal + " " + suffix();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String suffix() {
        if ((faction != null) && faction.isClan()) {
            return "Star";
        } else if ((faction != null) && faction.isComStarOrWoB()) {
            return "Level II";
        } else {
            return "Lance";
        }
    }
}
