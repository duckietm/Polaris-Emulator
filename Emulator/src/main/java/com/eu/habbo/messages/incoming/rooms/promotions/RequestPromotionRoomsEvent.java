package com.eu.habbo.messages.incoming.rooms.promotions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.promotions.PromoteOwnRoomsListComposer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class RequestPromotionRoomsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        RoomManager roomManager = Emulator.getGameEnvironment().getRoomManager();
        Map<Integer, Room> roomsToShow = new LinkedHashMap<>();
        for (Room room : roomManager.getOwnedRoomsForHabbo(habbo)) roomsToShow.putIfAbsent(room.getId(), room);
        for (Room room : roomManager.getRoomsWithRights(habbo)) roomsToShow.putIfAbsent(room.getId(), room);
        for (Room room : roomManager.getRoomsWithAdminRights(habbo)) roomsToShow.putIfAbsent(room.getId(), room);

        this.client.sendResponse(new PromoteOwnRoomsListComposer(new ArrayList<>(roomsToShow.values())));
    }
}
