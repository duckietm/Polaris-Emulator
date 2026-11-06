package polaris.messages.incoming.friends;

import java.util.Collections;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomCategory;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.FriendFindingRoomComposer;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

public class FindNewFriendsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        List<RoomCategory> roomCategories =
                Emulator.getGameEnvironment().getRoomManager().roomCategoriesForUser(this.client.getUser());
        Collections.shuffle(roomCategories);

        for (RoomCategory category : roomCategories) {
            List<Room> rooms = Emulator.getGameEnvironment().getRoomManager().getActiveRooms(category.getId());

            if (!rooms.isEmpty()) {
                Room room = rooms.get(0);

                if (room.getUserCount() > 0) {
                    this.client.sendResponse(new ForwardToRoomComposer(room.getId()));
                    return;
                }
            }
        }

        this.client.sendResponse(new FriendFindingRoomComposer(FriendFindingRoomComposer.NO_ROOM_FOUND));
    }
}
