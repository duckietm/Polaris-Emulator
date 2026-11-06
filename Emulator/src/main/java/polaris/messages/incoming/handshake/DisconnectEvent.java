package polaris.messages.incoming.handshake;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

public class DisconnectEvent extends MessageHandler {
    @Override
    public void handle() {
        if (this.client == null) return;

        Emulator.getGameServer().getGameClientManager().forceDisposeClient(this.client);
    }
}
