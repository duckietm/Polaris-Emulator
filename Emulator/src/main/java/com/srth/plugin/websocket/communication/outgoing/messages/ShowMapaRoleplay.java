package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowMapaRoleplay extends OutgoingMessage {
    public ShowMapaRoleplay(String header, Boolean show, JsonArray dataArray, JsonArray TypesRooms) throws SQLException{
        super("ShowMapaRoleplay");

        int mostrar;
        if(show == true){
            mostrar= 1;
        } else{
            mostrar= 0;
        }
        
        this.data.addProperty("ver", mostrar);
        // this.data.add("list", "dataArray");
        this.data.add("list", dataArray);
        this.data.add("types", TypesRooms);

        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
