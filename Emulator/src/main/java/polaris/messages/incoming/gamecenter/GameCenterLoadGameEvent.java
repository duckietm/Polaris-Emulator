package polaris.messages.incoming.gamecenter;

import polaris.messages.incoming.MessageHandler;

public class GameCenterLoadGameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        // The game id: no game center game is loaded on request yet.
        this.packet.readInt();
    }
}
