package com.eu.habbo.core.config;

import com.eu.habbo.core.ConfigurationManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigRegistry {

    private static final ConfigRegistry STANDARD = new ConfigRegistry(standardDefinitions());
    private final Map<String, ConfigKey> keys;

    public ConfigRegistry(List<ConfigKey> definitions) {
        Map<String, ConfigKey> indexed = new LinkedHashMap<>();
        for (ConfigKey definition : definitions) {
            if (indexed.putIfAbsent(definition.name(), definition) != null) {
                throw new IllegalArgumentException("Duplicate configuration key " + definition.name());
            }
        }
        this.keys = Map.copyOf(indexed);
    }

    public static ConfigRegistry standard() {
        return STANDARD;
    }

    public Set<String> names() {
        return this.keys.keySet();
    }

    public List<ValidationIssue> validate(ConfigurationManager configuration) {
        List<ValidationIssue> issues = new ArrayList<>();
        for (ConfigKey key : this.keys.values()) {
            String value = configuration.getValueIfPresent(key.name());
            if (value == null) {
                continue;
            }
            try {
                key.validate(value);
            } catch (RuntimeException exception) {
                issues.add(new ValidationIssue(key.name(), value, exception.getMessage()));
            }
        }
        return List.copyOf(issues);
    }

    public String renderMarkdown() {
        StringBuilder reference = new StringBuilder();
        reference.append("# Polaris startup configuration reference\n\n");
        reference.append(
                "Unknown keys remain allowed for plugins. Database-backed hotel settings are documented separately from this startup-file registry.\n\n");
        reference.append("| Key | Type | Default | Environment | Restart | Live reload | Description |\n");
        reference.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        this.keys.values().stream()
                .sorted(java.util.Comparator.comparing(ConfigKey::name))
                .forEach(key -> reference
                        .append("| `")
                        .append(key.name())
                        .append("` | ")
                        .append(key.type().name().toLowerCase(java.util.Locale.ROOT))
                        .append(" | `")
                        .append(escape(key.defaultValue()))
                        .append("` | ")
                        .append(key.environmentAlias().isBlank() ? "—" : "`" + key.environmentAlias() + "`")
                        .append(" | ")
                        .append(key.restartRequired() ? "yes" : "no")
                        .append(" | ")
                        .append(key.liveReload() ? "yes" : "no")
                        .append(" | ")
                        .append(key.description())
                        .append(" |\n"));
        return reference.toString();
    }

    private static String escape(String value) {
        return value.replace("|", "\\|").replace("`", "\\`");
    }

    private static List<ConfigKey> standardDefinitions() {
        List<ConfigKey> keys = new ArrayList<>();
        add(
                keys,
                ConfigKey.ValueType.STRING,
                "",
                true,
                "db.hostname",
                "db.database",
                "db.username",
                "db.password",
                "db.params",
                "db.migrations.backup.directory",
                "db.migrations.backup.executable",
                "game.host",
                "rcon.host",
                "rcon.allowed",
                "enc.e",
                "enc.n",
                "enc.d",
                "nitro.secure.config.root",
                "nitro.secure.gamedata.root",
                "nitro.secure.master_key",
                "login.remember.jwt.secret",
                "ws.host",
                "ws.whitelist",
                "client.release.allowed",
                "habbo.console.style");
        add(
                keys,
                ConfigKey.ValueType.INTEGER,
                "0",
                true,
                "db.port",
                "db.migrations.backup.keep",
                "db.migrations.backup.timeout_seconds",
                "db.pool.minsize",
                "db.pool.maxsize",
                "db.integrity.audit.sample_limit",
                "db.integrity.audit.query_timeout_seconds",
                "db.integrity.audit.max_duration_seconds",
                "game.port",
                "rcon.port",
                "nitro.secure.session_ttl_sec",
                "login.remember.duration.days",
                "login.sso.ticket.ttl.seconds",
                "login.news.limit",
                "db.slow_query.threshold_ms",
                "db.slow_query.max_sql_length",
                "ws.port",
                "session.reconnect.grace.seconds");
        keys.add(definition(
                "runtime.threads",
                ConfigKey.ValueType.INTEGER,
                "8",
                true));
        add(
                keys,
                ConfigKey.ValueType.LONG,
                "0",
                true,
                "db.pool.connection_timeout_ms",
                "db.pool.idle_timeout_ms",
                "db.pool.max_lifetime_ms",
                "db.pool.validation_timeout_ms",
                "db.pool.leak_detection_ms");
        add(
                keys,
                ConfigKey.ValueType.BOOLEAN,
                "false",
                true,
                "db.migrate.on_startup",
                "db.migrations.backup.enabled",
                "enc.enabled",
                "nitro.secure.assets.enabled",
                "nitro.secure.api.enabled",
                "login.remember.enabled",
                "db.slow_query.enabled",
                "ws.enabled",
                "crypto.ws.enabled",
                "e2e.enabled");
        keys.add(definition("db.integrity.audit.mode", ConfigKey.ValueType.STRING, "warn", true));
        return List.copyOf(keys);
    }

    private static void add(
            List<ConfigKey> keys,
            ConfigKey.ValueType type,
            String defaultValue,
            boolean restartRequired,
            String... names) {
        for (String name : names) {
            keys.add(definition(name, type, defaultValue, restartRequired));
        }
    }

    private static ConfigKey definition(
            String name, ConfigKey.ValueType type, String defaultValue, boolean restartRequired) {
        return new ConfigKey(
                name,
                type,
                defaultValue,
                environmentAlias(name),
                ConfigKey.Source.STARTUP,
                restartRequired,
                false,
                List.of(),
                description(name));
    }

    private static String environmentAlias(String name) {
        return switch (name) {
            case "db.hostname" -> "DB_HOSTNAME";
            case "db.port" -> "DB_PORT";
            case "db.database" -> "DB_DATABASE";
            case "db.username" -> "DB_USERNAME";
            case "db.password" -> "DB_PASSWORD";
            case "db.params" -> "DB_PARAMS";
            case "db.migrate.on_startup" -> "DB_MIGRATE_ON_STARTUP";
            case "game.host" -> "EMU_HOST";
            case "game.port" -> "EMU_PORT";
            case "rcon.host" -> "RCON_HOST";
            case "rcon.port" -> "RCON_PORT";
            case "rcon.allowed" -> "RCON_ALLOWED";
            default -> "";
        };
    }

    private static String description(String name) {
        if (name.startsWith("db.pool.")) {
            return "Database connection-pool setting.";
        }
        if (name.startsWith("db.migrations.")) {
            return "Migration backup setting.";
        }
        if (name.startsWith("db.integrity.")) {
            return "Startup integrity-audit setting.";
        }
        if (name.startsWith("db.slow_query.")) {
            return "Sanitized slow-query diagnostic setting.";
        }
        if (name.startsWith("db.")) {
            return "Database startup setting.";
        }
        if (name.startsWith("nitro.secure.")) {
            return "Nitro secure-asset runtime setting.";
        }
        if (name.startsWith("login.")) {
            return "Built-in login endpoint setting.";
        }
        if (name.startsWith("rcon.")) {
            return "RCON listener setting.";
        }
        if (name.startsWith("game.")) {
            return "Game listener setting.";
        }
        if (name.startsWith("ws.") || name.startsWith("crypto.ws.")) {
            return "WebSocket listener setting.";
        }
        if (name.startsWith("enc.")) {
            return "Legacy transport encryption setting.";
        }
        return "Polaris startup setting.";
    }

    public record ValidationIssue(String key, String value, String reason) {}
}
