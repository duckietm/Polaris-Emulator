package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserIdleEvent;

public class RoomUserSitEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int posture = this.packet.readInt();

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            if (this.client.getUser().getRoomUnit().isWalking()) {
                this.client.getUser().getRoomUnit().stopWalking();
            }

            if (posture == 0) {
                this.client.getUser().getUserInfo().getCurrentRoom().makeStand(this.client.getUser());
            } else {
                this.client.getUser().getUserInfo().getCurrentRoom().makeSit(this.client.getUser());
            }

            UserIdleEvent event = new UserIdleEvent(this.client.getUser(), UserIdleEvent.IdleReason.WALKED, false);
            Emulator.getPluginManager().fireEvent(event);

            if (!event.isCancelled()) {
                if (!event.idle) {
                    this.client.getUser().getUserInfo().getCurrentRoom().unIdle(this.client.getUser());
                }
            }
        }
    }
}
