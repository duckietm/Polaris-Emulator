package polaris.messages.incoming.users;

import polaris.WiredPlatform;
import polaris.hotel.rooms.RoomOwnerBlocks;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.BlockResultComposer;

/**
 * Official {@code BlockedUsersManager.blockUser(userId)} (header 697). The server answers with
 * the per-user result the client folds into its cache, and shows the blocked player out of the
 * blocker's room if they are in it.
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

        // A blocked player cannot enter the blocker's rooms; one who is in such a room right now is shown out.
        RoomOwnerBlocks.showOut(
                this.client.getUser(),
                WiredPlatform.gameEnvironment().getUserManager().getUser(userId));
    }
}
