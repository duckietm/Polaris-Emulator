package com.eu.habbo.messages.incoming.furnieditor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

public class FurniEditorResolverPreview {

    public static String build(Map<String, Object> item, String furniDataJson) {
        JsonObject root = new JsonObject();
        com.google.gson.JsonArray fields = new com.google.gson.JsonArray();

        JsonObject furniData = parseObject(furniDataJson);

        addField(fields, "name", "Name",
            getString(furniData, "name"),
            stringValue(item.get("public_name")),
            firstNonBlank(stringValue(item.get("public_name")), getString(furniData, "name"), stringValue(item.get("item_name"))));

        addField(fields, "description", "Description",
            getString(furniData, "description"),
            stringValue(item.get("description")),
            firstNonBlank(stringValue(item.get("description")), getString(furniData, "description"), ""));

        addField(fields, "classname", "Classname",
            getString(furniData, "classname"),
            stringValue(item.get("item_name")),
            firstNonBlank(stringValue(item.get("item_name")), getString(furniData, "classname"), ""));

        addField(fields, "width", "Width",
            getString(furniData, "xdim"),
            stringValue(item.get("width")),
            firstNonBlank(stringValue(item.get("width")), getString(furniData, "xdim"), "1"));

        addField(fields, "length", "Length",
            getString(furniData, "ydim"),
            stringValue(item.get("length")),
            firstNonBlank(stringValue(item.get("length")), getString(furniData, "ydim"), "1"));

        String furniDataStackHeight = getFirstString(furniData,
            "stackHeight", "stack_height", "stackheight", "height", "z");
        String dbStackHeight = stringValue(item.get("stack_height"));
        addField(fields, "stackHeight", "Stack Height",
            furniDataStackHeight,
            dbStackHeight,
            firstNonBlank(dbStackHeight, furniDataStackHeight, "0"));

        String furniDataInteractionType = getFirstString(furniData,
            "interactionType", "interaction_type", "interactiontype", "logic");
        String dbInteractionType = stringValue(item.get("interaction_type"));
        addField(fields, "interactionType", "Interaction",
            furniDataInteractionType,
            dbInteractionType,
            firstNonBlank(dbInteractionType, furniDataInteractionType, "default"));

        root.add("fields", fields);
        return root.toString();
    }

    private static JsonObject parseObject(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return new JsonObject();

        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void addField(com.google.gson.JsonArray fields, String key, String label, String jsonValue, String dbValue, String resolvedValue) {
        JsonObject field = new JsonObject();
        String normalizedJson = jsonValue == null ? "" : jsonValue;
        String normalizedDb = dbValue == null ? "" : dbValue;
        String normalizedResolved = resolvedValue == null ? "" : resolvedValue;

        field.addProperty("key", key);
        field.addProperty("label", label);
        field.addProperty("jsonValue", normalizedJson);
        field.addProperty("dbValue", normalizedDb);
        field.addProperty("resolvedValue", normalizedResolved);
        field.addProperty("source", !normalizedDb.isBlank() ? "items_base" : (!normalizedJson.isBlank() ? "json" : "fallback"));
        field.addProperty("different", !normalizedJson.isBlank() && !normalizedDb.isBlank() && !normalizedJson.equals(normalizedDb));
        fields.add(field);
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        JsonElement value = object.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static String getFirstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = getString(object, key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
