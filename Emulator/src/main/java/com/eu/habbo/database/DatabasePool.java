package com.eu.habbo.database;

import com.eu.habbo.core.ConfigurationManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DatabasePool {
    private final Logger log = LoggerFactory.getLogger(DatabasePool.class);

    // Connection settings
    private static final String DB_HOSTNAME_KEY = "db.hostname";
    private static final String DB_PORT_KEY     = "db.port";
    private static final String DB_PASSWORD_KEY = "db.password";
    private static final String DB_NAME_KEY     = "db.database";
    private static final String DB_USER_KEY     = "db.username";
    private static final String DB_PARAMS_KEY   = "db.params";

    // Pool sizing
    private static final String DB_POOL_MAX_SIZE = "db.pool.maxsize";
    private static final String DB_POOL_MIN_SIZE = "db.pool.minsize";

    // Pool tuning (all overridable via config.ini; sensible MariaDB defaults apply otherwise)
    private static final String DB_POOL_CONNECTION_TIMEOUT  = "db.pool.connection_timeout_ms";
    private static final String DB_POOL_IDLE_TIMEOUT        = "db.pool.idle_timeout_ms";
    private static final String DB_POOL_MAX_LIFETIME        = "db.pool.max_lifetime_ms";
    private static final String DB_POOL_LEAK_THRESHOLD      = "db.pool.leak_detection_ms";
    private static final String DB_POOL_VALIDATION_TIMEOUT  = "db.pool.validation_timeout_ms";

    private HikariDataSource database;

    DatabasePool() {
    }

    public boolean getStoragePooling(ConfigurationManager config) {
        try {
            HikariConfig databaseConfiguration = new HikariConfig();

            // Pool sizing
            databaseConfiguration.setMaximumPoolSize(config.getInt(DB_POOL_MAX_SIZE, 50));
            databaseConfiguration.setMinimumIdle(config.getInt(DB_POOL_MIN_SIZE, 10));

            // Pool timeouts (milliseconds)
            databaseConfiguration.setConnectionTimeout(config.getInt(DB_POOL_CONNECTION_TIMEOUT, 10_000));
            databaseConfiguration.setIdleTimeout(config.getInt(DB_POOL_IDLE_TIMEOUT, 600_000));
            databaseConfiguration.setMaxLifetime(config.getInt(DB_POOL_MAX_LIFETIME, 1_800_000));
            databaseConfiguration.setValidationTimeout(config.getInt(DB_POOL_VALIDATION_TIMEOUT, 5_000));

            // Leak detection: 0 disables it. Default 20s helps locate connections
            // that weren't closed in a try-with-resources block.
            int leakThreshold = config.getInt(DB_POOL_LEAK_THRESHOLD, 20_000);
            if (leakThreshold > 0) {
                databaseConfiguration.setLeakDetectionThreshold(leakThreshold);
            }

            // Use the MariaDB Connector/J native protocol instead of the Oracle MySQL driver.
            databaseConfiguration.setJdbcUrl("jdbc:mariadb://"
                    + config.getValue(DB_HOSTNAME_KEY, "localhost") + ":"
                    + config.getValue(DB_PORT_KEY, "3306") + "/"
                    + config.getValue(DB_NAME_KEY)
                    + config.getValue(DB_PARAMS_KEY));
            databaseConfiguration.setUsername(config.getValue(DB_USER_KEY));
            databaseConfiguration.setPassword(config.getValue(DB_PASSWORD_KEY));

            // Prepared-statement caching. Without these, Hikari's cache is off entirely
            // and every prepareStatement() call re-parses on the server side.
            databaseConfiguration.addDataSourceProperty("cachePrepStmts",          "true");
            databaseConfiguration.addDataSourceProperty("prepStmtCacheSize",       "500");
            databaseConfiguration.addDataSourceProperty("prepStmtCacheSqlLimit",   "2048");
            databaseConfiguration.addDataSourceProperty("useServerPrepStmts",      "true");

            // Bulk write throughput: rewrites batched INSERTs into a single multi-value
            // INSERT statement. Huge win for item/room/inventory persistence paths.
            databaseConfiguration.addDataSourceProperty("rewriteBatchedStatements", "true");

            // Cut per-connection round-trips.
            databaseConfiguration.addDataSourceProperty("cacheServerConfiguration", "true");
            databaseConfiguration.addDataSourceProperty("useLocalSessionState",     "true");
            databaseConfiguration.addDataSourceProperty("cacheResultSetMetadata",   "true");
            databaseConfiguration.addDataSourceProperty("elideSetAutoCommits",      "true");

            // Fail fast instead of pinning a pooled connection (and its worker
            // thread) indefinitely on a stalled/slow MariaDB. HikariCP's
            // connectionTimeout only bounds the pool *borrow*; these bound the
            // actual socket/connect round-trip. Overridable via db.params.
            databaseConfiguration.addDataSourceProperty("socketTimeout",  "30000");
            databaseConfiguration.addDataSourceProperty("connectTimeout", "10000");
            databaseConfiguration.addDataSourceProperty("tcpKeepAlive",   "true");
            databaseConfiguration.addDataSourceProperty("maintainTimeStats",        "false");

            databaseConfiguration.setPoolName("HabboHikariPool");

            log.info("INITIALIZING DATABASE SERVER: " + config.getValue(DB_HOSTNAME_KEY));
            log.info("ON PORT: " + config.getValue(DB_PORT_KEY));
            log.info("HABBO DATABASE: " + config.getValue(DB_NAME_KEY));
            log.info("USING DRIVER: MariaDB Connector/J");

            this.database = new HikariDataSource(databaseConfiguration);
        } catch (Exception e) {
            log.error("Error initializing database connection pool: {}", e.getMessage());
            return false;
        }
        return true;
    }

    public HikariDataSource getDatabase() {
        if (database == null) {
            throw new IllegalStateException("Database connection pool is not initialized.");
        }
        return database;
    }
}
