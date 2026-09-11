package com.eu.habbo.messages.incoming.rooms.competition;

import com.eu.habbo.habbohotel.rooms.competition.RoomCompetition;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;

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

        if (RoomCompetitionManager.getInstance().entryId(roomId, competition) <= 0) return;

        this.client.sendResponse(new ForwardToRoomComposer(roomId));
    }
}
