package polaris.messages.incoming.rooms.competition;

import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

/**
 * Sends the visitor to a named room of the competition. The room has to be taking part, otherwise
 * this would be a way to be forwarded anywhere; entering still runs every check.
 */
public class ForwardToACompetitionRoomEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String goalCode = this.packet.readString();
        int roomId = this.packet.readInt();

        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (competition == null || roomId <= 0) return;

        if (RoomCompetitionManager.getInstance().entryId(roomId, competition) <= 0) {
            // The hotel view hall of fame (CommunityGoalHallOfFameWidget) sends the user id of
            // the player whose "go to room" icon was clicked: forward to that player's entry.
            roomId = RoomCompetitionManager.getInstance().entryRoomId(roomId, competition);

            if (roomId <= 0) return;
        }

        this.client.sendResponse(new ForwardToRoomComposer(roomId));
    }
}
