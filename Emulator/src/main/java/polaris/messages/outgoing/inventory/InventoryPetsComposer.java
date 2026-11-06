package polaris.messages.outgoing.inventory;

import java.util.List;
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
        // One copy for the count and the list: they were read apart, and a pet added in between broke the packet.
        List<Pet> pets = this.user.getInventory().getPetsComponent().getPetsSnapshot();
        this.response.appendInt(pets.size());

        for (Pet pet : pets) {
            pet.serialize(this.response);
        }

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
