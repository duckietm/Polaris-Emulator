package com.eu.habbo.database.migrations;

import java.util.Locale;

public enum MigrationMode {
    VALIDATE,
    APPLY,
    OFF;

    public static MigrationMode parse(String value) {
        String normalized = value == null ? "validate" : value.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "", "validate" -> VALIDATE;
            case "apply" -> APPLY;
            case "off" -> OFF;
            default -> throw new IllegalArgumentException(
                    "Unsupported migration mode '" + value + "'; expected validate, apply or off");
        };
    }
}
