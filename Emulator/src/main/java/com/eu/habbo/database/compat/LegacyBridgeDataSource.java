package com.eu.habbo.database.compat;

import com.eu.habbo.database.observability.PoolSnapshot;
import com.eu.habbo.database.observability.Slf4jSlowQuerySink;
import com.eu.habbo.database.observability.SlowQueryMonitor;
import com.eu.habbo.database.observability.SlowQuerySettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariDataSource that routes every connection through the
 * {@link LegacySqlBridge}, so legacy plugin SQL is rewritten transparently.
 *
 * Extends HikariDataSource (instead of wrapping it) because
 * Database.getDataSource() must keep returning HikariDataSource — old plugin
 * jars were compiled against that exact signature.
 */
public class LegacyBridgeDataSource extends HikariDataSource {

    private final LegacySqlBridge bridge;
    private final SlowQueryMonitor slowQueryMonitor;

    /**
     * Preserves the existing public construction API for plugins, tests, and
     * embedding code while enabling slow-query diagnostics with safe defaults.
     */
    public LegacyBridgeDataSource(HikariConfig configuration, LegacySqlBridge bridge) {
        this(configuration, bridge, SlowQuerySettings.defaults());
    }

    public LegacyBridgeDataSource(
            HikariConfig configuration,
            LegacySqlBridge bridge,
            SlowQuerySettings slowQuerySettings) {
        super(configuration);
        this.bridge = bridge;
        this.slowQueryMonitor = new SlowQueryMonitor(
                slowQuerySettings,
                new Slf4jSlowQuerySink(),
                System::nanoTime,
                this::poolSnapshot,
                () -> Thread.currentThread().getName());
    }

    public LegacySqlBridge getBridge() {
        return this.bridge;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.bridge.wrap(this.slowQueryMonitor.wrap(super.getConnection()));
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return this.bridge.wrap(this.slowQueryMonitor.wrap(super.getConnection(username, password)));
    }

    private PoolSnapshot poolSnapshot() {
        HikariPoolMXBean pool = this.getHikariPoolMXBean();
        if (pool == null) {
            return PoolSnapshot.UNAVAILABLE;
        }
        return new PoolSnapshot(
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getTotalConnections(),
                pool.getThreadsAwaitingConnection());
    }
}
