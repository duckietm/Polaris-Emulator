package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowVentDeadth extends OutgoingMessage {
    public ShowVentDeadth(String header, Boolean show, JsonArray dataA) throws SQLException{
        super("ShowVentDeadth");

        int mostrar;
        if(show == true){mostrar= 1;} else{mostrar= 0;}
        this.data.addProperty("ver", mostrar);
        
        // this.data.add("view", dataA);
        this.data.add("view", dataA);

        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
