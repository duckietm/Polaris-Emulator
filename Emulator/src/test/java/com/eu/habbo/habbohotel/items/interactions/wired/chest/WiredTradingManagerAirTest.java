package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCancelledComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCompletedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeOpenComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionFailComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionSuccessComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * The AIR wired trade: the only way into a chest. Credit furni become a coin chest's coins, furni go
 * into a furni chest as themselves, a contract's payment lands in its chests and its reward comes out
 * of them -- and when anything cannot be met, everything on the table goes back.
 */
class WiredTradingManagerAirTest {
    private static final int PLAYER = 3;
    private static final int OWNER = 2;
    private static final int TROPHY = 900;
    private static final long LATER = 10_000L;

    private InMemoryChestStore store;
    private ChestStore previous;
    private MockedStatic<WiredManager> wired;
    private Room room;
    private Habbo habbo;
    private GameClient client;
    private final Map<Integer, HabboItem> inventory = new HashMap<>();

    @BeforeEach
    void setUp() {
        this.store = new InMemoryChestStore();
        this.previous = ChestStores.use(this.store);
        this.wired = mockStatic(WiredManager.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        this.wired.when(() -> WiredManager.dispatchEffectTriggeredEvent(any())).thenReturn(true);
        this.room = mock(Room.class);
        this.habbo = this.player();
    }

    @AfterEach
    void tearDown() {
        this.wired.close();
        ChestStores.use(this.previous);
    }

    private Habbo player() {
        Habbo player = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(PLAYER);
        when(info.getUsername()).thenReturn("Player");
        when(player.getHabboInfo()).thenReturn(info);
        this.client = mock(GameClient.class);
        when(player.getClient()).thenReturn(this.client);

        HabboInventory habboInventory = mock(HabboInventory.class);
        ItemsComponent items = mock(ItemsComponent.class);
        when(player.getInventory()).thenReturn(habboInventory);
        when(habboInventory.getItemsComponent()).thenReturn(items);
        when(items.getHabboItem(anyInt())).thenAnswer(call -> this.inventory.get(call.<Integer>getArgument(0)));
        when(items.takeHabboItemsAtomically(anyCollection())).thenAnswer(call -> {
            Collection<HabboItem> taken = call.getArgument(0);
            taken.forEach(item -> this.inventory.remove(item.getId()));
            return true;
        });
        doAnswer(call -> {
                    HabboItem item = call.getArgument(0);
                    this.inventory.put(item.getId(), item);
                    return null;
                })
                .when(items)
                .addItem(any(HabboItem.class));
        return player;
    }

    private HabboItem give(int id, String name, int baseId) {
        Item base = mock(Item.class);
        when(base.getName()).thenReturn(name);
        when(base.getId()).thenReturn(baseId);
        when(base.allowTrade()).thenReturn(true);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getExtradata()).thenReturn("0");
        this.inventory.put(id, item);
        this.store.giveInventoryItem(id, PLAYER, baseId);
        return item;
    }

    private static Item chestFurni() {
        Item base = mock(Item.class);
        ItemInteraction interaction = mock(ItemInteraction.class);
        doReturn(InteractionWiredChestFurni.class).when(interaction).getType();
        when(base.getInteractionType()).thenReturn(interaction);
        return base;
    }

    private static <T extends InteractionWiredChest> T wiredChest(T chest) {
        chest.getContents().enableWired();
        chest.getContents().setLocked(false);
        return chest;
    }

    private List<MessageComposer> sent() {
        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(this.client, atLeastOnce()).sendResponse(captor.capture());
        return captor.getAllValues();
    }

    private <T> List<T> sent(Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (MessageComposer composer : this.sent()) {
            if (type.isInstance(composer)) matches.add(type.cast(composer));
        }
        return matches;
    }

    private static int failureId(WiredTradeCancelledComposer composer) {
        try {
            java.lang.reflect.Field field = WiredTradeCancelledComposer.class.getDeclaredField("failureId");
            field.setAccessible(true);
            return field.getInt(composer);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private boolean acceptAndConfirm(WiredTradingManager manager) {
        WiredTradingSession session = manager.getSession(this.habbo);
        assertTrue(session.accept(0L), "the offer can be accepted");
        return manager.settle(this.habbo, LATER);
    }

    @Test
    void aCoinChestDepositRedeemsCreditFurniIntoCoins() {
        InteractionWiredChestCurrency chest = new InteractionWiredChestCurrency(10, OWNER, chestFurni(), "", 0, 0);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(101, "CF_50", 1);
        give(102, "CF_100", 2);
        give(103, "chair", 3);

        assertNotNull(manager.openDeposit(this.habbo, chest, 300));
        assertEquals(2, manager.offer(this.habbo, List.of(101, 102, 103), false));
        assertEquals(
                WiredTradeErrorComposer.INVALID_ITEM,
                this.sent(WiredTradeErrorComposer.class).get(0).getErrorId(),
                "a chair is no coin");

        assertTrue(this.acceptAndConfirm(manager));

        assertEquals(150, chest.getContents().getCoins());
        assertEquals(150, this.store.chests.get(10).coins());
        assertEquals(1, this.store.inventoryCount(PLAYER), "only the chair is left");
        assertTrue(this.inventory.containsKey(103), "the refused chair went back");
        assertEquals(1, this.sent(WiredTradeCompletedComposer.class).size());
        assertEquals(
                WiredTransactionSuccessComposer.TYPE_DEPOSIT,
                this.sent(WiredTransactionSuccessComposer.class).get(0).getType());
    }

    @Test
    void aFurniChestDepositMovesTheItemsThemselves() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(20, OWNER, chestFurni(), "", 0, 0);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(201, "chair", 3);
        give(202, "lamp", 4);

        manager.openDeposit(this.habbo, chest, 300);
        manager.offer(this.habbo, List.of(201, 202), false);
        assertTrue(this.acceptAndConfirm(manager));

        assertEquals(2, chest.getContents().furniItemCount());
        assertEquals(2, this.store.storedCount(20));
        assertEquals(0, this.store.inventoryCount(PLAYER));
        assertTrue(this.store.stored.containsKey(201), "the item keeps its id in the chest");
    }

    @Test
    void walkingAwayHandsEverythingBack() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(20, OWNER, chestFurni(), "", 0, 0);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(201, "chair", 3);

        manager.openDeposit(this.habbo, chest, 300);
        manager.offer(this.habbo, List.of(201), false);
        manager.cancel(this.habbo, WiredTradingSession.FAILURE_SILENT);

        assertTrue(this.inventory.containsKey(201));
        assertEquals(0, chest.getContents().furniItemCount());
        assertNull(manager.getSession(this.habbo));
    }

    private ContractRules thirtyCoinsForTrophies(int trophies) {
        return ContractRules.of(
                List.of(List.of(Term.currency(InteractionWiredContract.DIR_PAY, -1, 30))),
                List.of(Term.furni(InteractionWiredContract.DIR_RECEIVE, false, TROPHY, "", trophies)));
    }

    private InteractionWiredChestFurni trophyChest(int trophies) {
        InteractionWiredChestFurni chest =
                wiredChest(new InteractionWiredChestFurni(40, OWNER, chestFurni(), "", 0, 0));
        Item base = mock(Item.class);
        when(base.allowTrade()).thenReturn(true);
        when(base.getId()).thenReturn(TROPHY);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        List<HabboItem> stock = new ArrayList<>();
        for (int i = 0; i < trophies; i++) {
            HabboItem item = mock(HabboItem.class);
            when(item.getId()).thenReturn(500 + i);
            when(item.getBaseItem()).thenReturn(base);
            this.store.giveInventoryItem(500 + i, OWNER, TROPHY);
            stock.add(item);
        }
        assertEquals(trophies, chest.depositFurni(OWNER, stock).size());
        return chest;
    }

    @Test
    void aContractTradePaysIntoTheChestsAndRewardsOutOfThem() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(2);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_50", 1);

        assertNotNull(manager.open(
                this.habbo, this.thirtyCoinsForTrophies(1), 1, "Well done", "generic", 60, List.of(coins, trophies)));
        manager.offer(this.habbo, List.of(301), false);
        assertTrue(this.acceptAndConfirm(manager));

        assertEquals(50, coins.getContents().getCoins(), "the credit furni became the coin chest's coins");
        assertEquals(1, trophies.getContents().furniItemCount());
        assertEquals(1, this.store.inventoryCount(PLAYER), "the trophy is the player's now");
        List<WiredTransactionSuccessComposer> successes = this.sent(WiredTransactionSuccessComposer.class);
        assertEquals(
                WiredTransactionSuccessComposer.TYPE_REWARD, successes.get(0).getType());
        assertEquals(
                WiredTransactionSuccessComposer.TYPE_TRADE,
                successes.get(successes.size() - 1).getType());
    }

    @Test
    void aRewardTheChestsCannotPayRefusesAndReturnsThePayment() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(2);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_50", 1);

        manager.open(this.habbo, this.thirtyCoinsForTrophies(5), 1, "", "generic", 60, List.of(coins, trophies));
        manager.offer(this.habbo, List.of(301), false);
        assertFalse(this.acceptAndConfirm(manager));

        assertEquals(0, coins.getContents().getCoins());
        assertEquals(2, trophies.getContents().furniItemCount());
        assertTrue(this.inventory.containsKey(301), "the credit furni went back");
        assertEquals(1, this.store.inventoryCount(PLAYER));
        assertEquals(1, this.sent(WiredTradeCancelledComposer.class).size());
    }

    @Test
    void aContractWithoutAUsableChestDoesNotOpen() {
        InteractionWiredChestCurrency locked = new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0);
        locked.getContents().enableWired();
        WiredTradingManager manager = new WiredTradingManager(this.room);

        assertNull(manager.open(this.habbo, this.thirtyCoinsForTrophies(1), 1, "", "generic", 60, List.of(locked)));
        assertNull(manager.getSession(this.habbo));
        assertEquals(
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS,
                this.sent(WiredTransactionFailComposer.class).get(0).getReason());
    }

    @Test
    void notEnoughCoinsOnTheTableCannotBeAccepted() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(1);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_10", 1);

        manager.open(this.habbo, this.thirtyCoinsForTrophies(1), 1, "", "generic", 60, List.of(coins, trophies));
        manager.offer(this.habbo, List.of(301), false);

        assertFalse(manager.getSession(this.habbo).accept(0L));
        assertEquals(1, trophies.getContents().furniItemCount());
    }

    @Test
    void aDonationTakesEverythingOfferedAndCountsAsAPayment() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni furni =
                wiredChest(new InteractionWiredChestFurni(40, OWNER, chestFurni(), "", 0, 0));
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_50", 1);
        give(302, "chair", 3);

        assertNotNull(manager.openDonation(this.habbo, "A thank you", "games", 60, List.of(coins, furni)));
        assertEquals(2, manager.offer(this.habbo, List.of(301, 302), false));
        assertTrue(this.acceptAndConfirm(manager));

        assertEquals(50, coins.getContents().getCoins());
        assertEquals(1, furni.getContents().furniItemCount());
        assertEquals(0, this.store.inventoryCount(PLAYER));
        assertEquals(
                WiredTransactionSuccessComposer.TYPE_PAYMENT,
                this.sent(WiredTransactionSuccessComposer.class).get(0).getType());
        this.wired.verify(() -> WiredManager.dispatchEffectTriggeredEvent(any()), atLeastOnce());
    }

    @Test
    void aFixedMultiplierPaysAndRewardsTheContractThatManyTimes() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(3);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_50", 1);
        give(302, "CF_10", 2);

        assertNotNull(manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                new ContractMultiplier(ContractMultiplier.MODE_FIXED, 2),
                1,
                "",
                "generic",
                0,
                List.of(coins, trophies),
                List.of(77)));
        manager.offer(this.habbo, List.of(301, 302), false);
        assertTrue(this.acceptAndConfirm(manager));

        assertEquals(60, coins.getContents().getCoins(), "sixty coins paid for two runs");
        assertEquals(1, trophies.getContents().furniItemCount(), "two trophies handed out");
        assertEquals(2, this.store.inventoryCount(PLAYER));
    }

    @Test
    void aMultipliedRewardTheChestsCannotCoverPaysNothing() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(2);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_100", 1);

        manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                new ContractMultiplier(ContractMultiplier.MODE_FIXED, 3),
                1,
                "",
                "generic",
                0,
                List.of(coins, trophies),
                List.of());
        manager.offer(this.habbo, List.of(301), false);
        assertFalse(this.acceptAndConfirm(manager), "three trophies asked, two in the chest");

        assertEquals(0, coins.getContents().getCoins());
        assertEquals(2, trophies.getContents().furniItemCount());
        assertTrue(this.inventory.containsKey(301), "the payment went back");
    }

    @Test
    void theAutoMultiplierRunsAsOftenAsTheOfferPays() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(5);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_50", 1);
        give(302, "CF_50", 2);

        manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                new ContractMultiplier(ContractMultiplier.MODE_AUTO, 10),
                1,
                "",
                "generic",
                0,
                List.of(coins, trophies),
                List.of());
        manager.offer(this.habbo, List.of(301, 302), false);
        assertEquals(3, manager.getSession(this.habbo).timesMet(), "a hundred coins pay thirty three times");
        assertTrue(this.acceptAndConfirm(manager));

        assertEquals(100, coins.getContents().getCoins(), "both credit furni were needed for ninety coins");
        assertEquals(2, trophies.getContents().furniItemCount(), "three trophies handed out");
    }

    @Test
    void cancellingASpecificContractLeavesOtherNegotiationsAlone() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(1);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_50", 1);

        manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                ContractMultiplier.NONE,
                1,
                "",
                "generic",
                0,
                List.of(coins, trophies),
                List.of(77));
        manager.offer(this.habbo, List.of(301), false);

        assertFalse(manager.cancelRunning(this.habbo, List.of(78)), "another contract's cancel");
        assertNotNull(manager.getSession(this.habbo));
        assertTrue(manager.cancelRunning(this.habbo, List.of(77)));
        assertNull(manager.getSession(this.habbo));
        assertTrue(this.inventory.containsKey(301), "the offer went back");
        assertEquals(
                ChestTransactionFailures.TRADE_CANCELLED,
                failureId(this.sent(WiredTradeCancelledComposer.class).get(0)));
    }

    @Test
    void cancellingAnyEndsAContractButNotAPlainDeposit() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(20, OWNER, chestFurni(), "", 0, 0);
        WiredTradingManager manager = new WiredTradingManager(this.room);

        manager.openDeposit(this.habbo, chest, 300);
        assertFalse(manager.cancelRunning(this.habbo, null), "the player's own deposit stays open");

        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        manager.openDonation(this.habbo, "", "generic", 0, List.of(coins), List.of(5));
        assertTrue(manager.cancelRunning(this.habbo, null));
        assertNull(manager.getSession(this.habbo));
    }

    @Test
    void aLapsedNegotiationEndsWithTheTimeoutReason() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(1);
        WiredTradingManager manager = new WiredTradingManager(this.room);

        WiredTradingSession session = manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                ContractMultiplier.NONE,
                1,
                "",
                "generic",
                30,
                List.of(coins, trophies),
                List.of());
        long opened = System.currentTimeMillis();

        assertFalse(manager.expire(this.habbo, session, opened + 10_000L), "still open after ten seconds");
        assertTrue(manager.expire(this.habbo, session, opened + 31_000L));
        assertNull(manager.getSession(this.habbo));
        assertEquals(
                ChestTransactionFailures.TIMEOUT,
                failureId(this.sent(WiredTradeCancelledComposer.class).get(0)));
        assertFalse(manager.expire(this.habbo, session, opened + 60_000L), "only once");
    }

    @Test
    void anEmptyDonationIsRefused() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        WiredTradingManager manager = new WiredTradingManager(this.room);

        manager.openDonation(this.habbo, "", "generic", 60, List.of(coins));

        assertFalse(this.acceptAndConfirm(manager));
        assertEquals(0, coins.getContents().getCoins());
        assertEquals(1, this.sent(WiredTradeCancelledComposer.class).size());
    }

    private static boolean overridesPrevious(WiredTradeOpenComposer composer) {
        try {
            java.lang.reflect.Field field = WiredTradeOpenComposer.class.getDeclaredField("overridePreviousTrade");
            field.setAccessible(true);
            return field.getBoolean(composer);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void openTrophyTrade(WiredTradingManager manager, InteractionWiredChest... chests) {
        manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                ContractMultiplier.NONE,
                1,
                "",
                "generic",
                0,
                List.of(chests),
                List.of(77));
    }

    @Test
    void anInitiateStackedOnACancelReplacesTheTradeInTheOpenWindow() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(1);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_50", 1);

        this.openTrophyTrade(manager, coins, trophies);
        manager.offer(this.habbo, List.of(301), false);

        assertTrue(manager.cancelForReplacement(this.habbo, List.of(77)));
        assertNull(manager.getSession(this.habbo));
        assertTrue(this.inventory.containsKey(301), "the offer went back");
        assertTrue(manager.awaitsReplacement(this.habbo));

        this.openTrophyTrade(manager, coins, trophies);

        assertTrue(this.sent(WiredTradeCancelledComposer.class).isEmpty(), "the window is never told to close");
        List<WiredTradeOpenComposer> opened = this.sent(WiredTradeOpenComposer.class);
        assertFalse(overridesPrevious(opened.get(0)));
        assertTrue(overridesPrevious(opened.get(1)), "the new trade overrides the cancelled one in place");
        assertFalse(manager.awaitsReplacement(this.habbo));
        assertFalse(manager.flushReplacement(this.habbo), "nothing is left to tell");
        assertNotNull(manager.getSession(this.habbo));
    }

    @Test
    void aCancelWaitingForAReplacementThatNeverComesClosesTheWindow() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(1);
        WiredTradingManager manager = new WiredTradingManager(this.room);

        this.openTrophyTrade(manager, coins, trophies);
        assertFalse(manager.cancelForReplacement(this.habbo, List.of(78)), "another contract's cancel");
        assertTrue(manager.cancelForReplacement(this.habbo, null));

        assertTrue(manager.flushReplacement(this.habbo));
        assertEquals(
                ChestTransactionFailures.TRADE_CANCELLED,
                failureId(this.sent(WiredTradeCancelledComposer.class).get(0)));
        assertFalse(manager.flushReplacement(this.habbo), "only once");
    }

    private void verifyMonitorCount(String reason, int sourceId, int times) {
        this.wired.verify(
                () -> WiredManager.noteTransactionFailure(
                        org.mockito.ArgumentMatchers.eq(0),
                        org.mockito.ArgumentMatchers.contains(reason),
                        org.mockito.ArgumentMatchers.eq(WiredTradingManager.MONITOR_SOURCE),
                        org.mockito.ArgumentMatchers.eq(sourceId)),
                org.mockito.Mockito.times(times));
    }

    private void verifyNothingCountedOnTheMonitor() {
        this.wired.verify(
                () -> WiredManager.noteTransactionFailure(
                        anyInt(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        anyInt()),
                org.mockito.Mockito.never());
    }

    @Test
    void aRewardTheChestsRanOutOfCountsAsATransactionFailureOnTheMonitor() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(2);
        WiredTradingManager manager = new WiredTradingManager(this.room);
        give(301, "CF_100", 1);

        manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                new ContractMultiplier(ContractMultiplier.MODE_FIXED, 3),
                1,
                "",
                "generic",
                0,
                List.of(coins, trophies),
                List.of(77));
        manager.offer(this.habbo, List.of(301), false);
        assertFalse(this.acceptAndConfirm(manager));

        this.verifyMonitorCount("(" + ChestTransactionFailures.FUNDS_GONE + ")", 77, 1);
    }

    @Test
    void aContractWithoutAUsableChestCountsOnTheMonitor() {
        InteractionWiredChestCurrency locked = new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0);
        locked.getContents().enableWired();
        WiredTradingManager manager = new WiredTradingManager(this.room);

        assertNull(manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                ContractMultiplier.NONE,
                1,
                "",
                "generic",
                0,
                List.of(locked),
                List.of(55)));

        this.verifyMonitorCount("no usable chest", 55, 1);
    }

    @Test
    void whatThePlayerDoesIsNotCountedOnTheMonitor() {
        InteractionWiredChestCurrency coins =
                wiredChest(new InteractionWiredChestCurrency(30, OWNER, chestFurni(), "", 0, 0));
        InteractionWiredChestFurni trophies = this.trophyChest(1);
        WiredTradingManager manager = new WiredTradingManager(this.room);

        // Walking away, a lapsed offer, a Cancel Transaction box, an empty donation, a plain deposit.
        this.openTrophyTrade(manager, coins, trophies);
        manager.cancel(this.habbo, WiredTradingSession.FAILURE_SILENT);
        WiredTradingSession lapsing = manager.open(
                this.habbo,
                this.thirtyCoinsForTrophies(1),
                ContractMultiplier.NONE,
                1,
                "",
                "generic",
                30,
                List.of(coins, trophies),
                List.of(77));
        assertTrue(manager.expire(this.habbo, lapsing, System.currentTimeMillis() + 31_000L));
        this.openTrophyTrade(manager, coins, trophies);
        assertTrue(manager.cancelRunning(this.habbo, List.of(77)));
        manager.openDonation(this.habbo, "", "generic", 60, List.of(coins));
        assertFalse(this.acceptAndConfirm(manager));
        manager.openDeposit(this.habbo, new InteractionWiredChestFurni(20, OWNER, chestFurni(), "", 0, 0), 300);
        manager.cancel(this.habbo, WiredTradingSession.FAILURE_REWARD_UNAVAILABLE);

        this.verifyNothingCountedOnTheMonitor();
    }

    @Test
    void onlyTheRoomsAndTheServersFailuresCountOnTheMonitor() {
        for (int reason : List.of(
                ChestTransactionFailures.MISCONFIGURED,
                ChestTransactionFailures.FUNDS_GONE,
                ChestTransactionFailures.CHEST_FULL,
                ChestTransactionFailures.NOT_A_WIRED_CHEST,
                ChestTransactionFailures.CHEST_NOT_IN_ROOM,
                ChestTransactionFailures.NO_OR_LOCKED_CHESTS,
                ChestTransactionFailures.EXCEEDS_CAPACITY,
                ChestTransactionFailures.INTERNAL_ERROR,
                ChestTransactionFailures.DATABASE_ERROR)) {
            assertTrue(ChestTransactionFailures.countsOnMonitor(reason), "reason " + reason);
        }
        for (int reason : List.of(
                ChestTransactionFailures.USER_CANCELLED,
                ChestTransactionFailures.INVALID,
                ChestTransactionFailures.TIMEOUT,
                ChestTransactionFailures.TRADE_CANCELLED,
                ChestTransactionFailures.ALREADY_TRADING,
                ChestTransactionFailures.INSUFFICIENT_FUNDS,
                ChestTransactionFailures.EMPTY,
                ChestTransactionFailures.TOO_MANY_OFFERS)) {
            assertFalse(ChestTransactionFailures.countsOnMonitor(reason), "reason " + reason);
        }

        ChestTransactionFailures.noteOnMonitor(null, ChestTransactionFailures.INTERNAL_ERROR, "x", 1);
        ChestTransactionFailures.noteOnMonitor(this.room, ChestTransactionFailures.TIMEOUT, "x", 1);
        this.verifyNothingCountedOnTheMonitor();
    }
}
