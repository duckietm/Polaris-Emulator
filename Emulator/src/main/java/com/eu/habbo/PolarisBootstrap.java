package com.eu.habbo;

import com.eu.habbo.core.CleanerThread;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.CryptoConfig;
import com.eu.habbo.core.DatabaseLogger;
import com.eu.habbo.core.Logging;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.database.indexing.DatabaseIndexAuditor;
import com.eu.habbo.database.integrity.DatabaseIntegrityAudit;
import com.eu.habbo.database.integrity.IntegrityAuditOptions;
import com.eu.habbo.database.migration.MigrationOptions;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.networking.rconserver.RCONServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.util.imager.badges.BadgeImager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Manually wires the process runtime in explicit dependency phases.
 */
final class PolarisBootstrap {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PolarisBootstrap.class);

    private final PolarisRuntime runtime;
    private final Runnable registerConfigurationDefaults;

    PolarisBootstrap(
            PolarisRuntime runtime,
            Runnable registerConfigurationDefaults) {
        this.runtime = runtime;
        this.registerConfigurationDefaults = registerConfigurationDefaults;
    }

    void initializeLogging() {
        runtime.installLogging(new Logging());
        Emulator.synchronizeLegacyFacade(runtime);
    }

    boolean start(
            MigrationOptions migrationOptions,
            IntegrityAuditOptions integrityAuditOptions) throws Exception {
        return StartupPhases.run(List.of(
                new StartupPhases.Phase(
                        "configuration",
                        this::initializeConfiguration),
                new StartupPhases.Phase(
                        "database",
                        () -> initializeDatabase(
                                migrationOptions,
                                integrityAuditOptions)),
                new StartupPhases.Phase("plugins", this::initializePlugins),
                new StartupPhases.Phase("hotel", this::initializeHotel),
                new StartupPhases.Phase("network", this::initializeNetwork)));
    }

    private boolean initializeConfiguration() {
        ConfigurationManager configuration =
                new ConfigurationManager("config.ini");
        runtime.installConfiguration(configuration);
        runtime.installCrypto(new CryptoConfig(
                configuration.getBoolean("enc.enabled", false),
                configuration.getValue("enc.e"),
                configuration.getValue("enc.n"),
                configuration.getValue("enc.d")));
        Emulator.synchronizeLegacyFacade(runtime);
        return true;
    }

    private boolean initializeDatabase(
            MigrationOptions migrationOptions,
            IntegrityAuditOptions integrityAuditOptions) throws Exception {
        ConfigurationManager configuration = runtime.configuration();
        Database database = new Database(configuration);
        runtime.installDatabase(database);
        Emulator.synchronizeLegacyFacade(runtime);

        if (database.getDataSource() != null) {
            if (migrationOptions.mode() == MigrationOptions.Mode.VALIDATE) {
                System.out.print(MigrationRunner.statusAtStartup(
                        database.getDataSource()));
                DatabaseIntegrityAudit.auditAtStartup(
                        database.getDataSource(),
                        configuration,
                        integrityAuditOptions);
                return false;
            }

            if (migrationOptions.mode() == MigrationOptions.Mode.APPLY
                    || migrationOptions.migrationsOnly()) {
                MigrationRunner.migrateAtStartup(
                        database.getDataSource(),
                        configuration);
            } else {
                MigrationRunner.runAtStartup(
                        database.getDataSource(),
                        configuration);
            }

            DatabaseIntegrityAudit.auditAtStartup(
                    database.getDataSource(),
                    configuration,
                    integrityAuditOptions);

            if (migrationOptions.migrationsOnly()) {
                LOGGER.info(
                        "[migrate] Database migration completed; "
                                + "--migrations-only requested, so the "
                                + "emulator will not start.");
                return false;
            }
        }

        DatabaseIndexAuditor.auditAtStartup(database.getDataSource());
        runtime.installDatabaseLogger(new DatabaseLogger());
        configuration.loaded = true;
        configuration.loadFromDatabase();
        configuration.register("runtime.threads", "8");

        int runtimeThreads = resolveRuntimeThreads(configuration);
        PersistenceExecutor persistenceExecutor =
                PersistenceExecutor.forRuntimeThreads(
                        runtimeThreads);
        runtime.installPersistenceExecutor(persistenceExecutor);
        runtime.installThreading(new ThreadPooling(
                runtimeThreads,
                persistenceExecutor));
        Emulator.synchronizeLegacyFacade(runtime);
        registerConfigurationDefaults.run();
        return true;
    }

    static int resolveRuntimeThreads(ConfigurationManager configuration) {
        int configured = configuration.getInt("runtime.threads", 8);
        return configured > 0 ? configured : 8;
    }

    private boolean initializePlugins() {
        PluginManager plugins = new PluginManager();
        runtime.installPluginManager(plugins);
        plugins.reload();
        plugins.fireEvent(new EmulatorConfigUpdatedEvent());

        TextsManager texts = new TextsManager();
        runtime.installTexts(texts);
        Emulator.synchronizeLegacyFacade(runtime);
        String hotelTimezoneId = runtime.configuration().getValue(
                "hotel.timezone",
                java.time.ZoneId.systemDefault().getId());
        System.out.println(Emulator.startupCard(hotelTimezoneId));
        texts.register(
                "camera.permission",
                "You don't have permission to use the camera!");
        texts.register(
                "camera.wait",
                "Please wait %seconds% seconds before making another picture.");
        texts.register(
                "camera.error.creation",
                "Failed to create your picture. *sadpanda*");

        File thumbnailDirectory = new File(runtime.configuration().getValue(
                "imager.location.output.thumbnail"));
        if (!thumbnailDirectory.exists()) {
            thumbnailDirectory.mkdirs();
        }
        return true;
    }

    private boolean initializeHotel() throws Exception {
        new CleanerThread();
        GameEnvironment environment = new GameEnvironment(
                runtime.persistenceExecutor()::execute);
        runtime.installGameEnvironment(environment);
        Emulator.synchronizeLegacyFacade(runtime);
        environment.load();
        return true;
    }

    private boolean initializeNetwork() throws Exception {
        ConfigurationManager configuration = runtime.configuration();
        GameServer gameServer = new GameServer(
                configuration.getValue("game.host", "127.0.0.1"),
                configuration.getInt("game.port", 30000));
        runtime.installGameServer(gameServer);
        RCONServer rconServer = new RCONServer(
                configuration.getValue("rcon.host", "127.0.0.1"),
                configuration.getInt("rcon.port", 30001));
        runtime.installRconServer(rconServer);
        Emulator.synchronizeLegacyFacade(runtime);

        gameServer.initializePipeline();
        gameServer.connect();
        gameServer.getGameClientManager().CFKeepAlive();
        rconServer.initializePipeline();
        rconServer.connect();
        runtime.installBadgeImager(new BadgeImager());
        Emulator.synchronizeLegacyFacade(runtime);
        return true;
    }
}
