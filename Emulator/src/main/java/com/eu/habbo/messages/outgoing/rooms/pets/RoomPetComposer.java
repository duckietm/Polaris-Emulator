package com.eu.habbo.messages.outgoing.rooms.pets;

import com.eu.habbo.habbohotel.pets.*;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collection;
import java.util.Collections;

public class RoomPetComposer extends MessageComposer {
    private final Collection<Pet> pets;

    public RoomPetComposer(Pet pet) {
        this.pets = Collections.singletonList(pet);
    }

    public RoomPetComposer(Collection<Pet> pets) {
        this.pets = pets;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUsersComposer);
        this.response.appendInt(this.pets.size());
        for (Pet pet : this.pets) {
            this.serializePet(pet);
        }
        return this.response;
    }

    private void serializePet(Pet pet) {
        this.response.appendInt(pet.getId());
        this.response.appendString(pet.getName());
        this.response.appendString("");
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        if (pet instanceof IPetLook) {
            this.response.appendString(((IPetLook) pet).getLook());
        } else {
            this.response.appendString(pet.getPetData().getType() + " " + pet.getRace() + " " + pet.getColor() + " " + ((pet instanceof HorsePet ? (((HorsePet) pet).hasSaddle() ? "3" : "2") + " 2 " + ((HorsePet) pet).getHairStyle() + " " + ((HorsePet) pet).getHairColor() + " 3 " + ((HorsePet) pet).getHairStyle() + " " + ((HorsePet) pet).getHairColor() + (((HorsePet) pet).hasSaddle() ? " 4 9 0" : "") : pet instanceof MonsterplantPet ? (((MonsterplantPet) pet).look.isEmpty() ? "2 1 8 6 0 -1 -1" : ((MonsterplantPet) pet).look) : "2 2 -1 0 3 -1 0")));
        }
        this.response.appendInt(pet.getRoomUnit().getId());
        this.response.appendInt(pet.getRoomUnit().getX());
        this.response.appendInt(pet.getRoomUnit().getY());
        this.response.appendString(pet.getRoomUnit().getZ() + "");
        this.response.appendInt(0);
        this.response.appendInt(2);
        this.response.appendInt(pet.getPetData().getType());
        this.response.appendInt(pet.getUserId());
        this.response.appendString(pet.getRoom().getFurniOwnerNames().get(pet.getUserId()));
        this.response.appendInt(pet instanceof MonsterplantPet ? ((MonsterplantPet) pet).getRarity() : 1);
        this.response.appendBoolean(pet instanceof RideablePet && ((RideablePet) pet).hasSaddle());
        this.response.appendBoolean(false);
        this.response.appendBoolean((pet instanceof MonsterplantPet && ((MonsterplantPet) pet).canBreed())); //Has breeasasd//
        this.response.appendBoolean(!(pet instanceof MonsterplantPet && ((MonsterplantPet) pet).isFullyGrown())); //unknown 1
        this.response.appendBoolean(pet instanceof MonsterplantPet && ((MonsterplantPet) pet).isDead()); //Can revive
        this.response.appendBoolean(pet instanceof MonsterplantPet && ((MonsterplantPet) pet).isPubliclyBreedable()); //Breedable checkbox //Toggle breeding permission
        this.response.appendInt(pet instanceof MonsterplantPet ? ((MonsterplantPet) pet).getGrowthStage() : pet.getLevel());
        this.response.appendString("");
        this.response.appendString("unknown");
        this.response.appendInt(0);
        this.response.appendInt(0);
    }
}
