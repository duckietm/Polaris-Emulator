package polaris.messages.incoming.inventory;

import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryPetsComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

public class RequestInventoryPetDelete extends MessageHandler {
    public int getRatelimit() {
        return 500;
    }

    public void handle() {
        final int petId = this.packet.readInt();
        final User user = this.client.getUser();

        if (user == null) return;

        final Pet pet = user.getInventory().getPetsComponent().getPet(petId);

        if (pet == null) return;

        user.getInventory().getPetsComponent().removePet(pet);
        Emulator.getGameEnvironment().getPetManager().deletePet(pet);

        user.getClient().sendResponse(new InventoryRefreshComposer());
        user.getClient().sendResponse(new InventoryPetsComposer(user));
    }
}
