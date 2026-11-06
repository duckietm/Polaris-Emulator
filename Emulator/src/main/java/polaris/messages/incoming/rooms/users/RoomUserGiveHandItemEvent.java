package polaris.messages.incoming.rooms.users;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomHanditemBlockSupport;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.threading.runnables.RoomUnitWalkToRoomUnit;
import polaris.threading.runnables.UserGiveHandItemToUser;

public class RoomUserGiveHandItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (RoomHanditemBlockSupport.isHanditemBlocked(room)) {
                return;
            }

            User target = room.getUser(userId);

            if (target != null) {
                List<Runnable> executable = new ArrayList<>();
                executable.add(new UserGiveHandItemToUser(this.client.getUser(), target));
                Emulator.getThreading()
                        .run(new RoomUnitWalkToRoomUnit(
                                this.client.getUser().getRoomUnit(),
                                target.getRoomUnit(),
                                this.client.getUser().getUserInfo().getCurrentRoom(),
                                executable,
                                executable));
            }
        }
    }
}
