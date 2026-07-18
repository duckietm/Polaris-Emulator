package com.eu.habbo.database.migrations;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;

import java.util.Arrays;

public final class MigrationTool {
    private MigrationTool() {
    }

    public static void main(String[] arguments) {
        if (!Arrays.asList(arguments).contains("--migrations-only")) {
            throw new IllegalArgumentException(
                    "MigrationTool requires --migrations-only to prevent normal emulator startup");
        }

        ConfigurationManager config = new ConfigurationManager("config.ini");
        MigrationOptions options = MigrationOptions.resolve(config, arguments, System.getenv());
        if (options.mode() == MigrationMode.OFF) {
            throw new IllegalArgumentException("MigrationTool does not support migration mode off");
        }

        Database database = new Database(config, options);
        database.dispose();
    }
}
