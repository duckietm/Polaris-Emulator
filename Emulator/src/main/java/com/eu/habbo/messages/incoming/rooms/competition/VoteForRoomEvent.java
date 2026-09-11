package com.eu.habbo.messages.incoming.rooms.competition;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetition;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionManager;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionResult;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

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

        Habbo habbo = this.client.getHabbo();
        Room room = this.currentRoom();
        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (habbo == null || room == null || competition == null) return;

        if (!competition.votingOpen(Emulator.getIntUnixTimestamp())) return;

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();
        int entryId = RoomCompetitionSupport.votableEntryId(habbo, room, competition);
        int userId = habbo.getHabboInfo().getId();

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
