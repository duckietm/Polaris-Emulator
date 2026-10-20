package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomPlayTestSupport;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class MovePetEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Pet pet = this.client.getHabbo().getHabboInfo().getCurrentRoom().getPet(this.packet.readInt());

        if (pet != null) {
            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            // AIR playtest mode: pets are moved and rotated as by a visitor without rights.
            if (room != null
                    && room.hasRights(this.client.getHabbo())
                    && !RoomPlayTestSupport.isRestricted(this.client.getHabbo())) {
                if (pet.getRoomUnit() != null) {
                    int x = this.packet.readInt();
                    int y = this.packet.readInt();

                    RoomTile tile = room.getLayout().getTile((short) x, (short) y);

                    if (tile != null) {
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
