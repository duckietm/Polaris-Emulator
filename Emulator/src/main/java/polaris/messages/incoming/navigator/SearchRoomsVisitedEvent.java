package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.PrivateRoomsComposer;

public class SearchRoomsVisitedEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new PrivateRoomsComposer(
                Emulator.getGameEnvironment().getRoomManager().getRoomsVisited(this.client.getUser(), false, 25)));
    }
}
