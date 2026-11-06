package polaris.messages.incoming.rooms.pets;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetManager;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.AddPetComposer;

public class PetPickupEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // AIR playtest mode: no pet is picked up, the user's own included.
        if (RoomPlayTestSupport.isRestricted(this.client.getUser())) return;

        Pet pet = room.getPet(petId);

        if (pet != null) {
            if (this.client.getUser().getUserInfo().getId() == pet.getUserId()
                    || room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
                // The maximum is the picker's own: a room owner clearing a visitor's pet is not stopped by how
                // many pets they hold. Reserving the place before the pet leaves the room keeps two pickups at
                // the same moment from both passing the check.
                boolean ownPet = this.client.getUser().getUserInfo().getId() == pet.getUserId();
                boolean reserved = ownPet && !this.client.getUser().hasPermission(Permission.ACC_UNLIMITED_PETS);
                if (reserved
                        && !this.client
                                .getUser()
                                .getInventory()
                                .getPetsComponent()
                                .addPetWithin(pet, PetManager.MAXIMUM_PET_INVENTORY_SIZE)) {
                    this.client
                            .getUser()
                            .alert(Emulator.getTexts()
                                    .getValue("error.pets.max.inventory")
                                    .replace("%amount%", PetManager.MAXIMUM_PET_INVENTORY_SIZE + ""));
                    return;
                }

                if (pet instanceof RideablePet) {
                    RideablePet rideablePet = (RideablePet) pet;
                    if (rideablePet.getRider() != null) {
                        rideablePet.getRider().getUserInfo().dismountPet(true);
                    }
                }

                pet.removeFromRoom();
                pet.run(); // Run synchronously to ensure DB is updated before returning pet to inventory

                if (ownPet) {
                    this.client.sendResponse(new AddPetComposer(pet));
                    if (!reserved)
                        this.client.getUser().getInventory().getPetsComponent().addPet(pet);
                } else {
                    User user = Emulator.getGameEnvironment().getUserManager().getUser(pet.getUserId());

                    if (user != null) {
                        user.getClient().sendResponse(new AddPetComposer(pet));
                        user.getInventory().getPetsComponent().addPet(pet);
                    }
                }
            }
        }
    }
}
