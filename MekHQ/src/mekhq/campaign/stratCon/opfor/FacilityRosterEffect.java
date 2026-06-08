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

import mekhq.campaign.stratCon.StratConFacility.FacilityType;

/**
 * Lookup table mapping {@link FacilityType} to roster-size deltas applied when
 * the facility changes hands.
 *
 * <p>Each entry has two effect rows — one for player-capture (Hostile → Allied
 * ownership) and one for player-loss (Allied → Hostile ownership). Each row is
 * a signed pair: positive = roster grows by that many formations on the track,
 * negative = roster loses that many formations on the track (random selection,
 * preferring formations with the most living units to maximize impact).</p>
 *
 * <p>{@link FacilityType#OrbitalDefense} has no ground-roster effect — that
 * facility is space-only and not engaged with the player's ground forces.</p>
 */
public final class FacilityRosterEffect {

    /** Delta applied to a roster when a facility changes hands. */
    public record Effect(int enemyDelta, int allyDelta) {

        /** No effect — used for OrbitalDefense and unrecognised facility types. */
        public static final Effect NONE = new Effect(0, 0);

        /** Returns true if any roster changes. */
        public boolean isAnyChange() {
            return enemyDelta != 0 || allyDelta != 0;
        }
    }

    private FacilityRosterEffect() {
    }

    /**
     * Returns the effect of the player capturing a hostile facility.
     *
     * @param type the facility's type
     * @return the effect (never null; may be {@link Effect#NONE})
     */
    public static Effect onPlayerCapture(final FacilityType type) {
        if (type == null) {
            return Effect.NONE;
        }
        return switch (type) {
            case CommandCenter -> new Effect(-3, +1);
            case BaseOfOperations -> new Effect(-2, 0);
            case MekBase -> new Effect(-2, 0);
            case DataCenter -> new Effect(-2, 0);
            case IndustrialFacility -> new Effect(-2, 0);
            case TankBase, AirBase, ArtilleryBase,
                 EarlyWarningSystem, SpacePort -> new Effect(-1, 0);
            case OrbitalDefense -> Effect.NONE;
        };
    }

    /**
     * Returns the effect of the player losing a friendly facility (Allied →
     * Hostile transition).
     *
     * @param type the facility's type
     * @return the effect (never null; may be {@link Effect#NONE})
     */
    public static Effect onPlayerLoss(final FacilityType type) {
        if (type == null) {
            return Effect.NONE;
        }
        return switch (type) {
            case CommandCenter -> new Effect(+2, -3);
            case BaseOfOperations -> new Effect(0, -2);
            case MekBase -> new Effect(+2, 0);
            case DataCenter, IndustrialFacility,
                 TankBase, AirBase, ArtilleryBase,
                 EarlyWarningSystem -> new Effect(+1, 0);
            case SpacePort -> new Effect(0, -1);
            case OrbitalDefense -> Effect.NONE;
        };
    }
}
