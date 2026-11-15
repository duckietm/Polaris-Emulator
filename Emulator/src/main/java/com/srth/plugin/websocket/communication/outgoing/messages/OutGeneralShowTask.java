package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class OutGeneralShowTask extends OutgoingMessage {
    public OutGeneralShowTask(String header, Boolean show, JsonArray dataArray) throws SQLException{
        super("ShowVentanaTaskActive");

        int mostrar;
        if(show == true){
            mostrar= 1;
        } else{
            mostrar= 0;
        }
        
        this.data.addProperty("show_button", show);
        this.data.addProperty("ver", mostrar);
        
        // this.data.add("list", "dataArray");
        this.data.add("button", dataArray);

        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
