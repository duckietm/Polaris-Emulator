package com.eu.habbo.database.backup;

import com.eu.habbo.core.ConfigurationManager;
import com.google.gson.Gson;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;

public final class MariaDbMigrationBackup implements MigrationBackup {
    private static final Logger LOGGER = LoggerFactory.getLogger(MariaDbMigrationBackup.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_STDERR_BYTES = 16 * 1024;
    private static final Gson GSON = new Gson();

    private final DatabaseBackupOptions options;
    private final DatabaseBackupRequest request;
    private final ProcessStarter processStarter;
    private final Clock clock;

    public MariaDbMigrationBackup(
            DatabaseBackupOptions options,
            DatabaseBackupRequest request) {
        this(options, request, command -> new ProcessBuilder(command).start(), Clock.systemUTC());
    }

    MariaDbMigrationBackup(
            DatabaseBackupOptions options,
            DatabaseBackupRequest request,
            ProcessStarter processStarter,
            Clock clock) {
        this.options = java.util.Objects.requireNonNull(options, "options");
        this.request = java.util.Objects.requireNonNull(request, "request");
        this.processStarter = java.util.Objects.requireNonNull(processStarter, "processStarter");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public static MigrationBackup resolve(
            ConfigurationManager config,
            HikariDataSource migrationDataSource) {
        DatabaseBackupOptions options = DatabaseBackupOptions.resolve(config);
        if (!options.enabled()) return MigrationBackup.disabled();
        return new MariaDbMigrationBackup(
                options,
                DatabaseBackupRequest.fromJdbc(
                        migrationDataSource.getJdbcUrl(),
                        migrationDataSource.getUsername(),
                        migrationDataSource.getPassword()));
    }

    @Override
    public void beforeMigrations(List<String> pendingVersions) {
        List<String> pending = List.copyOf(pendingVersions);
        if (!options.enabled() || pending.isEmpty()) return;

        Path credentials = null;
        Path archivePart = null;
        Path archive = null;
        Path checksumPart = null;
        Path manifestPart = null;
        Path checksum = null;
        Path manifest = null;
        boolean archiveCreated = false;
        boolean checksumCreated = false;
        boolean manifestCreated = false;
        try {
            Files.createDirectories(options.directory());
            credentials = Files.createTempFile(options.directory(), ".polaris-db-backup-", ".cnf");
            harden(credentials);
            Files.writeString(
                    credentials,
                    optionFile(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);

            String baseName = artifactBaseName(pending);
            archive = options.directory().resolve(baseName + ".sql.gz");
            archivePart = options.directory().resolve(baseName + ".sql.gz.part");
            checksum = options.directory().resolve(baseName + ".sql.gz.sha256");
            checksumPart = options.directory().resolve(baseName + ".sql.gz.sha256.part");
            manifest = options.directory().resolve(baseName + ".sql.gz.json");
            manifestPart = options.directory().resolve(baseName + ".sql.gz.json.part");

            List<String> command = command(credentials);
            Process process = processStarter.start(command);
            ProcessResult result = collect(process, archivePart);
            if (result.exitCode() != 0) {
                throw new MigrationBackupException(
                        "mariadb-dump failed with exit code " + result.exitCode()
                                + diagnostic(result.stderr()));
            }
            if (result.uncompressedBytes() == 0) {
                throw new MigrationBackupException("mariadb-dump produced an empty backup");
            }

            harden(archivePart);
            String sha256 = sha256(archivePart);
            moveAtomically(archivePart, archive);
            archiveCreated = true;
            Files.writeString(
                    checksumPart,
                    sha256 + "  " + archive.getFileName() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            harden(checksumPart);
            moveAtomically(checksumPart, checksum);
            checksumCreated = true;

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("formatVersion", 1);
            metadata.put("createdAt", Instant.now(clock).toString());
            metadata.put("database", request.database());
            metadata.put("pendingVersions", pending);
            metadata.put("archive", archive.getFileName().toString());
            metadata.put("compressedBytes", Files.size(archive));
            metadata.put("uncompressedBytes", result.uncompressedBytes());
            metadata.put("sha256", sha256);
            metadata.put("tool", options.executable());
            Files.writeString(
                    manifestPart,
                    GSON.toJson(metadata) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            harden(manifestPart);
            moveAtomically(manifestPart, manifest);
            manifestCreated = true;

            pruneOldBackups();
            LOGGER.info(
                    "Database migration backup -> archive={}, bytes={}, sha256={}, pending={}",
                    archive,
                    Files.size(archive),
                    sha256,
                    pending);
        } catch (MigrationBackupException error) {
            if (manifestCreated) deleteQuietly(manifest);
            if (checksumCreated) deleteQuietly(checksum);
            if (archiveCreated) deleteQuietly(archive);
            throw error;
        } catch (IOException | RuntimeException error) {
            if (manifestCreated) deleteQuietly(manifest);
            if (checksumCreated) deleteQuietly(checksum);
            if (archiveCreated) deleteQuietly(archive);
            throw new MigrationBackupException(
                    "Unable to create the required database migration backup", error);
        } finally {
            deleteQuietly(credentials);
            deleteQuietly(archivePart);
            deleteQuietly(checksumPart);
            deleteQuietly(manifestPart);
        }
    }

    private ProcessResult collect(Process process, Path archivePart) throws IOException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Long> stdout = executor.submit(() -> compress(process.getInputStream(), archivePart));
            Future<String> stderr = executor.submit(() -> readDiagnostic(process.getErrorStream()));
            boolean finished;
            try {
                finished = process.waitFor(options.timeout().toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                stop(process);
                throw new MigrationBackupException("Interrupted while waiting for mariadb-dump", error);
            }
            if (!finished) {
                stop(process);
                stdout.cancel(true);
                stderr.cancel(true);
                throw new MigrationBackupException(
                        "mariadb-dump exceeded the configured timeout of "
                                + options.timeout().toSeconds() + " seconds");
            }
            try {
                long bytes = stdout.get(10, TimeUnit.SECONDS);
                String diagnostic = stderr.get(10, TimeUnit.SECONDS);
                return new ProcessResult(process.exitValue(), bytes, diagnostic);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new MigrationBackupException("Interrupted while finalizing mariadb-dump output", error);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                if (cause instanceof IOException io) throw io;
                throw new MigrationBackupException("Unable to capture mariadb-dump output", cause);
            } catch (TimeoutException error) {
                throw new MigrationBackupException("Timed out while finalizing mariadb-dump output", error);
            }
        }
    }

    private List<String> command(Path credentials) {
        return List.of(
                options.executable(),
                "--defaults-extra-file=" + credentials,
                "--lock-all-tables",
                "--quick",
                "--routines",
                "--events",
                "--triggers",
                "--hex-blob",
                "--default-character-set=utf8mb4",
                "--add-drop-database",
                "--databases",
                request.database());
    }

    private String optionFile() {
        return "[client]" + System.lineSeparator()
                + "protocol=tcp" + System.lineSeparator()
                + "host=" + quote(request.host()) + System.lineSeparator()
                + "port=" + request.port() + System.lineSeparator()
                + "user=" + quote(request.username()) + System.lineSeparator()
                + "password=" + quote(request.password()) + System.lineSeparator();
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private String artifactBaseName(List<String> pending) {
        String database = request.database().replaceAll("[^A-Za-z0-9._-]", "_");
        String base = "polaris-" + database + '-' + STAMP.format(Instant.now(clock))
                + "-before-v" + safeVersion(pending.getFirst())
                + "-v" + safeVersion(pending.getLast());
        String candidate = base;
        int suffix = 1;
        while (artifactNameExists(candidate)) {
            candidate = base + '-' + suffix++;
        }
        return candidate;
    }

    private static String safeVersion(String version) {
        return version.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean artifactNameExists(String baseName) {
        return Files.exists(options.directory().resolve(baseName + ".sql.gz"))
                || Files.exists(options.directory().resolve(baseName + ".sql.gz.part"))
                || Files.exists(options.directory().resolve(baseName + ".sql.gz.sha256"))
                || Files.exists(options.directory().resolve(baseName + ".sql.gz.json"));
    }

    private void pruneOldBackups() throws IOException {
        List<Path> archives;
        try (var stream = Files.list(options.directory())) {
            archives = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("polaris-"))
                    .filter(path -> path.getFileName().toString().endsWith(".sql.gz"))
                    .sorted(Comparator.comparing(MariaDbMigrationBackup::modifiedAt).reversed())
                    .toList();
        }
        for (Path expired : archives.stream().skip(options.retentionCount()).toList()) {
            Files.deleteIfExists(expired);
            Files.deleteIfExists(Path.of(expired + ".sha256"));
            Files.deleteIfExists(Path.of(expired + ".json"));
        }
    }

    private static long compress(InputStream source, Path target) throws IOException {
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = source;
             OutputStream file = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW);
             GZIPOutputStream gzip = new GZIPOutputStream(file, 64 * 1024)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                gzip.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    private static String readDiagnostic(InputStream source) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (InputStream input = source) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                int remaining = MAX_STDERR_BYTES - captured.size();
                if (remaining > 0) captured.write(buffer, 0, Math.min(read, remaining));
            }
        }
        return captured.toString(StandardCharsets.UTF_8).strip();
    }

    private static String diagnostic(String stderr) {
        return stderr.isBlank() ? "" : ": " + stderr;
    }

    private static void stop(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source, target);
        }
    }

    private static void harden(Path path) throws IOException {
        IOException posixFailure = null;
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
            return;
        } catch (IOException error) {
            posixFailure = error;
        } catch (UnsupportedOperationException ignored) {
            // Fall through to the Windows ACL view.
        }
        try {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view == null) {
                throw new IOException("filesystem exposes neither POSIX permissions nor Windows ACLs");
            }
            AclEntry ownerOnly = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            view.setAcl(List.of(ownerOnly));
        } catch (IOException | UnsupportedOperationException error) {
            IOException failure = new IOException(
                    "Unable to restrict database backup artifact permissions: " + path, error);
            if (posixFailure != null) failure.addSuppressed(posixFailure);
            throw failure;
        }
    }

    private static FileTime modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException error) {
            throw new MigrationBackupException("Unable to inspect backup retention metadata", error);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            LOGGER.warn("Unable to delete temporary database backup artifact {}", path, error);
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    private record ProcessResult(int exitCode, long uncompressedBytes, String stderr) {
    }
}
