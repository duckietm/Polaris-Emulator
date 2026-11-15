package com.srth.plugin.websocket.communication.outgoing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public abstract class OutgoingMessage {
    public final String header;

    public JsonObject outgoing;
    public JsonObject data;
    public JsonArray dataArray;
    public OutgoingMessage(String header){
        this.header = header;
        this.dataArray = new JsonArray();
        this.data = new JsonObject();
        this.outgoing = new JsonObject();
    }

    public abstract String execute();

}
