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

import java.util.UUID;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import megamek.common.annotations.Nullable;

/**
 * A single named unit in a static OpFor roster.
 *
 * <p>Each unit has a stable {@link UUID} identifier so that damage and status
 * transitions persist across scenarios for the life of the contract.  The
 * {@link #pilotPersistentId} is written onto the MegaMek {@code Crew}'s external
 * ID when the unit is materialised; this allows Phase 6 to reconcile captured
 * pilots back to their roster record.</p>
 */
@XmlRootElement(name = "opForUnit")
public class StratConOpForUnit {

    @XmlElement
    private UUID id = UUID.randomUUID();

    @XmlElement
    private UnitTemplate protoEntity;

    @XmlElement
    private String pilotName;

    @XmlElement
    private int gunnery;

    @XmlElement
    private int piloting;

    @XmlElement
    private UUID pilotPersistentId;

    @XmlElement
    private UUID formationId;

    @XmlElement
    private Status status = Status.READY;

    @XmlElement
    private boolean revealed = false;

    @XmlElement
    private PersistentDamageState persistentDamage = new PersistentDamageState();

    /**
     * Tracks which scenario this unit was last deployed in.
     * Used by {@code foldResolutionInto} to filter to units present in
     * the resolved scenario rather than the whole roster.
     */
    @XmlElement
    private UUID lastDeployedScenarioId;

    /** No-arg constructor required by JAXB. */
    public StratConOpForUnit() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UnitTemplate getProtoEntity() {
        return protoEntity;
    }

    public void setProtoEntity(final UnitTemplate protoEntity) {
        this.protoEntity = protoEntity;
    }

    public String getPilotName() {
        return pilotName;
    }

    public void setPilotName(final String pilotName) {
        this.pilotName = pilotName;
    }

    public int getGunnery() {
        return gunnery;
    }

    public void setGunnery(final int gunnery) {
        this.gunnery = gunnery;
    }

    public int getPiloting() {
        return piloting;
    }

    public void setPiloting(final int piloting) {
        this.piloting = piloting;
    }

    public UUID getPilotPersistentId() {
        return pilotPersistentId;
    }

    public void setPilotPersistentId(final UUID pilotPersistentId) {
        this.pilotPersistentId = pilotPersistentId;
    }

    public UUID getFormationId() {
        return formationId;
    }

    public void setFormationId(final UUID formationId) {
        this.formationId = formationId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(final boolean revealed) {
        this.revealed = revealed;
    }

    public PersistentDamageState getPersistentDamage() {
        return persistentDamage;
    }

    public void setPersistentDamage(final PersistentDamageState persistentDamage) {
        this.persistentDamage = persistentDamage;
    }

    public @Nullable UUID getLastDeployedScenarioId() {
        return lastDeployedScenarioId;
    }

    public void setLastDeployedScenarioId(@Nullable final UUID lastDeployedScenarioId) {
        this.lastDeployedScenarioId = lastDeployedScenarioId;
    }
}
