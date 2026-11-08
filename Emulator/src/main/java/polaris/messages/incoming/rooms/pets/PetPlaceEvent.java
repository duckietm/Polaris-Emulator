package polaris.messages.incoming.rooms.pets;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.PetErrorComposer;
import polaris.messages.outgoing.inventory.RemovePetComposer;
import polaris.messages.outgoing.rooms.pets.RoomPetComposer;

public class PetPlaceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // Playtest (the hotel's own rule beyond AIR): pets stay in the inventory.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) {
            this.client.sendResponse(new PetErrorComposer(PetErrorComposer.ROOM_ERROR_PETS_FORBIDDEN_IN_FLAT));
            return;
        }

        if (this.client.getUser().getUserInfo().getId() != room.getOwnerId()
                && !room.isAllowPets()
                && !(this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)
                        || this.client.getUser().hasPermission(Permission.ACC_PLACEFURNI))) {
            this.client.sendResponse(new PetErrorComposer(PetErrorComposer.ROOM_ERROR_PETS_FORBIDDEN_IN_FLAT));
            return;
        }

        int petId = this.packet.readInt();

        Pet pet = this.client.getUser().getInventory().getPetsComponent().getPet(petId);

        if (pet == null) {
            return;
        }
        if (isFullFor(room, this.client.getUser())) {
            this.client.sendResponse(new PetErrorComposer(PetErrorComposer.ROOM_ERROR_MAX_PETS));
            return;
        }

        int x = this.packet.readInt();
        int y = this.packet.readInt();

        RoomTile tile;
        RoomTile playerTile = this.client.getUser().getRoomUnit().getCurrentLocation();

        if ((x == 0 && y == 0) || !room.isOwner(this.client.getUser())) {
            // Place the pet in front of the player.
            tile = room.getLayout()
                    .getTileInFront(
                            this.client.getUser().getRoomUnit().getCurrentLocation(),
                            this.client
                                    .getUser()
                                    .getRoomUnit()
                                    .getBodyRotation()
                                    .getValue());

            if (tile == null || !tile.isWalkable()) {
                this.client.sendResponse(new PetErrorComposer(PetErrorComposer.ROOM_ERROR_PETS_NO_FREE_TILES));
            }

            // Check if tile exists and is walkable. Else place it in the current location the Habbo is standing.
            if (tile == null || !tile.isWalkable()) {
                tile = playerTile;

                // If the current tile is not walkable, place it at the door.
                if (tile == null || !tile.isWalkable()) {
                    tile = room.getLayout().getDoorTile();
                }
            }
        } else {
            tile = room.getLayout().getTile((short) x, (short) y);
        }

        if (tile == null || !tile.isWalkable() || !tile.getAllowStack()) {
            this.client.sendResponse(new PetErrorComposer(PetErrorComposer.ROOM_ERROR_PETS_SELECTED_TILE_NOT_FREE));
            return;
        }

        pet.setRoom(room);
        RoomUnit roomUnit = pet.getRoomUnit();

        if (roomUnit == null) {
            roomUnit = new RoomUnit();
        }

        roomUnit.setPathFinderRoom(room);

        roomUnit.setLocation(tile);
        roomUnit.setZ(tile.getStackHeight());
        roomUnit.setStatus(RoomUnitStatus.SIT, "0");
        roomUnit.setRoomUnitType(RoomUnitType.PET);
        if (playerTile != null) {
            roomUnit.lookAtPoint(playerTile);
        }
        pet.setRoomUnit(roomUnit);
        room.addPet(pet);
        pet.needsUpdate = true;
        Emulator.getThreading().run(pet);
        room.sendComposer(new RoomPetComposer(pet).compose());
        this.client.getUser().getInventory().getPetsComponent().removePet(pet);
        this.client.sendResponse(new RemovePetComposer(pet));
    }

    /**
     * Whether the room has no place for another pet of this user. Every way a pet enters a room asks it: a monsterplant
     * seed and a pet box used to place one past the limit.
     */
    public static boolean isFullFor(Room room, User user) {
        return room.getCurrentPets().size() >= Room.MAXIMUM_PETS && !user.hasPermission(Permission.ACC_UNLIMITED_PETS);
    }
}
