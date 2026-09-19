package com.eu.habbo.messages.incoming.furnieditor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The optional {@code structure} block of a furnidata update (10046): the structural
 * fields of an entry an admin wants aligned with items_base. Values come back as raw
 * JSON literals ready for {@code FurnidataWriter.writeStructure}.
 */
public final class FurnidataStructurePayload {
    public final Map<String, String> rawValues;
    public final String error;

    private FurnidataStructurePayload(Map<String, String> rawValues, String error) {
        this.rawValues = rawValues;
        this.error = error;
    }

    public boolean valid() {
        return this.error == null;
    }

    public boolean isEmpty() {
        return this.rawValues.isEmpty();
    }

    /** Parses {@code json.structure}; an absent block is valid and empty. */
    public static FurnidataStructurePayload parse(JsonObject json) {
        Map<String, String> values = new LinkedHashMap<>();
        if (json == null || !json.has("structure")) return new FurnidataStructurePayload(values, null);

        JsonElement block = json.get("structure");
        if (!block.isJsonObject()) return new FurnidataStructurePayload(values, "structure must be an object");

        for (Map.Entry<String, JsonElement> entry : block.getAsJsonObject().entrySet()) {
            JsonElement el = entry.getValue();
            if (el == null || !el.isJsonPrimitive()) return invalid(entry.getKey());
            JsonPrimitive p = el.getAsJsonPrimitive();
            String raw =
                    switch (entry.getKey()) {
                        case "xdim", "ydim" -> intLiteral(p, 1, 64);
                        case "height" -> numberLiteral(p, 0.0D, 99.99D);
                        case "canstandon", "cansiton", "canlayon" ->
                            p.isBoolean() ? String.valueOf(p.getAsBoolean()) : null;
                        default -> null;
                    };
            if (raw == null) return invalid(entry.getKey());
            values.put(entry.getKey(), raw);
        }

        return new FurnidataStructurePayload(values, null);
    }

    private static FurnidataStructurePayload invalid(String field) {
        return new FurnidataStructurePayload(Map.of(), "Invalid structure value for " + field);
    }

    private static String intLiteral(JsonPrimitive p, int min, int max) {
        if (!p.isNumber()) return null;
        double d = p.getAsDouble();
        if (d != Math.rint(d) || d < min || d > max) return null;
        return String.valueOf((int) d);
    }

    private static String numberLiteral(JsonPrimitive p, double min, double max) {
        if (!p.isNumber()) return null;
        double d = p.getAsDouble();
        if (Double.isNaN(d) || d < min || d > max) return null;
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 100.0D) / 100.0D);
    }
}
