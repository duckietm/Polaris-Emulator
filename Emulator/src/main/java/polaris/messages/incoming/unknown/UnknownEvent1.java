package polaris.messages.incoming.unknown;

import java.util.List;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.IgnoredUsersComposer;

public class UnknownEvent1 extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new IgnoredUsersComposer(List.of()));
    }
}
