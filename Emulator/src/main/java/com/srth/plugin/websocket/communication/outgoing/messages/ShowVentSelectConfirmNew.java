package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;
import java.sql.SQLException;

public class ShowVentSelectConfirmNew extends OutgoingMessage {
    public ShowVentSelectConfirmNew(String header, Boolean show, JsonArray Myjson) throws SQLException{

        super("ShowVentSelectConfirm");

        int mostrar;
        if(show == true){mostrar= 1;} else{mostrar= 0;}
        
        this.data.addProperty("ver", mostrar);
        // this.data.add("items", itemsobj);
        this.dataArray.add(Myjson);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
