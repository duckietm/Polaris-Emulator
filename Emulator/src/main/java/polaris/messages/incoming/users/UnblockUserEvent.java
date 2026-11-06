package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.BlockResultComposer;

/** Official {@code BlockedUsersManager.unblockUser(userId)} (header 1886). */
public class UnblockUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (this.client.getUser() == null) {
            return;
        }

        if (!this.client.getUser().getUserStats().unblockUser(userId)) {
            return;
        }

        this.client.sendResponse(new BlockResultComposer(BlockResultComposer.UNBLOCKED, userId));
    }
}
