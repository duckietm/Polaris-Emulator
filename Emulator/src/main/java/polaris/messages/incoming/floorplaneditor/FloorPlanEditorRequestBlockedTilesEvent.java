package polaris.messages.incoming.floorplaneditor;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.floorplaneditor.FloorPlanEditorBlockedTilesComposer;

public class FloorPlanEditorRequestBlockedTilesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        this.client.sendResponse(new FloorPlanEditorBlockedTilesComposer(
                this.client.getUser().getUserInfo().getCurrentRoom()));
    }
}
