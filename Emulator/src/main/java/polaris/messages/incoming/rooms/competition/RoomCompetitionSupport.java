package polaris.messages.incoming.rooms.competition;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.hotel.rooms.competition.RoomCompetitionResult;
import polaris.hotel.rooms.competition.RoomCompetitionSubmitState;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.competition.CompetitionEntrySubmitResultComposer;
import polaris.messages.outgoing.rooms.competition.CompetitionVotingInfoComposer;

/**
 * The two answers every competition packet ends with, in one place: the submit window state and the
 * voting window state. Both carry the competition id and its code, which is what the client
 * localizes every text with.
 */
final class RoomCompetitionSupport {
    private RoomCompetitionSupport() {}

    static void sendSubmitState(GameClient client, RoomCompetition competition, RoomCompetitionSubmitState state) {
        if (state.result() == RoomCompetitionResult.NOTHING) return;

        client.sendResponse(new CompetitionEntrySubmitResultComposer(
                competition.id(), competition.code(), state.result(), state.requiredFurni(), state.missingFurni()));
    }

    /**
     * The voting window. The result code says whether the visitor is eligible at all (the renderer
     * derives its "voting allowed" flag from it being zero) and the votes left decide whether the
     * button is offered: somebody who used today's votes is eligible but has none left.
     */
    static void sendVotingState(GameClient client, RoomCompetition competition, int resultCode, int votesLeft) {
        client.sendResponse(
                new CompetitionVotingInfoComposer(competition.id(), competition.code(), resultCode, votesLeft));
    }

    /** The competition a packet names, or null when it is not the one that is running. */
    static RoomCompetition running(String goalCode) {
        RoomCompetition competition = RoomCompetitionManager.getInstance().active();

        if (competition == null) {
            return null;
        }

        return goalCode == null || goalCode.isEmpty() || competition.code().equals(goalCode) ? competition : null;
    }

    /** Whether this visitor may vote for the room they are in, and for which entry. */
    static int votableEntryId(User user, Room room, RoomCompetition competition) {
        if (user == null
                || room == null
                || room.getOwnerId() == user.getUserInfo().getId()) {
            return 0;
        }

        return RoomCompetitionManager.getInstance().entryId(room.getId(), competition);
    }
}
