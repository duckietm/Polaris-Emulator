package polaris.messages.incoming.communitygoals;

import polaris.Emulator;
import polaris.hotel.communitygoals.CommunityGoalManager;
import polaris.hotel.hotelview.HallOfFame;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HallOfFameComposer;

/**
 * AIR {@code GetCommunityGoalHallOfFame} (2167, {@code string goalCode}) -&gt;
 * {@code CommunityGoalHallOfFame} (3005). The {@code CommunityGoalHallOfFameWidget}
 * of the hotel view asks for the code its timing schedule picked; the answer lists
 * the best contributors of that goal. An empty code means the active goal, and the
 * legacy competition name of {@code hotelview.halloffame.query} still answers with
 * the legacy list.
 */
public class GetCommunityGoalHallOfFameEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        if (this.client.getUser() == null) return;

        String goalCode = this.packet.readString();

        if (goalCode == null) goalCode = "";

        goalCode = goalCode.trim();

        HallOfFame legacy = Emulator.getGameEnvironment().getHotelViewManager().getHallOfFame();

        if (!goalCode.isEmpty() && legacy != null && goalCode.equals(legacy.getCompetitionName())) {
            this.client.sendResponse(new HallOfFameComposer(legacy));
            return;
        }

        CommunityGoalManager manager = Emulator.getGameEnvironment().getCommunityGoalManager();

        this.client.sendResponse(
                new HallOfFameComposer(manager.resolveGoalCode(goalCode), manager.getHallOfFame(goalCode)));
    }
}
