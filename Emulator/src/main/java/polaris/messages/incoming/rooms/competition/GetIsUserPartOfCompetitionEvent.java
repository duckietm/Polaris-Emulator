package polaris.messages.incoming.rooms.competition;

import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.competition.IsUserPartOfCompetitionComposer;

/** Whether the sender already entered a room, and which one. */
public class GetIsUserPartOfCompetitionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String goalCode = this.packet.readString();

        User user = this.client.getUser();
        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (user == null || competition == null) {
            this.client.sendResponse(new IsUserPartOfCompetitionComposer(false, 0));
            return;
        }

        int roomId = RoomCompetitionManager.getInstance()
                .entryRoomId(user.getUserInfo().getId(), competition);

        this.client.sendResponse(new IsUserPartOfCompetitionComposer(roomId > 0, roomId));
    }
}
