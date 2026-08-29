package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
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
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCancelledComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCompletedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeItemsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeOpenComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The room's open wired contract negotiations, one per player.
 *
 * <p>Lives on the room for the same reason {@code RoomTradeManager} does: when the room goes away, so
 * must every negotiation it was holding, and every item those negotiations were holding has to go
 * back to its owner. A session that outlived its room would be a furniture leak.
 *
 * <p>This is the only place that turns session state into packets. The session itself decides what is
 * true; the manager decides who is told.
 */
public class WiredTradingManager {
    /** A table opened to fill a chest rather than to satisfy a contract. */
    public static final int CONTRACT_TYPE_CHEST_DEPOSIT = 3;

    private static final String LAYOUT_GENERIC = "generic";

    private final Room room;
    private final Map<Integer, Negotiation> sessions = new HashMap<>();

    /**
     * A session plus the two things settling it will need: where the goods come from, and go to.
     *
     * <p>{@code depositOnly} separates the two reasons a table like this opens. A contract judges the
     * offer against its rules and moves only what a satisfied rule claims. A deposit judges nothing:
     * the player is filling a chest, so everything they put down goes in.
     */
    private record Negotiation(
            WiredTradingSession session, InteractionWiredChest chest, InventoryVault vault, boolean depositOnly) {}

    public WiredTradingManager(Room room) {
        this.room = room;
    }

    /** The negotiation this player has open, or null. */
    public synchronized WiredTradingSession getSession(Habbo habbo) {
        Negotiation negotiation =
                habbo == null ? null : this.sessions.get(habbo.getHabboInfo().getId());
        return negotiation == null ? null : negotiation.session();
    }

    /**
     * Open a negotiation, replacing whatever the player had open — a player only ever has one, and
     * the client is told this one overrides the last so it swaps rather than stacking windows.
     */
    public WiredTradingSession open(
            Habbo habbo,
            ContractRules rules,
            int contractType,
            String rewardText,
            String layoutType,
            int timeoutSeconds,
            InteractionWiredChest chest) {
        return open(habbo, rules, contractType, rewardText, layoutType, timeoutSeconds, chest, false);
    }

    /**
     * Open the same table for a plain deposit: nothing to satisfy, everything offered goes into the
     * chest. This is the official's start-deposit flow -- the button sends one message and the server
     * answers by opening the offering window, rather than the window opening itself.
     */
    public WiredTradingSession openDeposit(Habbo habbo, InteractionWiredChest chest, int timeoutSeconds) {
        if (chest == null) return null;

        return open(
                habbo,
                ContractRules.of(List.of(List.of()), List.of()),
                CONTRACT_TYPE_CHEST_DEPOSIT,
                "",
                LAYOUT_GENERIC,
                timeoutSeconds,
                chest,
                true);
    }

    private WiredTradingSession open(
            Habbo habbo,
            ContractRules rules,
            int contractType,
            String rewardText,
            String layoutType,
            int timeoutSeconds,
            InteractionWiredChest chest,
            boolean depositOnly) {
        if (habbo == null || habbo.getClient() == null || rules == null) return null;

        boolean replaced;
        WiredTradingSession session;

        synchronized (this) {
            replaced = closeQuietly(habbo);
            InventoryVault vault = new InventoryVault(habbo);
            session = new WiredTradingSession(
                    rules, vault, currencyLookup(habbo), timeoutSeconds, System.currentTimeMillis());
            this.sessions.put(habbo.getHabboInfo().getId(), new Negotiation(session, chest, vault, depositOnly));
        }

        habbo.getClient()
                .sendResponse(new WiredTradeOpenComposer(
                        contractType, rewardText, layoutType, rules.asksForNothing(), replaced, timeoutSeconds, rules));
        pushState(habbo, session);
        return session;
    }

    /** Push the table back to the player: state, clock, both sides, and what is still missing. */
    public void pushState(Habbo habbo, WiredTradingSession session) {
        if (habbo == null || habbo.getClient() == null || session == null) return;

        Match match = session.evaluate();
        habbo.getClient()
                .sendResponse(new WiredTradeItemsComposer(
                        session.getState(),
                        session.canAccept(),
                        session.secondsLeft(System.currentTimeMillis()),
                        session.getOfferedItems(),
                        session.getRules().getRule(),
                        match.missing()));
    }

    /** End a negotiation, hand back what it held, and tell the player why. */
    public void cancel(Habbo habbo, int failureId) {
        Negotiation negotiation;
        synchronized (this) {
            negotiation = this.sessions.remove(
                    habbo == null ? -1 : habbo.getHabboInfo().getId());
        }
        if (negotiation == null) return;

        returnHeldItems(habbo, negotiation.session());

        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new WiredTradeCancelledComposer(failureId));
        }

        raise(habbo, WiredEvent.Type.TRANSACTION_FAIL);
    }

    /** End a negotiation that settled. The items it consumed are gone by then, by design. */
    public void complete(Habbo habbo) {
        Negotiation negotiation;
        synchronized (this) {
            negotiation = this.sessions.remove(
                    habbo == null ? -1 : habbo.getHabboInfo().getId());
        }
        if (negotiation == null) return;

        returnHeldItems(habbo, negotiation.session());

        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new WiredTradeCompletedComposer());
        }

        raise(habbo, WiredEvent.Type.TRANSACTION_COMPLETE);
    }

    /**
     * The player confirmed. Judge the offer one last time, move what the settled alternative claims,
     * and close either way.
     *
     * <p>Everything is planned before anything moves, and a plan that cannot be met refuses rather
     * than half-executing — the player must never pay into a reward the chest ran out of.
     *
     * @return true when the exchange happened
     */
    public boolean settle(Habbo habbo, long nowMillis) {
        Negotiation negotiation = getNegotiation(habbo);
        if (negotiation == null) return false;

        WiredTradingSession.Confirmation confirmation = negotiation.session().confirm(nowMillis);
        if (!confirmation.settled()) {
            cancel(habbo, confirmation.failureId());
            return false;
        }

        if (negotiation.depositOnly()) return settleDeposit(habbo, negotiation);

        List<Term> paidRule = negotiation
                .session()
                .getRules()
                .giveRules()
                .get(confirmation.match().ruleIndex());

        WiredTradeSettlement.Plan plan = WiredTradeSettlement.plan(
                currencyLookup(habbo),
                paidRule,
                confirmation.match().consumedItemIds(),
                negotiation.session().getRules().getRule(),
                negotiation.chest() == null ? null : negotiation.chest().getContents());

        if (plan == null) {
            cancel(habbo, WiredTradingSession.FAILURE_REWARD_UNAVAILABLE);
            return false;
        }

        WiredTradeSettlement.apply(habbo, plan, negotiation.chest(), (itemId, chest) -> {
            HabboItem item = negotiation.vault().release(itemId);
            if (item == null) return;

            // Into the chest when the contract has one, out of existence when it does not: either
            // way the item has left the player, which is what they agreed to.
            if (chest == null
                    || !chest.getContents().tryDepositFurni(ChestFurniStoredItem.fromHabboItem(item, item.getId()))) {
                deleteFromDatabase(List.of(item));
            }
        });

        // The consumed items are gone; telling the session stops the close below handing them back.
        negotiation.session().consume(plan.itemsToTake());
        complete(habbo);
        return true;
    }

    /**
     * Move everything on the table into the chest.
     *
     * <p>Items are only released from the vault once the chest has actually accepted them, so a chest
     * that fills up mid-deposit stops rather than swallowing the rest: {@link #complete} hands back
     * everything not consumed, and nobody loses furniture to a full chest.
     */
    private boolean settleDeposit(Habbo habbo, Negotiation negotiation) {
        InteractionWiredChest chest = negotiation.chest();
        if (chest == null) {
            cancel(habbo, WiredTradingSession.FAILURE_CONTRACT_GONE);
            return false;
        }

        List<Integer> depositedIds = new ArrayList<>();
        List<HabboItem> depositedItems = new ArrayList<>();
        List<ChestFurniStoredItem> storedRows = new ArrayList<>();

        for (OfferedItem offered : negotiation.session().getOfferedItems()) {
            HabboItem item = negotiation.vault().peek(offered.itemId());
            if (item == null) continue;

            ChestFurniStoredItem stored = ChestFurniStoredItem.fromHabboItem(item, item.getId());
            if (!chest.getContents().tryDepositFurni(stored)) break;

            negotiation.vault().release(offered.itemId());
            depositedIds.add(offered.itemId());
            depositedItems.add(item);
            storedRows.add(stored);
        }

        if (!depositedItems.isEmpty()) {
            chest.getContents()
                    .addLog(new ChestStorage.LogEntry(
                            "deposit",
                            System.currentTimeMillis(),
                            habbo.getHabboInfo().getUsername(),
                            0,
                            storedRows.size()));
            ChestTransactionLog.record(
                    chest.getRoomId(),
                    chest.getId(),
                    ChestStorage.KIND_FURNI,
                    ChestTransactionLog.TYPE_DEPOSIT,
                    ChestTransactionLog.SOURCE_USER,
                    habbo,
                    -1,
                    0,
                    storedRows.size(),
                    storedRows);
            chest.persistContents();

            deleteFromDatabase(depositedItems);

            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new InventoryRefreshComposer());
                habbo.getClient().sendResponse(new ChestDataComposer(chest, habbo));
                ChestFurniPackets.sendDelta(habbo.getClient(), chest.getId(), List.of(), storedRows);
            }
        }

        negotiation.session().consume(depositedIds);
        complete(habbo);
        return true;
    }

    /**
     * The one place in this class that reaches the persistence queue, so an item leaving a player for
     * good always leaves the same way.
     */
    private static void deleteFromDatabase(List<HabboItem> items) {
        if (items.isEmpty()) return;

        Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(items));
    }

    private synchronized Negotiation getNegotiation(Habbo habbo) {
        return habbo == null ? null : this.sessions.get(habbo.getHabboInfo().getId());
    }

    /**
     * Fire the room's transaction triggers for how this negotiation ended.
     *
     * <p>Init Transaction used to raise these itself, because it knew the outcome the moment it ran.
     * A negotiated contract does not settle until the player confirms or walks away, so the triggers
     * have to fire from here or a room would react before anything had actually happened.
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
                habbo.getClient().sendResponse(new WiredTradeCancelledComposer(WiredTradingSession.FAILURE_LEFT_ROOM));
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

    private static java.util.function.IntUnaryOperator currencyLookup(Habbo habbo) {
        return type -> type < 0
                ? habbo.getHabboInfo().getCredits()
                : habbo.getHabboInfo().getCurrencyAmount(type);
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
        private final Map<Integer, HabboItem> held = new HashMap<>();

        private InventoryVault(Habbo habbo) {
            this.habbo = habbo;
        }

        @Override
        public synchronized boolean take(int itemId) {
            if (this.held.containsKey(itemId)) return false;

            HabboItem item = this.habbo.getInventory().getItemsComponent().getHabboItem(itemId);
            if (item == null) return false;
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

        /** The items still held, for a settlement that has to hand them somewhere other than back. */
        synchronized HabboItem release(int itemId) {
            return this.held.remove(itemId);
        }
    }
}
