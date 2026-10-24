package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPaymentService;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.catalog.LtdRaffleManager;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubAddonsLayout;
import com.eu.habbo.habbohotel.catalog.layouts.BuildersClubFrontPageLayout;
import com.eu.habbo.habbohotel.catalog.layouts.ClubBuyLayout;
import com.eu.habbo.habbohotel.catalog.layouts.RecentPurchasesLayout;
import com.eu.habbo.habbohotel.catalog.layouts.RoomBundleLayout;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.permissions.RankLimits;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.habbohotel.users.inventory.BotsComponent;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.habbohotel.users.inventory.PetsComponent;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubFurniCountComposer;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.navigator.CanCreateRoomComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.threading.ThreadPooling;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Records what {@link CatalogPurchaseApplicationService#purchase} does with one purchase request: the inventory and
 * page gates, a room bundle, a club or Builders Club offer, and an ordinary offer up to the hand-over to
 * {@code CatalogManager.purchaseItem} or the limited raffle. The traces live in
 * {@code catalog/purchase-service-traces.txt}; regenerate them with {@code -Dpolaris.purchaseServiceTraces.update=true}.
 */
class CatalogPurchaseApplicationServiceBehaviourTest {
    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private final Set<String> permissions = new LinkedHashSet<>();
    private final Set<String> ownedBadges = new LinkedHashSet<>();
    private boolean recording;

    private CatalogPage page;
    private int roomsOwned;
    private int roomLimit = 25;
    private boolean paymentAccepted = true;
    private boolean raffleTakesIt;
    private boolean subscriptionCreated = true;
    private int inventoryItems;
    private boolean inventoryThrows;
    private int bots;
    private int pets;
    private int credits = 1000;
    private int points = 100;
    private CatalogItem recentPurchase;
    private CatalogPage recentOfferPage;

    private GameClient client;
    private Habbo habbo;
    private HabboStats stats;
    private CatalogManager catalog;
    private RoomManager rooms;
    private CatalogPurchaseApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        this.statics.add(emulator);
        CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM = 3;
        CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM = 15;

        MockedStatic<CatalogPaymentService> payment = mockStatic(CatalogPaymentService.class);
        payment.when(() -> CatalogPaymentService.tryTake(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> {
                    this.record("CatalogPaymentService.tryTake(credits " + i.getArgument(1) + ", type "
                            + i.getArgument(2) + ", points " + i.getArgument(3) + ") -> " + this.paymentAccepted);
                    return this.paymentAccepted;
                });
        payment.when(() -> CatalogPaymentService.refund(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> this.record("CatalogPaymentService.refund(credits " + i.getArgument(1) + ", type "
                        + i.getArgument(2) + ", points " + i.getArgument(3) + ")"));
        this.statics.add(payment);
        MockedStatic<BuildersClubRoomSupport> builders = mockStatic(BuildersClubRoomSupport.class);
        builders.when(() -> BuildersClubRoomSupport.raisePackageFurniLimit(any(), anyInt()))
                .thenAnswer(
                        i -> this.record("BuildersClubRoomSupport.raisePackageFurniLimit(" + i.getArgument(1) + ")"));
        builders.when(() -> BuildersClubRoomSupport.getTrackedFurniCount(anyInt()))
                .thenReturn(3);
        this.statics.add(builders);
        MockedStatic<LtdRaffleManager> raffle = mockStatic(LtdRaffleManager.class);
        raffle.when(() -> LtdRaffleManager.enter(any(), any(), any(), any())).thenAnswer(i -> {
            this.record("LtdRaffleManager.enter(" + this.label(i.getArgument(2)) + ") -> " + this.raffleTakesIt);
            return this.raffleTakesIt;
        });
        this.statics.add(raffle);
        MockedStatic<RankLimits> limits = mockStatic(RankLimits.class);
        limits.when(() -> RankLimits.rooms(any())).thenAnswer(i -> this.roomLimit);
        this.statics.add(limits);

        this.composer(AlertPurchaseFailedComposer.class, "AlertPurchaseFailed");
        this.composer(AlertPurchaseUnavailableComposer.class, "AlertPurchaseUnavailable");
        this.composer(BuildersClubFurniCountComposer.class, "BuildersClubFurniCount");
        this.composer(BuildersClubSubscriptionStatusComposer.class, "BuildersClubSubscriptionStatus");
        this.composer(NotEnoughPointsTypeComposer.class, "NotEnoughPointsType");
        this.composer(PurchaseOKComposer.class, "PurchaseOK");
        this.composer(BubbleAlertComposer.class, "BubbleAlert");
        this.composer(InventoryRefreshComposer.class, "InventoryRefresh");
        this.composer(CanCreateRoomComposer.class, "CanCreateRoom");
        this.composer(AddUserBadgeComposer.class, "AddUserBadge");
        this.constructions.add(mockConstruction(
                CatalogPurchasePageResolver.class,
                (resolver, context) -> when(resolver.resolve(any(), any())).thenAnswer(i -> this.page)));

        this.catalog = mock(CatalogManager.class);
        setField(CatalogManager.class, this.catalog, "clubOffers", new LinkedHashMap<Integer, ClubOffer>());
        // Buying again from "recent purchases" re-reads the live offer and the page that
        // sells it, instead of trusting the instance the buyer's session remembers.
        when(this.catalog.getCatalogItem(anyInt()))
                .thenAnswer(i -> this.recentPurchase != null && this.recentPurchase.getId() == i.<Integer>getArgument(0)
                        ? this.recentPurchase
                        : null);
        when(this.catalog.getCatalogPage(anyInt())).thenAnswer(i -> this.recentOfferPage);
        doAnswer(i -> this.record("catalog.purchaseItem(" + this.label(i.getArgument(0)) + ", "
                        + this.label(i.getArgument(1)) + ", count " + i.getArgument(3) + ", \""
                        + String.valueOf((Object) i.getArgument(4)).replace("\n", "\\n") + "\", free "
                        + i.getArgument(5) + ")"))
                .when(this.catalog)
                .purchaseItem(any(), any(), any(), anyInt(), any(), anyBoolean());
        this.rooms = mock(RoomManager.class);
        when(this.rooms.getRoomsForHabbo(any(Habbo.class))).thenAnswer(i -> {
            List<Room> owned = new ArrayList<>();
            for (int n = 0; n < this.roomsOwned; n++) owned.add(mock(Room.class));
            return owned;
        });
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getCatalogManager()).thenReturn(this.catalog);
        when(environment.getRoomManager()).thenReturn(this.rooms);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> "<" + i.getArgument(0) + " %amount%>");
        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class)))
                .thenAnswer(i -> this.record("threading.run(" + this.label(i.getArgument(0)) + ")"));

        this.client = this.buyer();
        this.service = new CatalogPurchaseApplicationService(this.client, environment, texts, threading);
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- gates

    @Test
    void gates() {
        this.page = this.normalPage(0, this.offer(10, "chair", 5, 0, base("chair", FurnitureType.FLOOR)));
        this.inventoryItems = HabboInventory.MAXIMUM_ITEMS + 1;
        this.buy("gate / inventory full", 5, 10, "", 1);
        this.inventoryItems = 0;

        this.inventoryThrows = true;
        this.buy("gate / inventory check fails", 5, 10, "", 1);
        this.inventoryThrows = false;

        this.page = null;
        this.buy("gate / no page", 5, 10, "", 1);

        this.page = this.normalPage(9, this.offer(10, "chair", 5, 0, base("chair", FurnitureType.FLOOR)));
        this.buy("gate / page above the buyer's rank", 5, 10, "", 1);
    }

    // ---------------------------------------------------------------- room bundles

    @Test
    void roomBundles() {
        CatalogItem bundle = this.offer(
                20, "room_bundle", 50, 5, base("ACH_ROOM", FurnitureType.BADGE), base("sofa", FurnitureType.FLOOR));
        this.page = this.roomBundlePage(bundle);
        this.buy("room bundle / bought", 7, 20, "", 1);

        this.ownedBadges.add("ACH_ROOM");
        this.buy("room bundle / badge already owned", 7, 20, "", 1);
        this.ownedBadges.clear();

        this.permissions.add("acc_infinite_credits");
        this.permissions.add("acc_infinite_points");
        this.buy("room bundle / infinite credits and points", 7, 20, "", 1);
        this.permissions.clear();

        this.credits = 10;
        this.buy("room bundle / not enough credits", 7, 20, "", 1);
        this.credits = 1000;
        this.points = 1;
        this.buy("room bundle / not enough points", 7, 20, "", 1);
        this.points = 100;

        this.roomsOwned = 25;
        this.buy("room bundle / room limit reached", 7, 20, "", 1);
        this.roomsOwned = 0;

        this.page = this.roomBundlePage(this.offer(21, "bad_bundle", -5, 0, base("sofa", FurnitureType.FLOOR)));
        this.buy("room bundle / negative price", 7, 21, "", 1);

        this.page = this.roomBundlePage(null);
        this.buy("room bundle / no offer on the page", 7, 21, "", 1);

        this.page = this.roomBundlePage(bundle);
        this.paymentAccepted = false;
        this.buy("room bundle / payment refused", 7, 20, "", 1);
        this.paymentAccepted = true;

        this.stats.isPurchasingFurniture = true;
        this.buy("room bundle / a purchase already running", 7, 20, "", 1);
        this.stats.isPurchasingFurniture = false;

        doThrow(new IllegalStateException("room creation failed"))
                .when((RoomBundleLayout) this.page)
                .buyRoom(any(Habbo.class));
        this.buy("room bundle / the room cannot be created", 7, 20, "", 1);
    }

    // ---------------------------------------------------------------- club and builders club

    @Test
    void clubOffers() throws Exception {
        this.page = this.clubPage(ClubBuyLayout.class);
        this.clubOffer(30, 31, 25, 0, 5, ClubOffer.WINDOW_HABBO_CLUB, false, false);
        this.buy("club / habbo club bought", 8, 30, "", 1);
        this.buy("club / habbo club for 3 periods", 8, 30, "", 3);
        this.buy("club / unknown offer", 8, 99, "", 1);

        this.clubOffer(31, 31, 25, 0, 5, ClubOffer.WINDOW_BUILDERS_CLUB, true, false);
        this.buy("club / offer of another window", 8, 31, "", 1);

        this.clubOffer(32, 0, 25, 0, 5, ClubOffer.WINDOW_HABBO_CLUB, false, false);
        this.buy("club / offer of zero days", 8, 32, "", 1);

        this.credits = 10;
        this.buy("club / not enough credits", 8, 30, "", 1);
        this.credits = 1000;
        this.clubOffer(33, 31, 0, 500, 5, ClubOffer.WINDOW_HABBO_CLUB, false, false);
        this.buy("club / not enough points", 8, 33, "", 1);

        this.paymentAccepted = false;
        this.buy("club / payment refused", 8, 30, "", 1);
        this.paymentAccepted = true;

        this.subscriptionCreated = false;
        this.buy("club / subscription not created", 8, 30, "", 1);
        this.subscriptionCreated = true;

        this.buy("club / price overflow", 8, 30, "", Integer.MAX_VALUE);

        this.stats.isPurchasingFurniture = true;
        this.buy("club / a purchase already running", 8, 30, "", 1);
        this.stats.isPurchasingFurniture = false;

        this.page = this.clubPage(BuildersClubFrontPageLayout.class);
        this.clubOffer(34, 31, 25, 0, 5, ClubOffer.WINDOW_BUILDERS_CLUB, true, false);
        this.buy("builders club / subscription", 8, 34, "", 1);

        this.page = this.clubPage(BuildersClubAddonsLayout.class);
        this.clubOffer(35, 50, 25, 0, 5, ClubOffer.WINDOW_BUILDERS_CLUB_ADDONS, false, true);
        this.buy("builders club / furni addon", 8, 35, "", 1);
    }

    // ---------------------------------------------------------------- ordinary offers

    @Test
    void ordinaryOffers() {
        CatalogItem chair = this.offer(40, "chair", 5, 0, base("chair", FurnitureType.FLOOR));
        this.page = this.normalPage(0, chair);
        this.buy("offer / handed to the catalog", 5, 40, "extra", 2);
        this.buy("offer / found by its offer id", 5, 4040, "", 1);
        this.buy("offer / not on the page", 5, 77, "", 1);

        this.recentPurchase = chair;
        this.recentOfferPage = this.normalPage(0, chair);
        this.page = this.recentPage();
        this.buy("offer / from recent purchases", 5, 40, "", 1);

        // The page that sells the offer is re-checked, not the recent-purchases page the
        // buyer is looking at: a page since disabled, or above their rank, refuses.
        CatalogPage disabled = this.normalPage(0, chair);
        when(disabled.isEnabled()).thenReturn(false);
        this.recentOfferPage = disabled;
        this.buy("offer / from recent purchases, page since disabled", 5, 40, "", 1);

        CatalogPage staffOnly = this.normalPage(7, chair);
        this.recentOfferPage = staffOnly;
        this.buy("offer / from recent purchases, page above the buyer's rank", 5, 40, "", 1);

        this.recentOfferPage = null;
        this.buy("offer / from recent purchases, offer no longer in the catalog", 5, 40, "", 1);

        this.page = this.normalPage(0, chair);
        this.raffleTakesIt = true;
        this.buy("offer / taken by the limited raffle", 5, 40, "", 1);
        this.raffleTakesIt = false;

        CatalogItem bot = this.offer(41, "bot_generic", 5, 0, base("bot_generic", FurnitureType.FLOOR));
        this.page = this.normalPage(0, bot);
        this.bots = BotManager.MAXIMUM_BOT_INVENTORY_SIZE;
        this.buy("offer / bot inventory full", 5, 41, "", 1);
        this.permissions.add("acc_unlimited_bots");
        this.buy("offer / bot inventory full, unlimited bots", 5, 41, "", 1);
        this.permissions.clear();
        this.bots = 0;

        CatalogItem pet = this.offer(42, "a0 pet1", 5, 0, base("a0 pet1", FurnitureType.FLOOR));
        this.page = this.normalPage(0, pet);
        this.buy("offer / pet", 5, 42, "Rex\n1\nFFFFFF", 1);
        this.buy("offer / pet without race and colour", 5, 42, "Rex", 1);
        this.buy("offer / pet with a short name", 5, 42, "Re\n1\nFFFFFF", 1);
        this.buy("offer / pet with symbols in its name", 5, 42, "Rex!\n1\nFFFFFF", 1);
        this.pets = PetManager.MAXIMUM_PET_INVENTORY_SIZE;
        this.buy("offer / pet inventory full", 5, 42, "Rex\n1\nFFFFFF", 1);
        this.pets = 0;
    }

    // ---------------------------------------------------------------- the driver

    private void buy(String label, int pageId, int itemId, String extraData, int count) {
        this.trace.clear();
        this.recording = true;
        try {
            this.service.purchase(new CatalogPurchaseCommand(pageId, itemId, extraData, count));
            this.trace.add("returned");
        } catch (Exception e) {
            this.trace.add("threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            this.recording = false;
        }
        this.trace.add("purchasing flag " + this.stats.isPurchasingFurniture);
        this.check(label, String.join("\n", this.trace));
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
        if (value instanceof Map<?, ?> map) {
            return new TreeMap<>(map.entrySet().stream()
                            .collect(Collectors.toMap(
                                    e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue()))))
                    .toString();
        }
        if (value instanceof com.eu.habbo.habbohotel.users.HabboBadge badge) return "badge " + badge.getCode();
        if (value instanceof Number || value instanceof String || value instanceof Boolean)
            return String.valueOf(value);
        if (org.mockito.Mockito.mockingDetails(value).isMock()) {
            String name = org.mockito.Mockito.mockingDetails(value)
                    .getMockCreationSettings()
                    .getMockName()
                    .toString();
            if (!name.startsWith("Mock for")) return name;
        }
        return value.getClass().getSimpleName().replaceAll("\\$MockitoMock\\$.*", "");
    }

    private CatalogItem offer(int id, String name, int credits, int points, Item... baseItems) {
        CatalogItem item = mock(CatalogItem.class, withSettings().name("offer " + name));
        when(item.getId()).thenReturn(id);
        when(item.getOfferId()).thenReturn(id * 101);
        when(item.getName()).thenReturn(name);
        when(item.getCredits()).thenReturn(credits);
        when(item.getPoints()).thenReturn(points);
        when(item.getPointsType()).thenReturn(5);
        when(item.getBaseItems()).thenReturn(new LinkedHashSet<>(Arrays.asList(baseItems)));
        when(item.getPageId()).thenReturn(5);
        return item;
    }

    private static Item base(String name, FurnitureType type) {
        Item item = mock(Item.class, withSettings().name(name));
        when(item.getName()).thenReturn(name);
        when(item.getType()).thenReturn(type);
        return item;
    }

    private CatalogPage normalPage(int rank, CatalogItem... items) {
        CatalogPage page = mock(CatalogPage.class, withSettings().name("page"));
        this.fillPage(page, rank, items);
        return page;
    }

    private void fillPage(CatalogPage page, int rank, CatalogItem... items) {
        Int2ObjectOpenHashMap<CatalogItem> map = new Int2ObjectOpenHashMap<>();
        for (CatalogItem item : items) {
            if (item != null) map.put(item.getId(), item);
        }
        when(page.isEnabled()).thenReturn(true);
        when(page.getRank()).thenReturn(rank);
        when(page.getId()).thenReturn(5);
        when(page.getCaption()).thenReturn("Shop");
        when(page.getCatalogItems()).thenReturn(map);
        when(page.getCatalogItem(anyInt())).thenAnswer(i -> map.get(i.<Integer>getArgument(0)));
    }

    private CatalogPage roomBundlePage(CatalogItem item) {
        RoomBundleLayout page = mock(RoomBundleLayout.class, withSettings().name("room bundle page"));
        this.fillPage(page, 0, item);
        doAnswer(i -> this.record("page.buyRoom(buyer)")).when(page).buyRoom(any(Habbo.class));
        return page;
    }

    private CatalogPage clubPage(Class<? extends CatalogPage> type) {
        CatalogPage page = mock(type, withSettings().name(type.getSimpleName()));
        this.fillPage(page, 0);
        return page;
    }

    private CatalogPage recentPage() {
        RecentPurchasesLayout page =
                mock(RecentPurchasesLayout.class, withSettings().name("recent purchases page"));
        this.fillPage(page, 0);
        return page;
    }

    private void clubOffer(
            int id, int days, int credits, int points, int pointsType, int window, boolean builders, boolean addon) {
        ClubOffer offer = mock(ClubOffer.class, withSettings().name("club offer " + id));
        when(offer.getDays()).thenReturn(days);
        when(offer.getCredits()).thenReturn(credits);
        when(offer.getPoints()).thenReturn(points);
        when(offer.getPointsType()).thenReturn(pointsType);
        when(offer.belongsToWindow(anyInt())).thenAnswer(i -> i.<Integer>getArgument(0) == window);
        when(offer.isBuildersClubSubscription()).thenReturn(builders);
        when(offer.isBuildersClubAddon()).thenReturn(addon);
        this.catalog.clubOffers.put(id, offer);
    }

    private GameClient buyer() {
        GameClient client = mock(GameClient.class);
        this.habbo = mock(Habbo.class, withSettings().name("buyer"));
        HabboInfo info = mock(HabboInfo.class);
        this.stats = mock(HabboStats.class);
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(1);
        when(info.getId()).thenReturn(7);
        when(info.getRank()).thenReturn(rank);
        when(info.getCredits()).thenAnswer(i -> this.credits);
        when(info.getCurrencyAmount(anyInt())).thenAnswer(i -> this.points);
        when(this.stats.getRecentPurchase(anyInt())).thenAnswer(i -> this.recentPurchase);
        when(this.stats.createSubscription(anyString(), anyInt())).thenAnswer(i -> {
            this.record("stats.createSubscription(" + i.getArgument(0) + ", " + i.getArgument(1) + "s) -> "
                    + (this.subscriptionCreated ? "created" : "null"));
            return this.subscriptionCreated ? mock(Subscription.class) : null;
        });
        doAnswer(i -> this.record("stats.addBuildersClubBonusFurni(" + i.getArgument(0) + ")"))
                .when(this.stats)
                .addBuildersClubBonusFurni(anyInt());
        doAnswer(i -> this.record("stats.run()")).when(this.stats).run();

        HabboInventory inventory = mock(HabboInventory.class);
        ItemsComponent items = mock(ItemsComponent.class);
        when(items.itemCount()).thenAnswer(i -> this.inventoryItems);
        BadgesComponent badges = mock(BadgesComponent.class);
        when(badges.hasBadge(anyString())).thenAnswer(i -> this.ownedBadges.contains(i.<String>getArgument(0)));
        doAnswer(i -> this.record("inventory.addBadge(" + this.label(i.getArgument(0)) + ")"))
                .when(badges)
                .addBadge(any());
        BotsComponent botsComponent = mock(BotsComponent.class);
        when(botsComponent.getBots()).thenAnswer(i -> {
            Map<Integer, Bot> map = new HashMap<>();
            for (int n = 0; n < this.bots; n++) map.put(n, mock(Bot.class));
            return map;
        });
        PetsComponent petsComponent = mock(PetsComponent.class);
        when(petsComponent.getPets()).thenAnswer(i -> {
            Int2ObjectOpenHashMap<Pet> map = new Int2ObjectOpenHashMap<>();
            for (int n = 0; n < this.pets; n++) map.put(n, mock(Pet.class));
            return map;
        });
        when(inventory.getItemsComponent()).thenAnswer(i -> {
            if (this.inventoryThrows) throw new IllegalStateException("inventory not loaded");
            return items;
        });
        when(inventory.getBadgesComponent()).thenReturn(badges);
        when(inventory.getBotsComponent()).thenReturn(botsComponent);
        when(inventory.getPetsComponent()).thenReturn(petsComponent);

        when(this.habbo.getHabboInfo()).thenReturn(info);
        when(this.habbo.getHabboStats()).thenReturn(this.stats);
        when(this.habbo.getInventory()).thenReturn(inventory);
        when(this.habbo.hasPermission(anyString()))
                .thenAnswer(i -> this.permissions.contains(i.<String>getArgument(0)));
        doAnswer(i -> this.record("buyer alerted \"" + i.getArgument(0) + "\""))
                .when(this.habbo)
                .alert(anyString());
        doAnswer(i -> this.record("buyer.giveCredits(" + i.getArgument(0) + ")"))
                .when(this.habbo)
                .giveCredits(anyInt());
        doAnswer(i -> this.record("buyer.givePoints(type " + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(this.habbo)
                .givePoints(anyInt(), anyInt());
        when(client.getHabbo()).thenReturn(this.habbo);
        when(this.habbo.getClient()).thenReturn(client);
        doAnswer(i -> this.record("buyer <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(ServerMessage.class));
        doAnswer(i -> this.record("buyer <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(MessageComposer.class));
        return client;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.purchaseServiceTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/catalog/purchase-service-traces.txt");
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
