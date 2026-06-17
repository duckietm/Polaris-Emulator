package com.eu.habbo.messages.incoming.furnieditor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FurniEditorUpdatePayload {
    public final String setClauses;
    public final List<Object> values;
    public final String error;

    private FurniEditorUpdatePayload(String setClauses, List<Object> values, String error) {
        this.setClauses = setClauses;
        this.values = values;
        this.error = error;
    }

    public static FurniEditorUpdatePayload validate(JsonObject json) {
        if (json == null || json.size() == 0) {
            return invalid("No fields to update");
        }

        StringBuilder setClauses = new StringBuilder();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String dbColumn = FurniEditorHelper.FIELD_MAP.get(entry.getKey());
            if (dbColumn == null || !FurniEditorHelper.ALLOWED_UPDATE_FIELDS.contains(dbColumn)) {
                continue;
            }

            Object value = validateValue(dbColumn, entry.getValue());
            if (value == null) {
                return invalid("Invalid value for " + entry.getKey());
            }

            if (setClauses.length() > 0) setClauses.append(", ");
            setClauses.append("`").append(dbColumn).append("` = ?");
            values.add(value);
        }

        if (setClauses.length() == 0) {
            return invalid("No valid fields to update");
        }

        return new FurniEditorUpdatePayload(setClauses.toString(), values, null);
    }

    public boolean valid() {
        return this.error == null;
    }

    private static FurniEditorUpdatePayload invalid(String error) {
        return new FurniEditorUpdatePayload("", List.of(), error);
    }

    private static Object validateValue(String dbColumn, JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return switch (dbColumn) {
            case "public_name" -> boundedString(primitive, 0, 56);
            case "type" -> itemType(primitive);
            case "width", "length" -> boundedInt(primitive, 0, 64);
            case "stack_height" -> boundedDouble(primitive, 0.0D, 99.99D);
            case "allow_stack", "allow_walk", "allow_sit", "allow_lay", "allow_gift",
                 "allow_trade", "allow_recycle", "allow_marketplace_sell", "allow_inventory_stack" -> booleanFlag(primitive);
            case "interaction_type" -> boundedString(primitive, 0, 500);
            case "interaction_modes_count" -> boundedInt(primitive, 0, 100);
            case "vending_ids", "clothing_on_walk" -> boundedString(primitive, 0, 255);
            case "customparams" -> boundedString(primitive, 0, 256);
            case "multiheight" -> boundedString(primitive, 0, 50);
            case "effect_id_male", "effect_id_female", "sprite_id" -> boundedInt(primitive, 0, Integer.MAX_VALUE);
            case "description" -> boundedString(primitive, 0, 500);
            default -> null;
        };
    }

    private static String boundedString(JsonPrimitive primitive, int minLength, int maxLength) {
        if (!primitive.isString()) return null;
        String value = primitive.getAsString();
        if (value.length() < minLength || value.length() > maxLength) return null;
        return value;
    }

    private static String itemType(JsonPrimitive primitive) {
        String value = boundedString(primitive, 1, 3);
        if (value == null) return null;
        return value.matches("[a-z]+") ? value : null;
    }

    private static Integer boundedInt(JsonPrimitive primitive, int min, int max) {
        try {
            int value = primitive.getAsInt();
            return value >= min && value <= max ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Double boundedDouble(JsonPrimitive primitive, double min, double max) {
        try {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) && value >= min && value <= max ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String booleanFlag(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? "1" : "0";
        }

        if (primitive.isNumber()) {
            int value = primitive.getAsInt();
            return value == 0 || value == 1 ? String.valueOf(value) : null;
        }

        if (primitive.isString()) {
            String value = primitive.getAsString();
            return "0".equals(value) || "1".equals(value) ? value : null;
        }

        return null;
    }
}
