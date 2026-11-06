package polaris.messages.incoming.handshake;

import polaris.Emulator;
import polaris.messages.NoAuthMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.handshake.InitDiffieHandshakeComposer;

@NoAuthMessage
public class InitDiffieHandshakeEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (this.client.getEncryption() == null) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            return;
        }

        this.client.sendResponse(new InitDiffieHandshakeComposer(
                this.client.getEncryption().getDiffie().getSignedPrime(),
                this.client.getEncryption().getDiffie().getSignedGenerator()));
    }
}
