package polaris.messages.outgoing.navigator;

import java.util.List;
import polaris.hotel.navigation.NavigatorLiftedRoom;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

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
