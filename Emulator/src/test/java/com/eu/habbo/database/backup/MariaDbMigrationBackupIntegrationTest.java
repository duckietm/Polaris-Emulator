package com.eu.habbo.database.backup;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MariaDbMigrationBackupIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAndRestoresAConsistentMariaDbBackup() throws Exception {
        String host = System.getenv("MIGRATION_TEST_DB_HOST");
        Assumptions.assumeTrue(host != null && !host.isBlank(),
                "set MIGRATION_TEST_DB_HOST to run the real dump/restore test");
        int port = Integer.parseInt(value("MIGRATION_TEST_DB_PORT", "3306"));
        String username = value("MIGRATION_TEST_DB_USER", "root");
        String password = value("MIGRATION_TEST_DB_PASSWORD", "");
        String dumpExecutable = value("MIGRATION_TEST_DUMP_EXECUTABLE", "mariadb-dump");
        String clientExecutable = value("MIGRATION_TEST_CLIENT_EXECUTABLE", "mariadb");
        String database = "polaris_backup_it_" + UUID.randomUUID().toString().replace("-", "");
        String serverUrl = "jdbc:mariadb://" + host + ':' + port + '/';

        try {
            execute(serverUrl, username, password, "CREATE DATABASE `" + database + "`");
            String databaseUrl = serverUrl + database;
            execute(databaseUrl, username, password,
                    "CREATE TABLE transactional_data (id INT PRIMARY KEY, value VARCHAR(32)) ENGINE=InnoDB");
            execute(databaseUrl, username, password,
                    "CREATE TABLE legacy_data (id INT PRIMARY KEY, value VARCHAR(32)) ENGINE=MyISAM");
            execute(databaseUrl, username, password,
                    "INSERT INTO transactional_data VALUES (1, 'inno'), (2, 'db')");
            execute(databaseUrl, username, password,
                    "INSERT INTO legacy_data VALUES (1, 'myisam')");

            MariaDbMigrationBackup backup = new MariaDbMigrationBackup(
                    new DatabaseBackupOptions(
                            true, tempDir, 2, Duration.ofSeconds(60), dumpExecutable),
                    new DatabaseBackupRequest(host, port, database, username, password));
            backup.beforeMigrations(List.of("28"));

            Path archive;
            try (var files = Files.list(tempDir)) {
                archive = files.filter(path -> path.getFileName().toString().endsWith(".sql.gz"))
                        .findFirst()
                        .orElseThrow();
            }
            Path checksum = Path.of(archive + ".sha256");
            assertTrue(Files.readString(checksum).startsWith(sha256(archive)));

            execute(serverUrl, username, password, "DROP DATABASE `" + database + "`");
            restore(clientExecutable, host, port, username, password, archive);

            assertEquals(2, count(serverUrl + database, username, password, "transactional_data"));
            assertEquals(1, count(serverUrl + database, username, password, "legacy_data"));
        } finally {
            try {
                execute(serverUrl, username, password,
                        "DROP DATABASE IF EXISTS `" + database + "`");
            } catch (Exception ignored) {
            }
        }
    }

    private static void restore(
            String executable,
            String host,
            int port,
            String username,
            String password,
            Path archive) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                executable,
                "--protocol=tcp",
                "--host=" + host,
                "--port=" + port,
                "--user=" + username);
        builder.environment().put("MYSQL_PWD", password);
        Process process = builder.start();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread errorReader = Thread.startVirtualThread(() -> {
            try (var input = process.getErrorStream()) {
                input.transferTo(stderr);
            } catch (Exception ignored) {
            }
        });
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(archive));
             var output = process.getOutputStream()) {
            input.transferTo(output);
        }
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "restore client timed out");
        errorReader.join(Duration.ofSeconds(5));
        assertEquals(0, process.exitValue(), stderr.toString(StandardCharsets.UTF_8));
    }

    private static void execute(
            String url,
            String username,
            String password,
            String sql) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(
            String url,
            String username,
            String password,
            String table) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String sha256(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
