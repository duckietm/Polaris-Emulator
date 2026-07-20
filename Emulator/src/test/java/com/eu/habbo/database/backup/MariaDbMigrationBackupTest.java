package com.eu.habbo.database.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MariaDbMigrationBackupTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAtomicCompressedBackupChecksumAndManifestWithoutLeakingPassword() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        DatabaseBackupOptions options = new DatabaseBackupOptions(
                true, tempDir, 3, Duration.ofSeconds(30), "mariadb-dump");
        DatabaseBackupRequest request = new DatabaseBackupRequest(
                "db.internal", 3307, "polaris_prod", "polaris", "very-secret-password");
        MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                options,
                request,
                command -> {
                    commands.add(List.copyOf(command));
                    return FakeProcess.success("CREATE TABLE `users` (`id` int);\n");
                },
                Clock.fixed(Instant.parse("2026-07-18T20:45:12Z"), ZoneOffset.UTC));

        backup.beforeMigrations(migrations());

        List<Path> archives = files(".sql.gz");
        assertEquals(1, archives.size());
        assertEquals("CREATE TABLE `users` (`id` int);\n", gunzip(archives.getFirst()));
        assertEquals(1, files(".sql.gz.sha256").size());
        assertEquals(1, files(".sql.gz.json").size());
        assertEquals(List.of(), files(".part"));
        assertEquals(List.of(), files(".cnf"));

        String command = String.join(" ", commands.getFirst());
        assertFalse(command.contains("very-secret-password"));
        assertTrue(commands.getFirst().get(1).startsWith("--defaults-extra-file="));
        assertTrue(command.contains("--lock-all-tables"));
        assertTrue(command.contains("--routines"));
        assertTrue(command.contains("--events"));
        assertTrue(command.contains("--triggers"));
        assertTrue(command.endsWith("--databases polaris_prod"));

        String manifest = Files.readString(files(".sql.gz.json").getFirst());
        assertTrue(manifest.contains("\"pendingVersions\":[\"28\",\"29\"]"));
        assertTrue(manifest.contains("\"database\":\"polaris_prod\""));
        assertFalse(manifest.contains("very-secret-password"));
        assertFalse(manifest.contains("polaris\""));
    }

    @Test
    void failureDeletesPartialArtifactsAndKeepsExistingBackups() throws Exception {
        Path existing = tempDir.resolve("polaris-old.sql.gz");
        Files.writeString(existing, "keep");
        DatabaseBackupOptions options = new DatabaseBackupOptions(
                true, tempDir, 3, Duration.ofSeconds(30), "mariadb-dump");
        MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                options,
                new DatabaseBackupRequest("localhost", 3306, "polaris", "user", "password"),
                command -> FakeProcess.failure(2, "access denied"),
                Clock.systemUTC());

        MigrationBackupException error = assertThrows(
                MigrationBackupException.class,
                () -> backup.beforeMigrations(migrations()));

        assertTrue(error.getMessage().contains("access denied"));
        assertTrue(Files.exists(existing));
        assertEquals(List.of(), files(".part"));
        assertEquals(List.of(), files(".cnf"));
    }

    @Test
    void successfulBackupPrunesOldArchivesOnlyAfterTheNewArtifactExists() throws Exception {
        for (int index = 1; index <= 3; index++) {
            Path archive = tempDir.resolve("polaris-old-" + index + ".sql.gz");
            Files.writeString(archive, "old-" + index);
            Files.writeString(Path.of(archive + ".sha256"), "hash");
            Files.writeString(Path.of(archive + ".json"), "{}");
            Files.setLastModifiedTime(archive, java.nio.file.attribute.FileTime.fromMillis(index));
        }
        DatabaseBackupOptions options = new DatabaseBackupOptions(
                true, tempDir, 2, Duration.ofSeconds(30), "mariadb-dump");
        MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                options,
                new DatabaseBackupRequest("localhost", 3306, "polaris", "user", "password"),
                command -> FakeProcess.success("SELECT 1;\n"),
                Clock.fixed(Instant.parse("2026-07-18T20:45:12Z"), ZoneOffset.UTC));

        backup.beforeMigrations(migrations());

        assertEquals(2, files(".sql.gz").size());
        assertEquals(2, files(".sql.gz.sha256").size());
        assertEquals(2, files(".sql.gz.json").size());
    }

    @Test
    void nameCollisionNeverDeletesOrOverwritesAnExistingBackup() throws Exception {
        Path existing = tempDir.resolve(
                "polaris-polaris-20260718T204512000Z-before-v28-v29.sql.gz");
        Files.writeString(existing, "existing-backup");
        DatabaseBackupOptions options = new DatabaseBackupOptions(
                true, tempDir, 3, Duration.ofSeconds(30), "mariadb-dump");
        MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                options,
                new DatabaseBackupRequest("localhost", 3306, "polaris", "user", "password"),
                command -> FakeProcess.success("SELECT 1;\n"),
                Clock.fixed(Instant.parse("2026-07-18T20:45:12Z"), ZoneOffset.UTC));

        backup.beforeMigrations(migrations());

        assertEquals("existing-backup", Files.readString(existing));
        assertEquals(2, files(".sql.gz").size());
    }

    @Test
    void timeoutStopsTheDumpAndRemovesCredentialsAndPartialOutput() throws Exception {
        DatabaseBackupOptions options = new DatabaseBackupOptions(
                true, tempDir, 3, Duration.ofSeconds(1), "mariadb-dump");
        FakeProcess process = FakeProcess.timeout();
        MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                options,
                new DatabaseBackupRequest("localhost", 3306, "polaris", "user", "password"),
                command -> process,
                Clock.systemUTC());

        MigrationBackupException error = assertThrows(
                MigrationBackupException.class,
                () -> backup.beforeMigrations(migrations()));

        assertTrue(error.getMessage().contains("timeout"));
        assertTrue(process.destroyed);
        assertEquals(List.of(), files(".part"));
        assertEquals(List.of(), files(".cnf"));
        assertEquals(List.of(), files(".sql.gz"));
    }

    @Test
    void missingExplicitExecutableFailsBeforeLaunchWithRemedies() throws Exception {
        String executable = tempDir.resolve("tools").resolve("mariadb-dump").toString();
        DatabaseBackupOptions options = new DatabaseBackupOptions(
                true, tempDir, 3, Duration.ofSeconds(30), executable);
        MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                options,
                new DatabaseBackupRequest("localhost", 3306, "polaris", "user", "password"),
                command -> {
                    throw new AssertionError("the dump process must not be launched");
                },
                Clock.systemUTC());

        MigrationBackupException error = assertThrows(
                MigrationBackupException.class,
                () -> backup.beforeMigrations(migrations()));

        assertTrue(error.getMessage().contains(executable));
        assertTrue(error.getMessage().contains("MariaDB client tools"));
        assertTrue(error.getMessage().contains("db.migrations.backup.executable"));
        assertTrue(error.getMessage().contains("db.migrations.backup.enabled=0"));
        assertEquals(List.of(), files(".cnf"));
        assertEquals(List.of(), files(".part"));
    }

    @Test
    void unresolvableCommandLaunchFailureExplainsRemedies() {
        DatabaseBackupOptions options = new DatabaseBackupOptions(
                true, tempDir, 3, Duration.ofSeconds(30), "mariadb-dump");
        IOException launchFailure = new IOException(
                "Cannot run program \"mariadb-dump\": CreateProcess error=2");
        MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                options,
                new DatabaseBackupRequest("localhost", 3306, "polaris", "user", "password"),
                command -> {
                    throw launchFailure;
                },
                Clock.systemUTC());

        MigrationBackupException error = assertThrows(
                MigrationBackupException.class,
                () -> backup.beforeMigrations(migrations()));

        assertTrue(error.getMessage().contains("'mariadb-dump'"));
        assertTrue(error.getMessage().contains("MariaDB client tools"));
        assertTrue(error.getMessage().contains("db.migrations.backup.executable"));
        assertTrue(error.getMessage().contains("db.migrations.backup.enabled=0"));
        assertEquals(launchFailure, error.getCause());
    }

    private List<String> migrations() {
        return List.of("28", "29");
    }

    private List<Path> files(String suffix) throws Exception {
        try (var stream = Files.list(tempDir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static String gunzip(Path archive) throws Exception {
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class FakeProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        private final int exitCode;
        private final boolean timesOut;
        private boolean destroyed;

        private FakeProcess(String stdout, String stderr, int exitCode, boolean timesOut) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
            this.timesOut = timesOut;
        }

        static FakeProcess success(String stdout) {
            return new FakeProcess(stdout, "", 0, false);
        }

        static FakeProcess failure(int exitCode, String stderr) {
            return new FakeProcess("", stderr, exitCode, false);
        }

        static FakeProcess timeout() {
            return new FakeProcess("", "", 143, true);
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return destroyed || !timesOut;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
