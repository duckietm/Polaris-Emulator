package polaris.messages.incoming.rooms.competition;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

/** Sends the visitor to another room of the competition, never the one they are already in. */
public class ForwardToRandomCompetitionRoomEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String goalCode = this.packet.readString();

        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (competition == null) return;

        Room room = this.currentRoom();
        int roomId = RoomCompetitionManager.getInstance().randomEntryRoom(competition, room == null ? 0 : room.getId());

        if (roomId <= 0) return;

        this.client.sendResponse(new ForwardToRoomComposer(roomId));
    }
}
