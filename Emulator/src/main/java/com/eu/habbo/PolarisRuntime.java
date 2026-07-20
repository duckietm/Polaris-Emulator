package com.eu.habbo;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.CryptoConfig;
import com.eu.habbo.core.DatabaseLogger;
import com.eu.habbo.core.Logging;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.util.imager.badges.BadgeImager;
import java.util.Objects;

/**
 * Owns the services assembled during one Polaris process lifecycle.
 *
 * <p>The legacy {@link Emulator} facade remains the public compatibility
 * surface while bootstrap and new internal code move to explicit ownership.</p>
 */
final class PolarisRuntime {

    private final RuntimeLifecycle lifecycle;
    private ConfigurationManager configuration;
    private CryptoConfig crypto;
    private TextsManager texts;
    private Database database;
    private DatabaseLogger databaseLogger;
    private GameServer gameServer;
    private RCONServer rconServer;
    private Logging logging;
    private ThreadPooling threading;
    private PersistenceExecutor persistenceExecutor;
    private GameEnvironment gameEnvironment;
    private PluginManager pluginManager;
    private BadgeImager badgeImager;

    PolarisRuntime(Runnable sessionCleanup) {
        this.lifecycle = new RuntimeLifecycle(this, sessionCleanup);
    }

    void shutdown() {
        lifecycle.shutdown();
    }

    ConfigurationManager configuration() {
        return configuration;
    }

    void installConfiguration(ConfigurationManager configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    CryptoConfig crypto() {
        return crypto;
    }

    void installCrypto(CryptoConfig crypto) {
        this.crypto = Objects.requireNonNull(crypto);
    }

    TextsManager texts() {
        return texts;
    }

    void installTexts(TextsManager texts) {
        this.texts = Objects.requireNonNull(texts);
    }

    Database database() {
        return database;
    }

    void installDatabase(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    DatabaseLogger databaseLogger() {
        return databaseLogger;
    }

    void installDatabaseLogger(DatabaseLogger databaseLogger) {
        this.databaseLogger = Objects.requireNonNull(databaseLogger);
    }

    GameServer gameServer() {
        return gameServer;
    }

    void installGameServer(GameServer gameServer) {
        this.gameServer = Objects.requireNonNull(gameServer);
    }

    RCONServer rconServer() {
        return rconServer;
    }

    void installRconServer(RCONServer rconServer) {
        this.rconServer = Objects.requireNonNull(rconServer);
    }

    Logging logging() {
        return logging;
    }

    void installLogging(Logging logging) {
        this.logging = Objects.requireNonNull(logging);
    }

    ThreadPooling threading() {
        return threading;
    }

    void installThreading(ThreadPooling threading) {
        this.threading = Objects.requireNonNull(threading);
    }

    PersistenceExecutor persistenceExecutor() {
        return persistenceExecutor;
    }

    void installPersistenceExecutor(PersistenceExecutor persistenceExecutor) {
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor);
    }

    GameEnvironment gameEnvironment() {
        return gameEnvironment;
    }

    void installGameEnvironment(GameEnvironment gameEnvironment) {
        this.gameEnvironment = Objects.requireNonNull(gameEnvironment);
    }

    PluginManager pluginManager() {
        return pluginManager;
    }

    void installPluginManager(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager);
    }

    BadgeImager badgeImager() {
        return badgeImager;
    }

    void installBadgeImager(BadgeImager badgeImager) {
        this.badgeImager = Objects.requireNonNull(badgeImager);
    }
}
