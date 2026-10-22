package com.eu.habbo.messages.outgoing.navigator;

import com.eu.habbo.habbohotel.navigation.NavigatorLiftedRoom;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * NavigatorLifted (AIR class_1829 / class_2495): int count, then per room int flatId, int areaId,
 * string image, string caption. The rooms come from
 * {@code NavigatorManager.getLiftedRooms()} ({@code navigator_lifted_rooms}).
 */
public class NewNavigatorLiftedRoomsComposer extends MessageComposer {
    private final List<NavigatorLiftedRoom> rooms;

    /** An empty list, as before the lifted rooms existed. */
    public NewNavigatorLiftedRoomsComposer() {
        this(List.of());
    }

    public NewNavigatorLiftedRoomsComposer(List<NavigatorLiftedRoom> rooms) {
        this.rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorLiftedRoomsComposer);
        this.response.appendInt(this.rooms.size());
        for (NavigatorLiftedRoom room : this.rooms) {
            this.response.appendInt(room.getRoomId());
            this.response.appendInt(room.getAreaId());
            this.response.appendString(room.getImage());
            this.response.appendString(room.getCaption());
        }
        return this.response;
    }
}
