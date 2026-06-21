package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendFindingRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;

import java.util.Collections;
import java.util.List;

public class FindNewFriendsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        List<RoomCategory> roomCategories = Emulator.getGameEnvironment().getRoomManager().roomCategoriesForHabbo(this.client.getHabbo());
        Collections.shuffle(roomCategories);

        for (RoomCategory category : roomCategories) {
            List<Room> rooms = Emulator.getGameEnvironment().getRoomManager().getActiveRooms(category.getId());

            if (!rooms.isEmpty()) {
                Room room = rooms.getFirst();

                if (room.getUserCount() > 0) {
                    this.client.sendResponse(new ForwardToRoomComposer(room.getId()));
                    return;
                }
            }
        }

        this.client.sendResponse(new FriendFindingRoomComposer(FriendFindingRoomComposer.NO_ROOM_FOUND));
    }
}
