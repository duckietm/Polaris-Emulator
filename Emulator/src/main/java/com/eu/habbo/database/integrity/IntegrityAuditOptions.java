package com.eu.habbo.database.integrity;

import java.util.Locale;
import java.util.Optional;

public record IntegrityAuditOptions(Optional<IntegrityAuditMode> modeOverride) {
    public IntegrityAuditOptions {
        modeOverride = modeOverride == null ? Optional.empty() : modeOverride;
    }

    public static IntegrityAuditOptions parse(String[] arguments) {
        IntegrityAuditMode override = null;
        for (String argument : arguments) {
            String normalized = argument.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("--integrity=")) {
                if (override != null) {
                    throw new IllegalArgumentException(
                            "Integrity audit mode was specified more than once.");
                }
                override = IntegrityAuditMode.parse(
                        argument.substring("--integrity=".length()));
            } else if (normalized.startsWith("--integrity")) {
                throw new IllegalArgumentException(
                        "Unrecognised integrity option '" + argument
                                + "'; expected --integrity=off, --integrity=warn or --integrity=strict.");
            }
        }
        return new IntegrityAuditOptions(Optional.ofNullable(override));
    }
}
