package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The names a Text Output add-on may save (Username, Furni Name, Variable), as Habbo checks them (observed on AIR
 * WIN63-202610201032): the name, lowercased, 1 to 40 letters, digits or "_", not {@code trigger_text}; with every
 * value written, a delimiter of 1 to 5 characters. A name is refused, never cut.
 */
final class WiredPlaceholderNameRules {
    static final int MAX_NAME_LENGTH = 40;
    static final int MAX_DELIMITER_LENGTH = 5;

    private static final Pattern SYNTAX = Pattern.compile("^[a-z0-9_]+$");
    private static final Pattern WRAPPED = Pattern.compile("^\\$\\((.*)\\)$");

    private WiredPlaceholderNameRules() {}

    /** The name as stored: unwrapped from {@code $(...)} and lowercased. */
    static String storedName(String name) {
        String unwrapped = name == null ? "" : name.replace("\r", "").replace("\n", "");
        var wrapped = WRAPPED.matcher(unwrapped.trim());
        if (wrapped.matches()) unwrapped = wrapped.group(1);
        return unwrapped.toLowerCase(Locale.ROOT);
    }

    /** The error key that refuses the save, or null when it may be saved. */
    static String refusal(String name, boolean multiple, String delimiter) {
        String stored = storedName(name);
        if (stored.isEmpty() || stored.length() > MAX_NAME_LENGTH) {
            return "wiredfurni.error.placeholder.name_length";
        }
        if (!SYNTAX.matcher(stored).matches()) {
            return "wiredfurni.error.placeholder.name_syntax";
        }
        if ("trigger_text".equals(stored)) {
            return "wiredfurni.error.placeholder.reserved";
        }
        if (multiple) {
            String d = delimiter == null ? "" : delimiter;
            if (d.isEmpty()) return "wiredfurni.error.placeholder.delimiter_empty";
            if (d.length() > MAX_DELIMITER_LENGTH) return "wiredfurni.error.placeholder.delimiter_length";
        }
        return null;
    }
}
