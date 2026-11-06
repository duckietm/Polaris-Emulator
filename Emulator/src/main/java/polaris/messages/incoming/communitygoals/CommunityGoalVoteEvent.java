package polaris.messages.incoming.communitygoals;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewHideCommunityVoteButtonComposer;

/**
 * AIR 13 CommunityGoalVote (3536): one vote option id. The answer is the
 * "hide the vote button" packet (1435) the client reads as the acknowledgement.
 */
public class CommunityGoalVoteEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();
        int optionId = this.packet.readInt();

        if (user == null) return;

        boolean accepted = Emulator.getGameEnvironment()
                .getCommunityGoalManager()
                .vote(user.getUserInfo().getId(), optionId);

        this.client.sendResponse(new HotelViewHideCommunityVoteButtonComposer(accepted));
    }
}
