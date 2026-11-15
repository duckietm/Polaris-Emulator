package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowInfoTutos extends OutgoingMessage {
    public ShowInfoTutos(String header, Boolean show, JsonArray dataTuto) throws SQLException{
        super("ShowInfoTutos");

            int mostrar;
            if(show == true){mostrar= 1;} else{ mostrar= 0;}

            this.data.addProperty("ver", mostrar);

            // this.data.add("name", JsonArray);
            this.data.add("datatuto", dataTuto);

            this.dataArray.add(this.data);
            this.outgoing.addProperty("header", header);
            this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
