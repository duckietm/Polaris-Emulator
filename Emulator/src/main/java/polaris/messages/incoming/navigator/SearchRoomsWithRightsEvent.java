package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.PrivateRoomsComposer;

public class SearchRoomsWithRightsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new PrivateRoomsComposer(
                Emulator.getGameEnvironment().getRoomManager().getRoomsWithRights(this.client.getUser())));
    }
}
