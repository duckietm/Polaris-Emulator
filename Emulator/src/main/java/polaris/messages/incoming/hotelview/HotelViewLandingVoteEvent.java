package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class HotelViewLandingVoteEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null) return;

        Emulator.getGameEnvironment()
                .getHotelViewManager()
                .voteCommunityGoal(
                        this.packet.readInt(),
                        this.packet.readInt(),
                        user.getUserInfo().getId());
    }
}
