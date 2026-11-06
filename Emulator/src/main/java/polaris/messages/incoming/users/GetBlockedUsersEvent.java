package polaris.messages.incoming.users;

import java.util.ArrayList;
import java.util.List;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.BlockListComposer;

/**
 * Official {@code BlockedUsersManager.initBlockList()} (header 485): the client asks for the
 * whole block list once the session data is known.
 */
public class GetBlockedUsersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        List<Integer> blocked =
                new ArrayList<>(this.client.getUser().getUserStats().getBlockedUsers());

        this.client.sendResponse(new BlockListComposer(blocked));
    }
}
