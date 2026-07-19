package com.eu.habbo.database.backup;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public record DatabaseBackupRequest(
        String host,
        int port,
        String database,
        String username,
        String password) {

    public DatabaseBackupRequest {
        host = safe(Objects.requireNonNull(host, "host"), "host");
        database = safe(Objects.requireNonNull(database, "database"), "database");
        username = safe(Objects.requireNonNull(username, "username"), "username");
        password = safe(Objects.requireNonNull(password, "password"), "password");
        if (host.isBlank() || database.isBlank() || username.isBlank()) {
            throw new IllegalArgumentException("database backup connection fields must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("database backup port must be between 1 and 65535");
        }
    }

    public static DatabaseBackupRequest fromJdbc(
            String jdbcUrl,
            String username,
            String password) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        if (!jdbcUrl.startsWith("jdbc:mariadb://")) {
            throw new IllegalArgumentException(
                    "database backup requires a single-host jdbc:mariadb:// URL");
        }

        URI target;
        try {
            target = new URI(jdbcUrl.substring("jdbc:".length()));
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("database backup JDBC URL is invalid", error);
        }

        String path = target.getPath();
        String database = path == null || path.length() <= 1 ? "" : path.substring(1);
        if (database.contains("/")) {
            throw new IllegalArgumentException(
                    "database backup JDBC URL must identify exactly one database");
        }

        return new DatabaseBackupRequest(
                Objects.requireNonNullElse(target.getHost(), ""),
                target.getPort() == -1 ? 3306 : target.getPort(),
                database,
                Objects.requireNonNullElse(username, ""),
                Objects.requireNonNullElse(password, ""));
    }

    private static String safe(String value, String field) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " contains an unsafe control character");
        }
        return value;
    }
}
