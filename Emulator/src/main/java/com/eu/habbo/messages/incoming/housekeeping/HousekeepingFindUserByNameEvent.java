package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingUserDetailComposer;

public class HousekeepingFindUserByNameEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        String username = HousekeepingInputGuard.normalize(this.packet.readString());

        if (username.isEmpty() || !HousekeepingInputGuard.isWithinLimit(username, HousekeepingInputGuard.MAX_LOOKUP_LENGTH)) {
            this.client.sendResponse(new HousekeepingUserDetailComposer(null));
            return;
        }

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);
        HabboInfo info = online != null ? online.getHabboInfo() : HabboManager.getOfflineHabboInfo(username);

        this.client.sendResponse(new HousekeepingUserDetailComposer(info));
    }
}
