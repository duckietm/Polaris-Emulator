package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.google.gson.Gson;

public class CreateModToolTicket extends RCONMessage<CreateModToolTicket.JSON> {
    public CreateModToolTicket() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        var issue = new ModToolIssue(json.sender_id, json.sender_username, json.reported_id, json.reported_username, json.reported_room_id, json.message, ModToolTicketType.NORMAL);
        Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
    }

    static class JSON {

        public int sender_id;


        public String sender_username;


        public int reported_id;


        public String reported_username;


        public int reported_room_id = 0;


        public String message;
    }
}
