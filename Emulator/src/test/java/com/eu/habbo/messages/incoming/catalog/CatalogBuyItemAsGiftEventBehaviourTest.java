package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogLimitedConfiguration;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPaymentService;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubAddonsLayout;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubFrontPageLayout;
import com.eu.habbo.habbohotel.catalog.layouts.ClubBuyLayout;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionBadgeDisplay;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionGuildFurni;
import com.eu.habbo.habbohotel.items.interactions.InteractionHopper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionTrophy;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import com.zaxxer.hikari.HikariDataSource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;
import org.mockito.listeners.InvocationListener;

/**
 * Characterizes buying a catalog item as a gift, end to end: every response the sender and the recipient
 * receive, the payment reserved and refunded, the rows created and deleted, the SQL that runs, the limited
 * edition bookkeeping and the achievements, recorded in the order they happen. Each scenario pins the whole
 * trace, so the handler can be taken apart without changing what a gift does.
 */
class CatalogBuyItemAsGiftEventBehaviourTest {
    private static final int NOW = 1_000_000;
    private static final int SENDER_ID = 1;
    private static final int RECIPIENT_ID = 2;
    private static final int OFFLINE_ID = 3;
    private static final int PAGE_ID = 10;
    private static final int ITEM_ID = 20;
    private static final int PAID_WRAP_SPRITE = 100;
    private static final int FREE_WRAP_SPRITE = 200;
    private static final int COLOR = GiftConfigurationComposer.BOX_TYPES.get(1);
    private static final int RIBBON = GiftConfigurationComposer.RIBBON_TYPES.get(2);

    private final List<String> trace = new ArrayList<>();
    private final InvocationListener listener = report -> record((Invocation) report.getInvocation());

    private final Set<String> permissions = new HashSet<>();
    private final Map<String, Integer> configInts = new HashMap<>();
    private final Map<String, Boolean> configBooleans = new HashMap<>();
    private final Map<Integer, Item> items = new HashMap<>();
    private final Map<Integer, Integer> wrappers = new HashMap<>();
    private final Map<Integer, Integer> furnitureWraps = new HashMap<>();
    private final Int2ObjectOpenHashMap<CatalogPage> pages = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, ClubOffer> clubOffers = new HashMap<>();
    private final Set<String> failingCreations = new HashSet<>();

    private boolean recording = false;
    private int failingCreation = -1;
    private int creations = 0;
    private boolean tryTakeResult = true;
    private boolean giftCreated = true;
    private boolean recipientOnline = true;
    private boolean recipientInDatabase = true;
    private int recipientBadgeCount = 0;
    private boolean subscriptionCreated = true;
    private boolean offlineSubscriptionActive = false;
    private int nextItemId = 1000;

    private GameClient client;
    private Habbo sender;
    private HabboInfo senderInfo;
    private HabboStats senderStats;
    private Habbo recipient;
    private HabboInfo recipientInfo;
    private HabboStats recipientStats;
    private GameClient recipientClient;
    private BadgesComponent recipientBadges;
    private BadgesComponent senderBadges;
    private ItemManager itemManager;
    private CatalogManager catalogManager;
    private CatalogPage page;
    private CatalogItem item;
    private Item chair;
    private CatalogLimitedConfiguration limitedConfiguration;
    private Connection connection;

    private MockedStatic<Emulator> emulator;
    private MockedStatic<CatalogPaymentService> payment;
    private MockedStatic<AchievementManager> achievements;
    private MockedStatic<ScripterManager> scripters;

    @BeforeEach
    void setUp() throws Exception {
        ShutdownEmulator.timestamp = 0;

        senderInfo = traced(HabboInfo.class, "senderInfo");
        when(senderInfo.getId()).thenReturn(SENDER_ID);
        when(senderInfo.getUsername()).thenReturn("sender");
        when(senderInfo.getLook()).thenReturn("hd-180");
        when(senderInfo.getCredits()).thenReturn(1000);
        when(senderInfo.getCurrencyAmount(anyInt())).thenReturn(1000);
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(1);
        when(senderInfo.getRank()).thenReturn(rank);

        senderStats = traced(HabboStats.class, "senderStats");
        senderBadges = traced(BadgesComponent.class, "senderBadges");
        HabboInventory senderInventory = mock(HabboInventory.class);
        when(senderInventory.getBadgesComponent()).thenReturn(senderBadges);

        sender = traced(Habbo.class, "sender");
        when(sender.getHabboInfo()).thenReturn(senderInfo);
        when(sender.getHabboStats()).thenReturn(senderStats);
        when(sender.getInventory()).thenReturn(senderInventory);
        when(sender.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.<String>getArgument(0)));

        client = traced(GameClient.class, "senderClient");
        when(client.getHabbo()).thenReturn(sender);
        when(sender.getClient()).thenReturn(client);

        recipientClient = traced(GameClient.class, "recipientClient");
        recipientBadges = traced(BadgesComponent.class, "recipientBadges");
        HabboInventory recipientInventory = mock(HabboInventory.class);
        when(recipientInventory.getBadgesComponent()).thenReturn(recipientBadges);
        when(recipientInventory.getItemsComponent()).thenReturn(traced(ItemsComponent.class, "recipientItems"));
        recipientInfo = mock(HabboInfo.class);
        when(recipientInfo.getId()).thenReturn(RECIPIENT_ID);
        when(recipientInfo.getUsername()).thenReturn("friend");
        recipientStats = traced(HabboStats.class, "recipientStats");
        when(recipientStats.createSubscription(anyString(), anyInt()))
                .thenAnswer(call -> subscriptionCreated ? mock(Subscription.class, "subscription") : null);
        recipient = traced(Habbo.class, "recipient");
        when(recipient.getHabboInfo()).thenReturn(recipientInfo);
        when(recipient.getHabboStats()).thenReturn(recipientStats);
        when(recipient.getInventory()).thenReturn(recipientInventory);
        when(recipient.getClient()).thenReturn(recipientClient);
        when(recipientClient.getHabbo()).thenReturn(recipient);

        chair = baseItem(300, "chair", FurnitureType.FLOOR, InteractionDefault.class);

        item = traced(CatalogItem.class, "catalogItem");
        when(item.getId()).thenReturn(ITEM_ID);
        when(item.getOfferId()).thenReturn(77);
        when(item.getName()).thenReturn("chair_offer");
        when(item.getCredits()).thenReturn(5);
        when(item.getPoints()).thenReturn(0);
        when(item.getPointsType()).thenReturn(0);
        when(item.getAmount()).thenReturn(1);
        when(item.getItemAmount(anyInt())).thenReturn(1);
        when(item.getBaseItems()).thenAnswer(call -> new LinkedHashSet<>(List.of(chair)));

        page = traced(CatalogPage.class, "page");
        when(page.isEnabled()).thenReturn(true);
        when(page.isVisible()).thenReturn(true);
        when(page.getRank()).thenReturn(0);
        when(page.getCatalogItem(ITEM_ID)).thenReturn(item);
        when(page.getCatalogItems()).thenAnswer(call -> {
            Int2ObjectOpenHashMap<CatalogItem> map = new Int2ObjectOpenHashMap<>();
            map.put(ITEM_ID, item);
            return map;
        });
        pages.put(PAGE_ID, page);

        Item paidWrap = baseItem(500, "present_wrap", FurnitureType.FLOOR, InteractionDefault.class);
        Item freeWrap = baseItem(600, "present_gen", FurnitureType.FLOOR, InteractionDefault.class);
        wrappers.put(PAID_WRAP_SPRITE, 500);
        furnitureWraps.put(FREE_WRAP_SPRITE, 600);
        items.put(500, paidWrap);
        items.put(600, freeWrap);

        limitedConfiguration = traced(CatalogLimitedConfiguration.class, "limitedConfiguration");
        when(limitedConfiguration.available()).thenReturn(5);
        when(limitedConfiguration.getNumber()).thenReturn(7);
        when(limitedConfiguration.getTotalSet()).thenReturn(50);

        itemManager = traced(ItemManager.class, "itemManager");
        when(itemManager.getItem(anyInt())).thenAnswer(call -> items.get(call.<Integer>getArgument(0)));
        when(itemManager.createItem(anyInt(), any(Item.class), anyInt(), anyInt(), anyString()))
                .thenAnswer(call -> {
                    Item base = call.getArgument(1);
                    if (creations++ == failingCreation) return null;
                    if (failingCreations.contains(base.getName())) return null;
                    return habboItem(nextItemId++, base);
                });
        when(itemManager.createGift(anyString(), any(Item.class), anyString(), anyInt(), anyInt()))
                .thenAnswer(call -> giftCreated ? habboItem(9000, call.getArgument(1)) : null);

        catalogManager = traced(CatalogManager.class, "catalogManager");
        setField(catalogManager, "catalogPages", pages);
        setField(catalogManager, "clubOffers", clubOffers);
        when(catalogManager.getGiftWrappingSnapshot())
                .thenAnswer(call -> new CatalogManager.GiftWrappingSnapshot(wrappers, furnitureWraps));
        when(catalogManager.getLimitedConfig(any())).thenReturn(limitedConfiguration);

        HabboManager habboManager = mock(HabboManager.class);
        when(habboManager.getHabbo(anyString())).thenAnswer(call -> recipientOnline ? recipient : null);

        WordFilter wordFilter = mock(WordFilter.class);
        when(wordFilter.filter(anyString(), any())).thenAnswer(call -> "filtered:" + call.getArgument(0));

        AchievementManager achievementManager = mock(AchievementManager.class);
        when(achievementManager.getAchievement(anyString())).thenAnswer(call -> achievement(call.getArgument(0)));

        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(itemManager);
        when(environment.getCatalogManager()).thenReturn(catalogManager);
        when(environment.getHabboManager()).thenReturn(habboManager);
        when(environment.getWordFilter()).thenReturn(wordFilter);
        when(environment.getAchievementManager()).thenReturn(achievementManager);

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt()))
                .thenAnswer(call -> configInts.getOrDefault(call.<String>getArgument(0), call.<Integer>getArgument(1)));
        when(config.getInt(anyString())).thenAnswer(call -> configInts.getOrDefault(call.<String>getArgument(0), 0));
        when(config.getBoolean(anyString()))
                .thenAnswer(call -> configBooleans.getOrDefault(call.<String>getArgument(0), false));

        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(call -> "[" + call.getArgument(0) + "]");
        when(texts.getValue(anyString(), anyString())).thenAnswer(call -> "[" + call.getArgument(0) + "]");

        connection = traced(Connection.class, "connection");
        when(connection.prepareStatement(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
        when(connection.prepareStatement(anyString(), anyInt())).thenAnswer(call -> statement(call.getArgument(0)));
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);

        emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getTexts).thenReturn(texts);
        emulator.when(Emulator::getDatabase).thenReturn(database);
        emulator.when(Emulator::getThreading).thenReturn(traced(ThreadPooling.class, "threading"));
        emulator.when(Emulator::getRandom).thenReturn(new Random(0));
        emulator.when(Emulator::getIntUnixTimestamp).thenReturn(NOW);

        payment = mockStatic(CatalogPaymentService.class);
        payment.when(() -> CatalogPaymentService.tryTake(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> {
                    trace.add("payment.tryTake(" + call.getArgument(1) + ", " + call.getArgument(2) + ", "
                            + call.getArgument(3) + ") -> " + tryTakeResult);
                    return tryTakeResult;
                });
        payment.when(() -> CatalogPaymentService.refund(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> {
                    trace.add("payment.refund(" + call.getArgument(1) + ", " + call.getArgument(2) + ", "
                            + call.getArgument(3) + ")");
                    return null;
                });

        achievements = mockStatic(AchievementManager.class);
        achievements
                .when(() -> AchievementManager.progressAchievement(any(Habbo.class), any(Achievement.class)))
                .thenAnswer(call -> {
                    trace.add("achievement(" + render(call.getArgument(0)) + ", " + render(call.getArgument(1)) + ")");
                    return null;
                });
        achievements
                .when(() -> AchievementManager.progressAchievement(anyInt(), any(Achievement.class)))
                .thenAnswer(call -> {
                    trace.add("achievement(" + call.getArgument(0) + ", " + render(call.getArgument(1)) + ")");
                    return null;
                });

        scripters = mockStatic(ScripterManager.class);
        scripters
                .when(() -> ScripterManager.scripterDetected(any(), anyString()))
                .thenAnswer(call -> {
                    trace.add("scripterDetected(" + call.getArgument(1) + ")");
                    return null;
                });
    }

    @AfterEach
    void tearDown() {
        ShutdownEmulator.timestamp = 0;
        scripters.close();
        achievements.close();
        payment.close();
        emulator.close();
    }

    // ---------------------------------------------------------------- the scenarios

    @Test
    void anOnlineFriendReceivesTheGiftWithTheSendersName() {
        assertGift(gift(PAID_WRAP_SPRITE, true, "hello"));
    }

    @Test
    void aFreeWrapCostsOnlyTheItemAndAnOfflineFriendIsFoundInTheDatabase() {
        recipientOnline = false;

        assertGift(gift(FREE_WRAP_SPRITE, true, "hi"));
    }

    @Test
    void thePaidWrapAddsTheConfiguredFee() {
        configInts.put("hotel.gifts.special.price", 3);

        assertGift(gift(PAID_WRAP_SPRITE, true, "hi"));
    }

    @Test
    void onlyAPermittedSenderMayHideTheirName() {
        assertGift(gift(FREE_WRAP_SPRITE, false, "hidden?"));

        trace.clear();
        resetStats();
        permissions.add(CatalogBuyItemAsGiftEvent.GIFT_HIDE_SENDER);

        assertGift(gift(FREE_WRAP_SPRITE, false, "hidden"));
    }

    @Test
    void theMessageIsCutToTheConfiguredLengthAndLosesItsTabs() {
        configInts.put("hotel.gifts.length.max", 6);

        assertGift(gift(FREE_WRAP_SPRITE, true, "a\tb\tcdefgh"));
    }

    @Test
    void aSenderWithInfiniteCurrencyIsChargedNothing() {
        permissions.add(Permission.ACC_INFINITE_CREDITS);
        permissions.add(Permission.ACC_INFINITE_PIXELS);
        when(item.getPoints()).thenReturn(4);

        assertGift(gift(FREE_WRAP_SPRITE, true, "free"));
    }

    @Test
    void pointsAreChargedInTheirOwnCurrency() {
        when(item.getPoints()).thenReturn(4);
        when(item.getPointsType()).thenReturn(5);

        assertGift(gift(FREE_WRAP_SPRITE, true, "points"));

        trace.clear();
        resetStats();
        permissions.add(Permission.ACC_INFINITE_POINTS);

        assertGift(gift(FREE_WRAP_SPRITE, true, "points, infinite"));
    }

    @Test
    void aGiftToOneselfGivesNoAchievements() {
        when(recipient.getHabboInfo().getId()).thenReturn(SENDER_ID);

        assertGift(gift(FREE_WRAP_SPRITE, true, "me"));
    }

    @Test
    void anOfferIdFromSearchResultsFindsTheItemOnThePage() {
        when(page.getCatalogItem(77)).thenReturn(null);

        assertGift(giftOf(PAGE_ID, 77, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "search"));
    }

    @Test
    void aTeleporterIsGivenAsALinkedPair() {
        chair = baseItem(301, "teleport", FurnitureType.FLOOR, InteractionTeleport.class);

        assertGift(gift(FREE_WRAP_SPRITE, true, "tele"));
    }

    @Test
    void aHopperIsRegistered() {
        chair = baseItem(302, "hopper", FurnitureType.FLOOR, InteractionHopper.class);

        assertGift(gift(FREE_WRAP_SPRITE, true, "hop"));
    }

    @Test
    void aTrophyCarriesTheSenderAndTheDate() {
        chair = baseItem(303, "trophy", FurnitureType.FLOOR, InteractionTrophy.class);

        assertGift(giftWithExtraData("engraved", FREE_WRAP_SPRITE, true, "trophy"));
    }

    @Test
    void aBadgeDisplayWithABadgeTheRecipientLacksIsReportedAndRewritten() {
        chair = baseItem(304, "badge_display", FurnitureType.FLOOR, InteractionBadgeDisplay.class);

        assertGift(giftWithExtraData("ADM", FREE_WRAP_SPRITE, true, "display"));

        trace.clear();
        resetStats();
        when(recipientBadges.hasBadge("ADM")).thenReturn(true);

        assertGift(giftWithExtraData("ADM", FREE_WRAP_SPRITE, true, "display, owned"));
    }

    @Test
    void aLimitedItemIsNumberedAndLogged() {
        makeLimited();

        assertGift(gift(FREE_WRAP_SPRITE, true, "ltd"));
    }

    @Test
    void aLimitedTeleporterRecordsOneSaleForItsNumber() {
        makeLimited();
        chair = baseItem(301, "teleport", FurnitureType.FLOOR, InteractionTeleport.class);

        assertGift(gift(FREE_WRAP_SPRITE, true, "ltd tele"));
    }

    @Test
    void aLimitedItemWithoutAConfigurationGetsOne() {
        makeLimited();
        when(catalogManager.getLimitedConfig(any())).thenReturn(null);
        when(catalogManager.createOrUpdateLimitedConfig(any())).thenReturn(limitedConfiguration);

        assertGift(gift(FREE_WRAP_SPRITE, true, "ltd, new"));
    }

    @Test
    void limitedItemsRespectTheDailyLimits() {
        makeLimited();
        configBooleans.put("hotel.catalog.ltd.limit.enabled", true);
        configInts.put("hotel.purchase.ltd.limit.daily.total", 3);
        configInts.put("hotel.purchase.ltd.limit.daily.item", 1);

        when(senderStats.totalLtds()).thenReturn(3);
        assertGift(gift(FREE_WRAP_SPRITE, true, "total"));

        trace.clear();
        resetStats();
        when(senderStats.totalLtds()).thenReturn(2);
        when(senderStats.totalLtds(ITEM_ID)).thenReturn(1);
        assertGift(gift(FREE_WRAP_SPRITE, true, "item"));

        trace.clear();
        resetStats();
        when(senderStats.totalLtds(ITEM_ID)).thenReturn(0);
        assertGift(gift(FREE_WRAP_SPRITE, true, "under"));
    }

    @Test
    void aFailedLimitedGiftReturnsItsReservedNumber() {
        makeLimited();

        tryTakeResult = false;
        assertGift(gift(FREE_WRAP_SPRITE, true, "payment refused"));

        reset();
        tryTakeResult = true;
        giftCreated = false;
        assertGift(gift(FREE_WRAP_SPRITE, true, "no box"));

        reset();
        giftCreated = true;
        when(itemManager.createItem(anyInt(), any(Item.class), anyInt(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("database gone"));
        assertGift(gift(FREE_WRAP_SPRITE, true, "boom"));
    }

    @Test
    void aSoldOutLimitedItemIsRefused() {
        makeLimited();
        when(item.getLimitedSells()).thenReturn(50);

        assertGift(gift(FREE_WRAP_SPRITE, true, "sold out"));

        trace.clear();
        resetStats();
        when(item.getLimitedSells()).thenReturn(10);
        when(limitedConfiguration.available()).thenReturn(0);

        assertGift(gift(FREE_WRAP_SPRITE, true, "none available"));
    }

    @Test
    void aFailedGiftDeletesWhatWasCreatedAndRefunds() {
        giftCreated = false;

        assertGift(gift(PAID_WRAP_SPRITE, true, "no box"));
    }

    @Test
    void aTeleporterThatCannotBeCreatedRefunds() {
        chair = baseItem(301, "teleport", FurnitureType.FLOOR, InteractionTeleport.class);
        failingCreations.add("teleport");

        assertGift(gift(FREE_WRAP_SPRITE, true, "tele"));
    }

    @Test
    void aTeleporterWhoseSecondHalfFailsRefundsAndDeletesTheFirstHalf() {
        chair = baseItem(301, "teleport", FurnitureType.FLOOR, InteractionTeleport.class);
        failingCreation = 1;

        assertGift(gift(FREE_WRAP_SPRITE, true, "half"));
    }

    @Test
    void aLimitedHopperKeepsItsStackAndNumber() {
        makeLimited();
        chair = baseItem(302, "hopper", FurnitureType.FLOOR, InteractionHopper.class);

        assertGift(gift(FREE_WRAP_SPRITE, true, "ltd hopper"));
    }

    @Test
    void notEnoughPointsIsRefused() {
        when(item.getPoints()).thenReturn(4);
        when(item.getPointsType()).thenReturn(5);
        when(senderInfo.getCurrencyAmount(5)).thenReturn(3);

        assertGift(gift(FREE_WRAP_SPRITE, true, "poor in points"));
    }

    @Test
    void anItemThatCannotBeCreatedRefunds() {
        failingCreations.add("chair");

        assertGift(gift(FREE_WRAP_SPRITE, true, "chair"));

        trace.clear();
        resetStats();
        chair = baseItem(302, "hopper", FurnitureType.FLOOR, InteractionHopper.class);
        failingCreations.add("hopper");

        assertGift(gift(FREE_WRAP_SPRITE, true, "hopper"));
    }

    @Test
    void anExceptionWhileCreatingRefundsAndAnswersWithAnError() {
        when(itemManager.createItem(anyInt(), any(Item.class), anyInt(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("database gone"));

        assertGift(gift(FREE_WRAP_SPRITE, true, "boom"));
    }

    @Test
    void aPaymentThatCannotBeReservedCreatesNothing() {
        tryTakeResult = false;

        assertGift(gift(FREE_WRAP_SPRITE, true, "broke"));
    }

    @Test
    void theRequestIsRefusedBeforeAnythingIsCharged() {
        when(senderInfo.getCredits()).thenReturn(4);
        assertGift(gift(FREE_WRAP_SPRITE, true, "poor"));

        reset();
        when(senderInfo.getCredits()).thenReturn(1000);
        when(item.getCredits()).thenReturn(Integer.MAX_VALUE);
        configInts.put("hotel.gifts.special.price", 1);
        assertGift(gift(PAID_WRAP_SPRITE, true, "overflow"));

        reset();
        when(item.getCredits()).thenReturn(5);
        when(item.getPoints()).thenReturn(-1);
        assertGift(gift(FREE_WRAP_SPRITE, true, "negative points"));

        reset();
        when(item.getPoints()).thenReturn(0);
        when(item.isClubOnly()).thenReturn(true);
        assertGift(gift(FREE_WRAP_SPRITE, true, "club only"));

        reset();
        when(senderStats.hasActiveClub()).thenReturn(true);
        assertGift(gift(FREE_WRAP_SPRITE, true, "club only, member"));

        reset();
        when(item.isClubOnly()).thenReturn(false);
        when(item.getHabbiconId()).thenReturn(4);
        assertGift(gift(FREE_WRAP_SPRITE, true, "habbicon"));

        reset();
        when(item.getHabbiconId()).thenReturn(0);
        when(chair.allowGift()).thenReturn(false);
        assertGift(gift(FREE_WRAP_SPRITE, true, "not giftable"));
    }

    @Test
    void unsupportedItemsAreRefused() {
        when(item.getAmount()).thenReturn(2);
        assertGift(gift(FREE_WRAP_SPRITE, true, "amount"));

        reset();
        when(item.getAmount()).thenReturn(1);
        Item second = baseItem(305, "lamp", FurnitureType.FLOOR, InteractionDefault.class);
        when(item.getBaseItems()).thenAnswer(call -> new LinkedHashSet<>(List.of(chair, second)));
        assertGift(gift(FREE_WRAP_SPRITE, true, "two base items"));

        for (Item unsupported : List.of(
                baseItem(306, "bot_frank", FurnitureType.FLOOR, InteractionDefault.class),
                baseItem(307, "a0 pet0", FurnitureType.FLOOR, InteractionDefault.class),
                baseItem(308, "avatar_effect12", FurnitureType.EFFECT, InteractionDefault.class),
                baseItem(309, "guild_chair", FurnitureType.FLOOR, InteractionGuildFurni.class))) {
            reset();
            chair = unsupported;
            when(item.getBaseItems()).thenAnswer(call -> new LinkedHashSet<>(List.of(chair)));
            assertGift(gift(FREE_WRAP_SPRITE, true, unsupported.getName()));
        }

        reset();
        chair = baseItem(310, "chair", FurnitureType.FLOOR, InteractionDefault.class);
        when(item.getItemAmount(anyInt())).thenReturn(2);
        assertGift(gift(FREE_WRAP_SPRITE, true, "item amount"));
    }

    @Test
    void aBadgeTheRecipientAlreadyHasIsRefused() {
        chair = baseItem(311, "ACH_1", FurnitureType.BADGE, InteractionDefault.class);
        when(recipientBadges.hasBadge("ACH_1")).thenReturn(true);
        assertGift(gift(FREE_WRAP_SPRITE, true, "online"));

        reset();
        recipientOnline = false;
        recipientBadgeCount = 1;
        assertGift(gift(FREE_WRAP_SPRITE, true, "offline"));

        reset();
        recipientBadgeCount = 0;
        assertGift(gift(FREE_WRAP_SPRITE, true, "offline, without it"));
    }

    @Test
    void theRequestIsRefusedWhenTheRecipientOrThePageIsWrong() {
        recipientOnline = false;
        recipientInDatabase = false;
        assertGift(gift(FREE_WRAP_SPRITE, true, "nobody"));

        reset();
        recipientOnline = true;
        assertGift(giftOf(99, ITEM_ID, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "no page"));

        reset();
        when(page.getRank()).thenReturn(5);
        assertGift(gift(FREE_WRAP_SPRITE, true, "rank"));

        reset();
        when(page.getRank()).thenReturn(0);
        when(page.isEnabled()).thenReturn(false);
        assertGift(gift(FREE_WRAP_SPRITE, true, "disabled"));

        reset();
        when(page.isEnabled()).thenReturn(true);
        when(page.isVisible()).thenReturn(false);
        assertGift(gift(FREE_WRAP_SPRITE, true, "hidden"));

        reset();
        when(page.isVisible()).thenReturn(true);
        assertGift(giftOf(PAGE_ID, 55, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "no item"));
    }

    @Test
    void theWrappingIsChecked() {
        assertGift(gift(42, true, "unknown sprite"));

        reset();
        assertGift(giftOf(PAGE_ID, ITEM_ID, FREE_WRAP_SPRITE, 7, RIBBON, true, "bad colour"));

        reset();
        assertGift(giftOf(PAGE_ID, ITEM_ID, FREE_WRAP_SPRITE, COLOR, 99, true, "bad ribbon"));

        reset();
        items.remove(600);
        items.put(700, baseItem(700, "present_other", FurnitureType.FLOOR, InteractionDefault.class));
        furnitureWraps.put(201, 700);
        assertGift(gift(FREE_WRAP_SPRITE, true, "fallback wrap"));

        reset();
        items.remove(700);
        assertGift(gift(FREE_WRAP_SPRITE, true, "no wrap at all"));
    }

    @Test
    void aGiftIsRefusedWhileTheHotelClosesOrAnotherPurchaseRunsOrTooSoon() {
        ShutdownEmulator.timestamp = NOW + 300;
        assertGift(gift(FREE_WRAP_SPRITE, true, "closing"));

        reset();
        ShutdownEmulator.timestamp = 0;
        senderStats.isPurchasingFurniture = true;
        assertGift(gift(FREE_WRAP_SPRITE, true, "busy"));

        reset();
        senderStats.lastGiftTimestamp = NOW;
        assertGift(gift(FREE_WRAP_SPRITE, true, "too soon"));
    }

    @Test
    void aClubPageGiftGoesToTheClubOffer() throws Exception {
        CatalogPage clubPage = mock(ClubBuyLayout.class);
        when(clubPage.isEnabled()).thenReturn(true);
        when(clubPage.isVisible()).thenReturn(true);
        pages.put(11, clubPage);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "unknown offer"));

        reset();
        when(clubPage.isEnabled()).thenReturn(false);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "closed page"));
    }

    @Test
    void aClubGiftExtendsTheRecipientsClub() throws Exception {
        clubPage(11, ClubBuyLayout.class);
        clubOffer(404, ClubOffer.WINDOW_HABBO_CLUB, false, false);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "online"));

        reset();
        recipientOnline = false;
        offlineSubscriptionActive = true;
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "offline, a running club"));

        reset();
        offlineSubscriptionActive = false;
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "offline, no club"));

        reset();
        recipientOnline = true;
        subscriptionCreated = false;
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "not extended"));

        reset();
        subscriptionCreated = true;
        permissions.add(Permission.ACC_INFINITE_CREDITS);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "infinite credits"));

        reset();
        permissions.clear();
        clubPage(12, BuildersClubFrontPageLayout.class);
        clubOffer(405, ClubOffer.WINDOW_BUILDERS_CLUB, true, false);
        assertGift(giftOf(12, 405, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "builders club"));
    }

    @Test
    void aClubGiftIsRefused() throws Exception {
        clubPage(11, ClubBuyLayout.class);
        ClubOffer offer = clubOffer(404, ClubOffer.WINDOW_HABBO_CLUB, false, false);
        when(offer.isGiftable()).thenReturn(false);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "not giftable"));

        reset();
        clubPage(13, BuildersClubAddonsLayout.class);
        clubOffer(406, ClubOffer.WINDOW_BUILDERS_CLUB_ADDONS, false, true);
        assertGift(giftOf(13, 406, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "builders club addon"));

        reset();
        clubOffer(407, ClubOffer.WINDOW_BUILDERS_CLUB, true, false);
        assertGift(giftOf(11, 407, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "offer of another window"));

        reset();
        offer = clubOffer(404, ClubOffer.WINDOW_HABBO_CLUB, false, false);
        when(offer.getCredits()).thenReturn(5000);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "not enough credits"));

        reset();
        when(offer.getCredits()).thenReturn(-1);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "negative price"));

        reset();
        when(offer.getCredits()).thenReturn(25);
        recipientOnline = false;
        recipientInDatabase = false;
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "nobody"));

        reset();
        recipientOnline = true;
        when(recipientInfo.getId()).thenReturn(SENDER_ID);
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "to oneself"));

        reset();
        when(recipientInfo.getId()).thenReturn(RECIPIENT_ID);
        tryTakeResult = false;
        assertGift(giftOf(11, 404, FREE_WRAP_SPRITE, COLOR, RIBBON, true, "payment refused"));
    }

    @Test
    void aLimitedGiftWhoseLastNumberIsTakenMeanwhile() {
        makeLimited();
        when(limitedConfiguration.getNumber()).thenThrow(new NoSuchElementException());

        assertGift(gift(FREE_WRAP_SPRITE, true, "taken"));
    }

    private void clubPage(int pageId, Class<? extends CatalogPage> layout) {
        CatalogPage clubPage = mock(layout);
        when(clubPage.isEnabled()).thenReturn(true);
        when(clubPage.isVisible()).thenReturn(true);
        pages.put(pageId, clubPage);
    }

    private ClubOffer clubOffer(int offerId, int windowId, boolean buildersClub, boolean addon) {
        ClubOffer offer = mock(ClubOffer.class, withSettings().name("clubOffer" + offerId));
        when(offer.belongsToWindow(anyInt())).thenAnswer(call -> call.<Integer>getArgument(0) == windowId);
        when(offer.isGiftable()).thenReturn(true);
        when(offer.isBuildersClubSubscription()).thenReturn(buildersClub);
        when(offer.isBuildersClubAddon()).thenReturn(addon);
        when(offer.getCredits()).thenReturn(25);
        when(offer.getPoints()).thenReturn(0);
        when(offer.getPointsType()).thenReturn(0);
        when(offer.getDays()).thenReturn(31);
        clubOffers.put(offerId, offer);
        return offer;
    }

    // ---------------------------------------------------------------- the driver

    private String[] gift(int sprite, boolean showName, String message) {
        return giftOf(PAGE_ID, ITEM_ID, sprite, COLOR, RIBBON, showName, message);
    }

    private String[] giftWithExtraData(String extraData, int sprite, boolean showName, String message) {
        return run(packet(PAGE_ID, ITEM_ID, extraData, "friend", message, sprite, COLOR, RIBBON, showName));
    }

    private String[] giftOf(
            int pageId, int itemId, int sprite, int color, int ribbon, boolean showName, String message) {
        return run(packet(pageId, itemId, "", "friend", message, sprite, color, ribbon, showName));
    }

    private String[] run(ClientMessage packet) {
        CatalogBuyItemAsGiftEvent event = new CatalogBuyItemAsGiftEvent();
        event.client = client;
        event.packet = packet;

        recording = true;
        try {
            event.handle();
        } catch (Exception e) {
            trace.add("handle threw " + e);
        } finally {
            recording = false;
        }

        trace.add(
                "stats purchasing=" + senderStats.isPurchasingFurniture + " lastGift=" + senderStats.lastGiftTimestamp);

        Calendar now = Calendar.getInstance();
        String today =
                now.get(Calendar.DAY_OF_MONTH) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.YEAR);

        return inSetOrder(
                trace.stream().map(line -> line.replace(today, "<today>")).toList());
    }

    /**
     * The handler keeps the created items in a HashSet, whose order is not part of its behaviour: the item ids in
     * the gift data and runs of the same call over that set are put in a fixed order.
     */
    private static String[] inSetOrder(List<String> lines) {
        List<String> ordered = new ArrayList<>();

        for (int i = 0; i < lines.size(); ) {
            String line = lines.get(i);
            String call = line.contains("(") ? line.substring(0, line.indexOf('(')) : line;

            if (call.equals("limitedConfiguration.limitedSold") || call.equals("itemManager.deleteItem")) {
                int end = i;
                while (end < lines.size() && lines.get(end).startsWith(call + "(")) end++;
                ordered.addAll(lines.subList(i, end).stream().sorted().toList());
                i = end;
                continue;
            }

            if (call.equals("itemManager.createGift")) {
                String[] parts = line.split("\t", -1);
                String head = parts[0];
                int count = Integer.parseInt(head.substring(head.lastIndexOf(' ') + 1));
                String[] ids = Arrays.copyOfRange(parts, 1, 1 + count);
                Arrays.sort(ids);
                System.arraycopy(ids, 0, parts, 1, count);
                line = String.join("\t", parts);
            }

            ordered.add(line);
            i++;
        }

        return ordered.toArray(String[]::new);
    }

    private void reset() {
        trace.clear();
        resetStats();
    }

    private void resetStats() {
        senderStats.isPurchasingFurniture = false;
        senderStats.lastGiftTimestamp = 0;
        nextItemId = 1000;
        creations = 0;
    }

    private void makeLimited() {
        when(item.isLimited()).thenReturn(true);
        when(item.getLimitedStack()).thenReturn(50);
        when(item.getLimitedSells()).thenReturn(10);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.giftTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/catalog/gift-purchase-traces.txt");
    private static final Map<String, String> RECORDING = new ConcurrentSkipListMap<>();

    private int expectation = 0;

    /** Compares a scenario with its recorded trace; with -Dpolaris.giftTraces.update=true it records it instead. */
    private void assertGift(String[] actual) {
        String name = Thread.currentThread().getStackTrace()[2].getMethodName() + " #" + (expectation++);
        String trace = String.join("\n", actual);

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            RECORDING.put(name, trace);
            return;
        }

        assertEquals(expectedTraces().get(name), trace, name);
    }

    @AfterAll
    static void writeRecording() throws Exception {
        if (!Boolean.getBoolean(UPDATE_PROPERTY)) return;

        Map<String, String> traces = new TreeMap<>(Files.exists(TRACES) ? readTraces() : Map.of());
        traces.putAll(RECORDING);

        StringBuilder text = new StringBuilder();
        traces.forEach((name, lines) ->
                text.append("== ").append(name).append('\n').append(lines).append("\n\n"));
        Files.createDirectories(TRACES.getParent());
        Files.writeString(TRACES, text.toString());
    }

    private static Map<String, String> expectedTraces() {
        try {
            return readTraces();
        } catch (Exception e) {
            throw new IllegalStateException("Missing " + TRACES + "; record it with -D" + UPDATE_PROPERTY + "=true", e);
        }
    }

    private static Map<String, String> readTraces() throws Exception {
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
    }

    // ---------------------------------------------------------------- the fixtures

    private ClientMessage packet(
            int pageId,
            int itemId,
            String extraData,
            String username,
            String message,
            int sprite,
            int color,
            int ribbon,
            boolean showName) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(pageId);
        buffer.writeInt(itemId);
        writeString(buffer, extraData);
        writeString(buffer, username);
        writeString(buffer, message);
        buffer.writeInt(sprite);
        buffer.writeInt(color);
        buffer.writeInt(ribbon);
        buffer.writeByte(showName ? 1 : 0);
        return new ClientMessage(0, buffer);
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    private Item baseItem(int id, String name, FurnitureType type, Class<? extends HabboItem> interaction) {
        ItemInteraction itemInteraction = mock(ItemInteraction.class);
        when(itemInteraction.getType()).thenAnswer(call -> interaction);
        Item base = mock(Item.class, withSettings().name("item:" + name));
        when(base.getId()).thenReturn(id);
        when(base.getName()).thenReturn(name);
        when(base.getDisplayName()).thenReturn(name + " display");
        when(base.getType()).thenReturn(type);
        when(base.allowGift()).thenReturn(true);
        when(base.getInteractionType()).thenReturn(itemInteraction);
        items.put(id, base);
        return base;
    }

    private HabboItem habboItem(int id, Item base) {
        HabboItem created = mock(HabboItem.class, withSettings().name("habboItem" + id + ":" + base.getName()));
        when(created.getId()).thenReturn(id);
        when(created.getBaseItem()).thenReturn(base);
        return created;
    }

    private Achievement achievement(String name) {
        return mock(Achievement.class, withSettings().name("achievement:" + name));
    }

    private PreparedStatement statement(String sql) throws Exception {
        boolean wasRecording = recording;
        recording = false;

        try {
            PreparedStatement statement = traced(PreparedStatement.class, "statement");
            ResultSet set = mock(ResultSet.class);
            when(statement.executeQuery()).thenReturn(set);

            if (sql.startsWith("SELECT id FROM users")) {
                when(set.next()).thenReturn(recipientInDatabase);
                when(set.getInt(1)).thenReturn(OFFLINE_ID);
            } else if (sql.startsWith("SELECT COUNT(*)")) {
                when(set.next()).thenReturn(true);
                when(set.getInt("c")).thenReturn(recipientBadgeCount);
            } else if (sql.startsWith("SELECT id, duration FROM users_subscriptions")) {
                when(set.next()).thenReturn(offlineSubscriptionActive);
                when(set.getInt("id")).thenReturn(31);
                when(set.getInt("duration")).thenReturn(86_400);
            }

            return statement;
        } finally {
            recording = wasRecording;
        }
    }

    private <T> T traced(Class<T> type, String name) {
        return mock(type, withSettings().name(name).invocationListeners(listener));
    }

    private static final Set<String> RECORDED = Set.of(
            "sendResponse",
            "createItem",
            "createGift",
            "deleteItem",
            "insertTeleportPair",
            "insertHopper",
            "limitedSold",
            "addLtdLog",
            "alert",
            "addItem",
            "addBadge",
            "prepareStatement",
            "setInt",
            "setString",
            "execute",
            "executeQuery",
            "executeUpdate",
            "createSubscription",
            "run",
            "createOrUpdateLimitedConfig",
            "getNumber",
            "restoreNumber",
            "setExtradata",
            "needsUpdate",
            "close");

    private void record(Invocation invocation) {
        if (!recording) return;

        String method = invocation.getMethod().getName();
        if (!RECORDED.contains(method)) return;

        String target = mockingDetails(invocation.getMock())
                .getMockCreationSettings()
                .getMockName()
                .toString();
        if (method.equals("close") && !target.equals("connection")) return;

        String arguments =
                Arrays.stream(invocation.getArguments()).map(this::render).collect(Collectors.joining(", "));
        trace.add(target + "." + method + "(" + arguments + ")");
    }

    private String render(Object value) {
        if (value == null) return "null";
        if (mockingDetails(value).isMock()) {
            return mockingDetails(value).getMockCreationSettings().getMockName().toString();
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return new TreeMap<>(map).toString();
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::render).collect(Collectors.joining(", ", "[", "]"));
        }
        if (value instanceof ServerMessage message) {
            return "composed " + render(message.getComposer());
        }

        StringBuilder text = new StringBuilder(value.getClass().getSimpleName()).append('{');
        boolean first = true;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            try {
                if (!first) text.append(", ");
                text.append(field.getName()).append('=').append(render(field.get(value)));
                first = false;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return text.append('}').toString();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = CatalogManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
