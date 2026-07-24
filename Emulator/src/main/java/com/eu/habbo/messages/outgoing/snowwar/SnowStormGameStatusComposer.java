package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarGameEvent;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/**
 * Per-tick incremental update: 5 subturns of events plus the state checksum
 * (PROTOCOL.md 5015, README 13.6).
 */
public class SnowStormGameStatusComposer extends MessageComposer {

    private final int turn;
    private final int checksum;
    private final List<List<SnowWarGameEvent>> turnEventsList;

    public SnowStormGameStatusComposer(int turn, int checksum, List<List<SnowWarGameEvent>> turnEventsList) {
        this.turn = turn;
        this.checksum = checksum;
        this.turnEventsList = turnEventsList;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormGameStatusComposer);
        this.response.appendInt(this.turn);
        this.response.appendInt(this.checksum);
        this.response.appendInt(this.turnEventsList.size());

        for (List<SnowWarGameEvent> subturnEvents : this.turnEventsList) {
            this.response.appendInt(subturnEvents.size());

            for (SnowWarGameEvent event : subturnEvents) {
                event.serialize(this.response);
            }
        }

        return this.response;
    }
}
