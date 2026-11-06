package polaris.messages.incoming.rooms.competition;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.hotel.rooms.competition.RoomCompetitionResult;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.OpenRoomCreationWindowComposer;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

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
        User user = this.client.getUser();
        RoomCompetition competition = RoomCompetitionSupport.running(null);

        if (user == null || competition == null) return;

        List<Room> rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsForUser(user);

        if (rooms.isEmpty()) {
            this.client.sendResponse(new OpenRoomCreationWindowComposer());
            return;
        }

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();

        for (Room room : rooms) {
            if (manager.submitState(user, room, competition).result() == RoomCompetitionResult.READY) {
                this.client.sendResponse(new ForwardToRoomComposer(room.getId()));
                return;
            }
        }

        this.client.sendResponse(new ForwardToRoomComposer(rooms.getFirst().getId()));
    }
}
