package polaris.messages.incoming.rooms.items;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.MoodLightDataComposer;

public class MoodLightSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() != null)
            this.client.sendResponse(MoodLightDataComposer.forRoom(
                    this.client.getUser().getUserInfo().getCurrentRoom()));
    }
}
