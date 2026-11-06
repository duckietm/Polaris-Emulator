package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.housekeeping.HousekeepingUserDetailComposer;

public class HousekeepingFindUserByIdEvent extends HousekeepingHandler {
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

        int userId = this.packet.readInt();

        if (userId <= 0) {
            this.client.sendResponse(new HousekeepingUserDetailComposer(null));
            return;
        }

        // UserManager.getUserInfo(int) returns the in-memory record for
        // online users and falls through to the offline SQL lookup
        // otherwise, so a single call covers both branches.
        UserInfo info = Emulator.getGameEnvironment().getUserManager().getUserInfo(userId);

        this.client.sendResponse(new HousekeepingUserDetailComposer(info));
    }
}
