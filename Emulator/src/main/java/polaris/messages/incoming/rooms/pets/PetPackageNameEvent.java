package polaris.messages.incoming.rooms.pets;

import org.apache.commons.lang3.StringUtils;
import polaris.Emulator;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.CheckPetNameEvent;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.generic.alerts.PetErrorComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.pets.PetPackageNameValidationComposer;
import polaris.threading.runnables.QueryDeleteUserItem;

public class PetPackageNameEvent extends MessageHandler {
    /** The cursed dragon, the pet in the official {@code drago_cursed_<n>_package} crates. */
    static final int CURSED_DRAGON_PET_TYPE = 37;

    /**
     * The colour of the dragon in a cursed dragon crate: {@code drago_cursed_1_package} holds the blue one (breed 0),
     * {@code drago_cursed_2_package} the purple one (breed 1), as the official furni names them. -1 for any other furni.
     */
    static int cursedDragonBreed(String itemName) {
        if ("drago_cursed_1_package".equalsIgnoreCase(itemName)) return 0;
        if ("drago_cursed_2_package".equalsIgnoreCase(itemName)) return 1;

        return -1;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String name = this.packet.readString();

        if (!RoomItemInputGuard.isPositiveId(itemId)) {
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            UserItem item = room.getUserItem(itemId);
            if (item != null) {
                if (item.getUserId() == this.client.getUser().getUserInfo().getId()) {
                    int nameError = validatePetPackageName(name);
                    if (nameError == PetPackageNameValidationComposer.CLOSE_WIDGET) {
                        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
                        if (tile == null) {
                            this.client.sendResponse(
                                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                            return;
                        }

                        if (PetPlaceEvent.isFullFor(room, this.client.getUser())) {
                            this.client.sendResponse(new PetErrorComposer(PetErrorComposer.ROOM_ERROR_MAX_PETS));
                            return;
                        }

                        // Claim the box before a pet is created from it: a pickup or a room
                        // eviction racing this used to leave the box alive in an inventory
                        // while its row was already deleted, so it could be opened again.
                        if (!room.claimItem(item)) {
                            return;
                        }

                        Pet pet = null;
                        PetManager petManager = Emulator.getGameEnvironment().getPetManager();

                        if (item.getBaseItem().getName().equalsIgnoreCase("val11_present")) {
                            pet = petManager.createPet(11, name, this.client);
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("gnome_box")) {
                            pet = petManager.createGnome(name, room, this.client.getUser());
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("leprechaun_box")) {
                            pet = petManager.createLeprechaun(name, room, this.client.getUser());
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("velociraptor_egg")) {
                            pet = petManager.createPet(34, name, this.client);
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("pterosaur_egg")) {
                            pet = petManager.createPet(33, name, this.client);
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("petbox_epic")) {
                            pet = petManager.createPet(32, name, this.client);
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("cowbox")) {
                            pet = petManager.createPet(35, name, this.client);
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("cowbox_gold")) {
                            pet = petManager.createPet(35, 28, name, this.client);
                        }

                        int cursedDragonBreed =
                                cursedDragonBreed(item.getBaseItem().getName());
                        if (cursedDragonBreed >= 0) {
                            pet = petManager.createPet(CURSED_DRAGON_PET_TYPE, cursedDragonBreed, name, this.client);
                        }

                        if (pet != null) {
                            room.placePet(pet, item.getX(), item.getY(), item.getZ(), item.getRotation());
                            pet.setUserId(this.client.getUser().getUserInfo().getId());
                            pet.needsUpdate = true;
                            pet.getRoomUnit().setLocation(tile);
                            pet.getRoomUnit().setZ(item.getZ());
                            Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));
                            room.sendComposer(new RemoveFloorItemComposer(item).compose());
                            room.updateTile(tile);
                            room.sendComposer(
                                    new UpdateStackHeightComposer(tile.x, tile.y, tile.z, tile.relativeHeight())
                                            .compose());
                            item.setUserId(0);
                        } else {
                            // No pet matched this box after all: give the claim back so the
                            // furni stays in the room instead of vanishing until a reload.
                            room.addUserItem(item);
                            this.client.sendResponse(
                                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                        }
                    } else {
                        this.client.sendResponse(
                                new PetPackageNameValidationComposer(itemId, nameError, name == null ? "" : name));
                        return;
                    }
                }
            }
        }

        this.client.sendResponse(
                new PetPackageNameValidationComposer(itemId, PetPackageNameValidationComposer.CLOSE_WIDGET, ""));
    }

    static int validatePetPackageName(String name) {
        if (name == null || name.length() < CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM) {
            return PetPackageNameValidationComposer.NAME_TOO_SHORT;
        }

        if (name.length() > CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM) {
            return PetPackageNameValidationComposer.NAME_TOO_LONG;
        }

        if (!StringUtils.isAlphanumeric(name)) {
            return PetPackageNameValidationComposer.CONTAINS_INVALID_CHARS;
        }

        return PetPackageNameValidationComposer.CLOSE_WIDGET;
    }
}
