package polaris.messages.outgoing.inventory;

import polaris.hotel.pets.Pet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RemovePetComposer extends MessageComposer {
    private final int petId;

    public RemovePetComposer(Pet pet) {
        this.petId = pet.getId();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemovePetComposer);
        this.response.appendInt(this.petId);
        return this.response;
    }

    public int getPetId() {
        return petId;
    }
}
