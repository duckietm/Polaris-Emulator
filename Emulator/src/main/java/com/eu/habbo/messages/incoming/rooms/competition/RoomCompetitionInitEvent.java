package com.eu.habbo.messages.incoming.rooms.competition;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetition;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionManager;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionResult;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionSubmitState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

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
        Habbo habbo = this.client.getHabbo();
        Room room = this.currentRoom();
        RoomCompetition competition = RoomCompetitionSupport.running(null);

        if (habbo == null || room == null || competition == null) return;

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();
        int now = Emulator.getIntUnixTimestamp();

        if (room.getOwnerId() == habbo.getHabboInfo().getId()) {
            if (!competition.submissionOpen(now)) return;

            RoomCompetitionSubmitState state = manager.submitState(habbo, room, competition);

            RoomCompetitionSupport.sendSubmitState(
                    this.client,
                    competition,
                    state.result() == RoomCompetitionResult.READY
                            ? RoomCompetitionSubmitState.of(RoomCompetitionResult.RULES)
                            : state);
            return;
        }

        if (!competition.votingOpen(now)) return;

        int entryId = RoomCompetitionSupport.votableEntryId(habbo, room, competition);

        if (entryId <= 0) return;

        int votesLeft = manager.votesLeft(habbo.getHabboInfo().getId(), competition);

        RoomCompetitionSupport.sendVotingState(this.client, competition, RoomCompetitionResult.VOTE_ALLOWED, votesLeft);
    }
}
