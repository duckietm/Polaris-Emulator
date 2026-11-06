package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.pets.MonsterplantPet;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.RideablePet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetStatusUpdateComposer extends MessageComposer {
    private final Pet pet;

    public PetStatusUpdateComposer(Pet pet) {
        this.pet = pet;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetStatusUpdateComposer);
        this.response.appendInt(this.pet.getRoomUnit().getId());
        this.response.appendInt(this.pet instanceof RideablePet && ((RideablePet) this.pet).anyoneCanRide() ? 1 : 0);
        this.response.appendBoolean(
                (this.pet instanceof MonsterplantPet && ((MonsterplantPet) this.pet).canBreed())); // unknown 1
        this.response.appendBoolean(
                (this.pet instanceof MonsterplantPet && !((MonsterplantPet) this.pet).isFullyGrown()));
        this.response.appendBoolean(
                this.pet instanceof MonsterplantPet && ((MonsterplantPet) this.pet).isDead()); // State Grown
        this.response.appendBoolean(
                this.pet instanceof MonsterplantPet && ((MonsterplantPet) this.pet).isPubliclyBreedable());
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }
}
