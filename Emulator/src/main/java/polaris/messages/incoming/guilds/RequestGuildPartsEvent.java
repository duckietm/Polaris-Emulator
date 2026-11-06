package polaris.messages.incoming.guilds;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildPartsComposer;

public class RequestGuildPartsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new GuildPartsComposer());
    }
}
