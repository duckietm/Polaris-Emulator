package com.eu.habbo.networking.gameserver.wired;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict JSON in and the response shapes out. Bodies are small, so they are read into a plain tree
 * with duplicate names, trailing data, deep nesting and non-integer numbers refused up front.
 */
final class WiredApiJson {
    static final Object NULL = new Object();
    private static final int MAX_DEPTH = 4;
    private static final int MAX_STRING = 256;
    private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]{0,9})");

    /** A number exactly as written; only integers inside the int range are ever accepted. */
    record JsonNumber(String literal) {}

    private WiredApiJson() {}

    static Map<String, Object> parseObject(byte[] body) {
        if (body == null || body.length == 0) {
            throw WiredApiException.badRequest("A JSON object body is required.");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException e) {
            throw WiredApiException.badRequest("The body is not valid UTF-8.");
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw WiredApiException.badRequest("The body must be a JSON object.");
            }
            Object value = readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw WiredApiException.badRequest("Unexpected data after the JSON object.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            return object;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw WiredApiException.badRequest("Malformed JSON.");
        }
    }

    private static Object readValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw WiredApiException.badRequest("JSON nested too deeply.");
        }
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                Map<String, Object> object = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.length() > MAX_STRING) {
                        throw WiredApiException.badRequest("JSON name too long.");
                    }
                    if (object.containsKey(name)) {
                        throw WiredApiException.badRequest("Duplicate JSON field '" + safe(name) + "'.");
                    }
                    object.put(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY -> {
                List<Object> array = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readValue(reader, depth + 1));
                }
                reader.endArray();
                return array;
            }
            case STRING -> {
                String value = reader.nextString();
                if (value.length() > MAX_STRING) {
                    throw WiredApiException.badRequest("JSON string too long.");
                }
                return value;
            }
            case NUMBER -> {
                return new JsonNumber(reader.nextString());
            }
            case BOOLEAN -> {
                return reader.nextBoolean();
            }
            case NULL -> {
                reader.nextNull();
                return NULL;
            }
            default -> throw WiredApiException.badRequest("Malformed JSON.");
        }
    }

    static void allowOnly(Map<String, Object> object, Set<String> fields) {
        for (String name : object.keySet()) {
            if (!fields.contains(name)) {
                throw WiredApiException.badRequest("Unknown field '" + safe(name) + "'.");
            }
        }
    }

    static boolean isInteger(Object value) {
        return value instanceof JsonNumber number
                && INTEGER.matcher(number.literal()).matches();
    }

    static int intValue(Object value, String field) {
        if (!(value instanceof JsonNumber number)
                || !INTEGER.matcher(number.literal()).matches()) {
            throw WiredApiException.badRequest("'" + field + "' must be a 32-bit integer.");
        }
        long parsed = Long.parseLong(number.literal());
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw WiredApiException.badRequest("'" + field + "' must be a 32-bit integer.");
        }
        return (int) parsed;
    }

    static String stringValue(Object value, String field) {
        if (!(value instanceof String text)) {
            throw WiredApiException.badRequest("'" + field + "' must be a string.");
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    static List<Object> arrayValue(Object value, String field) {
        if (!(value instanceof List<?>)) {
            throw WiredApiException.badRequest("'" + field + "' must be an array.");
        }
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> objectValue(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) {
            throw WiredApiException.badRequest("'" + field + "' must be an object.");
        }
        return (Map<String, Object>) value;
    }

    /** Echoes client text in an error message without letting it grow or carry control characters. */
    static String safe(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < 40; i++) {
            char c = text.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '?');
        }
        return out.toString();
    }

    static JsonObject entry(WiredApiRooms.Entry entry, boolean withEntityId) {
        JsonObject json = new JsonObject();
        if (withEntityId) {
            json.addProperty("entityId", entry.entityId());
        }
        if (entry.value() != null) {
            json.addProperty("value", entry.value());
        }
        json.addProperty("createdAt", entry.createdAt());
        json.addProperty("updatedAt", entry.updatedAt());
        return json;
    }

    static JsonObject variable(WiredApiRooms.Variable variable) {
        JsonObject json = new JsonObject();
        json.addProperty("name", variable.name());
        json.addProperty("scope", variable.scope().path());
        json.addProperty("hasValue", variable.hasValue());
        json.addProperty("textConnected", variable.textConnected());
        return json;
    }

    static String error(String code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message == null ? "" : message);
        JsonObject body = new JsonObject();
        body.add("error", error);
        return body.toString();
    }

    static JsonObject errorObject(WiredApiException exception) {
        JsonObject error = new JsonObject();
        error.addProperty("code", exception.code());
        error.addProperty("message", exception.getMessage());
        return error;
    }

    static JsonArray array(List<JsonObject> items) {
        JsonArray array = new JsonArray();
        items.forEach(array::add);
        return array;
    }
}
