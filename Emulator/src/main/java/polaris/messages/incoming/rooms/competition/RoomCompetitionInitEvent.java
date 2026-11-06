package polaris.messages.incoming.rooms.competition;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.hotel.rooms.competition.RoomCompetitionResult;
import polaris.hotel.rooms.competition.RoomCompetitionSubmitState;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/**
 * The client asks this on every room entry. The answer is whichever competition window belongs in
 * this room: the submit window for an owner while the submission is open, the voting window for a
 * visitor of a room taking part while the voting is open, and nothing at all otherwise.
 *
 * <p>An owner who could still enter is shown the rules first, which is what the official window does
 * before it offers the button.
 */
public class RoomCompetitionInitEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        Room room = this.currentRoom();
        RoomCompetition competition = RoomCompetitionSupport.running(null);

        if (user == null || room == null || competition == null) return;

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();
        int now = Emulator.getIntUnixTimestamp();

        if (room.getOwnerId() == user.getUserInfo().getId()) {
            if (!competition.submissionOpen(now)) return;

            RoomCompetitionSubmitState state = manager.submitState(user, room, competition);

            RoomCompetitionSupport.sendSubmitState(
                    this.client,
                    competition,
                    state.result() == RoomCompetitionResult.READY
                            ? RoomCompetitionSubmitState.of(RoomCompetitionResult.RULES)
                            : state);
            return;
        }

        if (!competition.votingOpen(now)) return;

        int entryId = RoomCompetitionSupport.votableEntryId(user, room, competition);

        if (entryId <= 0) return;

        int votesLeft = manager.votesLeft(user.getUserInfo().getId(), competition);

        RoomCompetitionSupport.sendVotingState(this.client, competition, RoomCompetitionResult.VOTE_ALLOWED, votesLeft);
    }
}
