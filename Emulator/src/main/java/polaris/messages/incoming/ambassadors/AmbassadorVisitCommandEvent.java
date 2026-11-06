package polaris.messages.incoming.ambassadors;

import polaris.Emulator;
import polaris.hotel.commands.CommandTargetGuard;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

public class AmbassadorVisitCommandEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_AMBASSADOR)) {
            String username = this.packet.readString();

            User user = Emulator.getGameEnvironment().getUserManager().getUser(username);

            // The same privacy as following a friend: a user who hides being online or blocks following is not
            // found unless the ambassador may stalk; and only users the ambassador may act on.
            if (user != null
                    && user.getUserStats() != null
                    && (this.client.getUser().hasPermission("acc_can_stalk")
                            || (!user.getUserStats().hideOnline && !user.getUserStats().blockFollowing))
                    && CommandTargetGuard.canTarget(this.client.getUser(), user)) {
                if (user.getUserInfo().getCurrentRoom() != null) {
                    this.client.sendResponse(new ForwardToRoomComposer(
                            user.getUserInfo().getCurrentRoom().getId()));
                }
            }
        }
    }
}
