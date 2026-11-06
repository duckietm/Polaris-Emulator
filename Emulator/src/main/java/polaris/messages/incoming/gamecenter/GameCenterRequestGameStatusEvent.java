package polaris.messages.incoming.gamecenter;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.gamecenter.GameCenterGameComposer;

public class GameCenterRequestGameStatusEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new GameCenterGameComposer(this.packet.readInt(), GameCenterGameComposer.OK));
    }
}
