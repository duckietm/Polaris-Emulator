package com.eu.habbo.habbohotel.wired.link;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a send box puts through a WIRED Link: a message (at most 1000 bytes of text, the official
 * editor's limit) or a dataframe (at most 12 named values of at most 200 bytes each).
 */
public record WiredLinkPayload(Kind kind, String message, Map<String, String> values) {
    public static final int MAX_MESSAGE_BYTES = 1000;
    public static final int MAX_ROWS = 12;
    public static final int MAX_VALUE_BYTES = 200;

    public enum Kind {
        MESSAGE,
        DATAFRAME
    }

    public WiredLinkPayload {
        message = message == null ? "" : message;
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static WiredLinkPayload message(String text) {
        return new WiredLinkPayload(Kind.MESSAGE, truncate(text, MAX_MESSAGE_BYTES), Map.of());
    }

    public static WiredLinkPayload dataframe(Map<String, String> rows) {
        LinkedHashMap<String, String> capped = new LinkedHashMap<>();
        if (rows != null) {
            for (Map.Entry<String, String> row : rows.entrySet()) {
                if (capped.size() >= MAX_ROWS) break;
                if (row.getKey() == null || row.getValue() == null) continue;
                capped.put(row.getKey(), truncate(row.getValue(), MAX_VALUE_BYTES));
            }
        }
        return new WiredLinkPayload(Kind.DATAFRAME, "", capped);
    }

    /** The longest prefix of the text that fits the byte budget in UTF-8, never splitting a character. */
    public static String truncate(String text, int maxBytes) {
        if (text == null) return "";
        if (text.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return text;
        int bytes = 0;
        int end = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) break;
            bytes += size;
            end += Character.charCount(codePoint);
        }
        return text.substring(0, end);
    }
}
