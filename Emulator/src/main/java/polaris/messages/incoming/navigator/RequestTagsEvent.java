package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.TagsComposer;

public class RequestTagsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(
                new TagsComposer(Emulator.getGameEnvironment().getRoomManager().getTags()));
    }
}
