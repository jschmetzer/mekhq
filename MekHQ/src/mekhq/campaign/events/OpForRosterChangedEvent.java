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
package mekhq.campaign.events;

import megamek.common.annotations.Nullable;
import megamek.common.event.MMEvent;
import mekhq.campaign.stratCon.StratConTrackState;

/**
 * MekHQ event fired after the static OpFor <em>or allied</em> roster is updated — by a
 * scenario resolution that destroyed units, or by a morale-driven reinforcement event
 * (OpFor v1.1 / Ally v1.5).
 *
 * <p>Subscribers (e.g. {@code OpForRosterPanel}, {@code StratConTab}) should treat this
 * as a generic "static roster mutated" signal and refresh both the OpFor and ally UI
 * surfaces; the {@link StratConTrackState} payload identifies which track the change
 * applies to. Despite the name, this event covers both rosters — renaming would require
 * a broader event-bus refactor.</p>
 */
public class OpForRosterChangedEvent extends MMEvent {

    private final StratConTrackState track;

    /**
     * Creates an event for the given track.
     *
     * @param track the track whose roster changed; may be {@code null} if the track
     *              could not be resolved at fire-time
     */
    public OpForRosterChangedEvent(final @Nullable StratConTrackState track) {
        super();
        this.track = track;
    }

    /**
     * Returns the track associated with the roster change.
     *
     * @return the track, or {@code null} if unknown
     */
    public @Nullable StratConTrackState getTrack() {
        return track;
    }
}
