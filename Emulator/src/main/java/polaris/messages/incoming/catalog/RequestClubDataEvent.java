package polaris.messages.incoming.catalog;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.ClubDataComposer;

public class RequestClubDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new ClubDataComposer(this.client.getUser(), this.packet.readInt()));
    }
}
