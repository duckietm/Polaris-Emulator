package polaris.messages.incoming.navigator;

import java.util.ArrayList;
import java.util.Collections;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;
import polaris.messages.outgoing.users.UserHomeRoomComposer;

public class NewNavigatorActionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String data = this.packet.readString();

        if (data.equals("random_friending_room")) {
            ArrayList<Room> rooms =
                    Emulator.getGameEnvironment().getRoomManager().getActiveRooms();
            if (!rooms.isEmpty()) {
                Collections.shuffle(rooms);
                this.client.sendResponse(new ForwardToRoomComposer(rooms.get(0).getId()));
            }
        } else if (data.equalsIgnoreCase("predefined_noob_lobby")) {
            this.client.sendResponse(
                    new ForwardToRoomComposer(Emulator.getConfig().getInt("hotel.room.nooblobby")));
        } else {
            this.client.sendResponse(new UserHomeRoomComposer(
                    this.client.getUser().getUserInfo().getHomeRoom(),
                    this.client.getUser().getUserInfo().getHomeRoom()));
        }
    }
}
