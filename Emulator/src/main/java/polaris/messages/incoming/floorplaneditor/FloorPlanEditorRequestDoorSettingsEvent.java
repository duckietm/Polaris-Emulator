package polaris.messages.incoming.floorplaneditor;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.floorplaneditor.FloorPlanEditorDoorSettingsComposer;
import polaris.messages.outgoing.rooms.RoomFloorThicknessUpdatedComposer;
import polaris.messages.outgoing.rooms.RoomThicknessComposer;

public class FloorPlanEditorRequestDoorSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        this.client.sendResponse(new FloorPlanEditorDoorSettingsComposer(
                this.client.getUser().getUserInfo().getCurrentRoom()));
        this.client.sendResponse(new RoomFloorThicknessUpdatedComposer(
                this.client.getUser().getUserInfo().getCurrentRoom()));
        this.client.sendResponse(
                new RoomThicknessComposer(this.client.getUser().getUserInfo().getCurrentRoom()));
    }
}
