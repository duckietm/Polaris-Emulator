package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;


public class MessageManagerDoubleObjet extends OutgoingMessage {

    public MessageManagerDoubleObjet(String header, Boolean show, Integer message, JsonArray array, JsonObject objeto) throws SQLException {
        super("MessageManager");

        int mostrar = (show) ? 1 : 0;
        
        this.data.addProperty("ver", mostrar);
        this.data.addProperty("message", message);

        // Añadimos el array de items del usuario
        this.data.add("view", array);
        
        // IMPORTANTE: Si objeto es null, inicializalo para evitar que GSON lo ignore
        if (objeto == null) {
            objeto = new JsonObject();
        }
        this.data.add("objeto", objeto);

        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}