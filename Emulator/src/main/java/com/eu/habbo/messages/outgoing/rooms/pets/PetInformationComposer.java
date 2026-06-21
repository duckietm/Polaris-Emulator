package com.eu.habbo.messages.outgoing.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class PetInformationComposer extends MessageComposer {
    private final Pet pet;
    private final Room room;
    private final Habbo requestingHabbo;

    public PetInformationComposer(Pet pet, Room room, Habbo requestingHabbo) {
        this.pet = pet;
        this.room = room;
        this.requestingHabbo = requestingHabbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        double days = Math.floor((Emulator.getIntUnixTimestamp() - this.pet.getCreated()) / (3600 * 24));
        this.response.init(Outgoing.PetInformationComposer);
        this.response.appendInt(this.pet.getId());
        this.response.appendString(this.pet.getName());
        if (this.pet instanceof MonsterplantPet monsterplantPet) {
            this.response.appendInt(monsterplantPet.getGrowthStage()); //This equal
            this.response.appendInt(7);                                             //... to this means breedable
        } else {
            this.response.appendInt(this.pet.getLevel()); //level
            this.response.appendInt(20); //max level
        }
        this.response.appendInt(this.pet.getExperience());
        if (this.pet.getLevel() < PetManager.experiences.length + 1) {
            this.response.appendInt(PetManager.experiences[this.pet.getLevel() - 1]); //XP Goal
        } else {
            this.response.appendInt(this.pet.getExperience());
        }
        this.response.appendInt(this.pet.getEnergy());
        this.response.appendInt(this.pet.getMaxEnergy()); //Max energy
        this.response.appendInt(this.pet.getHappiness());
        this.response.appendInt(100);
        this.response.appendInt(this.pet.getRespect());
        this.response.appendInt(this.pet.getUserId());
        this.response.appendInt((int) days + 1);
        this.response.appendString(this.room.getFurniOwnerName(this.pet.getUserId())); //Owner name

        this.response.appendInt(this.pet instanceof MonsterplantPet ? ((MonsterplantPet) this.pet).getRarity() : 0);
        
        // Can ride: pet has saddle, no one riding or user owns pet, and (user owns pet or anyone can ride)
        boolean canRide = false;
        if (this.pet instanceof RideablePet rideablePet && this.requestingHabbo != null) {
            boolean hasSaddle = rideablePet.hasSaddle();
            boolean noRider = rideablePet.getRider() == null;
            boolean isOwner = this.pet.getUserId() == this.requestingHabbo.getHabboInfo().getId();
            boolean canRidePermission = isOwner || rideablePet.anyoneCanRide();
            canRide = hasSaddle && (noRider || isOwner) && canRidePermission;
        }
        this.response.appendBoolean(canRide);
        
        this.response.appendBoolean(this.pet instanceof RideablePet rideablePet && rideablePet.getRider() != null && this.requestingHabbo != null && rideablePet.getRider().getHabboInfo().getId() == this.requestingHabbo.getHabboInfo().getId()); // is current user riding
        this.response.appendInt(0);
        this.response.appendInt(this.pet instanceof RideablePet rideablePet && rideablePet.anyoneCanRide() ? 1 : 0); // anyone can ride
        this.response.appendBoolean(this.pet instanceof MonsterplantPet monsterplantPet && monsterplantPet.canBreed()); //State Grown
        this.response.appendBoolean(!(this.pet instanceof MonsterplantPet monsterplantPet && monsterplantPet.isFullyGrown())); //unknown 1
        this.response.appendBoolean(this.pet instanceof MonsterplantPet monsterplantPet && monsterplantPet.isDead()); //Dead
        this.response.appendInt(this.pet instanceof MonsterplantPet ? ((MonsterplantPet) this.pet).getRarity() : 0);
        this.response.appendInt(MonsterplantPet.timeToLive); //Maximum wellbeing
        this.response.appendInt(this.pet instanceof MonsterplantPet ? ((MonsterplantPet) this.pet).remainingTimeToLive() : 0); //Remaining Wellbeing
        this.response.appendInt(this.pet instanceof MonsterplantPet ? ((MonsterplantPet) this.pet).remainingGrowTime() : 0);
        this.response.appendBoolean(this.pet instanceof MonsterplantPet monsterplantPet && monsterplantPet.isPubliclyBreedable()); //Breedable checkbox


        return this.response;
    }

    public Pet getPet() {
        return pet;
    }

    public Room getRoom() {
        return room;
    }

    public Habbo getRequestingHabbo() {
        return requestingHabbo;
    }
}
