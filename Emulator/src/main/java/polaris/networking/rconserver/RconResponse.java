package polaris.networking.rconserver;

import com.google.gson.Gson;
import polaris.messages.rcon.RCONMessage;

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
