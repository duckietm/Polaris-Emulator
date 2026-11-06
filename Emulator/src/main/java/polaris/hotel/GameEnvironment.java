package polaris.hotel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.core.CreditsScheduler;
import polaris.core.GotwPointsScheduler;
import polaris.core.PixelScheduler;
import polaris.core.PointsScheduler;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.bots.BotManager;
import polaris.hotel.campaign.calendar.CalendarManager;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.commands.CommandHandler;
import polaris.hotel.communitygoals.CommunityGoalManager;
import polaris.hotel.crafting.CraftingManager;
import polaris.hotel.guides.GuideManager;
import polaris.hotel.guilds.GuildManager;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.hotelview.HotelViewManager;
import polaris.hotel.hotlooks.HotLooksManager;
import polaris.hotel.items.FurnitureTextProvider;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.interactions.wired.chest.ChestRelease;
import polaris.hotel.items.rentable.RentableFurnitureManager;
import polaris.hotel.mentions.MentionManager;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolSanctions;
import polaris.hotel.modtool.WordFilter;
import polaris.hotel.navigation.NavigatorManager;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.pets.PetManager;
import polaris.hotel.polls.PollManager;
import polaris.hotel.rooms.RoomChatBubbleManager;
import polaris.hotel.rooms.RoomCycleService;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.soundboard.SoundboardManager;
import polaris.hotel.translations.GoogleTranslateManager;
import polaris.hotel.traxeditor.TraxEditorManager;
import polaris.hotel.treasurehunt.TreasureHuntManager;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.custombadge.CustomBadgeManager;
import polaris.hotel.users.infostand.InfostandBackgroundManager;
import polaris.hotel.users.subscriptions.SubscriptionManager;
import polaris.hotel.users.subscriptions.SubscriptionScheduler;
import polaris.hotel.wheel.WheelManager;

public class GameEnvironment {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameEnvironment.class);
    private final HotelServiceRegistry services = new HotelServiceRegistry();
    private final Executor persistenceExecutor;

    public CreditsScheduler creditsScheduler;
    public PixelScheduler pixelScheduler;
    public PointsScheduler pointsScheduler;
    public GotwPointsScheduler gotwPointsScheduler;
    public SubscriptionScheduler subscriptionScheduler;

    private UserManager userManager;
    private NavigatorManager navigatorManager;
    private GuildManager guildManager;
    private ItemManager itemManager;
    private FurnitureTextProvider furnitureTextProvider;
    private CatalogManager catalogManager;
    private HotelViewManager hotelViewManager;
    private CommunityGoalManager communityGoalManager;
    private TreasureHuntManager treasureHuntManager;
    private RoomManager roomManager;
    private RoomCycleService roomCycleService;
    private CommandHandler commandHandler;
    private PermissionsManager permissionsManager;
    private BotManager botManager;
    private ModToolManager modToolManager;
    private ModToolSanctions modToolSanctions;
    private PetManager petManager;
    private AchievementManager achievementManager;
    private GuideManager guideManager;
    private WordFilter wordFilter;
    private CraftingManager craftingManager;
    private PollManager pollManager;
    private SubscriptionManager subscriptionManager;
    private CalendarManager calendarManager;
    private RoomChatBubbleManager roomChatBubbleManager;
    private GoogleTranslateManager googleTranslateManager;
    private CustomBadgeManager customBadgeManager;
    private InfostandBackgroundManager infostandBackgroundManager;
    private WheelManager wheelManager;
    private HotLooksManager hotLooksManager;
    private final HabbiconService habbiconService;
    private SoundboardManager soundboardManager;
    private TraxEditorManager traxEditorManager;
    private MentionManager mentionManager;
    private polaris.hotel.quests.QuestManager questManager;
    private polaris.hotel.quests.DailyTaskManager dailyTaskManager;
    private polaris.hotel.quests.RewardTrackManager rewardTrackManager;
    private RentableFurnitureManager rentableFurnitureManager;

    public GameEnvironment() {
        this(Runnable::run);
    }

    public GameEnvironment(Executor persistenceExecutor) {
        this(persistenceExecutor, null);
    }

    public GameEnvironment(Executor persistenceExecutor, HabbiconService habbiconService) {
        this.habbiconService = habbiconService;
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor, "persistenceExecutor");
    }

    public void load() throws Exception {
        LOGGER.info("GameEnvironment -> Loading...");

        this.permissionsManager = this.services.create("permissions manager", PermissionsManager::new);
        this.userManager = this.services.create(
                "user manager",
                () -> new UserManager(this.persistenceExecutor, this.habbiconService),
                UserManager::dispose);
        this.hotelViewManager =
                this.services.create("hotel view manager", HotelViewManager::new, HotelViewManager::dispose);
        this.communityGoalManager = this.services.create(
                "community goal manager", CommunityGoalManager::new, CommunityGoalManager::dispose);
        this.treasureHuntManager =
                this.services.create("treasure hunt manager", TreasureHuntManager::new, TreasureHuntManager::dispose);
        this.itemManager = this.services.create("item manager", ItemManager::new, ItemManager::dispose);
        this.itemManager.load();
        sweepOrphanedChests();
        this.furnitureTextProvider = this.services.create("furniture text provider", FurnitureTextProvider::new);
        this.furnitureTextProvider.init();
        this.botManager = this.services.create("bot manager", BotManager::new, BotManager::dispose);
        this.petManager = this.services.create("pet manager", PetManager::new);
        this.guildManager = this.services.create("guild manager", GuildManager::new, GuildManager::dispose);
        this.catalogManager = this.services.create("catalog manager", CatalogManager::new, CatalogManager::dispose);
        this.roomCycleService = this.services.create(
                "room cycle workers",
                () -> RoomCycleService.fromConfig(WiredPlatform.configuration()),
                RoomCycleService::dispose);
        this.roomManager = this.services.create(
                "room manager", () -> new RoomManager(this.persistenceExecutor), RoomManager::dispose);
        this.services.beforeDispose("room cycles", () -> this.roomManager.quiesceRoomCycles());
        this.navigatorManager = this.services.create("navigator manager", NavigatorManager::new);
        this.commandHandler = this.services.create("command handler", CommandHandler::new, CommandHandler::dispose);
        this.modToolManager = this.services.create("moderation tools", ModToolManager::new);
        this.modToolSanctions = this.services.create("moderation sanctions", ModToolSanctions::new);
        this.achievementManager = this.services.create("achievement manager", AchievementManager::new);
        this.achievementManager.reload();
        this.guideManager = this.services.create("guide manager", GuideManager::new);
        this.wordFilter = this.services.create("word filter", WordFilter::new);
        this.craftingManager = this.services.create("crafting manager", CraftingManager::new, CraftingManager::dispose);
        this.pollManager = this.services.create("poll manager", PollManager::new);
        this.calendarManager = this.services.create("calendar manager", CalendarManager::new, CalendarManager::dispose);
        this.roomChatBubbleManager = this.services.create("room chat bubbles", RoomChatBubbleManager::new);
        this.googleTranslateManager = this.services.create(
                "Google Translate cache", GoogleTranslateManager::new, GoogleTranslateManager::clearCache);
        this.customBadgeManager = this.services.create("custom badge manager", CustomBadgeManager::new);
        this.infostandBackgroundManager =
                this.services.create("infostand backgrounds", InfostandBackgroundManager::new);
        this.wheelManager = this.services.create("wheel manager", WheelManager::new);
        this.hotLooksManager = this.services.create("hot looks manager", HotLooksManager::new);
        this.soundboardManager =
                this.services.create("soundboard manager", () -> new SoundboardManager(this.permissionsManager));
        this.traxEditorManager = this.services.create("trax editor manager", TraxEditorManager::new);
        this.mentionManager = this.services.create("mention manager", MentionManager::new);
        this.questManager = this.services.create("quest manager", polaris.hotel.quests.QuestManager::new);
        this.dailyTaskManager = this.services.create("daily task manager", polaris.hotel.quests.DailyTaskManager::new);
        this.rewardTrackManager =
                this.services.create("reward track manager", polaris.hotel.quests.RewardTrackManager::new);
        this.rentableFurnitureManager = this.services.create(
                "rentable furniture", RentableFurnitureManager::new, RentableFurnitureManager::dispose);

        this.roomManager.loadPublicRooms();
        this.navigatorManager.loadNavigator();

        this.creditsScheduler = this.services.create("credits scheduler", CreditsScheduler::new);
        this.services.onDispose("credits scheduler", () -> {
            if (this.creditsScheduler != null) {
                this.creditsScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.creditsScheduler);
        this.pixelScheduler = this.services.create("pixel scheduler", PixelScheduler::new);
        this.services.onDispose("pixel scheduler", () -> {
            if (this.pixelScheduler != null) {
                this.pixelScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.pixelScheduler);
        this.pointsScheduler = this.services.create("points scheduler", PointsScheduler::new);
        this.services.onDispose("points scheduler", () -> {
            if (this.pointsScheduler != null) {
                this.pointsScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.pointsScheduler);
        this.gotwPointsScheduler = this.services.create("gotw points scheduler", GotwPointsScheduler::new);
        this.services.onDispose("gotw points scheduler", () -> {
            if (this.gotwPointsScheduler != null) {
                this.gotwPointsScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.gotwPointsScheduler);

        this.subscriptionManager =
                this.services.create("subscription manager", SubscriptionManager::new, SubscriptionManager::dispose);
        this.subscriptionManager.init();

        this.subscriptionScheduler = this.services.create("subscription scheduler", SubscriptionScheduler::new);
        this.services.onDispose("subscription scheduler", () -> {
            if (this.subscriptionScheduler != null) {
                this.subscriptionScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.subscriptionScheduler);

        LOGGER.info("GameEnvironment -> Loaded!");
    }

    /**
     * Give back what wired chests hold whose item was deleted without releasing them. Runs before any
     * player is online; a failure here never stops the hotel from starting.
     */
    private static void sweepOrphanedChests() {
        try {
            ChestRelease.sweepOrphans();
        } catch (RuntimeException e) {
            LOGGER.error("Could not sweep wired chests whose furni no longer exists", e);
        }
    }

    public void dispose() {
        if (this.services.hasServices()) {
            this.services.dispose();
            LOGGER.info("GameEnvironment -> Disposed!");
            return;
        }

        Map<String, Runnable> steps = new LinkedHashMap<>();
        steps.put(
                "points scheduler",
                this.pointsScheduler == null
                        ? null
                        : () -> {
                            this.pointsScheduler.setDisposed(true);
                        });
        steps.put(
                "pixel scheduler",
                this.pixelScheduler == null
                        ? null
                        : () -> {
                            this.pixelScheduler.setDisposed(true);
                        });
        steps.put(
                "credits scheduler",
                this.creditsScheduler == null
                        ? null
                        : () -> {
                            this.creditsScheduler.setDisposed(true);
                        });
        steps.put(
                "gotw points scheduler",
                this.gotwPointsScheduler == null
                        ? null
                        : () -> {
                            this.gotwPointsScheduler.setDisposed(true);
                        });
        steps.put(
                "subscription scheduler",
                this.subscriptionScheduler == null
                        ? null
                        : () -> {
                            this.subscriptionScheduler.setDisposed(true);
                        });
        steps.put("room cycles", this.roomManager == null ? null : () -> this.roomManager.quiesceRoomCycles());
        steps.put("crafting manager", this.craftingManager == null ? null : () -> this.craftingManager.dispose());
        steps.put("user manager", this.userManager == null ? null : () -> this.userManager.dispose());
        steps.put("command handler", this.commandHandler == null ? null : () -> this.commandHandler.dispose());
        steps.put("guild manager", this.guildManager == null ? null : () -> this.guildManager.dispose());
        steps.put("catalog manager", this.catalogManager == null ? null : () -> this.catalogManager.dispose());
        steps.put("room manager", this.roomManager == null ? null : () -> this.roomManager.dispose());
        steps.put("bot manager", this.botManager == null ? null : () -> this.botManager.dispose());
        steps.put("item manager", this.itemManager == null ? null : () -> this.itemManager.dispose());
        steps.put("hotel view manager", this.hotelViewManager == null ? null : () -> this.hotelViewManager.dispose());
        steps.put(
                "community goal manager",
                this.communityGoalManager == null ? null : () -> this.communityGoalManager.dispose());
        steps.put(
                "treasure hunt manager",
                this.treasureHuntManager == null ? null : () -> this.treasureHuntManager.dispose());
        steps.put(
                "subscription manager",
                this.subscriptionManager == null ? null : () -> this.subscriptionManager.dispose());
        steps.put("calendar manager", this.calendarManager == null ? null : () -> this.calendarManager.dispose());
        steps.put(
                "Google Translate cache",
                this.googleTranslateManager == null ? null : () -> this.googleTranslateManager.clearCache());
        steps.put("room cycle workers", this.roomCycleService == null ? null : () -> this.roomCycleService.dispose());

        disposeAll(steps);
        LOGGER.info("GameEnvironment -> Disposed!");
    }

    static void disposeAll(Map<String, Runnable> steps) {
        for (Map.Entry<String, Runnable> step : steps.entrySet()) {
            if (step.getValue() == null) {
                continue;
            }

            try {
                step.getValue().run();
            } catch (Exception exception) {
                LOGGER.error("GameEnvironment -> Failed to dispose {}", step.getKey(), exception);
            }
        }
    }

    public UserManager getUserManager() {
        return this.userManager;
    }

    public NavigatorManager getNavigatorManager() {
        return this.navigatorManager;
    }

    public GuildManager getGuildManager() {
        return this.guildManager;
    }

    public ItemManager getItemManager() {
        return this.itemManager;
    }

    public FurnitureTextProvider getFurnitureTextProvider() {
        return this.furnitureTextProvider;
    }

    public CatalogManager getCatalogManager() {
        return this.catalogManager;
    }

    public WheelManager getWheelManager() {
        return this.wheelManager;
    }

    public HotLooksManager getHotLooksManager() {
        return this.hotLooksManager;
    }

    public SoundboardManager getSoundboardManager() {
        return this.soundboardManager;
    }

    public TraxEditorManager getTraxEditorManager() {
        return this.traxEditorManager;
    }

    public HotelViewManager getHotelViewManager() {
        return this.hotelViewManager;
    }

    public CommunityGoalManager getCommunityGoalManager() {
        return this.communityGoalManager;
    }

    public TreasureHuntManager getTreasureHuntManager() {
        return this.treasureHuntManager;
    }

    public RoomManager getRoomManager() {
        return this.roomManager;
    }

    /** The room cycle workers, or null before load and in tests. */
    public RoomCycleService getRoomCycleService() {
        return this.roomCycleService;
    }

    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    public PermissionsManager getPermissionsManager() {
        return this.permissionsManager;
    }

    public BotManager getBotManager() {
        return this.botManager;
    }

    public ModToolManager getModToolManager() {
        return this.modToolManager;
    }

    public ModToolSanctions getModToolSanctions() {
        return this.modToolSanctions;
    }

    public PetManager getPetManager() {
        return this.petManager;
    }

    public MentionManager getMentionManager() {
        return this.mentionManager;
    }

    public polaris.hotel.quests.QuestManager getQuestManager() {
        return this.questManager;
    }

    public polaris.hotel.quests.DailyTaskManager getDailyTaskManager() {
        return this.dailyTaskManager;
    }

    public polaris.hotel.quests.RewardTrackManager getRewardTrackManager() {
        return this.rewardTrackManager;
    }

    public RentableFurnitureManager getRentableFurnitureManager() {
        return this.rentableFurnitureManager;
    }

    public AchievementManager getAchievementManager() {
        return this.achievementManager;
    }

    public GuideManager getGuideManager() {
        return this.guideManager;
    }

    public WordFilter getWordFilter() {
        return this.wordFilter;
    }

    public CraftingManager getCraftingManager() {
        return this.craftingManager;
    }

    public PollManager getPollManager() {
        return this.pollManager;
    }

    public CreditsScheduler getCreditsScheduler() {
        return this.creditsScheduler;
    }

    public PixelScheduler getPixelScheduler() {
        return this.pixelScheduler;
    }

    public PointsScheduler getPointsScheduler() {
        return this.pointsScheduler;
    }

    public GotwPointsScheduler getGotwPointsScheduler() {
        return this.gotwPointsScheduler;
    }

    public SubscriptionManager getSubscriptionManager() {
        return this.subscriptionManager;
    }

    public CalendarManager getCalendarManager() {
        return this.calendarManager;
    }

    public RoomChatBubbleManager getRoomChatBubbleManager() {
        return roomChatBubbleManager;
    }

    public GoogleTranslateManager getGoogleTranslateManager() {
        return this.googleTranslateManager;
    }

    public CustomBadgeManager getCustomBadgeManager() {
        return this.customBadgeManager;
    }

    public InfostandBackgroundManager getInfostandBackgroundManager() {
        return this.infostandBackgroundManager;
    }
}
