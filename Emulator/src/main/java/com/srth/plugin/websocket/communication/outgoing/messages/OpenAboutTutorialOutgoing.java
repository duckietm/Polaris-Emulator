package com.srth.plugin.websocket.communication.outgoing.messages;

import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class OpenAboutTutorialOutgoing extends OutgoingMessage {
    public OpenAboutTutorialOutgoing(String header, int id) throws  SQLException {
        super("OpenAboutTutorial");
            this.data.addProperty("tuto_id", id);
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
