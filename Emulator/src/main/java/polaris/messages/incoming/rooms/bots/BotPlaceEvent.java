package polaris.messages.incoming.rooms.bots;

import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomTile;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BotErrorComposer;

public class BotPlaceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // Playtest (the hotel's own rule beyond AIR): bots stay in the inventory.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) {
            this.client.sendResponse(new BotErrorComposer(BotErrorComposer.ROOM_ERROR_BOTS_FORBIDDEN_IN_FLAT));
            return;
        }

        Bot bot = this.client.getUser().getInventory().getBotsComponent().getBot(this.packet.readInt());

        if (bot == null) return;

        int x = this.packet.readInt();
        int y = this.packet.readInt();

        // A tile off the map used to reach the bot manager as null and throw there.
        RoomTile tile = room.getLayout().getTile((short) x, (short) y);
        if (tile == null) return;

        Emulator.getGameEnvironment()
                .getBotManager()
                .placeBot(
                        bot,
                        this.client.getUser(),
                        this.client.getUser().getUserInfo().getCurrentRoom(),
                        tile);
    }
}
