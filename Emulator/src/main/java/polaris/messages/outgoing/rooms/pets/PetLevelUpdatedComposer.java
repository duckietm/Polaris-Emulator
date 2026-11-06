package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.pets.Pet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetLevelUpdatedComposer extends MessageComposer {
    private final Pet pet;

    public PetLevelUpdatedComposer(Pet pet) {
        this.pet = pet;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetLevelUpdatedComposer);
        this.response.appendInt(this.pet.getRoomUnit().getId());
        this.response.appendInt(this.pet.getId());
        this.response.appendInt(this.pet.getLevel());
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }
}
