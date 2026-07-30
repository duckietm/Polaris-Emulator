package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarLeaderboardRepository;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

/** AIR Game2WeeklyLeaderboardParser payload used by all four SnowStorm views. */
public class SnowStormLeaderboardComposer extends MessageComposer {

    private final int header;
    private final SnowWarLeaderboardRepository.Page page;

    public SnowStormLeaderboardComposer(int header, SnowWarLeaderboardRepository.Page page) {
        this.header = header;
        this.page = page;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(this.header);
        this.response.appendInt(this.page.year());
        this.response.appendInt(this.page.week());
        this.response.appendInt(this.page.maxOffset());
        this.response.appendInt(this.page.currentOffset());
        this.response.appendInt(this.page.minutesUntilReset());
        this.response.appendInt(this.page.entries().size());
        for (SnowWarLeaderboardRepository.Entry entry : this.page.entries()) {
            this.response.appendInt(entry.userId());
            this.response.appendInt(entry.score());
            this.response.appendInt(entry.rank());
            this.response.appendString(entry.name());
            this.response.appendString(entry.figure());
            this.response.appendString(entry.gender());
        }
        this.response.appendInt(this.page.totalListSize());
        this.response.appendInt(this.page.gameTypeId());
        return this.response;
    }
}
