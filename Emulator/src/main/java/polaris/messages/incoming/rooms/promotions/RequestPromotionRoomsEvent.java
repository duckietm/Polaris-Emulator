package polaris.messages.incoming.rooms.promotions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.promotions.PromoteOwnRoomsListComposer;

public class RequestPromotionRoomsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        RoomManager roomManager = Emulator.getGameEnvironment().getRoomManager();
        Map<Integer, Room> roomsToShow = new LinkedHashMap<>();
        for (Room room : roomManager.getOwnedRoomsForUser(user)) roomsToShow.putIfAbsent(room.getId(), room);
        for (Room room : roomManager.getRoomsWithRights(user)) roomsToShow.putIfAbsent(room.getId(), room);
        for (Room room : roomManager.getRoomsWithAdminRights(user)) roomsToShow.putIfAbsent(room.getId(), room);

        this.client.sendResponse(new PromoteOwnRoomsListComposer(new ArrayList<>(roomsToShow.values())));
    }
}
