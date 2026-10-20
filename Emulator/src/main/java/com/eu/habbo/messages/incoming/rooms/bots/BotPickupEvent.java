package com.eu.habbo.messages.incoming.rooms.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomPlayTestSupport;
import com.eu.habbo.messages.incoming.MessageHandler;

public class BotPickupEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        // AIR playtest mode: no bot is picked up, the user's own included.
        if (RoomPlayTestSupport.isRestricted(this.client.getHabbo())) return;

        Emulator.getGameEnvironment().getBotManager().pickUpBot(this.packet.readInt(), this.client.getHabbo());
    }
}
