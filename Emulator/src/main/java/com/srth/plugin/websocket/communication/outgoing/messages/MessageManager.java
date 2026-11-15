package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class MessageManager extends OutgoingMessage {

    public MessageManager(String header, Boolean show, Integer message, JsonArray array) throws SQLException{
        super("MessageManager");

        int mostrar;
        if(show == true){mostrar= 1;} else{mostrar= 0;}
        this.data.addProperty("ver", mostrar);
        this.data.addProperty("message", message);

        // this.data.add("view", array);
        this.data.add("view", array);

        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
