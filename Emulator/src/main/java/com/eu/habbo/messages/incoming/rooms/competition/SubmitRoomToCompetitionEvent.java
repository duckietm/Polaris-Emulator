package com.eu.habbo.messages.incoming.rooms.competition;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetition;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionManager;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionResult;
import com.eu.habbo.habbohotel.rooms.competition.RoomCompetitionSubmitState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

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

        Habbo habbo = this.client.getHabbo();
        Room room = this.currentRoom();
        RoomCompetition competition = RoomCompetitionSupport.running(goalCode);

        if (habbo == null || room == null || competition == null) return;

        if (!competition.submissionOpen(Emulator.getIntUnixTimestamp())) return;

        RoomCompetitionManager manager = RoomCompetitionManager.getInstance();
        RoomCompetitionSubmitState state = manager.submitState(habbo, room, competition);

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
            boolean entered = manager.submit(habbo, room, competition);

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
