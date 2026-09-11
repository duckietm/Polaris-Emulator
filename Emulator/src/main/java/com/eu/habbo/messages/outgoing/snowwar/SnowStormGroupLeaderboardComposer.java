package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarLeaderboardRepository;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

/**
 * AIR Game2GroupLeaderboardParser (all-time group 1769) and
 * Game2WeeklyGroupLeaderboardParser (weekly group 2956): the same group page,
 * the weekly variant prefixed with the week header. Both end with the viewer's
 * own favourite guild so the list can highlight its row.
 */
public class SnowStormGroupLeaderboardComposer extends MessageComposer {

    private final int header;
    private final boolean weekly;
    private final SnowWarLeaderboardRepository.GroupPage groupPage;

    public SnowStormGroupLeaderboardComposer(
            int header, boolean weekly, SnowWarLeaderboardRepository.GroupPage groupPage) {
        this.header = header;
        this.weekly = weekly;
        this.groupPage = groupPage;
    }

    @Override
    protected ServerMessage composeInternal() {
        SnowWarLeaderboardRepository.Page page = this.groupPage.page();
        this.response.init(this.header);
        if (this.weekly) {
            this.response.appendInt(page.year());
            this.response.appendInt(page.week());
            this.response.appendInt(page.maxOffset());
            this.response.appendInt(page.currentOffset());
            this.response.appendInt(page.minutesUntilReset());
        }
        this.response.appendInt(page.entries().size());
        for (SnowWarLeaderboardRepository.Entry entry : page.entries()) {
            this.response.appendInt(entry.userId());
            this.response.appendInt(entry.score());
            this.response.appendInt(entry.rank());
            this.response.appendString(entry.name());
            this.response.appendString(entry.figure());
            this.response.appendString(entry.gender());
        }
        this.response.appendInt(page.totalListSize());
        this.response.appendInt(page.gameTypeId());
        this.response.appendInt(this.groupPage.favouriteGroupId());
        return this.response;
    }
}
