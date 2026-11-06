package polaris.messages.outgoing.catalog;

import polaris.hotel.pets.Pet;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PetBoughtNotificationComposer extends MessageComposer {
    private final Pet pet;
    private final boolean gift;

    public PetBoughtNotificationComposer(Pet pet, boolean gift) {
        this.pet = pet;
        this.gift = gift;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetBoughtNotificationComposer);
        this.response.appendBoolean(this.gift);
        this.pet.serialize(this.response);
        return this.response;
    }

    public Pet getPet() {
        return pet;
    }

    public boolean isGift() {
        return gift;
    }
}
