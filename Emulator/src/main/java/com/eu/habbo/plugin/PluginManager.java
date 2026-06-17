package com.eu.habbo.plugin;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Easter;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.tag.TagGame;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootballGate;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.navigation.EventCategory;
import com.eu.habbo.habbohotel.navigation.NavigatorManager;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreManager;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.FloorPlanEditorSaveEvent;
import com.eu.habbo.messages.incoming.hotelview.HotelViewRequestLTDAvailabilityEvent;
import com.eu.habbo.messages.incoming.rooms.promotions.BuyRoomPromotionEvent;
import com.eu.habbo.messages.incoming.users.ChangeNameCheckUsernameEvent;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.roomunit.RoomUnitLookAtPointEvent;
import com.eu.habbo.plugin.events.users.*;
import com.eu.habbo.threading.runnables.RoomTrashing;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gnu.trove.iterator.hash.TObjectHashIterator;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    // Gson is thread-safe and immutable once built — reuse one instance instead
    // of building a parser per plugin-config load.
    private static final Gson PLUGIN_GSON = new GsonBuilder().create();

    private final THashSet<HabboPlugin> plugins = new THashSet<>();
    private final THashSet<Method> methods = new THashSet<>();

    @EventHandler
    public static void globalOnConfigurationUpdated(EmulatorConfigUpdatedEvent event) {

        ItemManager.RECYCLER_ENABLED = Emulator.getConfig().getBoolean("hotel.catalog.recycler.enabled");
        MarketPlace.MARKETPLACE_ENABLED = Emulator.getConfig().getBoolean("hotel.marketplace.enabled");
        MarketPlace.MARKETPLACE_CURRENCY = Emulator.getConfig().getInt("hotel.marketplace.currency");
        Messenger.SAVE_PRIVATE_CHATS = Emulator.getConfig().getBoolean("save.private.chats", false);
        PacketManager.DEBUG_SHOW_PACKETS = Emulator.getConfig().getBoolean("debug.show.packets");
        PacketManager.MULTI_THREADED_PACKET_HANDLING = Emulator.getConfig().getBoolean("io.client.multithreaded.handler");
        Room.HABBO_CHAT_DELAY = Emulator.getConfig().getBoolean("room.chat.delay", false);
        Room.MUTEAREA_CAN_WHISPER = Emulator.getConfig().getBoolean("room.chat.mutearea.allow_whisper", false);
        RoomChatMessage.SAVE_ROOM_CHATS = Emulator.getConfig().getBoolean("save.room.chats", false);
        RoomLayout.MAXIMUM_STEP_HEIGHT = Emulator.getConfig().getDouble("pathfinder.step.maximum.height", 1.1);
        RoomLayout.ALLOW_FALLING = Emulator.getConfig().getBoolean("pathfinder.step.allow.falling", true);
        RoomTrade.TRADING_ENABLED = Emulator.getConfig().getBoolean("hotel.trading.enabled") && !ShutdownEmulator.instantiated;
        RoomTrade.TRADING_REQUIRES_PERK = Emulator.getConfig().getBoolean("hotel.trading.requires.perk");
        WordFilter.ENABLED_FRIENDCHAT = Emulator.getConfig().getBoolean("hotel.wordfilter.messenger");
        DiscountComposer.MAXIMUM_ALLOWED_ITEMS = Emulator.getConfig().getInt("discount.max.allowed.items", 100);
        DiscountComposer.DISCOUNT_BATCH_SIZE = Emulator.getConfig().getInt("discount.batch.size", 6);
        DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH = Emulator.getConfig().getInt("discount.batch.free.items", 1);
        DiscountComposer.MINIMUM_DISCOUNTS_FOR_BONUS = Emulator.getConfig().getInt("discount.bonus.min.discounts", 1);
        DiscountComposer.ADDITIONAL_DISCOUNT_THRESHOLDS = Arrays.stream(Emulator.getConfig().getValue("discount.additional.thresholds", "40;99").split(";")).mapToInt(Integer::parseInt).toArray();

        BotManager.MINIMUM_CHAT_SPEED = Emulator.getConfig().getInt("hotel.bot.chat.minimum.interval");
        BotManager.MAXIMUM_CHAT_LENGTH = Emulator.getConfig().getInt("hotel.bot.max.chatlength");
        BotManager.MAXIMUM_NAME_LENGTH = Emulator.getConfig().getInt("hotel.bot.max.namelength");
        BotManager.MAXIMUM_CHAT_SPEED = Emulator.getConfig().getInt("hotel.bot.max.chatdelay");
        Bot.PLACEMENT_MESSAGES = Emulator.getConfig().getValue("hotel.bot.placement.messages", "Yo!;Hello I'm a real party animal!;Hello!").split(";");
        Bot.BOT_LIMIT_WALKING_DISTANCE = Emulator.getConfig().getBoolean("hotel.bot.limit.walking.distance", true);
        Bot.BOT_WALKING_DISTANCE_RADIUS = Emulator.getConfig().getInt("hotel.bot.limit.walking.distance.radius", 5);

        HabboInventory.MAXIMUM_ITEMS = Emulator.getConfig().getInt("hotel.inventory.max.items");
        Messenger.MAXIMUM_FRIENDS = Emulator.getConfig().getInt("hotel.users.max.friends", 300);
        Messenger.MAXIMUM_FRIENDS_HC = Emulator.getConfig().getInt("hotel.users.max.friends.hc", 1100);
        Room.MAXIMUM_BOTS = Emulator.getConfig().getInt("hotel.max.bots.room");
        Room.MAXIMUM_PETS = Emulator.getConfig().getInt("hotel.pets.max.room");
        Room.MAXIMUM_FURNI = Emulator.getConfig().getInt("hotel.room.furni.max", 2500);
        Room.MAXIMUM_POSTITNOTES = Emulator.getConfig().getInt("hotel.room.stickies.max", 200);
        Room.HAND_ITEM_TIME = Emulator.getConfig().getInt("hotel.rooms.handitem.time");
        Room.IDLE_CYCLES = Emulator.getConfig().getInt("hotel.roomuser.idle.cycles", 240);
        Room.IDLE_CYCLES_KICK = Emulator.getConfig().getInt("hotel.roomuser.idle.cycles.kick", 480);
        Room.ROLLERS_MAXIMUM_ROLL_AVATARS = Emulator.getConfig().getInt("hotel.room.rollers.roll_avatars.max", 1);
        RoomManager.MAXIMUM_ROOMS_USER = Emulator.getConfig().getInt("hotel.users.max.rooms", 50);
        RoomManager.MAXIMUM_ROOMS_HC = Emulator.getConfig().getInt("hotel.users.max.rooms.hc", 75);
        RoomManager.HOME_ROOM_ID = Emulator.getConfig().getInt("hotel.home.room");
        WiredManager.MAXIMUM_FURNI_SELECTION = Emulator.getConfig().getInt("hotel.wired.furni.selection.count");
        WiredManager.TELEPORT_DELAY = Emulator.getConfig().getInt("wired.effect.teleport.delay", 500);
        WiredEffectSendSignal.MAX_SIGNAL_DEPTH = Emulator.getConfig().getInt("wired.signal.max.depth", 100);
        WiredEngine.MAX_RECURSION_DEPTH = Emulator.getConfig().getInt("wired.abuse.max.recursion.depth", 10);
        WiredEngine.MAX_EVENTS_PER_WINDOW = Emulator.getConfig().getInt("wired.abuse.max.events.per.window", 100);
        WiredEngine.RATE_LIMIT_WINDOW_MS = Emulator.getConfig().getInt("wired.abuse.rate.limit.window.ms", 10000);
        WiredEngine.WIRED_BAN_DURATION_MS = Emulator.getConfig().getInt("wired.abuse.ban.duration.ms", 600000);
        WiredEngine.MONITOR_USAGE_WINDOW_MS = Emulator.getConfig().getInt("wired.monitor.usage.window.ms", 1000);
        WiredEngine.MONITOR_USAGE_LIMIT = Emulator.getConfig().getInt("wired.monitor.usage.limit", 50000);
        WiredEngine.MONITOR_DELAYED_EVENTS_LIMIT = Emulator.getConfig().getInt("wired.monitor.delayed.events.limit", 50000);
        WiredEngine.MONITOR_OVERLOAD_AVERAGE_MS = Emulator.getConfig().getInt("wired.monitor.overload.average.ms", 50);
        WiredEngine.MONITOR_OVERLOAD_PEAK_MS = Emulator.getConfig().getInt("wired.monitor.overload.peak.ms", 150);
        WiredEngine.MONITOR_OVERLOAD_CONSECUTIVE_WINDOWS = Emulator.getConfig().getInt("wired.monitor.overload.consecutive.windows", 2);
        WiredEngine.MONITOR_HEAVY_USAGE_PERCENT = Emulator.getConfig().getInt("wired.monitor.heavy.usage.percent", 70);
        WiredEngine.MONITOR_HEAVY_CONSECUTIVE_WINDOWS = Emulator.getConfig().getInt("wired.monitor.heavy.consecutive.windows", 5);
        WiredEngine.MONITOR_HEAVY_DELAYED_PERCENT = Emulator.getConfig().getInt("wired.monitor.heavy.delayed.percent", 60);

        if (WiredManager.getEngine() != null) {
            WiredManager.getEngine().clearAllDiagnostics();
        }

        NavigatorManager.MAXIMUM_RESULTS_PER_PAGE = Emulator.getConfig().getInt("hotel.navigator.search.maxresults");
        NavigatorManager.CATEGORY_SORT_USING_ORDER_NUM = Emulator.getConfig().getBoolean("hotel.navigator.sort.ordernum");
        RoomChatMessage.MAXIMUM_LENGTH = Emulator.getConfig().getInt("hotel.chat.max.length");
        TraxManager.LARGE_JUKEBOX_LIMIT = Emulator.getConfig().getInt("hotel.jukebox.limit.large");
        TraxManager.NORMAL_JUKEBOX_LIMIT = Emulator.getConfig().getInt("hotel.jukebox.limit.normal");

        String[] bannedBubbles = Emulator.getConfig().getValue("commands.cmd_chatcolor.banned_numbers").split(";");
        RoomChatMessage.BANNED_BUBBLES = new int[bannedBubbles.length];
        for (int i = 0; i < RoomChatMessage.BANNED_BUBBLES.length; i++) {
            try {
                RoomChatMessage.BANNED_BUBBLES[i] = Integer.parseInt(bannedBubbles[i]);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        HabboManager.WELCOME_MESSAGE = Emulator.getConfig().getValue("hotel.welcome.alert.message").replace("<br>", "<br/>").replace("<br />", "<br/>").replace("\\r", "\r").replace("\\n", "\n").replace("\\t", "\t");
        Room.PREFIX_FORMAT = Emulator.getConfig().getValue("room.chat.prefix.format");
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH = Emulator.getConfig().getInt("hotel.floorplan.max.widthlength");
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_SIZE = Emulator.getConfig().getInt("hotel.floorplan.max.totalarea");

        HotelViewRequestLTDAvailabilityEvent.ENABLED = Emulator.getConfig().getBoolean("hotel.view.ltdcountdown.enabled");
        HotelViewRequestLTDAvailabilityEvent.TIMESTAMP = Emulator.getConfig().getInt("hotel.view.ltdcountdown.timestamp");
        HotelViewRequestLTDAvailabilityEvent.ITEM_ID = Emulator.getConfig().getInt("hotel.view.ltdcountdown.itemid");
        HotelViewRequestLTDAvailabilityEvent.PAGE_ID = Emulator.getConfig().getInt("hotel.view.ltdcountdown.pageid");
        HotelViewRequestLTDAvailabilityEvent.ITEM_NAME = Emulator.getConfig().getValue("hotel.view.ltdcountdown.itemname");
        InteractionPostIt.STICKYPOLE_PREFIX_TEXT = Emulator.getConfig().getValue("hotel.room.stickypole.prefix");
        TargetOffer.ACTIVE_TARGET_OFFER_ID = Emulator.getConfig().getInt("hotel.targetoffer.id");
        WordFilter.DEFAULT_REPLACEMENT = Emulator.getConfig().getValue("hotel.wordfilter.replacement");
        CatalogManager.PURCHASE_COOLDOWN = Emulator.getConfig().getInt("hotel.catalog.purchase.cooldown");
        CatalogManager.SORT_USING_ORDERNUM = Emulator.getConfig().getBoolean("hotel.catalog.items.display.ordernum");
        AchievementManager.TALENTTRACK_ENABLED = Emulator.getConfig().getBoolean("hotel.talenttrack.enabled");
        InteractionRoller.NO_RULES = Emulator.getConfig().getBoolean("hotel.room.rollers.norules");
        RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB = Emulator.getConfig().getBoolean("hotel.navigator.populartab.publics");
        CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM = Emulator.getConfig().getInt("hotel.pets.name.length.min");
        CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM = Emulator.getConfig().getInt("hotel.pets.name.length.max");


        ChangeNameCheckUsernameEvent.VALID_CHARACTERS = Emulator.getConfig().getValue("allowed.username.characters", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-=!?@:,.");

        BuyRoomPromotionEvent.ROOM_PROMOTION_BADGE = Emulator.getConfig().getValue("room.promotion.badge", "RADZZ");
        BotManager.MAXIMUM_BOT_INVENTORY_SIZE = Emulator.getConfig().getInt("hotel.bots.max.inventory");
        PetManager.MAXIMUM_PET_INVENTORY_SIZE = Emulator.getConfig().getInt("hotel.pets.max.inventory");


        SubscriptionHabboClub.HC_PAYDAY_ENABLED = Emulator.getConfig().getBoolean("subscriptions.hc.payday.enabled", false);

        try {
            SubscriptionHabboClub.HC_PAYDAY_NEXT_DATE = (int) (Emulator.stringToDate(Emulator.getConfig().getValue("subscriptions.hc.payday.next_date")).getTime() / 1000);
        }
        catch(Exception e) { SubscriptionHabboClub.HC_PAYDAY_NEXT_DATE = Integer.MAX_VALUE; }

        SubscriptionHabboClub.HC_PAYDAY_INTERVAL = Emulator.getConfig().getValue("subscriptions.hc.payday.interval");
        SubscriptionHabboClub.HC_PAYDAY_QUERY = Emulator.getConfig().getValue("subscriptions.hc.payday.query");
        SubscriptionHabboClub.HC_PAYDAY_CURRENCY = Emulator.getConfig().getValue("subscriptions.hc.payday.currency");
        SubscriptionHabboClub.HC_PAYDAY_KICKBACK_PERCENTAGE = Emulator.getConfig().getInt("subscriptions.hc.payday.percentage", 10) / 100.0;
        SubscriptionHabboClub.HC_PAYDAY_COINSSPENT_RESET_ON_EXPIRE = Emulator.getConfig().getBoolean("subscriptions.hc.payday.creditsspent_reset_on_expire", false);
        SubscriptionHabboClub.ACHIEVEMENT_NAME = Emulator.getConfig().getValue("subscriptions.hc.achievement", "VipHC");
        SubscriptionHabboClub.DISCOUNT_ENABLED = Emulator.getConfig().getBoolean("subscriptions.hc.discount.enabled", false);
        SubscriptionHabboClub.DISCOUNT_DAYS_BEFORE_END = Emulator.getConfig().getInt("subscriptions.hc.discount.days_before_end", 7);

        SubscriptionHabboClub.HC_PAYDAY_STREAK.clear();
        for (String streak : Emulator.getConfig().getValue("subscriptions.hc.payday.streak", "7=5;30=10;60=15;90=20;180=25;365=30").split(Pattern.quote(";"))) {
            if(streak.contains("=")) {
                SubscriptionHabboClub.HC_PAYDAY_STREAK.put(Integer.parseInt(streak.split(Pattern.quote("="))[0]), Integer.parseInt(streak.split(Pattern.quote("="))[1]));
            }
        }

        ClothingValidationManager.VALIDATE_ON_HC_EXPIRE = Emulator.getConfig().getBoolean("hotel.users.clothingvalidation.onhcexpired", false);
        ClothingValidationManager.VALIDATE_ON_LOGIN = Emulator.getConfig().getBoolean("hotel.users.clothingvalidation.onlogin", false);
        ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS = Emulator.getConfig().getBoolean("hotel.users.clothingvalidation.onchangelooks", false);
        ClothingValidationManager.VALIDATE_ON_MIMIC = Emulator.getConfig().getBoolean("hotel.users.clothingvalidation.onmimic", false);
        ClothingValidationManager.VALIDATE_ON_MANNEQUIN = Emulator.getConfig().getBoolean("hotel.users.clothingvalidation.onmannequin", false);
        ClothingValidationManager.VALIDATE_ON_FBALLGATE = Emulator.getConfig().getBoolean("hotel.users.clothingvalidation.onfballgate", false);

        String newUrl = Emulator.getConfig().getValue("gamedata.figuredata.url");
        if(!ClothingValidationManager.FIGUREDATA_URL.equals(newUrl)) {
            ClothingValidationManager.FIGUREDATA_URL = newUrl;
            ClothingValidationManager.reloadFiguredata(newUrl);
        }

        if(newUrl.isEmpty()) {
            ClothingValidationManager.VALIDATE_ON_HC_EXPIRE = false;
            ClothingValidationManager.VALIDATE_ON_LOGIN = false;
            ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS = false;
            ClothingValidationManager.VALIDATE_ON_MIMIC = false;
            ClothingValidationManager.VALIDATE_ON_MANNEQUIN = false;
            ClothingValidationManager.VALIDATE_ON_FBALLGATE = false;
        }


        NewNavigatorEventCategoriesComposer.CATEGORIES.clear();
        for (String category : Emulator.getConfig().getValue("navigator.eventcategories", "").split(";")) {
            try {
                NewNavigatorEventCategoriesComposer.CATEGORIES.add(new EventCategory(category));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (Emulator.isReady) {
            GiftConfigurationComposer.BOX_TYPES = Arrays.stream(Emulator.getConfig().getValue("hotel.gifts.box_types").split(",")).mapToInt(Integer::parseInt).boxed().collect(Collectors.toList());
            GiftConfigurationComposer.RIBBON_TYPES = Arrays.stream(Emulator.getConfig().getValue("hotel.gifts.ribbon_types").split(",")).mapToInt(Integer::parseInt).boxed().collect(Collectors.toList());

            Emulator.getGameEnvironment().getCreditsScheduler().reloadConfig();
            Emulator.getGameEnvironment().getPointsScheduler().reloadConfig();
            Emulator.getGameEnvironment().getPixelScheduler().reloadConfig();
            Emulator.getGameEnvironment().getGotwPointsScheduler().reloadConfig();
            Emulator.getGameEnvironment().subscriptionScheduler.reloadConfig();
        }
    }

    public void loadPlugins() {
        this.disposePlugins();

        File loc = new File("plugins");

        if (!loc.exists()) {
            if (loc.mkdirs()) {
                LOGGER.info("Created plugins directory!");
            }
        }

        for (File file : Objects.requireNonNull(loc.listFiles(file -> file.getPath().toLowerCase().endsWith(".jar")))) {
            URLClassLoader urlClassLoader;
            InputStream stream;
            try {
                urlClassLoader = URLClassLoader.newInstance(new URL[]{file.toURI().toURL()});
                stream = urlClassLoader.getResourceAsStream("plugin.json");

                if (stream == null) {
                    throw new RuntimeException("Invalid Jar! Missing plugin.json in: " + file.getName());
                }

                byte[] content = new byte[stream.available()];

                if (stream.read(content) > 0) {
                    String body = new String(content, java.nio.charset.StandardCharsets.UTF_8);

                    HabboPluginConfiguration pluginConfigurtion = PLUGIN_GSON.fromJson(body, HabboPluginConfiguration.class);

                    try {
                        Class<?> clazz = urlClassLoader.loadClass(pluginConfigurtion.main);
                        Class<? extends HabboPlugin> stackClazz = clazz.asSubclass(HabboPlugin.class);
                        Constructor<? extends HabboPlugin> constructor = stackClazz.getConstructor();
                        HabboPlugin plugin = constructor.newInstance();
                        plugin.configuration = pluginConfigurtion;
                        plugin.classLoader = urlClassLoader;
                        plugin.stream = stream;
                        this.plugins.add(plugin);
                        plugin.onEnable();
                    } catch (Exception e) {
                        LOGGER.error("Could not load plugin {}!", pluginConfigurtion.name);
                        LOGGER.error("Caught exception", e);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    public void registerEvents(HabboPlugin plugin, EventListener listener) {
        synchronized (plugin.registeredEvents) {
            Method[] methods = listener.getClass().getMethods();

            for (Method method : methods) {
                if (method.getAnnotation(EventHandler.class) != null) {
                    if (method.getParameterTypes().length == 1) {
                        if (Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                            final Class<?> eventClass = method.getParameterTypes()[0];

                            if (!plugin.registeredEvents.containsKey(eventClass.asSubclass(Event.class))) {
                                plugin.registeredEvents.put(eventClass.asSubclass(Event.class), new THashSet<>());
                            }

                            plugin.registeredEvents.get(eventClass.asSubclass(Event.class)).add(method);
                        }
                    }
                }
            }
        }
    }

    public <T extends Event> T fireEvent(T event) {
        for (Method method : this.methods) {
            if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isAssignableFrom(event.getClass())) {
                try {
                    method.invoke(null, event);
                } catch (Exception e) {
                    LOGGER.error("Could not pass default event {} to {}: {}!", event.getClass().getName(), method.getClass().getName(), method.getName());
                    LOGGER.error("Caught exception", e);
                }
            }
        }

        TObjectHashIterator<HabboPlugin> iterator = this.plugins.iterator();
        while (iterator.hasNext()) {
            try {
                HabboPlugin plugin = iterator.next();

                if (plugin != null) {
                    THashSet<Method> methods = plugin.registeredEvents.get(event.getClass().asSubclass(Event.class));

                    if (methods != null) {
                        for (Method method : methods) {
                            try {
                                method.invoke(plugin, event);
                            } catch (Exception e) {
                                LOGGER.error("Could not pass event {} to {}", event.getClass().getName(), plugin.configuration.name);
                                LOGGER.error("Caught exception", e);
                            }
                        }
                    }
                }
            } catch (NoSuchElementException e) {
                break;
            }
        }

        return event;
    }

    public boolean isRegistered(Class<? extends Event> clazz, boolean pluginsOnly) {
        TObjectHashIterator<HabboPlugin> iterator = this.plugins.iterator();
        while (iterator.hasNext()) {
            try {
                HabboPlugin plugin = iterator.next();
                if (plugin.isRegistered(clazz))
                    return true;
            } catch (NoSuchElementException e) {
                break;
            }
        }

        if (!pluginsOnly) {
            for (Method method : this.methods) {
                if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isAssignableFrom(clazz)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void dispose() {
        this.disposePlugins();

        LOGGER.info("Disposed Plugin Manager!");
    }

    private void disposePlugins() {
        TObjectHashIterator<HabboPlugin> iterator = this.plugins.iterator();
        while (iterator.hasNext()) {
            try {
                HabboPlugin p = iterator.next();

                if (p != null) {

                    try {
                        p.onDisable();
                        p.stream.close();
                        p.classLoader.close();
                    } catch (IOException e) {
                        LOGGER.error("Caught exception", e);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to disable {} because of an exception.", p.configuration.name, ex);
                    }
                }
            } catch (NoSuchElementException e) {
                break;
            }
        }
        this.plugins.clear();
    }

    public void reload() {
        long millis = System.currentTimeMillis();

        this.methods.clear();

        this.loadPlugins();

        LOGGER.info("Plugin Manager -> Loaded! {} plugins! ({} MS)", this.plugins.size(), System.currentTimeMillis() - millis);

        this.registerDefaultEvents();
    }

    private void registerDefaultEvents() {
        try {
            this.methods.add(RoomTrashing.class.getMethod("onUserWalkEvent", UserTakeStepEvent.class));
            this.methods.add(Easter.class.getMethod("onUserChangeMotto", UserSavedMottoEvent.class));
            this.methods.add(TagGame.class.getMethod("onUserLookAtPoint", RoomUnitLookAtPointEvent.class));
            this.methods.add(TagGame.class.getMethod("onUserWalkEvent", UserTakeStepEvent.class));
            this.methods.add(FreezeGame.class.getMethod("onConfigurationUpdated", EmulatorConfigUpdatedEvent.class));
            this.methods.add(PacketManager.class.getMethod("onConfigurationUpdated", EmulatorConfigUpdatedEvent.class));
            this.methods.add(InteractionFootballGate.class.getMethod("onUserDisconnectEvent", UserDisconnectEvent.class));
            this.methods.add(InteractionFootballGate.class.getMethod("onUserExitRoomEvent", UserExitRoomEvent.class));
            this.methods.add(InteractionFootballGate.class.getMethod("onUserSavedLookEvent", UserSavedLookEvent.class));
            this.methods.add(PluginManager.class.getMethod("globalOnConfigurationUpdated", EmulatorConfigUpdatedEvent.class));
            this.methods.add(WiredHighscoreManager.class.getMethod("onEmulatorLoaded", EmulatorLoadedEvent.class));
            this.methods.add(WiredManager.class.getMethod("onEmulatorLoaded", EmulatorLoadedEvent.class));
        } catch (NoSuchMethodException e) {
            LOGGER.info("Failed to define default events!");
            LOGGER.error("Caught exception", e);
        }
    }

    public THashSet<HabboPlugin> getPlugins() {
        return this.plugins;
    }
}
