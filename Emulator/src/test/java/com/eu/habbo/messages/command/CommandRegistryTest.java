package com.eu.habbo.messages.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.rcon.RCONMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class CommandRegistryTest {

    private final Gson gson = new Gson();

    @Test
    void keysAreNormalizedByRemovingUnderscoresAndLowerCasing() {
        assertEquals("givecredits", CommandRegistry.normalize("Give_Credits"));
        assertEquals("givecredits", CommandRegistry.normalize("GIVECREDITS"));
    }

    @Test
    void registrationIsMatchedCaseInsensitivelyAndIgnoringUnderscores() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("give_credits", RCONMessage.class);

        assertTrue(registry.isRegistered("GiveCredits"));
        assertTrue(registry.isRegistered("give_credits"));
        assertFalse(registry.isRegistered("unknown"));
        assertTrue(registry.getCommands().contains("givecredits"));
    }

    @Test
    void unknownCommandsReportTheStableErrorEnvelope() {
        CommandRegistry registry = new CommandRegistry();

        CommandResult result = registry.dispatch("does-not-exist", "{}");

        assertFalse(result.known());
        assertFalse(result.ok());
        assertEquals(RCONMessage.STATUS_ERROR, result.status());
        assertEquals("unknown command", result.message());
    }

    @Test
    void responseEnvelopeMatchesTheLegacyRconWireShape() {
        JsonObject response = gson.fromJson(CommandResult.unknownCommand().toResponseJson(), JsonObject.class);

        assertEquals(2, response.size());
        assertEquals(RCONMessage.STATUS_ERROR, response.get("status").getAsInt());
        assertEquals("unknown command", response.get("message").getAsString());
    }

    @Test
    void nullKeyIsTreatedAsUnknownRatherThanThrowing() {
        CommandRegistry registry = new CommandRegistry();

        CommandResult result = registry.dispatch(null, "{}");

        assertFalse(result.known());
        assertEquals(RCONMessage.STATUS_ERROR, result.status());
    }
}
