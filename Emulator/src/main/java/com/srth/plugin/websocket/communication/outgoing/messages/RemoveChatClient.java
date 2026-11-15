package com.srth.plugin.websocket.communication.outgoing.messages;

import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class RemoveChatClient extends OutgoingMessage {
    public RemoveChatClient(String header, Boolean remove) throws SQLException{
        super("RemoveChatClient");

        int mostrar;
        if(remove == true){
            mostrar= 1;
        } else{
            mostrar= 0;
        }
        
        this.data.addProperty("show_button", remove);
        this.data.addProperty("ver", mostrar);
        
        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
