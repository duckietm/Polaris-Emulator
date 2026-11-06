package polaris.messages.incoming.guilds;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildBuyRoomsComposer;

public class RequestGuildBuyRoomsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        List<Room> rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsForUser(this.client.getUser());

        List<Room> roomList = new ArrayList<>();

        for (Room room : rooms) {
            if (room.getGuildId() == 0) roomList.add(room);
        }

        this.client.sendResponse(new GuildBuyRoomsComposer(roomList));
    }
}
