package com.eu.habbo.plugin;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.config.ConfigurationBinder;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.navigation.EventCategory;
import com.eu.habbo.habbohotel.navigation.NavigatorManager;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.rooms.RoomWallItemLimit;
import com.eu.habbo.habbohotel.rooms.TraxManager;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.incoming.catalog.CheckPetNameEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.FloorPlanEditorSaveEvent;
import com.eu.habbo.messages.incoming.rooms.promotions.BuyRoomPromotionEvent;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RoomConfigurationBinder extends ConfigurationBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConfigurationBinder.class);
    private final ToLongFunction<String> dateToEpochSeconds;

    RoomConfigurationBinder(ConfigurationManager configuration, ToLongFunction<String> dateToEpochSeconds) {
        super(configuration);
        this.dateToEpochSeconds = dateToEpochSeconds;
    }

    void bind() {
        this.bindRoomBehavior();
        this.bindLimits();
        this.bindNavigatorAndChat();
        this.bindSubscriptions();
        this.bindClothingValidation();
        this.bindEventCategories();
    }

    private void bindRoomBehavior() {
        this.apply(
                "room.chat.delay",
                () -> Room.HABBO_CHAT_DELAY = this.configuration.getBoolean("room.chat.delay", false));
        this.apply(
                "room.chat.mutearea.allow_whisper",
                () -> Room.MUTEAREA_CAN_WHISPER =
                        this.configuration.getBoolean("room.chat.mutearea.allow_whisper", false));
        this.apply(
                "save.room.chats",
                () -> RoomChatMessage.SAVE_ROOM_CHATS = this.configuration.getBoolean("save.room.chats", false));
        this.apply(
                "pathfinder.step.maximum.height",
                () -> RoomLayout.MAXIMUM_STEP_HEIGHT =
                        this.configuration.getDouble("pathfinder.step.maximum.height", 1.1));
        this.apply(
                "pathfinder.step.allow.falling",
                () -> RoomLayout.ALLOW_FALLING = this.configuration.getBoolean("pathfinder.step.allow.falling", true));
        this.apply(
                "hotel.trading.enabled",
                () -> RoomTrade.TRADING_ENABLED =
                        this.configuration.getBoolean("hotel.trading.enabled") && !ShutdownEmulator.instantiated);
        this.apply(
                "hotel.trading.requires.perk",
                () -> RoomTrade.TRADING_REQUIRES_PERK = this.configuration.getBoolean("hotel.trading.requires.perk"));
        this.apply(
                "hotel.wordfilter.messenger",
                () -> WordFilter.ENABLED_FRIENDCHAT = this.configuration.getBoolean("hotel.wordfilter.messenger"));
        this.apply(
                "hotel.wordfilter.replacement",
                () -> WordFilter.DEFAULT_REPLACEMENT = this.configuration.getValue("hotel.wordfilter.replacement"));
        this.apply(
                "hotel.bot.chat.minimum.interval",
                () -> BotManager.MINIMUM_CHAT_SPEED = this.configuration.getInt("hotel.bot.chat.minimum.interval"));
        this.apply(
                "hotel.bot.max.chatlength",
                () -> BotManager.MAXIMUM_CHAT_LENGTH = this.configuration.getInt("hotel.bot.max.chatlength"));
        this.apply(
                "hotel.bot.max.namelength",
                () -> BotManager.MAXIMUM_NAME_LENGTH = this.configuration.getInt("hotel.bot.max.namelength"));
        this.apply(
                "hotel.bot.max.chatdelay",
                () -> BotManager.MAXIMUM_CHAT_SPEED = this.configuration.getInt("hotel.bot.max.chatdelay"));
        this.apply("hotel.bot.placement.messages", () -> {
            String[] messages = this.configuration
                    .getValue("hotel.bot.placement.messages", "Yo!;Hello I'm a real party animal!;Hello!")
                    .split(";");
            Bot.PLACEMENT_MESSAGES = messages;
        });
        this.apply(
                "hotel.bot.limit.walking.distance",
                () -> Bot.BOT_LIMIT_WALKING_DISTANCE =
                        this.configuration.getBoolean("hotel.bot.limit.walking.distance", true));
        this.apply(
                "hotel.bot.limit.walking.distance.radius",
                () -> Bot.BOT_WALKING_DISTANCE_RADIUS =
                        this.configuration.getInt("hotel.bot.limit.walking.distance.radius", 5));
    }

    private void bindLimits() {
        this.apply(
                "hotel.inventory.max.items",
                () -> HabboInventory.MAXIMUM_ITEMS = this.configuration.getInt("hotel.inventory.max.items"));
        this.apply("hotel.max.bots.room", () -> Room.MAXIMUM_BOTS = this.configuration.getInt("hotel.max.bots.room"));
        this.apply("hotel.pets.max.room", () -> Room.MAXIMUM_PETS = this.configuration.getInt("hotel.pets.max.room"));
        this.apply(
                "hotel.room.furni.max",
                () -> Room.MAXIMUM_FURNI = this.configuration.getInt("hotel.room.furni.max", 2500));
        this.apply(
                "hotel.room.wallfurni.max",
                () -> RoomWallItemLimit.configure(this.configuration.getInt(
                        "hotel.room.wallfurni.max", RoomWallItemLimit.DEFAULT_MAX_WALL_ITEMS)));
        this.apply(
                "hotel.room.stickies.max",
                () -> Room.MAXIMUM_POSTITNOTES = this.configuration.getInt("hotel.room.stickies.max", 200));
        this.apply(
                "hotel.rooms.handitem.time",
                () -> Room.HAND_ITEM_TIME = this.configuration.getInt("hotel.rooms.handitem.time"));
        this.apply(
                "hotel.roomuser.idle.cycles",
                () -> Room.IDLE_CYCLES = this.configuration.getInt("hotel.roomuser.idle.cycles", 240));
        this.apply(
                "hotel.roomuser.idle.cycles.kick",
                () -> Room.IDLE_CYCLES_KICK = this.configuration.getInt("hotel.roomuser.idle.cycles.kick", 480));
        this.apply(
                "hotel.room.rollers.roll_avatars.max",
                () -> Room.ROLLERS_MAXIMUM_ROLL_AVATARS =
                        this.configuration.getInt("hotel.room.rollers.roll_avatars.max", 1));
        this.apply(
                "hotel.users.max.rooms",
                () -> RoomManager.MAXIMUM_ROOMS_USER = this.configuration.getInt("hotel.users.max.rooms", 50));
        this.apply(
                "hotel.users.max.rooms.hc",
                () -> RoomManager.MAXIMUM_ROOMS_HC = this.configuration.getInt("hotel.users.max.rooms.hc", 75));
        this.apply("hotel.home.room", () -> RoomManager.HOME_ROOM_ID = this.configuration.getInt("hotel.home.room"));
        this.apply(
                "hotel.bots.max.inventory",
                () -> BotManager.MAXIMUM_BOT_INVENTORY_SIZE = this.configuration.getInt("hotel.bots.max.inventory"));
        this.apply(
                "hotel.pets.max.inventory",
                () -> PetManager.MAXIMUM_PET_INVENTORY_SIZE = this.configuration.getInt("hotel.pets.max.inventory"));
    }

    private void bindNavigatorAndChat() {
        this.apply(
                "hotel.navigator.search.maxresults",
                () -> NavigatorManager.MAXIMUM_RESULTS_PER_PAGE =
                        this.configuration.getInt("hotel.navigator.search.maxresults"));
        this.apply(
                "hotel.navigator.sort.ordernum",
                () -> NavigatorManager.CATEGORY_SORT_USING_ORDER_NUM =
                        this.configuration.getBoolean("hotel.navigator.sort.ordernum"));
        this.apply(
                "hotel.chat.max.length",
                () -> RoomChatMessage.MAXIMUM_LENGTH = this.configuration.getInt("hotel.chat.max.length"));
        this.apply(
                "hotel.jukebox.limit.large",
                () -> TraxManager.LARGE_JUKEBOX_LIMIT = this.configuration.getInt("hotel.jukebox.limit.large"));
        this.apply(
                "hotel.jukebox.limit.normal",
                () -> TraxManager.NORMAL_JUKEBOX_LIMIT = this.configuration.getInt("hotel.jukebox.limit.normal"));
        this.apply("commands.cmd_chatcolor.banned_numbers", () -> {
            String[] values = this.configuration
                    .getValue("commands.cmd_chatcolor.banned_numbers")
                    .split(";");
            int[] bannedBubbles = new int[values.length];
            for (int index = 0; index < values.length; index++) {
                bannedBubbles[index] = Integer.parseInt(values[index]);
            }
            RoomChatMessage.BANNED_BUBBLES = bannedBubbles;
        });
        this.apply(
                "room.chat.prefix.format",
                () -> Room.PREFIX_FORMAT = this.configuration.getValue("room.chat.prefix.format"));
        this.apply(
                "hotel.floorplan.max.widthlength",
                () -> FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH =
                        this.configuration.getInt("hotel.floorplan.max.widthlength"));
        this.apply(
                "hotel.floorplan.max.totalarea",
                () -> FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_SIZE =
                        this.configuration.getInt("hotel.floorplan.max.totalarea"));
        this.apply(
                "hotel.room.stickypole.prefix",
                () -> InteractionPostIt.STICKYPOLE_PREFIX_TEXT =
                        this.configuration.getValue("hotel.room.stickypole.prefix"));
        this.apply(
                "hotel.room.rollers.norules",
                () -> InteractionRoller.NO_RULES = this.configuration.getBoolean("hotel.room.rollers.norules"));
        this.apply(
                "hotel.navigator.populartab.publics",
                () -> RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB =
                        this.configuration.getBoolean("hotel.navigator.populartab.publics"));
        this.apply(
                "hotel.pets.name.length.min",
                () -> CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM =
                        this.configuration.getInt("hotel.pets.name.length.min"));
        this.apply(
                "hotel.pets.name.length.max",
                () -> CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM =
                        this.configuration.getInt("hotel.pets.name.length.max"));
        this.apply(
                "room.promotion.badge",
                () -> BuyRoomPromotionEvent.ROOM_PROMOTION_BADGE =
                        this.configuration.getValue("room.promotion.badge", "RADZZ"));
    }

    private void bindSubscriptions() {
        this.apply(
                "subscriptions.hc.payday.enabled",
                () -> SubscriptionHabboClub.HC_PAYDAY_ENABLED =
                        this.configuration.getBoolean("subscriptions.hc.payday.enabled", false));
        this.apply("subscriptions.hc.payday.next_date", () -> {
            String value = this.configuration.getValue("subscriptions.hc.payday.next_date");
            try {
                SubscriptionHabboClub.HC_PAYDAY_NEXT_DATE = Math.toIntExact(this.dateToEpochSeconds.applyAsLong(value));
            } catch (RuntimeException exception) {
                SubscriptionHabboClub.HC_PAYDAY_NEXT_DATE = Integer.MAX_VALUE;
            }
        });
        this.apply(
                "subscriptions.hc.payday.interval",
                () -> SubscriptionHabboClub.HC_PAYDAY_INTERVAL =
                        this.configuration.getValue("subscriptions.hc.payday.interval"));
        this.apply(
                "subscriptions.hc.payday.query",
                () -> SubscriptionHabboClub.HC_PAYDAY_QUERY =
                        this.configuration.getValue("subscriptions.hc.payday.query"));
        this.apply(
                "subscriptions.hc.payday.currency",
                () -> SubscriptionHabboClub.HC_PAYDAY_CURRENCY =
                        this.configuration.getValue("subscriptions.hc.payday.currency"));
        this.apply(
                "subscriptions.hc.payday.percentage",
                () -> SubscriptionHabboClub.HC_PAYDAY_KICKBACK_PERCENTAGE =
                        this.configuration.getInt("subscriptions.hc.payday.percentage", 10) / 100.0);
        this.apply(
                "subscriptions.hc.payday.creditsspent_reset_on_expire",
                () -> SubscriptionHabboClub.HC_PAYDAY_COINSSPENT_RESET_ON_EXPIRE =
                        this.configuration.getBoolean("subscriptions.hc.payday.creditsspent_reset_on_expire", false));
        this.apply(
                "subscriptions.hc.achievement",
                () -> SubscriptionHabboClub.ACHIEVEMENT_NAME =
                        this.configuration.getValue("subscriptions.hc.achievement", "VipHC"));
        this.apply(
                "subscriptions.hc.discount.enabled",
                () -> SubscriptionHabboClub.DISCOUNT_ENABLED =
                        this.configuration.getBoolean("subscriptions.hc.discount.enabled", false));
        this.apply(
                "subscriptions.hc.discount.days_before_end",
                () -> SubscriptionHabboClub.DISCOUNT_DAYS_BEFORE_END =
                        this.configuration.getInt("subscriptions.hc.discount.days_before_end", 7));
        this.apply("subscriptions.hc.payday.streak", () -> {
            TreeMap<Integer, Integer> streaks = new TreeMap<>();
            for (String streak : this.configuration
                    .getValue("subscriptions.hc.payday.streak", "7=5;30=10;60=15;90=20;180=25;365=30")
                    .split(Pattern.quote(";"))) {
                if (streak.contains("=")) {
                    String[] parts = streak.split(Pattern.quote("="));
                    streaks.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }
            TreeMap<Integer, Integer> publishedStreaks = SubscriptionHabboClub.HC_PAYDAY_STREAK;
            synchronized (publishedStreaks) {
                publishedStreaks.clear();
                publishedStreaks.putAll(streaks);
            }
            SubscriptionHabboClub.HC_PAYDAY_STREAK = publishedStreaks;
        });
    }

    private void bindClothingValidation() {
        this.apply("hotel.users.clothingvalidation", () -> {
            ClothingValidationManager.VALIDATE_ON_HC_EXPIRE =
                    this.configuration.getBoolean("hotel.users.clothingvalidation.onhcexpired", false);
            ClothingValidationManager.VALIDATE_ON_LOGIN =
                    this.configuration.getBoolean("hotel.users.clothingvalidation.onlogin", false);
            ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS =
                    this.configuration.getBoolean("hotel.users.clothingvalidation.onchangelooks", false);
            ClothingValidationManager.VALIDATE_ON_MIMIC =
                    this.configuration.getBoolean("hotel.users.clothingvalidation.onmimic", false);
            ClothingValidationManager.VALIDATE_ON_MANNEQUIN =
                    this.configuration.getBoolean("hotel.users.clothingvalidation.onmannequin", false);
            ClothingValidationManager.VALIDATE_ON_FBALLGATE =
                    this.configuration.getBoolean("hotel.users.clothingvalidation.onfballgate", false);

            String newUrl = this.configuration.getValue("gamedata.figuredata.url");
            if (!ClothingValidationManager.FIGUREDATA_URL.equals(newUrl)) {
                ClothingValidationManager.FIGUREDATA_URL = newUrl;
                ClothingValidationManager.reloadFiguredata(newUrl);
            }
            if (newUrl.isEmpty()) {
                ClothingValidationManager.VALIDATE_ON_HC_EXPIRE = false;
                ClothingValidationManager.VALIDATE_ON_LOGIN = false;
                ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS = false;
                ClothingValidationManager.VALIDATE_ON_MIMIC = false;
                ClothingValidationManager.VALIDATE_ON_MANNEQUIN = false;
                ClothingValidationManager.VALIDATE_ON_FBALLGATE = false;
            }
        });
    }

    private void bindEventCategories() {
        this.apply("navigator.eventcategories", () -> {
            List<EventCategory> categories = new ArrayList<>();
            for (String category :
                    this.configuration.getValue("navigator.eventcategories", "").split(";")) {
                try {
                    categories.add(new EventCategory(category));
                } catch (Exception exception) {
                    LOGGER.error("Invalid navigator event category {}", category, exception);
                }
            }
            List<EventCategory> publishedCategories = NewNavigatorEventCategoriesComposer.CATEGORIES;
            synchronized (publishedCategories) {
                publishedCategories.clear();
                publishedCategories.addAll(categories);
            }
            NewNavigatorEventCategoriesComposer.CATEGORIES = publishedCategories;
        });
    }
}
