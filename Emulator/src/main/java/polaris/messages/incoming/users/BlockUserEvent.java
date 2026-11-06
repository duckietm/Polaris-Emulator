package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.BlockResultComposer;

/**
 * Official {@code BlockedUsersManager.blockUser(userId)} (header 697). The server answers with
 * the per-user result the client folds into its cache.
 */
public class BlockUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (this.client.getUser() == null) {
            return;
        }

        if (!this.client.getUser().getUserStats().blockUser(userId)) {
            return;
        }

        this.client.sendResponse(new BlockResultComposer(BlockResultComposer.BLOCKED, userId));
    }
}
