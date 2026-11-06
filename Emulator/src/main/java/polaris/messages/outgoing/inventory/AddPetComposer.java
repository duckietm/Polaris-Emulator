package polaris.messages.outgoing.inventory;

import polaris.hotel.pets.Pet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AddPetComposer extends MessageComposer {
    private final Pet pet;

    public AddPetComposer(Pet pet) {
        this.pet = pet;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AddPetComposer);
        this.pet.serialize(this.response);
        this.response.appendBoolean(false);
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }
}
