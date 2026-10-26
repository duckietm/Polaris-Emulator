package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPaymentService;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import com.eu.habbo.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Records what {@link CatalogBuyClubDiscountEvent#handle} does with one purchase packet from the club extend window:
 * which offer is accepted and when, how the price is checked and taken, and what the buyer is told. The traces live in
 * {@code catalog/club-discount-traces.txt}; regenerate them with {@code -Dpolaris.clubDiscountTraces.update=true}.
 */
class CatalogBuyClubDiscountEventBehaviourTest {
    private static final int DEAL_ID = 40;
    private static final int REGULAR_ID = 41;
    private static final int OTHER_ID = 42;
    private static final int DAY = 86400;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private final Map<Integer, ClubOffer> clubOffers = new HashMap<>();
    private boolean recording;
    private boolean discountEnabled;
    private int discountDays;

    // the world of one scenario, reset by fresh()
    private Integer remaining;
    private int credits;
    private int points;
    private boolean infiniteCredits;
    private boolean infinitePoints;
    private boolean subscriptionCreated;
    private boolean paymentTaken;

    private GameClient client;
    private Habbo habbo;

    @BeforeEach
    void setUp() throws Exception {
        this.discountEnabled = SubscriptionHabboClub.DISCOUNT_ENABLED;
        this.discountDays = SubscriptionHabboClub.DISCOUNT_DAYS_BEFORE_END;
        SubscriptionHabboClub.DISCOUNT_ENABLED = true;
        SubscriptionHabboClub.DISCOUNT_DAYS_BEFORE_END = 7;

        CatalogManager catalog = mock(CatalogManager.class);
        Field offers = CatalogManager.class.getDeclaredField("clubOffers");
        offers.setAccessible(true);
        offers.set(catalog, this.clubOffers);
        when(catalog.getClubOffers()).thenCallRealMethod();
        when(catalog.getClubOffers(anyInt())).thenCallRealMethod();
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getCatalogManager()).thenReturn(catalog);

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        this.statics.add(emulator);

        MockedStatic<CatalogPaymentService> payment = mockStatic(CatalogPaymentService.class);
        payment.when(() -> CatalogPaymentService.tryTake(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> {
                    this.record("payment.tryTake(" + i.getArgument(1) + " credits, " + i.getArgument(3) + " of "
                            + i.getArgument(2) + ") -> " + this.paymentTaken);
                    return this.paymentTaken;
                });
        payment.when(() -> CatalogPaymentService.refund(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> this.record("payment.refund(" + i.getArgument(1) + " credits, " + i.getArgument(3)
                        + " of " + i.getArgument(2) + ")"));
        this.statics.add(payment);

        this.composer(AlertPurchaseFailedComposer.class, "AlertPurchaseFailed");
        this.composer(AlertPurchaseUnavailableComposer.class, "AlertPurchaseUnavailable");
        this.composer(NotEnoughPointsTypeComposer.class, "NotEnoughPointsType");
        this.composer(PurchaseOKComposer.class, "PurchaseOK");
        this.composer(InventoryRefreshComposer.class, "InventoryRefresh");
    }

    @AfterEach
    void tearDown() {
        SubscriptionHabboClub.DISCOUNT_ENABLED = this.discountEnabled;
        SubscriptionHabboClub.DISCOUNT_DAYS_BEFORE_END = this.discountDays;
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- which offer is accepted, and when

    @Test
    void offers() throws Exception {
        this.fresh(3 * DAY);
        this.buy("offer / the deal inside its window", DEAL_ID);

        this.fresh(3600);
        this.buy("offer / the deal in the last hour counts as one day", DEAL_ID);

        this.fresh(40 * DAY);
        this.buy("offer / the deal outside its window", DEAL_ID);

        this.fresh(null);
        this.buy("offer / the deal without a subscription", DEAL_ID);

        this.fresh(3 * DAY);
        SubscriptionHabboClub.DISCOUNT_ENABLED = false;
        this.buy("offer / the deal while discounts are off", DEAL_ID);

        this.fresh(null);
        this.buy("offer / the regular offer at full price", REGULAR_ID);

        this.fresh(3 * DAY);
        this.buy("offer / an offer outside the club", OTHER_ID);

        this.fresh(3 * DAY);
        this.buy("offer / an unknown offer", 999);

        this.fresh(3 * DAY);
        this.clubOffers.remove(REGULAR_ID);
        this.buy("offer / the deal without a regular offer of its length", DEAL_ID);
    }

    // ---------------------------------------------------------------- the price

    @Test
    void price() throws Exception {
        this.fresh(3 * DAY);
        this.credits = 10;
        this.buy("price / not enough credits", DEAL_ID);

        this.fresh(3 * DAY);
        this.points = 1;
        this.buy("price / not enough points", DEAL_ID);

        this.fresh(3 * DAY);
        this.infiniteCredits = true;
        this.infinitePoints = true;
        this.buy("price / staff with infinite credits and points", DEAL_ID);

        this.fresh(3 * DAY);
        this.paymentTaken = false;
        this.buy("price / the payment cannot be taken", DEAL_ID);

        this.fresh(3 * DAY);
        this.offer(DEAL_ID, true, true, 0, 20, 5);
        this.offer(REGULAR_ID, false, true, 0, 30, 0);
        this.buy("price / an offer of no days", DEAL_ID);

        this.fresh(3 * DAY);
        this.offer(DEAL_ID, true, true, 31, -20, 5);
        this.buy("price / a negative price", DEAL_ID);

        this.fresh(3 * DAY);
        this.subscriptionCreated = false;
        this.buy("price / the subscription is not created", DEAL_ID);
    }

    // ---------------------------------------------------------------- the harness

    private void fresh(Integer remainingSeconds) {
        this.trace.clear();
        this.recording = false;
        this.remaining = remainingSeconds;
        this.credits = 500;
        this.points = 100;
        this.infiniteCredits = false;
        this.infinitePoints = false;
        this.subscriptionCreated = true;
        this.paymentTaken = true;
        SubscriptionHabboClub.DISCOUNT_ENABLED = true;

        this.clubOffers.clear();
        this.offer(DEAL_ID, true, true, 31, 20, 5);
        this.offer(REGULAR_ID, false, true, 31, 30, 0);
        this.offer(OTHER_ID, false, false, 31, 15, 0);

        Subscription subscription = mock(Subscription.class, withSettings().name("hc"));
        when(subscription.getRemaining()).thenAnswer(i -> this.remaining);

        HabboStats stats = mock(HabboStats.class);
        when(stats.getSubscription(SubscriptionHabboClub.HABBO_CLUB))
                .thenAnswer(i -> this.remaining == null ? null : subscription);
        when(stats.createSubscription(anyString(), anyInt())).thenAnswer(i -> {
            this.record("stats.createSubscription(" + i.getArgument(0) + ", " + i.getArgument(1) + "s) -> "
                    + (this.subscriptionCreated ? "hc" : "null"));
            return this.subscriptionCreated ? subscription : null;
        });
        doAnswer(i -> this.record("stats.run()")).when(stats).run();

        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(7);
        when(info.getCredits()).thenAnswer(i -> this.credits);
        when(info.getCurrencyAmount(anyInt())).thenAnswer(i -> this.points);

        this.habbo = mock(Habbo.class, withSettings().name("ann"));
        when(this.habbo.getHabboInfo()).thenReturn(info);
        when(this.habbo.getHabboStats()).thenReturn(stats);
        when(this.habbo.hasPermission(eq(Permission.ACC_INFINITE_CREDITS))).thenAnswer(i -> this.infiniteCredits);
        when(this.habbo.hasPermission(eq(Permission.ACC_INFINITE_POINTS))).thenAnswer(i -> this.infinitePoints);
        doAnswer(i -> this.record("ann.giveCredits(" + i.getArgument(0) + ")"))
                .when(this.habbo)
                .giveCredits(anyInt());
        doAnswer(i -> this.record("ann.givePoints(" + i.getArgument(1) + " of " + i.getArgument(0) + ")"))
                .when(this.habbo)
                .givePoints(anyInt(), anyInt());

        this.client = mock(GameClient.class);
        when(this.client.getHabbo()).thenReturn(this.habbo);
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(MessageComposer.class));
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(ServerMessage.class));
    }

    private void offer(int id, boolean deal, boolean habboClub, int days, int credits, int points) {
        ClubOffer offer = mock(ClubOffer.class, withSettings().name("offer" + id));
        when(offer.getId()).thenReturn(id);
        when(offer.isDeal()).thenReturn(deal);
        when(offer.isHabboClubOffer()).thenReturn(habboClub);
        when(offer.belongsToWindow(anyInt()))
                .thenAnswer(i -> habboClub && i.<Integer>getArgument(0) == ClubOffer.WINDOW_HABBO_CLUB);
        when(offer.getDays()).thenReturn(days);
        when(offer.getCredits()).thenReturn(credits);
        when(offer.getPoints()).thenReturn(points);
        when(offer.getPointsType()).thenReturn(5);
        this.clubOffers.put(id, offer);
    }

    private void buy(String name, int offerId) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(offerId);
        CatalogBuyClubDiscountEvent handler = new CatalogBuyClubDiscountEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.CatalogBuyClubDiscountEvent, buffer);

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        this.recording = false;

        this.check(name, String.join("\n", this.trace));
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
        if (mockingDetails(value).isMock()) {
            return mockingDetails(value).getMockCreationSettings().getMockName().toString();
        }
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.clubDiscountTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/catalog/club-discount-traces.txt");
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
