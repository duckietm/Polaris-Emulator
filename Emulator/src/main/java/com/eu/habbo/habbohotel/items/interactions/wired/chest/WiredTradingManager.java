package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.Match;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCancelledComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCompletedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeItemsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeOpenComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionSuccessComposer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The room's open wired trades, one per player, after the official AIR model.
 *
 * <p>A wired trade is how anything goes into a chest: a player's own deposit (the chest window's
 * deposit button) and a contract's payment both open this table. The player puts inventory items on
 * it; credit furni count as coins. Settling moves the offered items into the chests (furni into furni
 * chests, credit furni into coin chests as coins) and hands a contract's reward out of the chests --
 * never from the player's wallet and never out of nothing.
 *
 * <p>Lives on the room for the same reason {@code RoomTradeManager} does: when the room goes away, so
 * must every trade it was holding, and every item on those tables goes back to its owner.
 */
public class WiredTradingManager {
    /** A table opened to fill a chest rather than to satisfy a contract. */
    public static final int CONTRACT_TYPE_CHEST_DEPOSIT = 3;

    private static final String LAYOUT_GENERIC = "generic";

    private final Room room;
    private final Map<Integer, Negotiation> sessions = new HashMap<>();

    /**
     * A session plus what settling it needs: the chests it uses and the reward's text.
     *
     * <p>{@code depositOnly} separates the two reasons a table opens. A contract judges the offer
     * against its rules and moves only what the satisfied rule claims. A deposit judges nothing: the
     * player is filling a chest, so everything they put down goes in.
     */
    private record Negotiation(
            WiredTradingSession session,
            List<InteractionWiredChest> chests,
            InventoryVault vault,
            boolean depositOnly,
            int contractType,
            String rewardText) {}

    public WiredTradingManager(Room room) {
        this.room = room;
    }

    /** The negotiation this player has open, or null. */
    public synchronized WiredTradingSession getSession(Habbo habbo) {
        Negotiation negotiation =
                habbo == null ? null : this.sessions.get(habbo.getHabboInfo().getId());
        return negotiation == null ? null : negotiation.session();
    }

    /** Open a contract trade against one chest. Kept for callers that pass a single chest. */
    public WiredTradingSession open(
            Habbo habbo,
            ContractRules rules,
            int contractType,
            String rewardText,
            String layoutType,
            int timeoutSeconds,
            InteractionWiredChest chest) {
        return open(
                habbo,
                rules,
                contractType,
                rewardText,
                layoutType,
                timeoutSeconds,
                chest == null ? List.of() : List.of(chest));
    }

    /**
     * Open a contract trade, replacing whatever the player had open (the client is told this one
     * overrides the last). AIR: a contract only works with chests that answer wired and are not
     * locked; with none the player is told so (fail 15) and nothing opens.
     */
    public WiredTradingSession open(
            Habbo habbo,
            ContractRules rules,
            int contractType,
            String rewardText,
            String layoutType,
            int timeoutSeconds,
            List<InteractionWiredChest> chests) {
        List<InteractionWiredChest> usable = WiredChestPayouts.usable(chests);
        if (usable.isEmpty()) {
            ChestTransactionFailures.send(habbo, ChestTransactionFailures.NO_OR_LOCKED_CHESTS);
            return null;
        }
        return this.start(
                habbo,
                rules,
                contractType,
                rewardText,
                layoutType,
                timeoutSeconds,
                usable,
                false,
                WiredTradeOpenComposer.REQUIREMENT_RULES);
    }

    /**
     * Open the table for a plain deposit into one chest (AIR start deposit): any coins for a coin
     * chest, any furni for a furni chest, and everything offered goes in.
     */
    public WiredTradingSession openDeposit(Habbo habbo, InteractionWiredChest chest, int timeoutSeconds) {
        if (chest == null) return null;

        int requirement = chest.chestKind() == ChestStorage.KIND_CURRENCY
                ? WiredTradeOpenComposer.REQUIREMENT_ANY_COINS
                : WiredTradeOpenComposer.REQUIREMENT_ANY_FURNI;
        return this.start(
                habbo,
                ContractRules.of(List.of(List.of()), List.of()),
                CONTRACT_TYPE_CHEST_DEPOSIT,
                "",
                LAYOUT_GENERIC,
                timeoutSeconds,
                List.of(chest),
                true,
                requirement);
    }

    private WiredTradingSession start(
            Habbo habbo,
            ContractRules rules,
            int contractType,
            String rewardText,
            String layoutType,
            int timeoutSeconds,
            List<InteractionWiredChest> chests,
            boolean depositOnly,
            int requirementType) {
        if (habbo == null || habbo.getClient() == null || rules == null) return null;

        boolean replaced;
        WiredTradingSession session;

        synchronized (this) {
            replaced = closeQuietly(habbo);
            InventoryVault vault = new InventoryVault(habbo);
            // Coins are paid with credit furni, so the "wallet" a contract is judged against is the
            // value of the credit furni on the table.
            session = new WiredTradingSession(
                    rules, vault, type -> vault.heldCredits(), timeoutSeconds, System.currentTimeMillis());
            this.sessions.put(
                    habbo.getHabboInfo().getId(),
                    new Negotiation(session, List.copyOf(chests), vault, depositOnly, contractType, rewardText));
        }

        habbo.getClient()
                .sendResponse(new WiredTradeOpenComposer(
                        requirementType,
                        rewardText,
                        layoutType,
                        rules.asksForNothing() && !depositOnly,
                        replaced,
                        timeoutSeconds,
                        rules,
                        true));
        pushState(habbo, session);
        return session;
    }

    /**
     * Put items on the table or take them off. Items that may not be offered here are refused with
     * the official trade error (0 not a valid item, 1 too many items, 9 more than the chest holds).
     *
     * @return how many items moved
     */
    public int offer(Habbo habbo, List<Integer> itemIds, boolean remove) {
        Negotiation negotiation = getNegotiation(habbo);
        if (negotiation == null || itemIds == null || itemIds.isEmpty()) return 0;

        WiredTradingSession session = negotiation.session();
        if (remove) return session.withdrawItems(itemIds);

        List<Integer> allowed = new ArrayList<>();
        int error = -1;
        int already = session.getOfferedItems().size();
        for (Integer itemId : itemIds) {
            if (itemId == null) continue;
            HabboItem item = habbo.getInventory().getItemsComponent().getHabboItem(itemId);
            if (item == null || !acceptable(negotiation, item)) {
                error = WiredTradeErrorComposer.INVALID_ITEM;
                continue;
            }
            if (already + allowed.size() >= WiredTradingSession.MAX_OFFERED_ITEMS) {
                error = WiredTradeErrorComposer.TOO_MANY_ITEMS;
                break;
            }
            if (negotiation.depositOnly() && exceedsDepositRoom(negotiation, already + allowed.size() + 1, item)) {
                error = WiredTradeErrorComposer.EXCEEDS_CAPACITY;
                break;
            }
            allowed.add(itemId);
        }

        if (error >= 0 && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new WiredTradeErrorComposer(error));
        }
        return allowed.isEmpty() ? 0 : session.offerItems(allowed);
    }

    /** A coin chest takes credit furni only; furni chests and contracts take any storable furni. */
    private static boolean acceptable(Negotiation negotiation, HabboItem item) {
        boolean credit = ChestAirWire.creditValue(item) > 0;
        if (negotiation.depositOnly()) {
            InteractionWiredChest chest = negotiation.chests().get(0);
            if (chest.chestKind() == ChestStorage.KIND_CURRENCY) return credit && InteractionWiredChest.canStore(item);
        }
        return InteractionWiredChest.canStore(item);
    }

    private static boolean exceedsDepositRoom(Negotiation negotiation, int count, HabboItem next) {
        InteractionWiredChest chest = negotiation.chests().get(0);
        ChestStorage storage = chest.getContents();
        if (chest.chestKind() == ChestStorage.KIND_FURNI) {
            return storage.furniItemCount() + count > storage.getCapacity();
        }
        long coins = (long) negotiation.vault().heldCredits() + ChestAirWire.creditValue(next);
        return storage.getCoins() + coins > storage.getCapacity();
    }

    /** Push the table back to the player: both sides and whether it can be accepted. */
    public void pushState(Habbo habbo, WiredTradingSession session) {
        if (habbo == null || habbo.getClient() == null || session == null) return;

        Negotiation negotiation = getNegotiation(habbo);
        List<HabboItem> offered =
                negotiation == null ? List.of() : negotiation.vault().heldItems();
        int credits = negotiation == null ? 0 : negotiation.vault().heldCredits();

        List<Term> reward = session.getRules().getRule();
        List<ChestFurniStoredItem> rewardFurni = negotiation == null || negotiation.depositOnly()
                ? List.of()
                : WiredChestPayouts.preview(negotiation.chests(), reward);
        boolean canAccept = negotiation != null && negotiation.depositOnly()
                ? !session.getOfferedItems().isEmpty()
                : session.canAccept();

        habbo.getClient()
                .sendResponse(new WiredTradeItemsComposer(
                        habbo.getHabboInfo().getId(),
                        offered,
                        credits,
                        rewardFurni,
                        WiredChestPayouts.coins(reward),
                        canAccept,
                        canAccept ? 1 : 0));
    }

    /** End a negotiation, hand back what it held, and tell the player why (AIR reason). */
    public void cancel(Habbo habbo, int failureId) {
        Negotiation negotiation;
        synchronized (this) {
            negotiation = this.sessions.remove(
                    habbo == null ? -1 : habbo.getHabboInfo().getId());
        }
        if (negotiation == null) return;

        returnHeldItems(habbo, negotiation.session());

        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient()
                    .sendResponse(new WiredTradeCancelledComposer(ChestTransactionFailures.fromSession(failureId)));
        }

        if (!negotiation.depositOnly()) raise(habbo, WiredEvent.Type.TRANSACTION_FAIL);
    }

    /** End a negotiation that settled. The items it consumed are gone by then, by design. */
    public void complete(Habbo habbo) {
        complete(habbo, -1);
    }

    private void complete(Habbo habbo, int successType) {
        Negotiation negotiation;
        synchronized (this) {
            negotiation = this.sessions.remove(
                    habbo == null ? -1 : habbo.getHabboInfo().getId());
        }
        if (negotiation == null) return;

        returnHeldItems(habbo, negotiation.session());

        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new WiredTradeCompletedComposer());
            if (successType >= 0) habbo.getClient().sendResponse(new WiredTransactionSuccessComposer(successType));
        }

        if (!negotiation.depositOnly()) raise(habbo, WiredEvent.Type.TRANSACTION_COMPLETE);
    }

    /**
     * The player confirmed. Judge the offer one last time, move what the satisfied rule claims and
     * hand out the reward, and close either way.
     *
     * <p>Everything is checked before anything moves, while every chest involved is held, and a plan
     * that cannot be met refuses rather than half-executing: the player must never pay into a reward
     * the chests ran out of.
     *
     * @return true when the exchange happened
     */
    public boolean settle(Habbo habbo, long nowMillis) {
        Negotiation negotiation = getNegotiation(habbo);
        if (negotiation == null) return false;

        WiredTradingSession.Confirmation confirmation = negotiation.session().confirm(nowMillis);
        if (negotiation.depositOnly()) {
            if (!confirmation.settled() && confirmation.failureId() != WiredTradingSession.FAILURE_REQUIREMENTS) {
                cancel(habbo, confirmation.failureId());
                return false;
            }
            return settleDeposit(habbo, negotiation);
        }

        if (!confirmation.settled()) {
            cancel(habbo, confirmation.failureId());
            return false;
        }

        Match match = confirmation.match();
        List<Term> paidRule = negotiation.session().getRules().giveRules().get(match.ruleIndex());
        List<Term> reward = negotiation.session().getRules().getRule();
        List<InteractionWiredChest> chests = WiredChestPayouts.usable(negotiation.chests());

        int failure = WiredChestPayouts.holding(chests, () -> {
            if (chests.isEmpty()) return ChestTransactionFailures.NO_OR_LOCKED_CHESTS;

            int cover = WiredChestPayouts.coverFailure(chests, reward);
            if (cover != ChestTransactionFailures.NONE) return cover;

            // What pays: the furni the rule matched, plus enough credit furni for its coins.
            List<HabboItem> furniPayment = new ArrayList<>();
            for (Integer itemId : match.consumedItemIds()) {
                HabboItem item = negotiation.vault().peek(itemId);
                if (item != null) furniPayment.add(item);
            }
            List<HabboItem> creditPayment =
                    pickCredits(negotiation.vault(), match.consumedItemIds(), WiredChestPayouts.coins(paidRule));
            if (creditPayment == null) return ChestTransactionFailures.INSUFFICIENT_FUNDS;

            int room = storeFailure(chests, furniPayment, creditPayment);
            if (room != ChestTransactionFailures.NONE) return room;

            List<Integer> consumed = pay(habbo, negotiation, chests, furniPayment, creditPayment);
            negotiation.session().consume(consumed);

            List<Term> given = WiredChestPayouts.give(habbo, this.room, chests, reward);
            if (!given.isEmpty() && habbo.getClient() != null) {
                habbo.getClient()
                        .sendResponse(WiredTransactionSuccessComposer.reward(given, negotiation.rewardText(), false));
            }
            return ChestTransactionFailures.NONE;
        });

        if (failure != ChestTransactionFailures.NONE) {
            cancelWithReason(habbo, failure);
            return false;
        }

        complete(
                habbo,
                reward.isEmpty()
                        ? WiredTransactionSuccessComposer.TYPE_PAYMENT
                        : WiredTransactionSuccessComposer.TYPE_TRADE);
        return true;
    }

    /** Credit furni from the table, worth at least {@code coins}, largest first; null when short. */
    private static List<HabboItem> pickCredits(InventoryVault vault, List<Integer> exclude, int coins) {
        List<HabboItem> picked = new ArrayList<>();
        if (coins <= 0) return picked;

        List<HabboItem> credits = new ArrayList<>();
        for (HabboItem item : vault.heldItems()) {
            if (!exclude.contains(item.getId()) && ChestAirWire.creditValue(item) > 0) credits.add(item);
        }
        credits.sort(Comparator.comparingInt(ChestAirWire::creditValue).reversed());

        int covered = 0;
        for (HabboItem item : credits) {
            if (covered >= coins) break;
            picked.add(item);
            covered += ChestAirWire.creditValue(item);
        }
        return covered >= coins ? picked : null;
    }

    /** Whether the chests have room for a payment; {@link ChestTransactionFailures#NONE} when they do. */
    private static int storeFailure(
            List<InteractionWiredChest> chests, List<HabboItem> furni, List<HabboItem> credits) {
        long furniRoom = 0;
        long coinRoom = 0;
        boolean furniChest = false;
        boolean coinChest = false;
        for (InteractionWiredChest chest : chests) {
            ChestStorage storage = chest.getContents();
            if (chest.chestKind() == ChestStorage.KIND_FURNI) {
                furniChest = true;
                furniRoom += Math.max(0, storage.getCapacity() - storage.furniItemCount());
            } else {
                coinChest = true;
                coinRoom = Math.max(coinRoom, storage.getCapacity() - (long) storage.getCoins());
            }
        }
        long coins = credits.stream().mapToLong(ChestAirWire::creditValue).sum();
        if ((!furni.isEmpty() && !furniChest) || (coins > 0 && !coinChest)) {
            return ChestTransactionFailures.NO_OR_LOCKED_CHESTS;
        }
        if (furni.size() > furniRoom || coins > coinRoom) return ChestTransactionFailures.CHEST_FULL;
        return ChestTransactionFailures.NONE;
    }

    /** Move a payment off the table into the chests. Returns the item ids that left the table. */
    private List<Integer> pay(
            Habbo habbo,
            Negotiation negotiation,
            List<InteractionWiredChest> chests,
            List<HabboItem> furni,
            List<HabboItem> credits) {
        List<Integer> consumed = new ArrayList<>();
        int userId = habbo.getHabboInfo().getId();

        List<HabboItem> remaining = new ArrayList<>(furni);
        for (InteractionWiredChest chest : chests) {
            if (remaining.isEmpty()) break;
            if (chest.chestKind() != ChestStorage.KIND_FURNI) continue;

            List<HabboItem> accepted = chest.depositFurni(userId, remaining);
            for (HabboItem item : accepted) {
                negotiation.vault().release(item.getId());
                consumed.add(item.getId());
            }
            remaining.removeAll(accepted);
            if (!accepted.isEmpty()) this.afterDeposit(chest, habbo, accepted.size(), 0, accepted);
        }

        int coins = credits.stream().mapToInt(ChestAirWire::creditValue).sum();
        if (coins > 0) {
            for (InteractionWiredChest chest : chests) {
                if (chest.chestKind() != ChestStorage.KIND_CURRENCY) continue;
                if (chest.redeemCreditFurni(userId, credits, coins)) {
                    for (HabboItem item : credits) {
                        negotiation.vault().release(item.getId());
                        consumed.add(item.getId());
                    }
                    this.afterDeposit(chest, habbo, 0, coins, List.of());
                    break;
                }
            }
        }
        return consumed;
    }

    /**
     * Move everything on the table into the deposit's chest: furni into a furni chest, credit furni
     * into a coin chest as coins. What a full chest refuses goes back to the player.
     */
    private boolean settleDeposit(Habbo habbo, Negotiation negotiation) {
        InteractionWiredChest chest = negotiation.chests().get(0);
        List<HabboItem> offered = negotiation.vault().heldItems();
        if (offered.isEmpty()) {
            cancelWithReason(habbo, ChestTransactionFailures.EMPTY);
            return false;
        }

        int userId = habbo.getHabboInfo().getId();
        List<Integer> consumed = new ArrayList<>();
        if (chest.chestKind() == ChestStorage.KIND_FURNI) {
            List<HabboItem> accepted = chest.depositFurni(userId, offered);
            for (HabboItem item : accepted) {
                negotiation.vault().release(item.getId());
                consumed.add(item.getId());
            }
            if (!accepted.isEmpty()) this.afterDeposit(chest, habbo, accepted.size(), 0, accepted);
        } else {
            int coins = offered.stream().mapToInt(ChestAirWire::creditValue).sum();
            if (chest.redeemCreditFurni(userId, offered, coins)) {
                for (HabboItem item : offered) {
                    negotiation.vault().release(item.getId());
                    consumed.add(item.getId());
                }
                this.afterDeposit(chest, habbo, 0, coins, List.of());
            }
        }

        if (consumed.isEmpty()) {
            cancelWithReason(habbo, ChestTransactionFailures.CHEST_FULL);
            return false;
        }

        negotiation.session().consume(consumed);
        complete(habbo, WiredTransactionSuccessComposer.TYPE_DEPOSIT);
        return true;
    }

    /** Logs, notifications, open windows and the furni's look after something went into a chest. */
    private void afterDeposit(InteractionWiredChest chest, Habbo habbo, int furni, int coins, List<HabboItem> items) {
        int amount = coins > 0 ? coins : furni;
        int storedNow = chest.chestKind() == ChestStorage.KIND_FURNI
                ? chest.getContents().furniItemCount()
                : chest.getContents().getCoins();

        List<ChestFurniStoredItem> rows = new ArrayList<>();
        for (HabboItem item : items) {
            for (ChestFurniStoredItem row : chest.getContents().furniItems()) {
                if (row.inventoryId == item.getId()) rows.add(row);
            }
        }

        chest.getContents()
                .addLog(new ChestStorage.LogEntry(
                        "deposit",
                        System.currentTimeMillis(),
                        habbo.getHabboInfo().getUsername(),
                        0,
                        amount));
        ChestTransactionLog.record(
                chest.getRoomId(),
                chest.getId(),
                chest.chestKind(),
                ChestTransactionLog.TYPE_DEPOSIT,
                ChestTransactionLog.SOURCE_USER,
                habbo,
                coins > 0 ? ChestStorage.COINS_TYPE : -1,
                0,
                amount,
                coins > 0 ? null : rows);

        if (habbo.getClient() != null) {
            for (HabboItem item : items)
                habbo.getClient().sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }

        chest.notifyViewers(this.room, List.of(), rows);
        chest.persistContents(this.room);
        ChestNotifications.donation(chest, this.room, habbo, amount);
        ChestNotifications.afterChange(chest, this.room, storedNow - amount, storedNow);
    }

    private void cancelWithReason(Habbo habbo, int airReason) {
        Negotiation negotiation;
        synchronized (this) {
            negotiation = this.sessions.remove(
                    habbo == null ? -1 : habbo.getHabboInfo().getId());
        }
        if (negotiation == null) return;

        returnHeldItems(habbo, negotiation.session());
        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new WiredTradeCancelledComposer(airReason));
        }
        if (!negotiation.depositOnly()) raise(habbo, WiredEvent.Type.TRANSACTION_FAIL);
    }

    private synchronized Negotiation getNegotiation(Habbo habbo) {
        return habbo == null ? null : this.sessions.get(habbo.getHabboInfo().getId());
    }

    /**
     * Fire the room's transaction triggers for how a contract trade ended. A negotiated contract does
     * not settle until the player confirms or walks away, so the triggers fire from here.
     */
    private void raise(Habbo habbo, WiredEvent.Type type) {
        if (this.room == null || habbo == null) return;

        WiredEvent.Builder builder = WiredEvent.builder(type, this.room);
        if (habbo.getRoomUnit() != null) builder.actor(habbo.getRoomUnit());

        WiredManager.dispatchEffectTriggeredEvent(builder.build());
    }

    /** Drop every negotiation, handing every held item back. Called when the room lets go. */
    public void dispose() {
        Map<Integer, Negotiation> open;
        synchronized (this) {
            open = new HashMap<>(this.sessions);
            this.sessions.clear();
        }

        for (Map.Entry<Integer, Negotiation> entry : open.entrySet()) {
            Habbo habbo = this.room == null ? null : this.room.getHabbo(entry.getKey());
            returnHeldItems(habbo, entry.getValue().session());

            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient()
                        .sendResponse(new WiredTradeCancelledComposer(ChestTransactionFailures.TRADE_CANCELLED));
            }
        }
    }

    /**
     * Close without telling anyone — used when a new negotiation replaces an old one, where the open
     * packet that follows is the message.
     */
    private boolean closeQuietly(Habbo habbo) {
        Negotiation previous = this.sessions.remove(habbo.getHabboInfo().getId());
        if (previous == null) return false;

        returnHeldItems(habbo, previous.session());
        return true;
    }

    private void returnHeldItems(Habbo habbo, WiredTradingSession session) {
        List<Integer> returned = session.close();
        if (returned.isEmpty() || habbo == null || habbo.getClient() == null) return;

        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }

    /**
     * The real inventory behind a session. Takes an item out while it sits on the table and puts it
     * back on the way out, which is exactly what a room trade does — offering something must not
     * leave it sellable somewhere else at the same time.
     */
    private static final class InventoryVault implements WiredTradingSession.ItemVault {
        private final Habbo habbo;

        /**
         * Taking an item removes it from the inventory, so the inventory can no longer answer for it.
         * The vault keeps the reference: without it, handing an item back after a cancellation would
         * silently do nothing and the player would simply have lost it.
         */
        private final Map<Integer, HabboItem> held = new LinkedHashMap<>();

        private InventoryVault(Habbo habbo) {
            this.habbo = habbo;
        }

        @Override
        public synchronized boolean take(int itemId) {
            if (this.held.containsKey(itemId)) return false;

            HabboItem item = this.habbo.getInventory().getItemsComponent().getHabboItem(itemId);
            if (item == null || !InteractionWiredChest.canStore(item)) return false;
            if (!this.habbo.getInventory().getItemsComponent().takeHabboItemsAtomically(List.of(item))) return false;

            this.held.put(itemId, item);
            return true;
        }

        @Override
        public synchronized void giveBack(int itemId) {
            HabboItem item = this.held.remove(itemId);
            if (item != null) this.habbo.getInventory().getItemsComponent().addItem(item);
        }

        @Override
        public synchronized OfferedItem describe(int itemId) {
            HabboItem item = this.held.get(itemId);
            if (item == null)
                item = this.habbo.getInventory().getItemsComponent().getHabboItem(itemId);
            if (item == null || item.getBaseItem() == null) return null;

            return new OfferedItem(
                    itemId,
                    item.getBaseItem().getType() == FurnitureType.WALL,
                    item.getBaseItem().getId());
        }

        /** Look at a held item without taking it out of the vault's hands. */
        synchronized HabboItem peek(int itemId) {
            return this.held.get(itemId);
        }

        /** Every item on the table, in the order it was put down. */
        synchronized List<HabboItem> heldItems() {
            return new ArrayList<>(this.held.values());
        }

        /** What the credit furni on the table are worth together. */
        synchronized int heldCredits() {
            long sum = 0;
            for (HabboItem item : this.held.values()) sum += ChestAirWire.creditValue(item);
            return (int) Math.min(sum, Integer.MAX_VALUE);
        }

        /** The item leaves the table for good (it went into a chest). */
        synchronized HabboItem release(int itemId) {
            return this.held.remove(itemId);
        }
    }
}
