package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.habbohotel.pets.PetRace;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Set;

public class PetBreedsComposer extends MessageComposer {
    private final String petName;
    private final Set<PetRace> petRaces;

    public PetBreedsComposer(String petName, Set<PetRace> petRaces) {
        this.petName = petName;
        this.petRaces = petRaces;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.petRaces == null)
            return null;
        this.response.init(Outgoing.PetBreedsComposer);
        this.response.appendString(this.petName);
        this.response.appendInt(this.petRaces.size());
        for (PetRace race : this.petRaces) {
            this.response.appendInt(race.race);
            this.response.appendInt(race.colorOne);
            this.response.appendInt(race.colorTwo);
            this.response.appendBoolean(race.hasColorOne);
            this.response.appendBoolean(race.hasColorTwo);
        }
        return this.response;
    }

    public String getPetName() {
        return petName;
    }

    public Set<PetRace> getPetRaces() {
        return petRaces;
    }
}
