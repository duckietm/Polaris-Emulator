package com.eu.habbo.messages.incoming.furnieditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FurnidataStructurePayloadTest {

    private static FurnidataStructurePayload parse(String json) {
        return FurnidataStructurePayload.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void turnsTheBlockIntoRawJsonLiterals() {
        FurnidataStructurePayload payload =
                parse("{\"structure\":{\"xdim\":2,\"ydim\":1,\"height\":1.5,\"canstandon\":true,\"cansiton\":false}}");

        assertTrue(payload.valid());
        assertEquals(
                Map.of("xdim", "2", "ydim", "1", "height", "1.5", "canstandon", "true", "cansiton", "false"),
                payload.rawValues);
    }

    @Test
    void writesWholeNumbersWithoutADecimalPoint() {
        assertEquals("1", parse("{\"structure\":{\"height\":1.0}}").rawValues.get("height"));
        assertEquals(
                "0.25", parse("{\"structure\":{\"height\":0.251}}").rawValues.get("height"));
    }

    @Test
    void isEmptyAndValidWithoutTheBlock() {
        FurnidataStructurePayload payload = parse("{\"name\":\"x\"}");

        assertTrue(payload.valid());
        assertTrue(payload.isEmpty());
    }

    @Test
    void refusesUnknownFieldsAndValuesOutOfRange() {
        assertFalse(parse("{\"structure\":{\"id\":5}}").valid());
        assertFalse(parse("{\"structure\":{\"xdim\":0}}").valid());
        assertFalse(parse("{\"structure\":{\"xdim\":1.5}}").valid());
        assertFalse(parse("{\"structure\":{\"height\":100}}").valid());
        assertFalse(parse("{\"structure\":{\"canlayon\":\"yes\"}}").valid());
        assertFalse(parse("{\"structure\":[]}").valid());
        assertEquals("Invalid structure value for xdim", parse("{\"structure\":{\"xdim\":0}}").error);
    }
}
