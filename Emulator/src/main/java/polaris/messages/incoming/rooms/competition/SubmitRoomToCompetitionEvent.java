package polaris.messages.incoming.rooms.competition;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.competition.RoomCompetition;
import polaris.hotel.rooms.competition.RoomCompetitionManager;
import polaris.hotel.rooms.competition.RoomCompetitionResult;
import polaris.hotel.rooms.competition.RoomCompetitionSubmitState;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

/**
 * The three steps of entering a room, driven by the level the window is at: it accepts the rules,
 * then asks to submit, then confirms. Level zero is the window asking again because something in the
 * room changed. Every step re-reads the room state, so furniture removed between two clicks is
 * caught, and only the last step writes anything.
 */
public class SubmitRoomToCompetitionEvent extends MessageHandler {
    static final int LEVEL_REFRESH = 0;

    static final int LEVEL_RULES_ACCEPTED = 1;

    static final int LEVEL_SUBMIT = 2;

    static final int LEVEL_CONFIRM = 3;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String goalCode = this.packet.readString();
        int level = this.packet.readInt();

        User user = this.client.getUser();
        Room room = this.currentRoom();
        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (user == null || room == null || competition == null) return;

        if (!competition.submissionOpen(Emulator.getIntUnixTimestamp())) return;

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();
        RoomCompetitionSubmitState state = manager.submitState(user, room, competition);

        if (state.result() != RoomCompetitionResult.READY || level == LEVEL_REFRESH) {
            RoomCompetitionSupport.sendSubmitState(this.client, competition, state);
            return;
        }

        if (level == LEVEL_SUBMIT) {
            RoomCompetitionSupport.sendSubmitState(
                    this.client, competition, RoomCompetitionSubmitState.of(RoomCompetitionResult.CONFIRM));
            return;
        }

        if (level == LEVEL_CONFIRM) {
            boolean entered = manager.submit(user, room, competition);

            RoomCompetitionSupport.sendSubmitState(
                    this.client,
                    competition,
                    RoomCompetitionSubmitState.of(
                            entered ? RoomCompetitionResult.SUBMITTED : RoomCompetitionResult.ROOM_NOT_ELIGIBLE));
            return;
        }

        if (level == LEVEL_RULES_ACCEPTED) {
            RoomCompetitionSupport.sendSubmitState(this.client, competition, state);
        }
    }
}
