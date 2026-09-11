package com.eu.habbo.messages.incoming.rooms.competition;

import com.eu.habbo.habbohotel.rooms.competition.RoomCompetition;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.competition.IsUserPartOfCompetitionComposer;

/** Whether the sender already entered a room, and which one. */
public class GetIsUserPartOfCompetitionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String goalCode = this.packet.readString();

        Habbo habbo = this.client.getHabbo();
        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (habbo == null || competition == null) {
            this.client.sendResponse(new IsUserPartOfCompetitionComposer(false, 0));
            return;
        }

        int roomId = RoomCompetitionManager.getInstance()
                .entryRoomId(habbo.getHabboInfo().getId(), competition);

        this.client.sendResponse(new IsUserPartOfCompetitionComposer(roomId > 0, roomId));
    }
}
