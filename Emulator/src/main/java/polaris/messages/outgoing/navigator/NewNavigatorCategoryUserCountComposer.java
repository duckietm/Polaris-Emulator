package polaris.messages.outgoing.navigator;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomCategory;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

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
