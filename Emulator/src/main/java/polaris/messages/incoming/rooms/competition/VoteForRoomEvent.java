package polaris.messages.incoming.rooms.competition;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.hotel.rooms.competition.RoomCompetitionResult;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/**
 * One vote for the room the sender is standing in. Voting for your own room is refused, a room that
 * never entered has nothing to vote for, and a second vote for the same room is refused by the
 * table; the answer always carries how many votes are left, which is what the window shows.
 */
public class VoteForRoomEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String goalCode = this.packet.readString();

        User user = this.client.getUser();
        Room room = this.currentRoom();
        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (user == null || room == null || competition == null) return;

        if (!competition.votingOpen(Emulator.getIntUnixTimestamp())) return;

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();
        int entryId = RoomCompetitionSupport.votableEntryId(user, room, competition);
        int userId = user.getUserInfo().getId();

        // Nothing to vote for: the window is not open on a room that never entered, so a packet
        // for one is either stale or crafted and gets no answer.
        if (entryId <= 0) return;

        int votesLeft = manager.votesLeft(userId, competition);

        if (votesLeft > 0) {
            manager.vote(userId, entryId, competition);
            votesLeft = manager.votesLeft(userId, competition);
        }

        RoomCompetitionSupport.sendVotingState(this.client, competition, RoomCompetitionResult.VOTE_ALLOWED, votesLeft);
    }
}
