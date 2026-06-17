package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;

public class ForwardUser extends RCONMessage<ForwardUser.ForwardUserJSON> {

    public ForwardUser() {
        super(ForwardUserJSON.class);
    }

    @Override
    public void handle(Gson gson, ForwardUserJSON object) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(object.room_id);

            if (room != null) {
                if (habbo.getHabboInfo().getCurrentRoom() != null) {
                    Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, habbo.getHabboInfo().getCurrentRoom());
                }

                habbo.getClient().sendResponse(new ForwardToRoomComposer(object.room_id));
                Emulator.getGameEnvironment().getRoomManager().enterRoom(habbo, object.room_id, "", true);
                return;
            } else {
                this.status = RCONMessage.ROOM_NOT_FOUND;
                return;
            }
        }

        this.status = RCONMessage.HABBO_NOT_FOUND;
    }

    static class ForwardUserJSON {

        @Positive(message = "invalid user")
        public int user_id;


        @Positive(message = "invalid room")
        public int room_id;
    }
}
