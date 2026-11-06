package polaris.messages.incoming.handshake;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.handshake.PongComposer;

public class PingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new PongComposer(this.packet.readInt()));
    }
}
