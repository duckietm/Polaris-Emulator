package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.PrivateRoomsComposer;

public class RequestPopularRoomsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readString(); // search query
        this.packet.readInt(); // ad index
        this.client.sendResponse(new PrivateRoomsComposer(
                Emulator.getGameEnvironment().getRoomManager().getActiveRooms(-1)));
    }
}
