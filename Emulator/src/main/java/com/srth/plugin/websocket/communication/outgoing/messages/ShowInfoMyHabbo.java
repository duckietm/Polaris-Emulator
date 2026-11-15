package com.srth.plugin.websocket.communication.outgoing.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;

import java.sql.SQLException;

public class ShowInfoMyHabbo extends OutgoingMessage {
    public ShowInfoMyHabbo(String header, boolean show, JsonArray array) throws SQLException {

        super("ShowInfoMyHabbo");

        int mostrar;
        if (show == true) {
            mostrar = 1;
        } else {
            mostrar = 0;
        }
        this.data.addProperty("ver", mostrar);
        // this.data.add("view", array);
        this.data.add("view", array);

        // this.data.add("tasks", tasksobj);
        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
