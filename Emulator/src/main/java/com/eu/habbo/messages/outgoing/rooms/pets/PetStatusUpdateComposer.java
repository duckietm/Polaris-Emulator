package com.eu.habbo.messages.outgoing.rooms.pets;

import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class PetStatusUpdateComposer extends MessageComposer {
    private final Pet pet;

    public PetStatusUpdateComposer(Pet pet) {
        this.pet = pet;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetStatusUpdateComposer);
        this.response.appendInt(this.pet.getRoomUnit().getId());
        this.response.appendInt(this.pet instanceof RideablePet ridablePet && ridablePet.anyoneCanRide() ? 1 : 0);
        this.response.appendBoolean((this.pet instanceof MonsterplantPet monsterplantPet && monsterplantPet.canBreed())); //unknown 1
        this.response.appendBoolean((this.pet instanceof MonsterplantPet monsterplantPet && !monsterplantPet.isFullyGrown()));
        this.response.appendBoolean(this.pet instanceof MonsterplantPet monsterplantPet && monsterplantPet.isDead()); //State Grown
        this.response.appendBoolean(this.pet instanceof MonsterplantPet monsterplantPet && monsterplantPet.isPubliclyBreedable());
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }
}
