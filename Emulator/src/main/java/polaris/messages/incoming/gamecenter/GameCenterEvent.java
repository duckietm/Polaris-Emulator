package polaris.messages.incoming.gamecenter;

import polaris.messages.incoming.MessageHandler;

public class GameCenterEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // requested game type
    }
}
