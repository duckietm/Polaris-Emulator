package com.srth.plugin.websocket.communication.outgoing.messages.outworks;

import com.google.gson.JsonArray;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class OutShowUserActions extends OutgoingMessage {
    public OutShowUserActions(String header, boolean show, JsonArray dataArray) throws SQLException {
        super("ShowUserActions");

        int total_show;
        if (show == true) {
            total_show = 1;
        } else {
            total_show = 0;
        }

        this.data.addProperty("show", total_show);
        this.data.addProperty("ver", total_show);

        // this.data.add("button", jsonObject);
        this.data.add("button", dataArray);

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
