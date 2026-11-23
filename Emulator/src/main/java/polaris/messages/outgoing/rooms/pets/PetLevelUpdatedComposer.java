package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.pets.Pet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetLevelUpdatedComposer extends MessageComposer {
    private final Pet pet;

    private final int level;

    public PetLevelUpdatedComposer(Pet pet) {
        this(pet, pet.getLevel());
    }

    /** For a pet whose level in the room is not its experience level: a monster plant shows its growth stage. */
    public PetLevelUpdatedComposer(Pet pet, int level) {
        this.pet = pet;
        this.level = level;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetLevelUpdatedComposer);
        this.response.appendInt(this.pet.getRoomUnit().getId());
        this.response.appendInt(this.pet.getId());
        this.response.appendInt(this.level);
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }
}
