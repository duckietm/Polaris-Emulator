package polaris.messages.incoming.rooms.bots;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.messages.incoming.MessageHandler;

public class BotPickupEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        // AIR playtest mode: no bot is picked up, the user's own included.
        if (RoomPlayTestSupport.isRestricted(this.client.getUser())) return;

        Emulator.getGameEnvironment().getBotManager().pickUpBot(this.packet.readInt(), this.client.getUser());
    }
}
