package com.eu.habbo.database.backup;

import java.util.List;

@FunctionalInterface
public interface MigrationBackup {
    void beforeMigrations(List<String> pendingVersions);

    static MigrationBackup disabled() {
        return pendingVersions -> {
        };
    }
}
