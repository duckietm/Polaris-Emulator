package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class HotelViewLandingResetVotesEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null || !user.hasPermission(Permission.ACC_HOTELVIEW_EDIT)) return;

        Emulator.getGameEnvironment().getHotelViewManager().resetCommunityGoalVotes(this.packet.readInt());
    }
}
