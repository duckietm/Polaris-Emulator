package com.eu.habbo.database.migration;

import java.util.Locale;

/** Command-line migration options for deployment and troubleshooting. */
public record MigrationOptions(Mode mode, boolean migrationsOnly) {

    public enum Mode {
        CONFIGURED,
        APPLY,
        VALIDATE
    }

    public static MigrationOptions parse(String[] arguments) {
        Mode mode = Mode.CONFIGURED;
        boolean migrationsOnly = false;

        for (String argument : arguments) {
            if ("--migrations-only".equals(argument)) {
                migrationsOnly = true;
                continue;
            }
            if (argument.startsWith("--migrations=")) {
                String value = argument.substring("--migrations=".length())
                        .trim()
                        .toLowerCase(Locale.ROOT);
                mode = switch (value) {
                    case "apply" -> Mode.APPLY;
                    case "validate", "status" -> Mode.VALIDATE;
                    case "off" -> throw new IllegalArgumentException(
                            "--migrations=off is intentionally not supported. "
                                    + "Set db.migrate.on_startup=false explicitly in config.ini instead.");
                    default -> throw new IllegalArgumentException(
                            "Unsupported migration mode '" + value + "'; expected apply or validate.");
                };
                continue;
            }
            // A silently ignored near-miss (--migration=apply, --migrations-only=true)
            // would boot the hotel when the operator asked for a migration step.
            if (argument.toLowerCase(Locale.ROOT).startsWith("--migration")) {
                throw new IllegalArgumentException(
                        "Unrecognised migration option '" + argument
                                + "'; expected --migrations=apply, --migrations=validate or --migrations-only.");
            }
        }

        return new MigrationOptions(mode, migrationsOnly);
    }
}
