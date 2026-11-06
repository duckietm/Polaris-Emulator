package polaris.messages.outgoing.rooms.pets;

import polaris.hotel.pets.Pet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetLevelUpComposer extends MessageComposer {
    private final Pet pet;

    public PetLevelUpComposer(Pet pet) {
        this.pet = pet;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetLevelUpComposer);
        this.response.appendInt(this.pet.getId());
        this.response.appendString(this.pet.getName());
        this.response.appendInt(this.pet.getLevel());
        this.response.appendInt(this.pet.getPetData().getType());
        this.response.appendInt(this.pet.getRace());
        this.response.appendString(this.pet.getColor());
        this.response.appendInt(0);
        this.response.appendInt(0);

        // :test 2329  i:0 s:a i:3 i:1 i:1 s:FF00FF i:0 i:0
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }
}
