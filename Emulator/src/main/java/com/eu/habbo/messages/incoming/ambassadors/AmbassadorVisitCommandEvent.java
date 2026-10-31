package com.eu.habbo.messages.incoming.ambassadors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandTargetGuard;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;

public class AmbassadorVisitCommandEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_AMBASSADOR)) {
            String username = this.packet.readString();

            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);

            // The same privacy as following a friend: a user who hides being online or blocks following is not
            // found unless the ambassador may stalk; and only users the ambassador may act on.
            if (habbo != null
                    && habbo.getHabboStats() != null
                    && (this.client.getHabbo().hasPermission("acc_can_stalk")
                            || (!habbo.getHabboStats().hideOnline && !habbo.getHabboStats().blockFollowing))
                    && CommandTargetGuard.canTarget(this.client.getHabbo(), habbo)) {
                if (habbo.getHabboInfo().getCurrentRoom() != null) {
                    this.client.sendResponse(new ForwardToRoomComposer(
                            habbo.getHabboInfo().getCurrentRoom().getId()));
                }
            }
        }
    }
}
