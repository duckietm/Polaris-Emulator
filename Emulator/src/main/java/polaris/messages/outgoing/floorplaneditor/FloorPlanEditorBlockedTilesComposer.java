package polaris.messages.outgoing.floorplaneditor;

import java.util.Set;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FloorPlanEditorBlockedTilesComposer extends MessageComposer {
    private final Room room;

    public FloorPlanEditorBlockedTilesComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FloorPlanEditorBlockedTilesComposer);

        Set<RoomTile> tileList = this.room.getLockedTiles();

        this.response.appendInt(tileList.size());
        for (RoomTile node : tileList) {
            this.response.appendInt((int) node.x);
            this.response.appendInt((int) node.y);
        }

        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
