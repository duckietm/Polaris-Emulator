package com.eu.habbo.networking.rconserver;

import com.eu.habbo.messages.rcon.RCONMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RconResponseContractTest {
    private final Gson gson = new Gson();

    @Test
    void infrastructureErrorsUseTheSameJsonEnvelopeAsCommandResponses() {
        String json = RconResponse.error(RCONMessage.STATUS_ERROR, "rate limited").toJson(gson);
        JsonObject response = gson.fromJson(json, JsonObject.class);

        assertEquals(RCONMessage.STATUS_ERROR, response.get("status").getAsInt());
        assertEquals("rate limited", response.get("message").getAsString());
        assertEquals(2, response.size());
    }

    @Test
    void infrastructureSuccessUsesStatusZero() {
        JsonObject response = gson.fromJson(RconResponse.success().toJson(gson), JsonObject.class);

        assertEquals(RCONMessage.STATUS_OK, response.get("status").getAsInt());
        assertEquals("", response.get("message").getAsString());
    }

    @Test
    void handlerAndServerDoNotReturnLegacyPlainTextErrors() throws Exception {
        String handler = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/eu/habbo/networking/rconserver/RCONServerHandler.java"));
        String server = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/eu/habbo/networking/rconserver/RCONServer.java"));

        assertTrue(handler.contains("RconResponse.error"));
        assertTrue(server.contains("RconResponse.error"));
    }
}
