package polaris.messages.incoming.gamecenter;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.gamecenter.basejump.BaseJumpUnloadGameComposer;

public class GameCenterLeaveGameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // unloaded game type
        this.client.sendResponse(new BaseJumpUnloadGameComposer());
    }
}
