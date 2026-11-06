package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.CustomWordFilterWordsComposer;

/** GetCustomFilter (145): sent when the word filter settings window opens. */
public class RequestCustomWordFilterEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new CustomWordFilterWordsComposer(
                this.client.getUser().getUserStats().getCustomWordFilter().words()));
    }
}
