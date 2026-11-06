package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.housekeeping.HousekeepingUserDetailComposer;

public class HousekeepingFindUserByNameEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        String username = HousekeepingInputGuard.normalize(this.packet.readString());

        if (username.isEmpty()
                || !HousekeepingInputGuard.isWithinLimit(username, HousekeepingInputGuard.MAX_LOOKUP_LENGTH)) {
            this.client.sendResponse(new HousekeepingUserDetailComposer(null));
            return;
        }

        User online = Emulator.getGameEnvironment().getUserManager().getUser(username);
        UserInfo info = online != null ? online.getUserInfo() : UserManager.getOfflineUserInfo(username);

        this.client.sendResponse(new HousekeepingUserDetailComposer(info));
    }
}
