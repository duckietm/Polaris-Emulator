package com.eu.habbo.messages.incoming.rooms.competition;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetition;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionManager;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionResult;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.OpenRoomCreationWindowComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.util.List;

/**
 * Takes the owner to a room of theirs that could still enter the competition. Somebody with no room
 * at all gets the room creator instead, which is the alert the official client shows for this.
 */
public class ForwardToASubmittableRoomEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        RoomCompetition competition = RoomCompetitionSupport.running(null);

        if (habbo == null || competition == null) return;

        List<Room> rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsForHabbo(habbo);

        if (rooms.isEmpty()) {
            this.client.sendResponse(new OpenRoomCreationWindowComposer());
            return;
        }

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();

        for (Room room : rooms) {
            if (manager.submitState(habbo, room, competition).result() == RoomCompetitionResult.READY) {
                this.client.sendResponse(new ForwardToRoomComposer(room.getId()));
                return;
            }
        }

        this.client.sendResponse(new ForwardToRoomComposer(rooms.getFirst().getId()));
    }
}
