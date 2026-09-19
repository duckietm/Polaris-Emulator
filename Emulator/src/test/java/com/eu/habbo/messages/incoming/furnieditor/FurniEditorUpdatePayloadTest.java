package com.eu.habbo.messages.incoming.furnieditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class FurniEditorUpdatePayloadTest {
    private static final Predicate<String> ANY_INTERACTION = type -> true;
    private static final Predicate<String> REGISTERED = Set.of("default", "gate", "vendingmachine")::contains;

    @Test
    void acceptsSafeEditorFields() {
        FurniEditorUpdatePayload payload =
                FurniEditorUpdatePayload.validate(JsonParser.parseString("""
                {
                  "publicName": "Rare Chair",
                  "type": "s",
                  "width": 2,
                  "length": 1,
                  "stackHeight": 1.5,
                  "allowTrade": true,
                  "interactionModesCount": 3
                }
                """).getAsJsonObject(), ANY_INTERACTION);

        assertTrue(payload.valid());
        assertEquals(7, payload.values.size());
    }

    @Test
    void rejectsOutOfRangeAndOversizedFields() {
        assertFalse(FurniEditorUpdatePayload.validate(
                        JsonParser.parseString("{\"width\":-1}").getAsJsonObject(), ANY_INTERACTION)
                .valid());
        assertFalse(FurniEditorUpdatePayload.validate(
                        JsonParser.parseString("{\"stackHeight\":1000}").getAsJsonObject(), ANY_INTERACTION)
                .valid());
        assertFalse(FurniEditorUpdatePayload.validate(
                        JsonParser.parseString("{\"allowTrade\":2}").getAsJsonObject(), ANY_INTERACTION)
                .valid());
        assertFalse(FurniEditorUpdatePayload.validate(
                        JsonParser.parseString("{\"publicName\":\"" + "x".repeat(57) + "\"}")
                                .getAsJsonObject(),
                        ANY_INTERACTION)
                .valid());
    }

    @Test
    void ignoresUnknownFieldsButRequiresAtLeastOneValidField() {
        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(
                JsonParser.parseString("{\"itemName\":\"blocked\",\"unknown\":true}")
                        .getAsJsonObject(),
                ANY_INTERACTION);

        assertFalse(payload.valid());
        assertEquals("No valid fields to update", payload.error);
    }

    @Test
    void rejectsAnInteractionTypeNoClassIsRegisteredFor() {
        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(
                JsonParser.parseString("{\"interactionType\":\"wf_trg_typo\"}").getAsJsonObject(), REGISTERED);

        assertFalse(payload.valid());
        assertEquals("Unknown interaction type: wf_trg_typo (no class is registered for it)", payload.error);
    }

    @Test
    void acceptsARegisteredInteractionTypeRegardlessOfCase() {
        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(
                JsonParser.parseString("{\"interactionType\":\"VendingMachine\"}")
                        .getAsJsonObject(),
                REGISTERED);

        assertTrue(payload.valid());
        assertEquals("vendingmachine", payload.values.get(0));
    }

    @Test
    void acceptsAnEmptyInteractionTypeAsTheDefaultBehaviour() {
        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(
                JsonParser.parseString("{\"interactionType\":\"\"}").getAsJsonObject(), REGISTERED);

        assertTrue(payload.valid());
    }

    @Test
    void buildsCatalogItemIdsTokenPattern() {
        assertEquals("%,12,%", FurniEditorHelper.catalogItemIdsTokenPattern(12));
        assertTrue((",112,12,13,").contains(",12,"));
        assertFalse((",112,13,").contains(",12,"));
    }
}
