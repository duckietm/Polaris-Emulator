package polaris.messages.outgoing.inventory;

import polaris.hotel.pets.Pet;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InventoryPetsComposer extends MessageComposer {
    private final User user;

    public InventoryPetsComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InventoryPetsComposer);

        this.response.appendInt(1);
        this.response.appendInt(1);
        this.response.appendInt(this.user.getInventory().getPetsComponent().getPetsCount());

        for (Pet pet : this.user.getInventory().getPetsComponent().getPets().values()) {
            pet.serialize(this.response);
        }

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
