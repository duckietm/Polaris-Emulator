package com.eu.habbo.messages.incoming.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.CryptoConfig;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.MaintenanceMode;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.habbohotel.gameclients.SessionResumeManager;
import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.quests.QuestProgressEvents;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.habbohotel.users.inventory.EffectsComponent;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.collectibles.CollectibleWardrobe;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.commands.AvailableCommandsComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterGameListComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.MessagesForYouComposer;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NewUserExperienceNotCompleteComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NewUserIdentityComposer;
import com.eu.habbo.messages.outgoing.handshake.AvailabilityStatusMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.EnableNotificationsComposer;
import com.eu.habbo.messages.outgoing.handshake.PingComposer;
import com.eu.habbo.messages.outgoing.handshake.SecureLoginOKComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.AvatarEffectSelectedComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryAchievementsComposer;
import com.eu.habbo.messages.outgoing.inventory.UserEffectsListComposer;
import com.eu.habbo.messages.outgoing.modtool.CfhTopicsMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueHandlerDimensionsComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolSanctionInfoComposer;
import com.eu.habbo.messages.outgoing.mysterybox.MysteryBoxKeysComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;
import com.eu.habbo.messages.outgoing.unknown.UnknownStatusComposer;
import com.eu.habbo.messages.outgoing.users.FavoriteRoomsCountComposer;
import com.eu.habbo.messages.outgoing.users.UserAchievementScoreComposer;
import com.eu.habbo.messages.outgoing.users.UserCitizinShipComposer;
import com.eu.habbo.messages.outgoing.users.UserClothesComposer;
import com.eu.habbo.messages.outgoing.users.UserClubComposer;
import com.eu.habbo.messages.outgoing.users.UserHomeRoomComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import com.eu.habbo.networking.gameserver.GameServer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import com.eu.habbo.resilience.RuntimeResilienceController;
import com.eu.habbo.resilience.RuntimeResilienceRuntime;
import com.eu.habbo.session.SessionRecoveryRuntime;
import com.eu.habbo.threading.ThreadPooling;
import com.zaxxer.hikari.HikariDataSource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Records what {@link SecureLoginEvent#handle} does with one login packet: the connection gates, the ticket lookup,
 * a resumed or a fresh session, the login state it sends, the sanctions it replays and the login-only events. The
 * traces live in {@code handshake/secure-login-traces.txt}; regenerate them with
 * {@code -Dpolaris.secureLoginTraces.update=true}.
 */
class SecureLoginEventBehaviourTest {
    private static final int NOW = 1_700_000_000;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private final List<Runnable> delayed = new ArrayList<>();
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private final Map<String, Boolean> flags = new HashMap<>();
    private final Map<String, String> values = new HashMap<>();
    private final Set<String> permissions = new LinkedHashSet<>();
    private boolean channelOpen;
    private boolean handshakeFinished;
    private String release;
    private RuntimeResilienceController.Action admission;
    private int lookupUserId;
    private boolean lookupThrows;
    private Habbo parked;
    private Habbo bySso;
    private Habbo byRecovery;
    private int recoveredUserId;
    private GameClient earlierConnection;
    private GameClient previousSession;
    private boolean securityChecksPass;
    private boolean connectAccepted;
    private boolean maintenanceAllows;
    private String validatedLook;
    private boolean habbiconsThrow;
    private List<Integer> unseenHabbicons;
    private boolean buildersClubActive;
    private int buildersClubSecondsLeft;
    private ArrayList<ModToolSanctionItem> sanctions;
    private boolean pluginCancels;

    private GameClient client;
    private Habbo habbo;
    private HabboInfo info;
    private HabboStats stats;

    private boolean wasReady;
    private boolean validatedOnLogin;
    private boolean payday;
    private int homeRoom;
    private String welcome;

    @BeforeEach
    void setUp() throws Exception {
        this.wasReady = Emulator.isReady;
        this.validatedOnLogin = ClothingValidationManager.VALIDATE_ON_LOGIN;
        this.payday = SubscriptionHabboClub.HC_PAYDAY_ENABLED;
        this.homeRoom = RoomManager.HOME_ROOM_ID;
        this.welcome = HabboManager.WELCOME_MESSAGE;
        HabboManager.WELCOME_MESSAGE = "Welcome %username% (%user%)<br/>have fun";

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString())).thenAnswer(i -> this.flags.getOrDefault(i.<String>getArgument(0), false));
        when(config.getBoolean(anyString(), anyBoolean()))
                .thenAnswer(i -> this.flags.getOrDefault(i.<String>getArgument(0), i.<Boolean>getArgument(1)));
        when(config.getValue(anyString(), anyString()))
                .thenAnswer(i -> this.values.getOrDefault(i.<String>getArgument(0), i.getArgument(1)));
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        CryptoConfig crypto = mock(CryptoConfig.class);
        when(crypto.isEnabled()).thenReturn(true);

        GameClientManager clients = mock(GameClientManager.class);
        doAnswer(i -> this.record("clients.disposeClient(" + this.label(i.getArgument(0)) + ")"))
                .when(clients)
                .disposeClient(any(GameClient.class));
        doAnswer(i -> this.record("clients.forceDisposeClient(" + this.label(i.getArgument(0)) + ")"))
                .when(clients)
                .forceDisposeClient(any(GameClient.class));
        doAnswer(i -> this.record(
                        "clients.disconnectWithReason(" + this.label(i.getArgument(0)) + ", " + i.getArgument(1) + ")"))
                .when(clients)
                .disconnectWithReason(any(GameClient.class), anyInt());
        when(clients.findClientBySsoTicket(anyString())).thenAnswer(i -> {
            this.record(
                    "clients.findClientBySsoTicket(" + i.getArgument(0) + ") -> " + this.label(this.earlierConnection));
            return this.earlierConnection;
        });
        when(clients.claimAuthenticatedSession(anyInt(), any())).thenAnswer(i -> {
            this.record("clients.claimAuthenticatedSession(" + i.getArgument(0) + ", " + this.label(i.getArgument(1))
                    + ") -> " + this.label(this.previousSession));
            return this.previousSession;
        });
        GameServer server = mock(GameServer.class);
        when(server.getGameClientManager()).thenReturn(clients);

        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenAnswer(i -> this.connection());

        HabboManager habbos = mock(HabboManager.class);
        when(habbos.loadHabbo(anyString())).thenAnswer(i -> {
            this.record("habbos.loadHabbo(" + i.getArgument(0) + ") -> " + this.label(this.bySso));
            return this.bySso;
        });
        when(habbos.loadHabboById(anyInt())).thenAnswer(i -> {
            this.record("habbos.loadHabboById(" + i.getArgument(0) + ") -> " + this.label(this.byRecovery));
            return this.byRecovery;
        });
        doAnswer(i -> this.record("habbos.consumeSsoTicket(" + i.getArgument(0) + ")"))
                .when(habbos)
                .consumeSsoTicket(anyInt());
        doAnswer(i -> this.record("habbos.addHabbo(" + this.label(i.getArgument(0)) + ")"))
                .when(habbos)
                .addHabbo(any());
        CommandHandler commands = mock(CommandHandler.class);
        when(commands.getCommandsForRank(anyInt())).thenReturn(List.of());
        ModToolSanctions modToolSanctions = mock(ModToolSanctions.class);
        when(modToolSanctions.getSanctions(anyInt())).thenAnswer(i -> {
            Map<Integer, ArrayList<ModToolSanctionItem>> map = new HashMap<>();
            if (this.sanctions != null) map.put(i.getArgument(0), this.sanctions);
            return map;
        });
        doAnswer(i -> this.record("sanctions.updateSanction(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(modToolSanctions)
                .updateSanction(anyInt(), anyInt());
        doAnswer(i -> this.record(
                        "sanctions.updateTradeLockedUntil(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(modToolSanctions)
                .updateTradeLockedUntil(anyInt(), anyInt());
        doAnswer(i -> this.record("sanctions.updateMuteDuration(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(modToolSanctions)
                .updateMuteDuration(anyInt(), anyInt());
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getHabboManager()).thenReturn(habbos);
        when(environment.getCommandHandler()).thenReturn(commands);
        when(environment.getModToolSanctions()).thenReturn(modToolSanctions);

        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class)))
                .thenAnswer(i -> this.record("threading.run(" + this.label(i.getArgument(0)) + ")"));
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(i -> {
            this.record("threading.run(delayed task, " + i.getArgument(1) + "ms)");
            this.delayed.add(i.getArgument(0));
            return null;
        });
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.fireEvent(any())).thenAnswer(i -> {
            Object event = i.getArgument(0);
            if (event instanceof UserLoginEvent login) {
                this.record("plugins.fireEvent(UserLoginEvent " + this.label(login.habbo) + ") -> "
                        + (this.pluginCancels ? "cancelled" : "accepted"));
                login.setCancelled(this.pluginCancels);
            }
            return event;
        });

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getCrypto).thenReturn(crypto);
        emulator.when(Emulator::getGameServer).thenReturn(server);
        emulator.when(Emulator::getDatabase).thenReturn(database);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        emulator.when(Emulator::getIntUnixTimestamp).thenReturn(NOW);
        emulator.when(Emulator::getDate).thenAnswer(i -> new Date(NOW * 1000L));
        this.statics.add(emulator);

        MockedStatic<RuntimeResilienceRuntime> resilience = mockStatic(RuntimeResilienceRuntime.class);
        resilience
                .when(() -> RuntimeResilienceRuntime.admit(any()))
                .thenAnswer(i -> new RuntimeResilienceController.Admission(
                        null, i.getArgument(0), this.admission, this.admission));
        this.statics.add(resilience);
        SessionResumeManager resume = mock(SessionResumeManager.class);
        when(resume.resumeSession(anyInt())).thenAnswer(i -> {
            this.record("resume.resumeSession(" + i.getArgument(0) + ") -> " + this.label(this.parked));
            return this.parked;
        });
        MockedStatic<SessionResumeManager> resumeManager = mockStatic(SessionResumeManager.class);
        resumeManager.when(SessionResumeManager::getInstance).thenReturn(resume);
        this.statics.add(resumeManager);
        MockedStatic<SessionRecoveryRuntime> recovery = mockStatic(SessionRecoveryRuntime.class);
        recovery.when(() -> SessionRecoveryRuntime.consume(anyString())).thenAnswer(i -> {
            OptionalInt user = this.recoveredUserId > 0 ? OptionalInt.of(this.recoveredUserId) : OptionalInt.empty();
            this.record("recovery.consume(" + i.getArgument(0) + ") -> " + user);
            return user;
        });
        recovery.when(() -> SessionRecoveryRuntime.issue(anyInt())).thenAnswer(i -> "next-token-" + i.getArgument(0));
        this.statics.add(recovery);
        MockedStatic<MaintenanceMode> maintenance = mockStatic(MaintenanceMode.class);
        maintenance.when(() -> MaintenanceMode.canLogin(anyInt())).thenAnswer(i -> {
            this.record("maintenance.canLogin(" + i.getArgument(0) + ") -> " + this.maintenanceAllows);
            return this.maintenanceAllows;
        });
        maintenance.when(MaintenanceMode::getMessage).thenReturn("closed for maintenance");
        this.statics.add(maintenance);
        MockedStatic<ClothingValidationManager> clothing = mockStatic(ClothingValidationManager.class);
        clothing.when(() -> ClothingValidationManager.validateLook(any(Habbo.class)))
                .thenAnswer(i -> this.validatedLook);
        this.statics.add(clothing);
        MockedStatic<CollectibleWardrobe> wardrobe = mockStatic(CollectibleWardrobe.class);
        wardrobe.when(() -> CollectibleWardrobe.load(any()))
                .thenAnswer(i -> this.record("CollectibleWardrobe.load(" + this.label(i.getArgument(0)) + ")"));
        this.statics.add(wardrobe);
        MockedStatic<BuildersClubRoomSupport> builders = mockStatic(BuildersClubRoomSupport.class);
        builders.when(() -> BuildersClubRoomSupport.syncOwnedRooms(anyInt()))
                .thenAnswer(i -> this.record("BuildersClub.syncOwnedRooms(" + i.getArgument(0) + ")"));
        builders.when(() -> BuildersClubRoomSupport.getMembershipSecondsLeft(anyInt()))
                .thenAnswer(i -> this.buildersClubSecondsLeft);
        builders.when(() -> BuildersClubRoomSupport.hasTrackedItemsInOwnedRooms(anyInt()))
                .thenReturn(true);
        builders.when(() -> BuildersClubRoomSupport.sendMembershipExpiringAlert(anyInt()))
                .thenAnswer(i -> this.record("BuildersClub.sendMembershipExpiringAlert(" + i.getArgument(0) + ")"));
        builders.when(() -> BuildersClubRoomSupport.sendMembershipExpiredAlert(anyInt(), anyBoolean()))
                .thenAnswer(i -> this.record(
                        "BuildersClub.sendMembershipExpiredAlert(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"));
        this.statics.add(builders);
        MockedStatic<UserCitizinShipComposer> citizenship = mockStatic(UserCitizinShipComposer.class);
        citizenship.when(() -> UserCitizinShipComposer.forHabbo(any(), any())).thenAnswer(i -> {
            UserCitizinShipComposer composer = mock(UserCitizinShipComposer.class);
            this.labels.put(composer, "UserCitizinShip(" + i.<TalentTrackType>getArgument(1) + ")");
            return composer;
        });
        this.statics.add(citizenship);
        MockedStatic<QuestProgressEvents> quests = mockStatic(QuestProgressEvents.class);
        quests.when(() -> QuestProgressEvents.loggedIn(any()))
                .thenAnswer(i -> this.record("QuestProgressEvents.loggedIn(" + this.label(i.getArgument(0)) + ")"));
        this.statics.add(quests);
        MockedStatic<UsernameEvent> username = mockStatic(UsernameEvent.class);
        username.when(() -> UsernameEvent.completeLogin(any()))
                .thenAnswer(i -> this.record("UsernameEvent.completeLogin(" + this.label(i.getArgument(0)) + ")"));
        username.when(() -> UsernameEvent.isFirstLoginOfDay(anyInt(), anyInt())).thenReturn(true);
        this.statics.add(username);
        MockedStatic<SubscriptionHabboClub> club = mockStatic(SubscriptionHabboClub.class);
        club.when(() -> SubscriptionHabboClub.processUnclaimed(any()))
                .thenAnswer(i -> this.record("HabboClub.processUnclaimed(" + this.label(i.getArgument(0)) + ")"));
        club.when(() -> SubscriptionHabboClub.processClubBadge(any()))
                .thenAnswer(i -> this.record("HabboClub.processClubBadge(" + this.label(i.getArgument(0)) + ")"));
        this.statics.add(club);
        MockedStatic<Messenger> messenger = mockStatic(Messenger.class);
        messenger
                .when(() -> Messenger.checkFriendSizeProgress(any()))
                .thenAnswer(
                        i -> this.record("Messenger.checkFriendSizeProgress(" + this.label(i.getArgument(0)) + ")"));
        this.statics.add(messenger);

        this.composer(SecureLoginOKComposer.class, "SecureLoginOK");
        this.composer(UserHomeRoomComposer.class, "UserHomeRoom");
        this.composer(UserEffectsListComposer.class, "UserEffectsList");
        this.composer(UserClothesComposer.class, "UserClothes");
        this.composer(UserHabbiconsComposer.class, "UserHabbicons");
        this.composer(AddHabboItemComposer.class, "AddHabboItem");
        this.composer(AvatarEffectSelectedComposer.class, "AvatarEffectSelected");
        this.composer(NewUserIdentityComposer.class, "NewUserIdentity");
        this.composer(NewUserExperienceNotCompleteComposer.class, "NewUserExperienceNotComplete");
        this.composer(UserPermissionsComposer.class, "UserPermissions");
        this.composer(AvailableCommandsComposer.class, "AvailableCommands");
        this.composer(AvailabilityStatusMessageComposer.class, "AvailabilityStatus");
        this.composer(PingComposer.class, "Ping");
        this.composer(EnableNotificationsComposer.class, "EnableNotifications");
        this.composer(UserAchievementScoreComposer.class, "UserAchievementScore");
        this.composer(IsFirstLoginOfDayComposer.class, "IsFirstLoginOfDay");
        this.composer(MysteryBoxKeysComposer.class, "MysteryBoxKeys");
        this.composer(BuildersClubSubscriptionStatusComposer.class, "BuildersClubSubscriptionStatus");
        this.composer(CfhTopicsMessageComposer.class, "CfhTopics");
        this.composer(FavoriteRoomsCountComposer.class, "FavoriteRoomsCount");
        this.composer(GameCenterGameListComposer.class, "GameCenterGameList");
        this.composer(GameCenterAccountInfoComposer.class, "GameCenterAccountInfo");
        this.composer(UserClubComposer.class, "UserClub");
        this.composer(ModToolComposer.class, "ModTool");
        this.composer(InventoryAchievementsComposer.class, "InventoryAchievements");
        this.composer(UnknownStatusComposer.class, "SafetyLockStatus");
        this.composer(ModToolIssueHandlerDimensionsComposer.class, "ModToolIssueHandlerDimensions");
        this.composer(ModToolSanctionInfoComposer.class, "ModToolSanctionInfo");
        this.composer(MessagesForYouComposer.class, "MessagesForYou");
        this.composer(GenericAlertComposer.class, "GenericAlert");
        this.composer(NewNavigatorSavedSearchesComposer.class, "NewNavigatorSavedSearches");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
        Emulator.isReady = this.wasReady;
        ClothingValidationManager.VALIDATE_ON_LOGIN = this.validatedOnLogin;
        SubscriptionHabboClub.HC_PAYDAY_ENABLED = this.payday;
        RoomManager.HOME_ROOM_ID = this.homeRoom;
        HabboManager.WELCOME_MESSAGE = this.welcome;
    }

    // ---------------------------------------------------------------- gates before the ticket is read

    @Test
    void connectionGates() throws Exception {
        this.fresh();
        this.channelOpen = false;
        this.login("gate / channel already closed", "ticket-1", "");

        this.fresh();
        Emulator.isReady = false;
        this.login("gate / emulator not ready", "ticket-1", "");

        this.fresh();
        this.flags.put("encryption.forced", true);
        this.handshakeFinished = false;
        this.login("gate / encryption forced, handshake not finished", "ticket-1", "");

        this.fresh();
        this.flags.put("encryption.forced", true);
        this.login("gate / encryption forced, handshake finished", "ticket-1", "");

        this.fresh();
        this.release = "OTHER-RELEASE";
        this.login("gate / client release refused", "ticket-1", "");

        this.fresh();
        this.admission = RuntimeResilienceController.Action.REJECT;
        this.login("gate / hotel too busy", "ticket-1", "");

        this.fresh();
        this.admission = RuntimeResilienceController.Action.DEFER;
        this.login("gate / deferred admission still logs in", "ticket-1", "");
    }

    @Test
    void ticketGates() throws Exception {
        this.fresh();
        this.login("ticket / empty", "", "");

        this.fresh();
        this.login("ticket / only spaces", "   ", "");

        this.fresh();
        this.login("ticket / longer than 128", "t".repeat(129), "");

        this.fresh();
        this.login("ticket / longer than 512", "t".repeat(513), "");

        this.fresh();
        this.login("ticket / spaces are removed", " ticket 1 ", "");

        this.fresh();
        when(this.client.getHabbo()).thenReturn(this.habbo);
        this.login("ticket / connection already logged in", "ticket-1", "");
    }

    // ---------------------------------------------------------------- finding the user

    @Test
    void lookup() throws Exception {
        this.fresh();
        this.bySso = null;
        this.login("lookup / unknown ticket", "ticket-1", "");

        this.fresh();
        this.bySso = null;
        this.byRecovery = this.habbo;
        this.recoveredUserId = 7;
        this.login("lookup / recovered by token", "ticket-1", "recovery-1");

        this.fresh();
        this.bySso = null;
        this.login("lookup / recovery token unknown", "ticket-1", "recovery-1");

        this.fresh();
        this.bySso = null;
        this.recoveredUserId = 7;
        this.byRecovery = null;
        this.login("lookup / recovered user cannot be loaded", "ticket-1", "recovery-1");

        this.fresh();
        this.lookupThrows = true;
        this.login("lookup / database lookup fails", "ticket-1", "");

        this.fresh();
        this.earlierConnection = this.namedClient("earlier connection");
        this.login("lookup / earlier connection with the same ticket", "ticket-1", "");

        this.fresh();
        this.previousSession = this.namedClient("previous session");
        this.login("lookup / duplicate session replaced", "ticket-1", "");
    }

    // ---------------------------------------------------------------- a fresh login

    @Test
    void freshLogin() throws Exception {
        this.fresh();
        this.login("fresh / default", "ticket-1", "");

        this.fresh();
        this.connectAccepted = false;
        this.login("fresh / connect refused", "ticket-1", "");

        this.fresh();
        when(this.habbo.getHabboInfo()).thenReturn(null);
        this.login("fresh / no info", "ticket-1", "");

        this.fresh();
        when(this.info.getRank()).thenReturn(null);
        this.login("fresh / rank missing", "ticket-1", "");

        this.fresh();
        this.maintenanceAllows = false;
        this.login("fresh / maintenance refuses the rank", "ticket-1", "");

        this.fresh();
        when(this.client.getMachineId()).thenReturn("machine-9");
        this.login("fresh / machine id already known", "ticket-1", "");

        this.fresh();
        ClothingValidationManager.VALIDATE_ON_LOGIN = true;
        this.validatedLook = "hd-180-1";
        this.login("fresh / look corrected on login", "ticket-1", "");

        this.fresh();
        ClothingValidationManager.VALIDATE_ON_LOGIN = true;
        this.login("fresh / look already valid", "ticket-1", "");

        this.fresh();
        this.pluginCancels = true;
        this.login("fresh / plugin cancels the login", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.welcome.alert.enabled", true);
        this.login("fresh / welcome alert", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.welcome.alert.enabled", true);
        this.flags.put("hotel.welcome.alert.oldstyle", true);
        this.login("fresh / welcome alert old style", "ticket-1", "");

        this.fresh();
        SubscriptionHabboClub.HC_PAYDAY_ENABLED = true;
        this.login("fresh / club payday", "ticket-1", "");

        this.fresh();
        this.stats.hasGottenDefaultSavedSearches = true;
        this.login("fresh / saved searches already given", "ticket-1", "");
    }

    @Test
    void loginState() throws Exception {
        this.fresh();
        this.unseenHabbicons = List.of(4, 2);
        this.login("state / unseen habbicons", "ticket-1", "");

        this.fresh();
        when(this.habbo.getHabbiconService()).thenReturn(null);
        this.login("state / no habbicon service", "ticket-1", "");

        this.fresh();
        this.habbiconsThrow = true;
        this.login("state / habbicons cannot be read", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.nux.gifts.enabled", true);
        this.stats.nuxReward = false;
        this.login("state / new user gift offered", "ticket-1", "");

        this.fresh();
        this.permissions.add(Permission.ACC_SUPPORTTOOL);
        this.stats.modToolWindowX = 10;
        this.stats.modToolWindowY = 20;
        this.stats.modToolWindowWidth = 300;
        this.stats.modToolWindowHeight = 400;
        this.login("state / moderator with a saved window", "ticket-1", "");

        this.fresh();
        this.permissions.add(Permission.ACC_SUPPORTTOOL);
        this.login("state / moderator without a saved window", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.talenttrack.enabled", true);
        this.login("state / talent tracks", "ticket-1", "");

        this.fresh();
        this.stats.safetyLocked = true;
        this.login("state / account safety locked", "ticket-1", "");

        this.fresh();
        this.flags.put("bubblealerts.enabled", false);
        this.login("state / bubble alerts disabled", "ticket-1", "");
    }

    @Test
    void homeRoom() throws Exception {
        RoomManager.HOME_ROOM_ID = 900;

        this.fresh();
        this.stats.nux = false;
        when(this.info.getHomeRoom()).thenReturn(12);
        this.login("home room / tour not done, own home room", "ticket-1", "");

        this.fresh();
        this.stats.nux = false;
        this.login("home room / tour not done, no home room", "ticket-1", "");

        this.fresh();
        when(this.info.getHomeRoom()).thenReturn(12);
        this.login("home room / tour done, own home room", "ticket-1", "");

        this.fresh();
        this.flags.put("retro.style.homeroom", true);
        when(this.info.getHomeRoom()).thenReturn(12);
        this.login("home room / retro, own home room", "ticket-1", "");

        this.fresh();
        this.flags.put("retro.style.homeroom", true);
        this.login("home room / retro, hotel home room", "ticket-1", "");

        RoomManager.HOME_ROOM_ID = 0;
        this.fresh();
        this.flags.put("retro.style.homeroom", true);
        this.login("home room / retro, no hotel home room", "ticket-1", "");
    }

    @Test
    void buildersClub() throws Exception {
        this.fresh();
        this.buildersClubActive = true;
        this.buildersClubSecondsLeft = 3600;
        this.login("builders club / expiring soon", "ticket-1", "");

        this.fresh();
        this.buildersClubActive = true;
        this.buildersClubSecondsLeft = 80 * 3600;
        this.login("builders club / plenty left", "ticket-1", "");

        this.fresh();
        Subscription expired = mock(Subscription.class);
        when(expired.getSubscriptionType()).thenReturn(Subscription.BUILDERS_CLUB);
        this.stats.subscriptions.add(expired);
        this.login("builders club / expired", "ticket-1", "");
    }

    // ---------------------------------------------------------------- resuming a parked session

    @Test
    void resume() throws Exception {
        this.fresh();
        this.lookupUserId = 7;
        this.parked = this.habbo;
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(55);
        when(this.info.getCurrentRoom()).thenReturn(room);
        this.login("resume / still in a room", "ticket-1", "");

        this.fresh();
        this.lookupUserId = 7;
        this.parked = this.habbo;
        this.login("resume / in no room", "ticket-1", "");

        this.fresh();
        this.lookupUserId = 7;
        this.parked = this.habbo;
        this.securityChecksPass = false;
        this.login("resume / banned meanwhile", "ticket-1", "");

        this.fresh();
        this.lookupUserId = 7;
        this.parked = this.habbo;
        this.maintenanceAllows = false;
        this.login("resume / maintenance refuses the rank", "ticket-1", "");

        this.fresh();
        this.lookupUserId = 7;
        this.parked = this.habbo;
        this.previousSession = this.namedClient("previous session");
        this.login("resume / duplicate session replaced", "ticket-1", "");

        this.fresh();
        this.lookupUserId = 7;
        this.parked = null;
        this.login("resume / nothing parked", "ticket-1", "");
    }

    // ---------------------------------------------------------------- sanctions replayed on login

    @Test
    void sanctions() throws Exception {
        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.login("sanctions / none", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 2, NOW + 600, false, 0, 0, "old"));
        this.login("sanctions / probation running", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 2, NOW - 600, false, 0, 0, "old"));
        this.login("sanctions / probation over", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 0, 0, false, 0, NOW + 600, "trade"));
        this.login("sanctions / trade lock running", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 0, 0, false, 0, NOW - 600, "trade"));
        this.login("sanctions / trade lock over", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 0, 0, true, NOW - 600, 0, "mute"));
        this.login("sanctions / mute over", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 0, 0, true, NOW + 600, 0, "mute"));
        this.login("sanctions / mute running, nothing stored", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        when(this.stats.getMuteEndTime()).thenReturn(NOW + 600);
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 0, 0, true, NOW + 600, 0, "mute"));
        this.login("sanctions / mute running, already stored", "ticket-1", "");

        this.fresh();
        this.flags.put("hotel.sanctions.enabled", true);
        this.sanctions = this.sanctionList(
                new ModToolSanctionItem(1, 7, 2, NOW + 600, false, 0, 0, "older"),
                new ModToolSanctionItem(2, 7, 0, 0, false, 0, 0, "latest"));
        this.login("sanctions / only the latest counts", "ticket-1", "");

        this.fresh();
        this.sanctions = this.sanctionList(new ModToolSanctionItem(1, 7, 2, NOW + 600, true, NOW + 600, 0, "off"));
        this.login("sanctions / disabled", "ticket-1", "");
    }

    // ---------------------------------------------------------------- the harness

    private void fresh() throws Exception {
        this.trace.clear();
        this.delayed.clear();
        this.flags.clear();
        this.values.clear();
        this.permissions.clear();
        this.recording = false;
        Emulator.isReady = true;
        ClothingValidationManager.VALIDATE_ON_LOGIN = false;
        SubscriptionHabboClub.HC_PAYDAY_ENABLED = false;
        this.channelOpen = true;
        this.handshakeFinished = true;
        this.release = ClientReleaseGuard.DEFAULT_ALLOWED_RELEASES;
        this.admission = RuntimeResilienceController.Action.ALLOW;
        this.lookupUserId = 0;
        this.lookupThrows = false;
        this.parked = null;
        this.byRecovery = null;
        this.recoveredUserId = 0;
        this.earlierConnection = null;
        this.previousSession = null;
        this.securityChecksPass = true;
        this.connectAccepted = true;
        this.maintenanceAllows = true;
        this.validatedLook = "hd-180-1.ch-210-66";
        this.habbiconsThrow = false;
        this.unseenHabbicons = List.of();
        this.buildersClubActive = false;
        this.buildersClubSecondsLeft = 0;
        this.sanctions = null;
        this.pluginCancels = false;

        this.client = this.namedClient("client");
        this.habbo = this.user();
        this.bySso = this.habbo;
    }

    private void login(String name, String ticket, String recoveryToken) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        writeString(buffer, ticket);
        buffer.writeInt(12345);
        if (!recoveryToken.isEmpty()) writeString(buffer, recoveryToken);
        SecureLoginEvent handler = new SecureLoginEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.SecureLoginEvent, buffer);

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        for (Runnable task : new ArrayList<>(this.delayed)) {
            this.record("-- delayed task runs");
            task.run();
        }
        this.recording = false;

        this.check(name, String.join("\n", this.trace));
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    private Connection connection() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(i -> {
            this.record("db.prepare(" + i.getArgument(0) + ")");
            if (this.lookupThrows) throw new SQLException("database down");
            return statement;
        });
        doAnswer(i -> this.record("db.setString(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(statement)
                .setString(anyInt(), anyString());
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenAnswer(i -> this.lookupUserId > 0);
        when(rows.getInt("id")).thenAnswer(i -> this.lookupUserId);
        return connection;
    }

    private GameClient namedClient(String name) {
        GameClient client = mock(GameClient.class, withSettings().name(name));
        this.labels.put(client, name);
        Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenAnswer(i -> this.channelOpen);
        when(channel.close()).thenAnswer(i -> this.record(name + ".channel.close()"));
        when(client.getChannel()).thenReturn(channel);
        when(client.isHandshakeFinished()).thenAnswer(i -> this.handshakeFinished);
        when(client.getReleaseVersion()).thenAnswer(i -> this.release);
        Habbo[] attached = new Habbo[1];
        when(client.getHabbo()).thenAnswer(i -> attached[0]);
        doAnswer(i -> {
                    attached[0] = i.getArgument(0);
                    return this.record(name + ".setHabbo(" + this.label(attached[0]) + ")");
                })
                .when(client)
                .setHabbo(any());
        doAnswer(i -> this.record(name + ".setSsoTicket(" + i.getArgument(0) + ")"))
                .when(client)
                .setSsoTicket(anyString());
        doAnswer(i -> this.record(name + ".setMachineId(" + i.getArgument(0) + ")"))
                .when(client)
                .setMachineId(any());
        doAnswer(i -> this.record(name + " <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(ServerMessage.class));
        doAnswer(i -> this.record(name + " <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(MessageComposer.class));
        doAnswer(i -> {
                    this.record(
                            name + " <- batch of " + i.<List<?>>getArgument(0).size());
                    for (Object message : i.<List<?>>getArgument(0)) this.record("    " + this.label(message));
                    return null;
                })
                .when(client)
                .sendResponses(any());
        return client;
    }

    private Habbo user() throws Exception {
        Habbo habbo = mock(Habbo.class, withSettings().name("ann"));
        this.labels.put(habbo, "ann");
        this.info = mock(HabboInfo.class);
        this.stats = mock(HabboStats.class);
        this.stats.nux = true;
        this.stats.nuxReward = true;
        this.stats.subscriptions = new HashSet<>();
        this.labels.put(this.stats, "ann's stats");
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(3);
        when(this.info.getId()).thenReturn(7);
        when(this.info.getUsername()).thenReturn("ann");
        when(this.info.getRank()).thenReturn(rank);
        when(this.info.getMachineID()).thenReturn("stored-machine");
        when(this.info.getLook()).thenReturn("hd-180-1.ch-210-66");
        when(this.info.getIpLogin()).thenReturn("10.0.0.1");
        when(this.info.getLastOnline()).thenReturn(NOW - 86_400);
        List<NavigatorSavedSearch> searches = new ArrayList<>();
        when(this.info.getSavedSearches()).thenReturn(searches);
        doAnswer(i -> {
                    NavigatorSavedSearch search = i.getArgument(0);
                    searches.add(search);
                    return this.record("info.addSavedSearch(" + search.getSearchCode() + ")");
                })
                .when(this.info)
                .addSavedSearch(any());
        doAnswer(i -> this.record("info.setLook(" + i.getArgument(0) + ")"))
                .when(this.info)
                .setLook(anyString());
        doAnswer(i -> this.record("info.setMachineID(" + i.getArgument(0) + ")"))
                .when(this.info)
                .setMachineID(anyString());
        when(this.stats.hasSubscription(anyString()))
                .thenAnswer(i -> Subscription.BUILDERS_CLUB.equals(i.getArgument(0)) && this.buildersClubActive);
        doAnswer(i -> this.record("stats.setAllowTrade(" + i.getArgument(0) + ")"))
                .when(this.stats)
                .setAllowTrade(anyBoolean());

        EffectsComponent effects = mock(EffectsComponent.class);
        Field effectsField = EffectsComponent.class.getField("effects");
        effectsField.setAccessible(true);
        effectsField.set(effects, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
        effects.activatedEffect = 4;
        HabboInventory inventory = mock(HabboInventory.class);
        when(inventory.getEffectsComponent()).thenReturn(effects);

        HabbiconService habbicons = mock(HabbiconService.class);
        when(habbicons.load(anyInt())).thenAnswer(i -> {
            if (this.habbiconsThrow) throw new SQLException("habbicons down");
            return new HabbiconService.Snapshot(List.of(), Map.of(), List.of(), this.unseenHabbicons);
        });
        this.labels.put(habbicons, "habbicons");

        when(habbo.getHabboInfo()).thenReturn(this.info);
        when(habbo.getHabboStats()).thenReturn(this.stats);
        when(habbo.getInventory()).thenReturn(inventory);
        when(habbo.getHabbiconService()).thenReturn(habbicons);
        when(habbo.hasPermission(anyString())).thenAnswer(i -> this.permissions.contains(i.<String>getArgument(0)));
        when(habbo.connect()).thenAnswer(i -> {
            this.record("ann.connect() -> " + this.connectAccepted);
            return this.connectAccepted;
        });
        when(habbo.passesConnectionSecurityChecks()).thenAnswer(i -> {
            this.record("ann.passesConnectionSecurityChecks() -> " + this.securityChecksPass);
            return this.securityChecksPass;
        });
        doAnswer(i -> this.record("ann.setClient(" + this.label(i.getArgument(0)) + ")"))
                .when(habbo)
                .setClient(any());
        doAnswer(i -> this.record("ann.mute(" + i.getArgument(0) + "s, flood " + i.getArgument(1) + ")"))
                .when(habbo)
                .mute(anyInt(), anyBoolean());
        doAnswer(i -> this.record("ann.unMute()")).when(habbo).unMute();
        return habbo;
    }

    private ArrayList<ModToolSanctionItem> sanctionList(ModToolSanctionItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private Object record(String line) {
        if (this.recording) this.trace.add(line);
        return null;
    }

    private <T> void composer(Class<T> type, String name) {
        this.constructions.add(mockConstruction(type, (composer, context) -> {
            String text =
                    name + "(" + context.arguments().stream().map(this::label).collect(Collectors.joining(", ")) + ")";
            this.labels.put(composer, text);
            if (composer instanceof MessageComposer messageComposer) {
                ServerMessage message = mock(ServerMessage.class);
                this.labels.put(message, text);
                when(messageComposer.compose()).thenReturn(message);
            }
        }));
    }

    private String label(Object value) {
        if (value == null) return "null";
        String known = this.labels.get(value);
        if (known != null) return known;
        if (value instanceof int[] array) return Arrays.toString(array);
        if (value instanceof Object[] array)
            return Arrays.stream(array).map(this::label).collect(Collectors.joining(", ", "[", "]"));
        if (value instanceof NavigatorSavedSearch search) return "search " + search.getSearchCode();
        if (value instanceof Collection<?> collection)
            return collection.stream().map(this::label).collect(Collectors.joining(", ", "[", "]"));
        if (value instanceof Enum<?> constant) return constant.name();
        if (value instanceof Number || value instanceof String || value instanceof Boolean)
            return String.valueOf(value);
        if (value instanceof HabboStats) return "stats";
        if (value instanceof Runnable) return "task";
        return value.getClass().getSimpleName();
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.secureLoginTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/handshake/secure-login-traces.txt");
    private static final Map<String, String> RECORDING = new ConcurrentSkipListMap<>();

    private void check(String name, String trace) {
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            RECORDING.put(name, trace);
            return;
        }

        assertEquals(expectedTraces().get(name), trace, name);
    }

    @AfterAll
    static void writeRecording() throws Exception {
        if (!Boolean.getBoolean(UPDATE_PROPERTY)) return;

        StringBuilder text = new StringBuilder();
        new TreeMap<>(RECORDING)
                .forEach((name, lines) -> text.append("== ")
                        .append(name)
                        .append('\n')
                        .append(lines)
                        .append("\n\n"));
        Files.createDirectories(TRACES.getParent());
        Files.writeString(TRACES, text.toString());
    }

    private static Map<String, String> expectedTraces() {
        try {
            Map<String, String> traces = new TreeMap<>();
            String name = null;
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(TRACES)) {
                if (line.startsWith("== ")) {
                    if (name != null) traces.put(name, String.join("\n", lines).strip());
                    name = line.substring(3);
                    lines.clear();
                } else {
                    lines.add(line);
                }
            }
            if (name != null) traces.put(name, String.join("\n", lines).strip());
            return traces;
        } catch (Exception e) {
            throw new IllegalStateException("Missing " + TRACES + "; record it with -D" + UPDATE_PROPERTY + "=true", e);
        }
    }
}
