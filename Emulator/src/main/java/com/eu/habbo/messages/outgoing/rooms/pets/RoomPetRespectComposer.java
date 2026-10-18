package com.eu.habbo.messages.outgoing.rooms.pets;

import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomPetRespectComposer extends MessageComposer {
    public static final int PET_RESPECTED = 1;
    public static final int PET_TREATED = 2;

    private final Pet pet;
    private final int type;

    public RoomPetRespectComposer(Pet pet) {
        this.pet = pet;
        this.type = 1;
    }

    public RoomPetRespectComposer(Pet pet, int type) {
        this.pet = pet;
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomPetRespectComposer);
        // Habbo's notification carries the pet's respect count and its owner, then the pet; whether
        // it was a respect or a treat the client tells from the pet type.
        this.response.appendInt(this.pet.getRespect());
        this.response.appendInt(this.pet.getUserId());
        this.pet.serialize(this.response);
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }

    public int getType() {
        return type;
    }
}
