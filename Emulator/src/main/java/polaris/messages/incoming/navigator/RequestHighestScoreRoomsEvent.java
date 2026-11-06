package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.PrivateRoomsComposer;

public class RequestHighestScoreRoomsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // ad index
        this.client.sendResponse(new PrivateRoomsComposer(
                Emulator.getGameEnvironment().getRoomManager().getRoomsByScore()));
    }
}
