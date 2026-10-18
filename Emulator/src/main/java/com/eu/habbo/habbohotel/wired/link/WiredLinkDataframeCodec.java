package com.eu.habbo.habbohotel.wired.link;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The dataframe storage of the send and receive link boxes, as the official editor writes it (AIR
 * {@code LinkDataframeCodec}): the string param is a JSON array of {@code [key, text]} rows, one per
 * row in use, the int params hold the row count and each row's kind, and there are always 12 variable
 * ids. The same validation runs here so a crafted save cannot store what the editor would refuse.
 */
public final class WiredLinkDataframeCodec {
    public static final int MAX_ROWS = 12;
    public static final int MAX_TEXT_BYTES = 3000;
    public static final int MAX_MESSAGE_BYTES = 200;
    public static final int MAX_KEY_LENGTH = 40;

    /** Send row modes. */
    public static final int NUMBER_LITERAL = 0;

    public static final int NUMBER_VARIABLE = 1;
    public static final int VARIABLE_TEXT = 2;
    public static final int CUSTOM_MESSAGE = 3;

    /** Receive row kinds. */
    public static final int PLACEHOLDER_DESTINATION = 0;

    public static final int VARIABLE_DESTINATION = 1;

    public static final String INVALID_DATAFRAME = "wiredfurni.error.link.invalid_dataframe";
    public static final String KEY_LENGTH = "wiredfurni.error.link.key_length";
    public static final String NAME_SYNTAX = "wiredfurni.error.variables.name_syntax";
    public static final String NAME_UNIQUE = "wiredfurni.error.variables.name_uniq";
    public static final String PLACEHOLDER_LENGTH = "wiredfurni.error.placeholder.name_length";
    public static final String PLACEHOLDER_SYNTAX = "wiredfurni.error.placeholder.name_syntax";
    public static final String PLACEHOLDER_RESERVED = "wiredfurni.error.placeholder.reserved";
    public static final String DESTINATION_CONFLICT = "wiredfurni.error.link.destination_conflict";
    public static final String INVALID_CAPTURE_VARIABLE = "wiredfurni.error.link.invalid_capture_variable";
    public static final String CONFIGURATION_TOO_LARGE = "wiredfurni.error.link.configuration_too_large";
    public static final String TOO_MUCH_TEXT = "wiredfurni.error.too_much_text";
    public static final String SELECT_SOURCE_VARIABLE = "wiredfurni.params.link.select_source_variable";

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern LITERAL = Pattern.compile("-?\\d+");

    private WiredLinkDataframeCodec() {}

    /** One stored row. */
    public record Row(String key, String text) {}

    /** The rows of the string param, or null when it does not hold exactly {@code count} rows. */
    public static List<Row> decode(String json, int count) {
        if (count < 0 || count > MAX_ROWS) return null;
        if ((json == null || json.isEmpty()) && count == 0) return List.of();
        if (json == null || json.isEmpty()) return null;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return null;
            JsonArray array = root.getAsJsonArray();
            if (array.size() != count) return null;
            List<Row> rows = new ArrayList<>(count);
            for (JsonElement element : array) {
                if (!element.isJsonArray() || element.getAsJsonArray().size() != 2) return null;
                JsonElement key = element.getAsJsonArray().get(0);
                JsonElement text = element.getAsJsonArray().get(1);
                if (!isString(key) || !isString(text)) return null;
                rows.add(new Row(key.getAsString(), text.getAsString()));
            }
            return rows;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }

    /** Underscores and spaces are the same, outer blanks go, and keys are lower case. */
    public static String normalizeKey(String key) {
        if (key == null) return "";
        String spaced = key.replace('_', ' ');
        int start = 0;
        int end = spaced.length();
        while (start < end && spaced.charAt(start) <= ' ') start++;
        while (end > start && spaced.charAt(end - 1) <= ' ') end--;
        return spaced.substring(start, end).replace(' ', '_').toLowerCase(Locale.ROOT);
    }

    public static String keyError(String key) {
        String normalized = normalizeKey(key);
        if (normalized.isEmpty() || normalized.length() > MAX_KEY_LENGTH) return KEY_LENGTH;
        if (!WORD.matcher(normalized).matches()) return NAME_SYNTAX;
        return null;
    }

    public static String placeholderError(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_KEY_LENGTH) return PLACEHOLDER_LENGTH;
        if (!WORD.matcher(name).matches()) return PLACEHOLDER_SYNTAX;
        if (name.equalsIgnoreCase("trigger_text")) return PLACEHOLDER_RESERVED;
        return null;
    }

    /** The official variable targets: 0 furni, 1 user, -10 global, -20 context. */
    public static boolean isTarget(int target) {
        return target == 0 || target == 1 || target == -10 || target == -20;
    }

    public static boolean isLiteral(String text) {
        if (text == null || !LITERAL.matcher(text).matches()) return false;
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int utf8Length(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    /** The official size check of a whole configuration. */
    public static String storageError(String stringParam, int[] intParams, String[] variableIds) {
        if (utf8Length(stringParam) > MAX_TEXT_BYTES) return CONFIGURATION_TOO_LARGE;
        StringBuilder ints = new StringBuilder();
        for (int i = 0; i < intParams.length; i++) {
            if (i > 0) ints.append('|');
            ints.append(intParams[i]);
        }
        if (ints.length() > 400 || String.join("|", variableIds).length() > 240) return CONFIGURATION_TOO_LARGE;
        return null;
    }

    /** The 12 variable ids of a save, padded with empty ids. */
    public static String[] twelveVariableIds(String[] variableIds) {
        String[] ids = new String[MAX_ROWS];
        for (int i = 0; i < MAX_ROWS; i++) {
            String id = variableIds != null && i < variableIds.length ? variableIds[i] : null;
            ids[i] = id == null ? "" : id;
        }
        return ids;
    }

    /** A variable id as the editor sends it: a bare id or our {@code custom:<id>} token; 0 when none. */
    public static int parseVariableId(String text) {
        if (text == null) return 0;
        String value = text.trim();
        if (value.startsWith("custom:")) value = value.substring("custom:".length());
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
