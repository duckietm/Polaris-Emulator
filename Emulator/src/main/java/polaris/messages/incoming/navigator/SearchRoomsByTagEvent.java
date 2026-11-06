package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.PrivateRoomsComposer;

public class SearchRoomsByTagEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String tag = NavigatorInputGuard.normalizeSearch(this.packet.readString());

        this.client.sendResponse(new PrivateRoomsComposer(
                Emulator.getGameEnvironment().getRoomManager().getRoomsWithTag(tag)));
    }
}
