package com.eu.habbo.messages.rcon;

import com.google.gson.*;

import java.lang.reflect.Type;

public abstract class RCONMessage<T> {

    public final static int STATUS_OK = 0;


    public final static int STATUS_ERROR = 1;


    public final static int HABBO_NOT_FOUND = 2;


    public final static int ROOM_NOT_FOUND = 3;


    public final static int SYSTEM_ERROR = 4;


    public final Class<T> type;
    public int status = STATUS_OK;
    public String message = "";

    public RCONMessage(Class<T> type) {
        this.type = type;
    }

    public abstract void handle(Gson gson, T json);

    public boolean validate(T json) {
        String validationError = RconPayloadValidator.validate(json);
        if (validationError == null) {
            return true;
        }

        this.status = STATUS_ERROR;
        this.message = validationError;
        return false;
    }

    @SuppressWarnings("rawtypes")
    public static class RCONMessageSerializer implements JsonSerializer<RCONMessage> {
        @Override
        public JsonElement serialize(final RCONMessage rconMessage, final Type type, final JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            result.add("status", new JsonPrimitive(rconMessage.status));
            result.add("message", new JsonPrimitive(rconMessage.message));
            return result;
        }
    }
}
