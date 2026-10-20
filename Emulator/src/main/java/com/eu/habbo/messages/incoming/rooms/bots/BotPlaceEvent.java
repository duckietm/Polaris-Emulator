package com.eu.habbo.messages.incoming.rooms.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomPlayTestSupport;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BotErrorComposer;

public class BotPlaceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        // Playtest (the hotel's own rule beyond AIR): bots stay in the inventory.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getHabbo())) {
            this.client.sendResponse(new BotErrorComposer(BotErrorComposer.ROOM_ERROR_BOTS_FORBIDDEN_IN_FLAT));
            return;
        }

        Bot bot = this.client.getHabbo().getInventory().getBotsComponent().getBot(this.packet.readInt());

        if (bot == null) return;

        int x = this.packet.readInt();
        int y = this.packet.readInt();

        Emulator.getGameEnvironment()
                .getBotManager()
                .placeBot(
                        bot,
                        this.client.getHabbo(),
                        this.client.getHabbo().getHabboInfo().getCurrentRoom(),
                        room.getLayout().getTile((short) x, (short) y));
    }
}
