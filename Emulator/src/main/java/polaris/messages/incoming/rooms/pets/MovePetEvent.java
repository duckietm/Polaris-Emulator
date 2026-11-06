package polaris.messages.incoming.rooms.pets;

import polaris.hotel.pets.Pet;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class MovePetEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        // A user outside any room threw here before any check.
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        Pet pet = room.getPet(this.packet.readInt());

        if (pet != null) {
            // AIR playtest mode: pets are moved and rotated as by a visitor without rights.
            if (room != null
                    && room.hasRights(this.client.getUser())
                    && !RoomPlayTestSupport.isRestricted(this.client.getUser())) {
                if (pet.getRoomUnit() != null) {
                    int x = this.packet.readInt();
                    int y = this.packet.readInt();

                    RoomTile tile = room.getLayout().getTile((short) x, (short) y);

                    // Only to a tile a pet can stand on, and never a pet someone is riding.
                    boolean ridden = pet instanceof RideablePet rideable && rideable.getRider() != null;
                    if (tile != null && tile.isWalkable() && !ridden) {
                        pet.getRoomUnit().setLocation(tile);
                        pet.getRoomUnit().setPreviousLocation(tile);
                        pet.getRoomUnit().setZ(tile.z);
                        pet.getRoomUnit().setRotation(RoomUserRotation.fromValue(this.packet.readInt()));
                        pet.getRoomUnit().setPreviousLocationZ(pet.getRoomUnit().getZ());
                        room.sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
                        pet.needsUpdate = true;
                    }
                }
            }
        }
    }
}
