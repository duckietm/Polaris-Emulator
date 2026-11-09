package polaris;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.core.CleanerThread;
import polaris.core.ConfigurationManager;
import polaris.core.ContentReloader;
import polaris.core.CryptoConfig;
import polaris.core.DatabaseLogger;
import polaris.core.Logging;
import polaris.core.OperationalProfile;
import polaris.core.TextsManager;
import polaris.database.Database;
import polaris.database.PersistenceExecutor;
import polaris.database.indexing.DatabaseIndexAuditor;
import polaris.database.integrity.DatabaseIntegrityAudit;
import polaris.database.integrity.IntegrityAuditOptions;
import polaris.database.migration.MigrationOptions;
import polaris.database.migration.MigrationRunner;
import polaris.hotel.GameEnvironment;
import polaris.hotel.catalog.marketplace.MarketplaceTokenService;
import polaris.hotel.catalog.marketplace.NftMarketplaceRuntime;
import polaris.hotel.catalog.marketplace.NftMarketplaceSettings;
import polaris.hotel.catalog.specialitems.SpecialItemClaimService;
import polaris.hotel.collectibles.CollectiblesRuntime;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.gameclients.SessionResumeManager;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.items.FurnitureDirectionsRuntime;
import polaris.hotel.items.FurnitureTextProvider;
import polaris.hotel.modtool.ConsoleModeration;
import polaris.hotel.users.UserInfo;
import polaris.imaging.camera.CameraRenderRuntime;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.CatalogUpdatedComposer;
import polaris.messages.outgoing.catalog.DiscountComposer;
import polaris.messages.outgoing.handshake.DisconnectReasonComposer;
import polaris.messages.outgoing.users.BanInfoComposer;
import polaris.networking.gameserver.GameServer;
import polaris.networking.rconserver.RCONServer;
import polaris.plugin.PluginManager;
import polaris.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import polaris.resilience.RuntimeResilienceRuntime;
import polaris.session.SessionRecoveryRuntime;
import polaris.stress.StressLimits;
import polaris.stress.StressRunManager;
import polaris.stress.StressRunRegistry;
import polaris.threading.ThreadPooling;
import polaris.util.imager.badges.BadgeImager;
import polaris.util.imager.badges.GuildBadgeSource;

/**
 * Manually wires the process runtime in explicit dependency phases.
 */
final class PolarisBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarisBootstrap.class);
    private static final int MAX_PERSISTENCE_QUEUE_CAPACITY = 65_536;

    private final PolarisRuntime runtime;
    private final Runnable registerConfigurationDefaults;

    PolarisBootstrap(PolarisRuntime runtime, Runnable registerConfigurationDefaults) {
        this.runtime = runtime;
        this.registerConfigurationDefaults = registerConfigurationDefaults;
    }

    void initializeLogging() {
        runtime.installLogging(new Logging());
        Emulator.synchronizeLegacyFacade(runtime);
    }

    boolean start(MigrationOptions migrationOptions, IntegrityAuditOptions integrityAuditOptions) throws Exception {
        return StartupPhases.run(List.of(
                new StartupPhases.Phase("configuration", this::initializeConfiguration),
                new StartupPhases.Phase("database", () -> initializeDatabase(migrationOptions, integrityAuditOptions)),
                new StartupPhases.Phase("plugins", this::initializePlugins),
                new StartupPhases.Phase("hotel", this::initializeHotel),
                new StartupPhases.Phase("network", this::initializeNetwork)));
    }

    private boolean initializeConfiguration() {
        ConfigurationManager configuration = new ConfigurationManager("config.ini");
        runtime.installConfiguration(configuration);
        runtime.installCrypto(new CryptoConfig(
                configuration.getBoolean("enc.enabled", false),
                configuration.getValue("enc.e"),
                configuration.getValue("enc.n"),
                configuration.getValue("enc.d")));
        Emulator.synchronizeLegacyFacade(runtime);
        return true;
    }

    private boolean initializeDatabase(MigrationOptions migrationOptions, IntegrityAuditOptions integrityAuditOptions)
            throws Exception {
        ConfigurationManager configuration = runtime.configuration();
        Database database = new Database(configuration);
        runtime.installDatabase(database);
        Emulator.synchronizeLegacyFacade(runtime);

        if (database.getDataSource() != null) {
            if (migrationOptions.mode() == MigrationOptions.Mode.REPAIR) {
                MigrationRunner.repairAtStartup(database.getDataSource());
                return false;
            }

            if (migrationOptions.mode() == MigrationOptions.Mode.RECONCILE) {
                System.out.print(MigrationRunner.reconcileAtStartup(database.getDataSource()));
                return false;
            }

            if (migrationOptions.mode() == MigrationOptions.Mode.VALIDATE) {
                System.out.print(MigrationRunner.statusAtStartup(database.getDataSource()));
                DatabaseIntegrityAudit.auditAtStartup(database.getDataSource(), configuration, integrityAuditOptions);
                return false;
            }

            if (migrationOptions.mode() == MigrationOptions.Mode.APPLY || migrationOptions.migrationsOnly()) {
                MigrationRunner.migrateAtStartup(database.getDataSource(), configuration);
            } else {
                MigrationRunner.runAtStartup(database.getDataSource(), configuration);
            }

            DatabaseIntegrityAudit.auditAtStartup(database.getDataSource(), configuration, integrityAuditOptions);

            if (migrationOptions.migrationsOnly()) {
                LOGGER.info("[migrate] Database migration completed; "
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
        configuration.register("db.persistence.queue.capacity", "0");

        int runtimeThreads = resolveRuntimeThreads(configuration);
        OperationalProfile.Settings operationalSettings = OperationalProfile.resolve(
                configuration.getValue("runtime.operational.profile", "custom"),
                runtimeThreads,
                configuration.getInt("persistence.executor.threads", 0),
                resolvePersistenceQueueCapacityOverride(configuration));
        PersistenceExecutor persistenceExecutor = new PersistenceExecutor(
                operationalSettings.persistenceThreads(), operationalSettings.persistenceQueueCapacity());
        LOGGER.info(
                "Operational profile {}: persistence threads={}, queue capacity={}",
                operationalSettings.profile().name().toLowerCase(java.util.Locale.ROOT),
                operationalSettings.persistenceThreads(),
                operationalSettings.persistenceQueueCapacity());
        runtime.installPersistenceExecutor(persistenceExecutor);
        runtime.installThreading(new ThreadPooling(runtimeThreads, persistenceExecutor));
        Emulator.synchronizeLegacyFacade(runtime);
        registerConfigurationDefaults.run();
        RuntimeResilienceRuntime.install(
                configuration, runtime.database(), runtime.persistenceExecutor(), runtime.threading());
        SessionRecoveryRuntime.install(configuration, runtime.database());
        return true;
    }

    static int resolveRuntimeThreads(ConfigurationManager configuration) {
        int configured = configuration.getInt("runtime.threads", 8);
        return configured > 0 ? configured : 8;
    }

    static int resolvePersistenceQueueCapacityOverride(ConfigurationManager configuration) {
        int configured = configuration.getInt("db.persistence.queue.capacity", 0);
        if (configured < 0 || configured > MAX_PERSISTENCE_QUEUE_CAPACITY) {
            LOGGER.warn("Ignoring invalid db.persistence.queue.capacity {}; using profile default", configured);
            return 0;
        }

        return configured;
    }

    private boolean initializePlugins() {
        PluginManager plugins = new PluginManager(runtime.configuration());
        runtime.installPluginManager(plugins);
        plugins.reload();
        plugins.fireEvent(new EmulatorConfigUpdatedEvent());

        TextsManager texts = new TextsManager();
        runtime.installTexts(texts);
        Emulator.synchronizeLegacyFacade(runtime);
        String hotelTimezoneId = runtime.configuration()
                .getValue("hotel.timezone", java.time.ZoneId.systemDefault().getId());
        System.out.println(Emulator.startupCard(hotelTimezoneId));
        texts.register("camera.permission", "You don't have permission to use the camera!");
        texts.register("camera.wait", "Please wait %seconds% seconds before making another picture.");
        texts.register("camera.error.creation", "Failed to create your picture. *sadpanda*");
        texts.register("camera.render.busy", "The camera is busy right now. Please try again in a moment.");

        File thumbnailDirectory = new File(runtime.configuration().getValue("imager.location.output.thumbnail"));
        if (!thumbnailDirectory.exists()) {
            thumbnailDirectory.mkdirs();
        }
        return true;
    }

    private boolean initializeHotel() throws Exception {
        new CleanerThread();
        GameEnvironment environment = new GameEnvironment(
                runtime.persistenceExecutor()::execute,
                new HabbiconService(runtime.database().getDataSource()));
        runtime.installGameEnvironment(environment);
        Emulator.synchronizeLegacyFacade(runtime);
        environment.load();
        return true;
    }

    private boolean initializeNetwork() throws Exception {
        ConfigurationManager configuration = runtime.configuration();
        polaris.networking.gameserver.auth.CloudflareIpRanges.loadAtStartup(configuration);
        GameServer gameServer = new GameServer(
                configuration.getValue("game.host", "127.0.0.1"), configuration.getInt("game.port", 30000));
        runtime.installGameServer(gameServer);
        // A plugin unloaded by :update_plugins takes its packet handlers and callables with it.
        runtime.pluginManager().onPluginUnload(gameServer.getPacketManager()::removeLoadedBy);
        ExecutorService catalogReloadExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CatalogReload");
            thread.setDaemon(true);
            return thread;
        });
        CatalogStudioRuntime.install(
                runtime.database().getDataSource(),
                runtime.gameEnvironment().getItemManager(),
                (published, nextDraftId) -> catalogReloadExecutor.submit(() -> {
                    try {
                        runtime.gameEnvironment().getCatalogManager().initialize();
                        gameServer.getGameClientManager().sendBroadcastResponse(new CatalogUpdatedComposer());
                    } catch (Exception exception) {
                        LOGGER.error("Catalog reload after publish failed", exception);
                    }
                }));
        SpecialItemClaimService.install(new SpecialItemClaimService(
                runtime.database().getDataSource(), runtime.gameEnvironment().getItemManager()));
        MarketplaceTokenService.install(
                new MarketplaceTokenService(runtime.database().getDataSource(), configuration));
        // The :update_* commands' work, for RCON reloadcontent (All-in-1-Manager). The managers are looked up when
        // a reload runs: the badge imager, for one, is only installed further down.
        ContentReloader.install(new ContentReloader(Map.ofEntries(
                Map.entry(
                        "achievements",
                        () -> runtime.gameEnvironment().getAchievementManager().reload()),
                Map.entry(
                        "dailytasks",
                        () -> runtime.gameEnvironment().getDailyTaskManager().reload()),
                Map.entry(
                        "quests",
                        () -> runtime.gameEnvironment().getQuestManager().reload()),
                Map.entry(
                        "rewardtracks",
                        () -> runtime.gameEnvironment().getRewardTrackManager().reloadAndBroadcast()),
                Map.entry(
                        "polls",
                        () -> runtime.gameEnvironment().getPollManager().loadPolls()),
                Map.entry("navigator", () -> {
                    runtime.gameEnvironment().getNavigatorManager().loadNavigator();
                    runtime.gameEnvironment().getRoomManager().loadRoomModels();
                }),
                Map.entry(
                        "petdata",
                        () -> runtime.gameEnvironment().getPetManager().reloadPetData()),
                Map.entry("guildparts", () -> {
                    runtime.gameEnvironment().getGuildManager().loadGuildParts();
                    if (runtime.badgeImager() != null) runtime.badgeImager().reload();
                }),
                Map.entry(
                        "chatbubbles",
                        () -> runtime.gameEnvironment()
                                .getRoomChatBubbleManager()
                                .reload()),
                Map.entry(
                        "calendar",
                        () -> runtime.gameEnvironment().getCalendarManager().reload()),
                Map.entry("hotelview", () -> {
                    runtime.gameEnvironment()
                            .getHotelViewManager()
                            .getNewsList()
                            .reload();
                    runtime.gameEnvironment()
                            .getHotelViewManager()
                            .getHallOfFame()
                            .reload();
                    runtime.gameEnvironment().getHotelViewManager().reloadAndBroadcastLandingView();
                }),
                Map.entry(
                        "bots", () -> runtime.gameEnvironment().getBotManager().reload()),
                Map.entry("config", configuration::reload),
                // The bulk discount settings, then the new rules to every client so prices shown match.
                Map.entry("discounts", () -> {
                    configuration.reload();
                    runtime.gameServer().getGameClientManager().sendBroadcastResponse(new DiscountComposer());
                }))));
        // Bans from the hotel's console, for RCON banuser / unbanuser (All-in-1-Manager).
        ConsoleModeration.install(new ConsoleModeration(
                id -> runtime.gameEnvironment().getUserManager().getUserInfo(id),
                id -> runtime.gameEnvironment().getUserManager().getUser(id),
                (id, seconds, reason, type) -> runtime.gameEnvironment()
                        .getModToolManager()
                        .createOfflineUserBan(id, 0, seconds, reason, type),
                (user, ban) -> {
                    if (user.getClient() == null) return;
                    user.getClient()
                            .sendResponse(new BanInfoComposer(
                                    ban.userId, ban.reason, Math.max(0, ban.expireDate - ban.timestamp), ""));
                    runtime.gameServer()
                            .getGameClientManager()
                            .disconnectWithReason(user.getClient(), DisconnectReasonComposer.JUST_BANNED);
                },
                id -> SessionResumeManager.getInstance().disposeGhostSession(id),
                id -> {
                    UserInfo info = runtime.gameEnvironment().getUserManager().getUserInfo(id);
                    return info != null
                            && runtime.gameEnvironment().getModToolManager().unban(info.getUsername());
                }));
        NftMarketplaceRuntime.install(new NftMarketplaceRuntime(
                runtime.database().getDataSource(),
                () -> NftMarketplaceSettings.from(configuration),
                runtime.gameEnvironment().getItemManager(),
                id -> runtime.gameEnvironment().getUserManager().getUser(id),
                runtime.pluginManager(),
                task -> runtime.threading().run(task),
                () -> (int) (System.currentTimeMillis() / 1000L)));
        CollectiblesRuntime.install(new CollectiblesRuntime(
                new CollectiblesService(runtime.database().getDataSource()),
                () -> CollectiblesSettings.from(configuration),
                runtime.gameEnvironment().getItemManager(),
                id -> runtime.gameEnvironment().getUserManager().getUser(id),
                () -> (int) (System.currentTimeMillis() / 1000L)));
        boolean stressEnabled = configuration.getBoolean("stress.enabled", false);
        StressRunRegistry.install(new StressRunManager(
                runtime.gameEnvironment().getRoomManager(),
                runtime.gameEnvironment().getItemManager(),
                runtime.threading(),
                StressLimits.configured(configuration)));
        RCONServer rconServer = new RCONServer(
                configuration.getValue("rcon.host", "127.0.0.1"),
                configuration.getInt("rcon.port", 30001),
                stressEnabled);
        runtime.installRconServer(rconServer);
        Emulator.synchronizeLegacyFacade(runtime);

        FurnitureTextProvider furnitureTexts = runtime.gameEnvironment().getFurnitureTextProvider();
        FurnitureDirectionsRuntime.start(
                configuration, furnitureTexts == null ? null : furnitureTexts::getDefaultDirection);

        gameServer.initializePipeline();
        gameServer.connect();
        gameServer.getGameClientManager().CFKeepAlive();
        rconServer.initializePipeline();
        rconServer.connect();
        BadgeImager badgeImager = new BadgeImager();
        runtime.installBadgeImager(badgeImager);
        badgeImager.installGuildBadgeSource(
                GuildBadgeSource.fromDataSource(runtime.database().getDataSource()));
        Emulator.synchronizeLegacyFacade(runtime);
        CameraRenderRuntime.start(configuration, badgeImager::compose);
        return true;
    }
}
