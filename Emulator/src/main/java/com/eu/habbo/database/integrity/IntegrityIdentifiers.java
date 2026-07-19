package com.eu.habbo.database.integrity;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class IntegrityIdentifiers {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern CHECK_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private IntegrityIdentifiers() {
    }

    static String identifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    static List<String> identifiers(List<String> values, String label) {
        Objects.requireNonNull(values, label);
        if (values.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        List<String> copy = List.copyOf(values);
        copy.forEach(value -> identifier(value, label));
        if (copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException(label + " contains duplicate columns");
        }
        return copy;
    }

    static String checkId(String value) {
        Objects.requireNonNull(value, "check id");
        if (!CHECK_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid check id: " + value);
        }
        return value;
    }

    static String description(String value) {
        Objects.requireNonNull(value, "description");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 300) {
            throw new IllegalArgumentException("Description must contain 1 to 300 characters");
        }
        return normalized;
    }
}
