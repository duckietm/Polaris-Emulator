package com.eu.habbo.networking.rconserver;

import com.eu.habbo.messages.rcon.RCONMessage;
import com.google.gson.Gson;

record RconResponse(int status, String message) {
    static RconResponse success() {
        return new RconResponse(RCONMessage.STATUS_OK, "");
    }

    static RconResponse error(int status, String message) {
        return new RconResponse(status, message == null ? "" : message);
    }

    String toJson(Gson gson) {
        return gson.toJson(this);
    }
}
