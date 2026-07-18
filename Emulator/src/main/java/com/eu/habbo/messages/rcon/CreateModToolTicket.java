package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class CreateModToolTicket extends RCONMessage<CreateModToolTicket.JSON> {
    public CreateModToolTicket() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        if (!RconUserLookup.userExists(json.sender_id) || !RconUserLookup.userExists(json.reported_id)) {
            this.status = HABBO_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        if (json.reported_room_id > 0 && Emulator.getGameEnvironment().getRoomManager().loadRoom(json.reported_room_id) == null) {
            this.status = ROOM_NOT_FOUND;
            this.message = "room not found";
            return;
        }

        ModToolIssue issue = new ModToolIssue(json.sender_id, json.sender_username, json.reported_id, json.reported_username, json.reported_room_id, json.message, ModToolTicketType.NORMAL);
        Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
    }

    static class JSON {
        @Positive(message = "invalid sender")
        public int sender_id;

        @NotBlank(message = "invalid sender username")
        @Size(max = 64, message = "invalid sender username")
        public String sender_username;

        @Positive(message = "invalid reported user")
        public int reported_id;

        @NotBlank(message = "invalid reported username")
        @Size(max = 64, message = "invalid reported username")
        public String reported_username;

        @PositiveOrZero(message = "invalid room")
        public int reported_room_id = 0;

        @NotBlank(message = "invalid message")
        @Size(max = 4096, message = "invalid message")
        public String message;
    }
}
