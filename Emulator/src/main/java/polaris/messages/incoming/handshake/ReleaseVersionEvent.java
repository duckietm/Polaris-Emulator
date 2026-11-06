package polaris.messages.incoming.handshake;

import polaris.messages.NoAuthMessage;
import polaris.messages.incoming.MessageHandler;

@NoAuthMessage
public class ReleaseVersionEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        this.client.setReleaseVersion(ClientReleaseGuard.normalize(this.packet.readString()));
    }
}
