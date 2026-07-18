package com.eu.habbo.database.integrity;

import java.util.Locale;

public enum IntegrityAuditMode {
    OFF,
    WARN,
    STRICT;

    static IntegrityAuditMode parse(String value) {
        if (value == null) throw new IllegalArgumentException("Integrity audit mode is required");
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> OFF;
            case "warn" -> WARN;
            case "strict" -> STRICT;
            default -> throw new IllegalArgumentException(
                    "Unsupported integrity audit mode '" + value
                            + "'; expected off, warn or strict.");
        };
    }
}
