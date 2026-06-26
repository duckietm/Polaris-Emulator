package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserIdleEvent;

public class RoomUserSitEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int posture = this.packet.readInt();

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            if (this.client.getHabbo().getRoomUnit().isWalking()) {
                this.client.getHabbo().getRoomUnit().stopWalking();
            }

            if (posture == 0) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().makeStand(this.client.getHabbo());
            } else {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().makeSit(this.client.getHabbo());
            }

            var event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.WALKED, false);
            Emulator.getPluginManager().fireEvent(event);

            if (!event.isCancelled()) {
                if (!event.idle) {
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().unIdle(this.client.getHabbo());
                }
            }
        }
    }
}
