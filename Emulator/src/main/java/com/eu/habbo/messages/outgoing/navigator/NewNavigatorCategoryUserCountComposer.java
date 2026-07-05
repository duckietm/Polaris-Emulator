package com.eu.habbo.messages.outgoing.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class NewNavigatorCategoryUserCountComposer extends MessageComposer {
    public final List<RoomCategory> roomCategories;

    public NewNavigatorCategoryUserCountComposer(List<RoomCategory> roomCategories) {
        this.roomCategories = roomCategories;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorCategoryUserCountComposer);
        this.response.appendInt(this.roomCategories.size());

        for (RoomCategory category : this.roomCategories) {
            int current = 0;
            for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms(category.getId())) {
                current += room.getUserCount();
            }

            this.response.appendInt(category.getId());
            this.response.appendInt(current);
            this.response.appendInt(category.getMaxUserCount());
        }
        return this.response;
    }

    public List<RoomCategory> getRoomCategories() {
        return roomCategories;
    }
}
