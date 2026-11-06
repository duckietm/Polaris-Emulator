package polaris.messages.incoming.communitygoals;

import polaris.Emulator;
import polaris.hotel.communitygoals.CommunityGoalManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewConcurrentUsersComposer;

/**
 * AIR 13 GetConcurrentUsersReward (3872): the player claims the badge of the
 * concurrent-users goal. The answer is the refreshed progress, so the button
 * turns itself off.
 */
public class HotelViewConcurrentUsersButtonEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null) return;

        CommunityGoalManager manager = Emulator.getGameEnvironment().getCommunityGoalManager();
        manager.claimConcurrentUsersReward(user);

        this.client.sendResponse(new HotelViewConcurrentUsersComposer(
                manager.getConcurrentUsersState(user.getUserInfo().getId()),
                manager.getConcurrentUsersCount(),
                manager.getConcurrentUsersGoal()));
    }
}
