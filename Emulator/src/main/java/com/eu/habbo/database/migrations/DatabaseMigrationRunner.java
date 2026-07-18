package com.eu.habbo.database.migrations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DatabaseMigrationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private static final int MAX_LOCK_TIMEOUT_SECONDS = 60;
    private static final String LOCK_PREFIX = "polaris-migrations-";

    private final Connection connection;
    private final MigrationCatalog catalog;
    private final int lockTimeoutSeconds;
    private final String lockName;

    public DatabaseMigrationRunner(
            Connection connection,
            MigrationCatalog catalog,
            int lockTimeoutSeconds) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        if (lockTimeoutSeconds < 1 || lockTimeoutSeconds > MAX_LOCK_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("lockTimeoutSeconds must be between 1 and 60");
        }
        this.lockTimeoutSeconds = lockTimeoutSeconds;
        this.lockName = lockName(connection);
    }

    public MigrationReport run(MigrationMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (mode == MigrationMode.OFF) return MigrationReport.off();

        boolean acquired = false;
        RuntimeException failure = null;
        try {
            acquireLock();
            acquired = true;
            return runLocked(mode);
        } catch (SQLException error) {
            failure = new MigrationValidationException("Unable to validate migration state", error);
            throw failure;
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } finally {
            if (acquired) {
                try {
                    releaseLock();
                } catch (SQLException | RuntimeException releaseError) {
                    if (failure != null) {
                        failure.addSuppressed(releaseError);
                    } else {
                        throw new MigrationValidationException(
                                "Unable to release database migration lock", releaseError);
                    }
                }
            }
        }
    }

    private MigrationReport runLocked(MigrationMode mode) throws SQLException {
        MigrationHistoryRepository repository = new MigrationHistoryRepository(connection);
        if (!repository.historyTableExists()) {
            if (!repository.isRecognizablePolarisSchema()) {
                throw new MigrationValidationException(
                        "Database is not a recognizable Polaris schema; refusing to create migration history");
            }
            repository.ensureHistoryTable();
            repository.baselineAt027();
        }

        List<AppliedMigration> applied = repository.loadApplied();
        validateHistory(applied);

        Map<Integer, AppliedMigration> appliedByVersion = new HashMap<>();
        for (AppliedMigration migration : applied) appliedByVersion.put(migration.version(), migration);

        List<MigrationDescriptor> pending = catalog.migrations().stream()
                .filter(migration -> migration.version() > MigrationCatalog.BASELINE_VERSION)
                .filter(migration -> !appliedByVersion.containsKey(migration.version()))
                .toList();
        int installedVersion = applied.getLast().version();
        int packagedVersion = catalog.migrations().stream()
                .mapToInt(MigrationDescriptor::version)
                .max()
                .orElse(MigrationCatalog.BASELINE_VERSION);

        if (mode == MigrationMode.VALIDATE) {
            return new MigrationReport(
                    mode,
                    installedVersion,
                    packagedVersion,
                    pending.stream().map(MigrationDescriptor::version).toList(),
                    List.of());
        }

        List<Integer> newlyApplied = new ArrayList<>();
        for (MigrationDescriptor migration : pending) {
            apply(repository, migration);
            newlyApplied.add(migration.version());
        }
        int resultingVersion = newlyApplied.isEmpty() ? installedVersion : newlyApplied.getLast();
        return new MigrationReport(mode, resultingVersion, packagedVersion, List.of(), newlyApplied);
    }

    private void validateHistory(List<AppliedMigration> applied) {
        if (applied.isEmpty()) {
            throw new MigrationValidationException(
                    "schema_migrations exists but is empty; refusing to infer a baseline");
        }

        AppliedMigration baseline = applied.getFirst();
        if (!isValidBaseline(baseline)) {
            throw new MigrationValidationException(
                    "schema_migrations is missing the required historical baseline at version 27");
        }

        Map<Integer, MigrationDescriptor> packaged = new HashMap<>();
        int packagedLatest = MigrationCatalog.BASELINE_VERSION;
        for (MigrationDescriptor migration : catalog.migrations()) {
            if (migration.version() > MigrationCatalog.BASELINE_VERSION) {
                packaged.put(migration.version(), migration);
                packagedLatest = Math.max(packagedLatest, migration.version());
            }
        }

        int expectedVersion = MigrationCatalog.BASELINE_VERSION;
        for (int index = 1; index < applied.size(); index++) {
            AppliedMigration stored = applied.get(index);
            expectedVersion++;
            if (stored.version() != expectedVersion) {
                throw new MigrationValidationException(
                        "Malformed migration history: expected version " + expectedVersion
                                + " but found " + stored.version());
            }
            if (stored.version() > packagedLatest) {
                throw new MigrationValidationException(
                        "Database migration version " + stored.version()
                                + " is newer than packaged version " + packagedLatest);
            }
            MigrationDescriptor expected = packaged.get(stored.version());
            if (expected == null || !matches(stored, expected)) {
                throw new MigrationValidationException(
                        "Stored migration metadata does not match packaged script for version "
                                + stored.version());
            }
        }
    }

    private static boolean isValidBaseline(AppliedMigration baseline) {
        return baseline.version() == MigrationCatalog.BASELINE_VERSION
                && baseline.description().equals("historical baseline")
                && baseline.scriptName().equals("<< baseline >>")
                && baseline.checksumSha256().equals("0".repeat(64));
    }

    private static boolean matches(AppliedMigration stored, MigrationDescriptor expected) {
        return stored.description().equals(expected.description())
                && stored.scriptName().equals(expected.scriptName())
                && stored.checksumSha256().equals(expected.checksumSha256());
    }

    private void apply(MigrationHistoryRepository repository, MigrationDescriptor migration)
            throws SQLException {
        List<String> statements = SqlScriptSplitter.split(migration.sql());
        long startedAt = System.nanoTime();
        for (int index = 0; index < statements.size(); index++) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(statements.get(index));
            } catch (SQLException error) {
                throw new MigrationExecutionException(
                        migration.version(), migration.scriptName(), index + 1, error);
            }
        }

        long elapsedMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        try {
            repository.recordApplied(migration, elapsedMs);
            LOGGER.info(
                    "Database migration {} ({}) applied in {} ms",
                    migration.version(),
                    migration.scriptName(),
                    elapsedMs);
        } catch (SQLException error) {
            throw new MigrationExecutionException(
                    migration.version(), migration.scriptName(), statements.size() + 1, error);
        }
    }

    private void acquireLock() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)");) {
            statement.setString(1, lockName);
            statement.setInt(2, lockTimeoutSeconds);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new MigrationValidationException(
                            "Could not acquire database migration lock within "
                                    + lockTimeoutSeconds + " seconds");
                }
            }
        }
    }

    private void releaseLock() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new MigrationValidationException("Database migration lock was not owned");
                }
            }
        }
    }

    private static String lockName(Connection connection) {
        try {
            String catalog = connection.getCatalog();
            String identity = catalog == null || catalog.isBlank() ? "default" : catalog;
            return LOCK_PREFIX + MigrationCatalog.sha256(identity).substring(0, 40);
        } catch (SQLException error) {
            throw new MigrationValidationException(
                    "Unable to derive the database migration lock name", error);
        }
    }
}
