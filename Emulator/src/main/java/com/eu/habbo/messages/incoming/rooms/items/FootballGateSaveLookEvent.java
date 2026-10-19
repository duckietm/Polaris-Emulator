package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootballGate;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class FootballGateSaveLookEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        // AIR FurnitureClothingChangeWidgetHandler offers the editor to the room owner, any-room controllers and
        // anyone with rights in the room (controller level 1+), which is what Room.hasRights covers.
        if (room == null || !room.hasRights(this.client.getHabbo())) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionFootballGate)) return;

        String gender = this.packet.readString();
        String look = this.packet.readString();

        if (!RoomItemInputGuard.isValidGender(gender) || !RoomItemInputGuard.isValidFigure(look)) return;

        switch (gender.toLowerCase()) {
            case "m":
                ((InteractionFootballGate) item).setFigureM(look);
                room.updateItem(item);
                break;

            case "f":
                ((InteractionFootballGate) item).setFigureF(look);
                room.updateItem(item);
                break;
        }
    }
}
