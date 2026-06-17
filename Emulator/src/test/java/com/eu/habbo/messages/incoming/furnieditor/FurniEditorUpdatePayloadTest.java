package com.eu.habbo.messages.incoming.furnieditor;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurniEditorUpdatePayloadTest {
    @Test
    void acceptsSafeEditorFields() {
        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(JsonParser.parseString("""
                {
                  "publicName": "Rare Chair",
                  "type": "s",
                  "width": 2,
                  "length": 1,
                  "stackHeight": 1.5,
                  "allowTrade": true,
                  "interactionModesCount": 3
                }
                """).getAsJsonObject());

        assertTrue(payload.valid());
        assertEquals(7, payload.values.size());
    }

    @Test
    void rejectsOutOfRangeAndOversizedFields() {
        assertFalse(FurniEditorUpdatePayload.validate(JsonParser.parseString("{\"width\":-1}").getAsJsonObject()).valid());
        assertFalse(FurniEditorUpdatePayload.validate(JsonParser.parseString("{\"stackHeight\":1000}").getAsJsonObject()).valid());
        assertFalse(FurniEditorUpdatePayload.validate(JsonParser.parseString("{\"allowTrade\":2}").getAsJsonObject()).valid());
        assertFalse(FurniEditorUpdatePayload.validate(JsonParser.parseString("{\"publicName\":\"" + "x".repeat(57) + "\"}").getAsJsonObject()).valid());
    }

    @Test
    void ignoresUnknownFieldsButRequiresAtLeastOneValidField() {
        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(
                JsonParser.parseString("{\"itemName\":\"blocked\",\"unknown\":true}").getAsJsonObject());

        assertFalse(payload.valid());
        assertEquals("No valid fields to update", payload.error);
    }

    @Test
    void buildsCatalogItemIdsTokenPattern() {
        assertEquals("%,12,%", FurniEditorHelper.catalogItemIdsTokenPattern(12));
        assertTrue((",112,12,13,").contains(",12,"));
        assertFalse((",112,13,").contains(",12,"));
    }
}
