package polaris.messages.incoming.rooms.pets;

import org.apache.commons.lang3.StringUtils;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetManager;
import polaris.hotel.pets.PetPackage;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.CheckPetNameEvent;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.generic.alerts.PetErrorComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.pets.PetPackageNameValidationComposer;
import polaris.threading.runnables.QueryDeleteUserItem;

public class PetPackageNameEvent extends MessageHandler {
    /** The direction the nest of an opened package faces. */
    static final int NEST_DIRECTION = 4;

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
                        boolean packageGone = false;
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

                        // Any other pet package: a pet of the type its furni data names, the one its state
                        // describes when it holds a particular pet. The package makes way for its nest first, so
                        // the nest is placed on a free tile and the pet then stands on it.
                        PetPackage petPackage = pet == null ? PetPackage.of(item.getBaseItem()) : null;
                        if (petPackage != null) {
                            pet = createPackagedPet(petManager, petPackage, item.getExtradata(), name, this.client);
                            if (pet != null) {
                                room.sendComposer(new RemoveFloorItemComposer(item).compose());
                                room.updateTile(tile);
                                this.placeNest(room, tile, petPackage);
                                packageGone = true;
                            }
                        }

                        if (pet != null) {
                            room.placePet(pet, item.getX(), item.getY(), item.getZ(), item.getRotation());
                            pet.setUserId(this.client.getUser().getUserInfo().getId());
                            pet.needsUpdate = true;
                            pet.getRoomUnit().setLocation(tile);
                            pet.getRoomUnit().setZ(item.getZ());
                            Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));
                            if (!packageGone) {
                                room.sendComposer(new RemoveFloorItemComposer(item).compose());
                            }
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
                                new PetPackageNameValidationComposer(itemId, nameError, errorDetail(nameError, name)));
                        return;
                    }
                }
            }
        }

        this.client.sendResponse(
                new PetPackageNameValidationComposer(itemId, PetPackageNameValidationComposer.CLOSE_WIDGET, ""));
    }

    /** The pet of a package: the one its state describes, else any pet of the package's type. */
    static Pet createPackagedPet(
            PetManager petManager, PetPackage petPackage, String extradata, String name, GameClient client) {
        String figure = petPackage.figureIn(extradata);
        if (figure == null) return petManager.createPet(petPackage.petType(), name, client);

        String[] parts = figure.split(" ");
        Pet pet = petManager.createPet(petPackage.petType(), Integer.parseInt(parts[1]), name, client);
        if (pet != null) pet.setColor(parts[2]);
        return pet;
    }

    /**
     * Puts the package's nest where the package stood, facing direction 4. A hotel without that furni gets no nest;
     * a nest that does not fit there goes to the owner's inventory.
     */
    private void placeNest(Room room, RoomTile tile, PetPackage petPackage) {
        ItemManager items = Emulator.getGameEnvironment().getItemManager();
        Item nestDefinition = items.getItem(petPackage.nestItemName());
        if (nestDefinition == null) return;

        User owner = this.client.getUser();
        UserItem nest = items.createItem(owner.getUserInfo().getId(), nestDefinition, 0, 0, "");
        if (nest == null) return;

        if (room.placeFloorFurniAt(nest, tile, NEST_DIRECTION, owner) == FurnitureMovementError.NONE) return;

        owner.getInventory().getItemsComponent().addItem(nest);
        this.client.sendResponse(new AddUserItemComposer(nest));
        this.client.sendResponse(new InventoryRefreshComposer());
    }

    /**
     * What the name dialog adds to its message: the longest name allowed for "too long", the shortest for "too
     * short", the characters that are not allowed for "invalid characters", nothing for the rest.
     */
    static String errorDetail(int nameError, String name) {
        if (nameError == PetPackageNameValidationComposer.NAME_TOO_LONG) {
            return Integer.toString(CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM);
        }
        if (nameError == PetPackageNameValidationComposer.NAME_TOO_SHORT) {
            return Integer.toString(CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM);
        }
        if (nameError != PetPackageNameValidationComposer.CONTAINS_INVALID_CHARS || name == null) {
            return "";
        }

        StringBuilder refused = new StringBuilder();
        name.codePoints().filter(c -> !Character.isLetterOrDigit(c)).distinct().forEach(refused::appendCodePoint);
        return refused.toString();
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
