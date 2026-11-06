package polaris.messages.incoming.communitygoals;

import polaris.Emulator;
import polaris.hotel.communitygoals.CommunityGoalManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewConcurrentUsersComposer;

/**
 * AIR 13 GetConcurrentUsersGoalProgress (1343): the state, the number of players
 * online and the goal, which the `element_concurrentusersinfo` element polls
 * every five seconds until the goal is reached.
 */
public class HotelViewRequestConcurrentUsersEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null) return;

        CommunityGoalManager manager = Emulator.getGameEnvironment().getCommunityGoalManager();

        this.client.sendResponse(new HotelViewConcurrentUsersComposer(
                manager.getConcurrentUsersState(user.getUserInfo().getId()),
                manager.getConcurrentUsersCount(),
                manager.getConcurrentUsersGoal()));
    }
}
