package com.eu.habbo.habbohotel;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CreditsScheduler;
import com.eu.habbo.core.GotwPointsScheduler;
import com.eu.habbo.core.PixelScheduler;
import com.eu.habbo.core.PointsScheduler;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarManager;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.crafting.CraftingManager;
import com.eu.habbo.habbohotel.guides.GuideManager;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.hotelview.HotelViewManager;
import com.eu.habbo.habbohotel.items.FurnitureTextProvider;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.mentions.MentionManager;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.navigation.NavigatorManager;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.polls.PollManager;
import com.eu.habbo.habbohotel.rooms.RoomChatBubbleManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.translations.GoogleTranslateManager;
import com.eu.habbo.habbohotel.traxeditor.TraxEditorManager;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.custombadge.CustomBadgeManager;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionScheduler;
import com.eu.habbo.habbohotel.wheel.WheelManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameEnvironment {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameEnvironment.class);
    private final HotelServiceRegistry services = new HotelServiceRegistry();
    private final Executor persistenceExecutor;

    public CreditsScheduler creditsScheduler;
    public PixelScheduler pixelScheduler;
    public PointsScheduler pointsScheduler;
    public GotwPointsScheduler gotwPointsScheduler;
    public SubscriptionScheduler subscriptionScheduler;

    private HabboManager habboManager;
    private NavigatorManager navigatorManager;
    private GuildManager guildManager;
    private ItemManager itemManager;
    private FurnitureTextProvider furnitureTextProvider;
    private CatalogManager catalogManager;
    private HotelViewManager hotelViewManager;
    private RoomManager roomManager;
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
    private SoundboardManager soundboardManager;
    private TraxEditorManager traxEditorManager;
    private MentionManager mentionManager;

    public GameEnvironment() {
        this(Runnable::run);
    }

    public GameEnvironment(Executor persistenceExecutor) {
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor, "persistenceExecutor");
    }

    public void load() throws Exception {
        LOGGER.info("GameEnvironment -> Loading...");

        this.permissionsManager = this.services.create("permissions manager", PermissionsManager::new);
        this.habboManager = this.services.create(
                "habbo manager", () -> new HabboManager(this.persistenceExecutor), HabboManager::dispose);
        this.hotelViewManager =
                this.services.create("hotel view manager", HotelViewManager::new, HotelViewManager::dispose);
        this.itemManager = this.services.create("item manager", ItemManager::new, ItemManager::dispose);
        this.itemManager.load();
        this.furnitureTextProvider = this.services.create("furniture text provider", FurnitureTextProvider::new);
        this.furnitureTextProvider.init();
        this.botManager = this.services.create("bot manager", BotManager::new, BotManager::dispose);
        this.petManager = this.services.create("pet manager", PetManager::new);
        this.guildManager = this.services.create("guild manager", GuildManager::new, GuildManager::dispose);
        this.catalogManager = this.services.create("catalog manager", CatalogManager::new, CatalogManager::dispose);
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
        this.soundboardManager = this.services.create("soundboard manager", SoundboardManager::new);
        this.traxEditorManager = this.services.create("trax editor manager", TraxEditorManager::new);
        this.mentionManager = this.services.create("mention manager", MentionManager::new);

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
        steps.put("habbo manager", this.habboManager == null ? null : () -> this.habboManager.dispose());
        steps.put("command handler", this.commandHandler == null ? null : () -> this.commandHandler.dispose());
        steps.put("guild manager", this.guildManager == null ? null : () -> this.guildManager.dispose());
        steps.put("catalog manager", this.catalogManager == null ? null : () -> this.catalogManager.dispose());
        steps.put("room manager", this.roomManager == null ? null : () -> this.roomManager.dispose());
        steps.put("bot manager", this.botManager == null ? null : () -> this.botManager.dispose());
        steps.put("item manager", this.itemManager == null ? null : () -> this.itemManager.dispose());
        steps.put("hotel view manager", this.hotelViewManager == null ? null : () -> this.hotelViewManager.dispose());
        steps.put(
                "subscription manager",
                this.subscriptionManager == null ? null : () -> this.subscriptionManager.dispose());
        steps.put("calendar manager", this.calendarManager == null ? null : () -> this.calendarManager.dispose());
        steps.put(
                "Google Translate cache",
                this.googleTranslateManager == null ? null : () -> this.googleTranslateManager.clearCache());

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

    public HabboManager getHabboManager() {
        return this.habboManager;
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

    public SoundboardManager getSoundboardManager() {
        return this.soundboardManager;
    }

    public TraxEditorManager getTraxEditorManager() {
        return this.traxEditorManager;
    }

    public HotelViewManager getHotelViewManager() {
        return this.hotelViewManager;
    }

    public RoomManager getRoomManager() {
        return this.roomManager;
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
