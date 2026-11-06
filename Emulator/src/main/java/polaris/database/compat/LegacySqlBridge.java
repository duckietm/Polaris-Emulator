package polaris.database.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.permissions.PermissionsManager;

/**
 * The Polaris legacy bridge.
 *
 * When the gameserver was renamed to Polaris several database structures were
 * renamed as well (for example the legacy permissions table became
 * permission_ranks + permission_definitions). Old third-party plugins still
 * issue raw SQL against those legacy names through
 * Emulator.getDatabase().getDataSource().getConnection().
 *
 * This bridge sits between the connection pool and every consumer: each SQL
 * statement is offered to a chain of {@link LegacySqlTranslator}s that rewrite
 * recognised legacy statements to their Polaris equivalents on the fly, so old
 * plugin jars keep working without a recompile.
 *
 * Config keys (config.ini / emulator_settings):
 *   polaris.legacy.bridge.enabled       - master switch, default 1
 *   polaris.legacy.bridge.log           - log every distinct translation, default 1
 *   polaris.legacy.bridge.table_renames - extra plain table renames, "old:new;old2:new2"
 */
public class LegacySqlBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacySqlBridge.class);

    public static final String CONFIG_ENABLED = "polaris.legacy.bridge.enabled";
    public static final String CONFIG_LOG = "polaris.legacy.bridge.log";
    public static final String CONFIG_TABLE_RENAMES = "polaris.legacy.bridge.table_renames";

    private static final int MAX_LOGGED_TRANSLATIONS = 512;
    private static final long RANK_IDS_CACHE_TTL_MS = 30_000L;

    private final List<LegacySqlTranslator> translators = new ArrayList<>();

    private final Set<String> loggedTranslations = ConcurrentHashMap.newKeySet();

    private volatile Boolean normalizedSchemaCache = null;
    private volatile List<Integer> rankIdsCache = null;
    private volatile long rankIdsCacheExpires = 0L;

    public LegacySqlBridge() {
        this.translators.add(new LegacyTableRenameTranslator());
        this.translators.add(new LegacyPermissionsSqlTranslator());
    }

    public void registerTranslator(LegacySqlTranslator translator) {
        this.translators.add(translator);
    }

    /**
     * Drops the cached schema/rank information, e.g. after :update_permissions.
     */
    public void invalidateCaches() {
        this.normalizedSchemaCache = null;
        this.rankIdsCache = null;
        this.rankIdsCacheExpires = 0L;
    }

    public boolean isEnabled() {
        ConfigurationManager config = Emulator.getConfig();
        return config == null || config.getBoolean(CONFIG_ENABLED, true);
    }

    private boolean isLoggingEnabled() {
        ConfigurationManager config = Emulator.getConfig();
        return config == null || config.getBoolean(CONFIG_LOG, true);
    }

    /**
     * Rewrites a legacy statement to its Polaris equivalent, or returns the
     * statement unchanged when no translator recognises it.
     */
    public String translate(String sql, Connection connection) {
        if (sql == null || sql.isEmpty() || !this.isEnabled()) {
            return sql;
        }

        String lower = sql.toLowerCase();
        TranslationContext context = null;

        for (LegacySqlTranslator translator : this.translators) {
            if (!translator.appliesTo(lower)) {
                continue;
            }

            if (context == null) {
                context = new JdbcTranslationContext(connection);
            }

            try {
                String translated = translator.translate(sql, context);

                if (translated != null && !translated.equals(sql)) {
                    this.logTranslation(translator, sql, translated);
                    return translated;
                }
            } catch (SQLException e) {
                LOGGER.warn(
                        "Polaris legacy bridge -> {} failed to translate legacy SQL, passing statement through unchanged: {}",
                        translator.getClass().getSimpleName(),
                        sql,
                        e);
            }
        }

        return sql;
    }

    private void logTranslation(LegacySqlTranslator translator, String original, String translated) {
        if (!this.isLoggingEnabled()) {
            return;
        }

        if (this.loggedTranslations.size() > MAX_LOGGED_TRANSLATIONS) {
            this.loggedTranslations.clear();
        }

        if (this.loggedTranslations.add(original)) {
            LOGGER.info(
                    "Polaris legacy bridge -> translated legacy SQL from an old plugin.\n    old: {}\n    new: {}",
                    original,
                    translated);
        }
    }

    /**
     * Wraps a pooled connection so every statement it produces runs through
     * {@link #translate(String, Connection)} first.
     */
    public Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                LegacySqlBridge.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new ConnectionHandler(connection));
    }

    private class ConnectionHandler implements InvocationHandler {
        private final Connection delegate;

        private ConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            try {
                if (args != null
                        && args.length > 0
                        && args[0] instanceof String
                        && ("prepareStatement".equals(name)
                                || "prepareCall".equals(name)
                                || "nativeSQL".equals(name))) {
                    args = args.clone();
                    args[0] = translate((String) args[0], this.delegate);
                    return method.invoke(this.delegate, args);
                }

                Object result = method.invoke(this.delegate, args);

                if ("createStatement".equals(name) && result instanceof Statement) {
                    return wrapStatement((Statement) result, this.delegate);
                }

                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    private Statement wrapStatement(Statement statement, Connection connection) {
        return (Statement) Proxy.newProxyInstance(
                LegacySqlBridge.class.getClassLoader(),
                new Class<?>[] {Statement.class},
                new StatementHandler(statement, connection));
    }

    private class StatementHandler implements InvocationHandler {
        private final Statement delegate;
        private final Connection connection;

        private StatementHandler(Statement delegate, Connection connection) {
            this.delegate = delegate;
            this.connection = connection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    String name = method.getName();

                    if (name.startsWith("execute") || "addBatch".equals(name)) {
                        args = args.clone();
                        args[0] = translate((String) args[0], this.connection);
                    }
                }

                return method.invoke(this.delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    /**
     * JDBC-backed {@link TranslationContext}. Schema detection prefers the
     * loaded {@link PermissionsManager}; before the game environment exists it
     * probes information_schema directly (cached).
     */
    private class JdbcTranslationContext implements TranslationContext {
        private final Connection connection;

        private JdbcTranslationContext(Connection connection) {
            this.connection = connection;
        }

        @Override
        public boolean isNormalizedPermissionsSchema() throws SQLException {
            GameEnvironment environment = Emulator.getGameEnvironment();

            if (environment != null) {
                PermissionsManager permissionsManager = environment.getPermissionsManager();

                if (permissionsManager != null) {
                    return permissionsManager.isNormalizedSchemaEnabled();
                }
            }

            Boolean cached = normalizedSchemaCache;

            if (cached != null) {
                return cached;
            }

            boolean normalized = this.probeNormalizedSchema();
            normalizedSchemaCache = normalized;
            return normalized;
        }

        private boolean probeNormalizedSchema() throws SQLException {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('permission_ranks', 'permission_definitions')")) {
                try (ResultSet set = statement.executeQuery()) {
                    if (!set.next() || set.getInt(1) < 2) {
                        return false;
                    }
                }
            }

            try (Statement statement = this.connection.createStatement();
                    ResultSet set = statement.executeQuery("SELECT COUNT(*) FROM permission_ranks")) {
                return set.next() && set.getInt(1) > 0;
            }
        }

        @Override
        public List<Integer> rankIds() throws SQLException {
            List<Integer> cached = rankIdsCache;

            if (cached != null && System.currentTimeMillis() < rankIdsCacheExpires) {
                return cached;
            }

            List<Integer> ids = new ArrayList<>();

            try (Statement statement = this.connection.createStatement();
                    ResultSet set = statement.executeQuery("SELECT id FROM permission_ranks ORDER BY id ASC")) {
                while (set.next()) {
                    ids.add(set.getInt("id"));
                }
            }

            List<Integer> immutable = Collections.unmodifiableList(ids);
            rankIdsCache = immutable;
            rankIdsCacheExpires = System.currentTimeMillis() + RANK_IDS_CACHE_TTL_MS;
            return immutable;
        }
    }
}
