package com.eu.habbo.habbohotel.rooms.competition;

import java.util.List;

/**
 * One room competition as the hotel configured it. The client only ever sees {@link #code()}, which
 * it uses to localize every text as {@code roomcompetition.<code>.*}; the schedule and the entry
 * rules never leave the server.
 */
public record RoomCompetition(
        int id,
        String code,
        String name,
        List<String> requiredFurni,
        int votesPerUser,
        int submitStarts,
        int submitEnds,
        int voteStarts,
        int voteEnds,
        int roomsCreatedAfter) {

    /** Whether a room built at this moment may enter; zero leaves the rule off. */
    public boolean acceptsRoomCreatedAt(int createdAt) {
        return this.roomsCreatedAfter <= 0 || createdAt > this.roomsCreatedAfter;
    }

    public boolean submissionOpen(int now) {
        return this.submitStarts <= now && now < this.submitEnds;
    }

    public boolean votingOpen(int now) {
        return this.voteStarts <= now && now < this.voteEnds;
    }
}
