package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootballGate;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class FootballGateSaveLookEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || this.client.getHabbo().getHabboInfo().getId() != room.getOwnerId())
            return;

        int itemId = this.packet.readInt();
        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionFootballGate))
            return;

        String gender = this.packet.readString();
        String look = RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_LOOK_LENGTH);

        if (!RoomItemInputGuard.isValidGender(gender) || look.isEmpty())
            return;

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
