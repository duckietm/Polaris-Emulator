package polaris.messages.outgoing.floorplaneditor;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FloorPlanEditorDoorSettingsComposer extends MessageComposer {
    private final Room room;

    public FloorPlanEditorDoorSettingsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FloorPlanEditorDoorSettingsComposer);
        this.response.appendInt(this.room.getLayout().getDoorX());
        this.response.appendInt(this.room.getLayout().getDoorY());
        this.response.appendInt(this.room.getLayout().getDoorDirection());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
