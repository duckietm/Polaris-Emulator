package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.earnings.WiredEarnings;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.Match;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTransactionVariables;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCancelledComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCompletedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeItemsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeOpenComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionSuccessComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** The source label of a failed contract trade on the wired monitor. */
    static final String MONITOR_SOURCE = "contract";

    /** How long a negotiation Cancel Transaction ended waits for its replacement before the window is told. */
    static final long REPLACEMENT_GRACE_MS = 1000L;

    private final Room room;
    private final Map<Integer, Negotiation> sessions = new HashMap<>();

    /** Players whose negotiation was cancelled to be replaced, with the reason to send if it is not. */
    private final Map<Integer, Integer> pendingCancels = new HashMap<>();

    /**
     * A session plus what settling it needs: the chests it uses and the reward's text.
     *
     * <p>{@code depositOnly} separates the two reasons a table opens. A contract judges the offer
     * against its rules and moves only what the satisfied rule claims. A deposit judges nothing: the
     * player is filling a chest, so everything they put down goes in.
     *
     * <p>{@code donation} is a payment contract in the official "something (donation)" mode: like a deposit it
     * takes whatever is offered, but it is a contract, so it settles into every chest it may use and fires the
     * transaction triggers.
     *
     * <p>{@code contractIds} are the contract furni the negotiation was started from, which is what
     * Cancel Transaction's "specific contract" matches on.
     */
    private record Negotiation(
            WiredTradingSession session,
            List<InteractionWiredChest> chests,
            InventoryVault vault,
            boolean depositOnly,
            int contractType,
            String rewardText,
            boolean donation,
            List<Integer> contractIds) {}

    public WiredTradingManager(Room room) {
        this.room = room;
    }

    /** The negotiation this player has open, or null. */
    public synchronized WiredTradingSession getSession(Habbo habbo) {
        Negotiation negotiation =
                habbo == null ? null : this.sessions.get(habbo.getHabboInfo().getId());
        return negotiation == null ? null : negotiation.session();
    }

    /** The first contract furni the player's negotiation was started from; 0 for none or no negotiation. */
    public synchronized int contractId(Habbo habbo) {
        Negotiation negotiation =
                habbo == null ? null : this.sessions.get(habbo.getHabboInfo().getId());
        return negotiation == null ? 0 : firstOf(negotiation.contractIds());
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
        return open(
                habbo,
                rules,
                ContractMultiplier.NONE,
                contractType,
                rewardText,
                layoutType,
                Math.max(1, timeoutSeconds),
                chests,
                List.of());
    }

    /**
     * Open a contract trade that runs {@code multiplier} times ({@code rules} is the contract once),
     * started from {@code contractIds}. A {@code timeoutSeconds} of zero or less never lapses.
     */
    public WiredTradingSession open(
            Habbo habbo,
            ContractRules rules,
            ContractMultiplier multiplier,
            int contractType,
            String rewardText,
            String layoutType,
            int timeoutSeconds,
            List<InteractionWiredChest> chests,
            Collection<Integer> contractIds) {
        List<InteractionWiredChest> usable = WiredChestPayouts.usable(chests);
        if (usable.isEmpty()) {
            ChestTransactionFailures.send(habbo, ChestTransactionFailures.NO_OR_LOCKED_CHESTS);
            ChestTransactionFailures.noteOnMonitor(
                    this.room, ChestTransactionFailures.NO_OR_LOCKED_CHESTS, MONITOR_SOURCE, firstOf(contractIds));
            return null;
        }
        return this.start(
                habbo,
                rules,
                multiplier,
                contractType,
                rewardText,
                layoutType,
                timeoutSeconds,
                usable,
                false,
                false,
                WiredTradeOpenComposer.REQUIREMENT_RULES,
                contractIds);
    }

    /**
     * Open the table for a payment contract in donation mode (AIR {@code PaymentContract} mode 0): the player may
     * offer anything, and everything offered goes into the chests.
     */
    public WiredTradingSession openDonation(
            Habbo habbo,
            String receiveText,
            String layoutType,
            int timeoutSeconds,
            List<InteractionWiredChest> chests) {
        return openDonation(habbo, receiveText, layoutType, Math.max(1, timeoutSeconds), chests, List.of());
    }

    /**
     * A donation started from {@code contractIds}. It takes whatever is offered and has no amounts, so
     * a multiplier has nothing to scale; a {@code timeoutSeconds} of zero or less never lapses.
     */
    public WiredTradingSession openDonation(
            Habbo habbo,
            String receiveText,
            String layoutType,
            int timeoutSeconds,
            List<InteractionWiredChest> chests,
            Collection<Integer> contractIds) {
        List<InteractionWiredChest> usable = WiredChestPayouts.usable(chests);
        if (usable.isEmpty()) {
            ChestTransactionFailures.send(habbo, ChestTransactionFailures.NO_OR_LOCKED_CHESTS);
            ChestTransactionFailures.noteOnMonitor(
                    this.room, ChestTransactionFailures.NO_OR_LOCKED_CHESTS, MONITOR_SOURCE, firstOf(contractIds));
            return null;
        }
        return this.start(
                habbo,
                ContractRules.of(List.of(List.of()), List.of()),
                ContractMultiplier.NONE,
                InteractionWiredContract.TYPE_PAYMENT,
                receiveText,
                layoutType,
                timeoutSeconds,
                usable,
                false,
                true,
                WiredTradeOpenComposer.REQUIREMENT_ANYTHING,
                contractIds);
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
                ContractMultiplier.NONE,
                CONTRACT_TYPE_CHEST_DEPOSIT,
                "",
                LAYOUT_GENERIC,
                Math.max(1, timeoutSeconds),
                List.of(chest),
                true,
                false,
                requirement,
                List.of());
    }

    private WiredTradingSession start(
            Habbo habbo,
            ContractRules rules,
            ContractMultiplier multiplier,
            int contractType,
            String rewardText,
            String layoutType,
            int timeoutSeconds,
            List<InteractionWiredChest> chests,
            boolean depositOnly,
            boolean donation,
            int requirementType,
            Collection<Integer> contractIds) {
        if (habbo == null || habbo.getClient() == null || rules == null) return null;

        boolean replaced;
        WiredTradingSession session;

        synchronized (this) {
            // A negotiation Cancel Transaction ended to be replaced counts as the previous trade: the window keeps
            // open and takes the new requirements.
            boolean awaited = this.pendingCancels.remove(habbo.getHabboInfo().getId()) != null;
            replaced = closeQuietly(habbo) || awaited;
            InventoryVault vault = new InventoryVault(habbo);
            // Coins are paid with credit furni, so the "wallet" a contract is judged against is the
            // value of the credit furni on the table.
            session = new WiredTradingSession(
                    rules, multiplier, vault, type -> vault.heldCredits(), timeoutSeconds, System.currentTimeMillis());
            // A deposit or a donation takes anything; a contract's table no more than the contract asks.
            if (depositOnly || donation) session.openTable();
            this.sessions.put(
                    habbo.getHabboInfo().getId(),
                    new Negotiation(
                            session,
                            List.copyOf(chests),
                            vault,
                            depositOnly,
                            contractType,
                            rewardText,
                            donation,
                            contractIds == null ? List.of() : List.copyOf(contractIds)));
        }

        habbo.getClient()
                .sendResponse(new WiredTradeOpenComposer(
                        requirementType,
                        rewardText,
                        layoutType,
                        rules.asksForNothing() && !depositOnly && !donation,
                        replaced,
                        timeoutSeconds,
                        rules,
                        session.getMultiplier()));
        pushState(habbo, session);
        if (!depositOnly) this.scheduleTimeout(habbo, session, timeoutSeconds);
        return session;
    }

    /**
     * AIR "cancel a trade automatically on timeout": a contract negotiation with a timeout ends when
     * it lapses, not only when a late confirmation arrives.
     */
    private void scheduleTimeout(Habbo habbo, WiredTradingSession session, int timeoutSeconds) {
        if (timeoutSeconds <= 0) return;
        ThreadPooling threading = Emulator.getThreading();
        if (threading == null) return;
        threading.run(() -> this.expire(habbo, session, System.currentTimeMillis()), timeoutSeconds * 1000L + 250L);
    }

    /**
     * End the player's negotiation with the timeout reason when it is still {@code session} and has
     * lapsed by {@code nowMillis}. One replaced, settled or cancelled in the meantime is left alone.
     *
     * @return true when it was ended
     */
    public boolean expire(Habbo habbo, WiredTradingSession session, long nowMillis) {
        Negotiation negotiation = getNegotiation(habbo);
        if (negotiation == null || negotiation.session() != session || !session.hasExpired(nowMillis)) return false;

        cancel(habbo, WiredTradingSession.FAILURE_TIMEOUT);
        return true;
    }

    /**
     * Cancel Transaction: end the player's running contract negotiation with the official "trade
     * cancelled" reason. With {@code contractIds} only a negotiation started from one of those
     * contracts ends (match criteria 0); null ends any (1). A plain chest deposit is the player's own
     * and is left alone.
     *
     * @return true when a negotiation was ended
     */
    public boolean cancelRunning(Habbo habbo, Collection<Integer> contractIds) {
        Negotiation negotiation = getNegotiation(habbo);
        if (negotiation == null || negotiation.depositOnly()) return false;
        if (contractIds != null && !startedFromAny(negotiation, contractIds)) return false;

        cancelWithReason(habbo, ChestTransactionFailures.TRADE_CANCELLED);
        return true;
    }

    /**
     * Cancel Transaction with an Initiate Transaction stacked on top of it (AIR {@code
     * wiredfurni.params.cancel_transaction.usage_info}: the running transaction is replaced without refreshing the
     * trade window). Ends the negotiation as {@link #cancelRunning} does -- the offer goes back and the failure
     * triggers fire -- but holds back the "trade cancelled" message: the negotiation opened next for the player is
     * sent as overriding the previous trade (AIR {@code WiredTradingModel.onWiredTradeInitiate} then swaps the
     * requirements in the open window and highlights them). When nothing replaces it, {@link #flushReplacement}
     * sends the held message, and so does a short timer as the last resort.
     *
     * @return true when a negotiation was ended
     */
    public boolean cancelForReplacement(Habbo habbo, Collection<Integer> contractIds) {
        Negotiation negotiation = getNegotiation(habbo);
        if (negotiation == null || negotiation.depositOnly()) return false;
        if (contractIds != null && !startedFromAny(negotiation, contractIds)) return false;

        int userId = habbo.getHabboInfo().getId();
        synchronized (this) {
            if (this.sessions.get(userId) != negotiation) return false;
            this.sessions.remove(userId);
            this.pendingCancels.put(userId, ChestTransactionFailures.TRADE_CANCELLED);
        }

        returnHeldItems(habbo, negotiation.session());
        raiseFailure(habbo, ChestTransactionFailures.TRADE_CANCELLED);

        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) threading.run(() -> this.flushReplacement(habbo), REPLACEMENT_GRACE_MS);
        return true;
    }

    /**
     * Send the "trade cancelled" message {@link #cancelForReplacement} held back, when no negotiation replaced the
     * cancelled one.
     *
     * @return true when the message was sent
     */
    public boolean flushReplacement(Habbo habbo) {
        if (habbo == null) return false;

        Integer reason;
        synchronized (this) {
            reason = this.pendingCancels.remove(habbo.getHabboInfo().getId());
        }
        if (reason == null) return false;

        if (habbo.getClient() != null) habbo.getClient().sendResponse(new WiredTradeCancelledComposer(reason));
        return true;
    }

    /** Whether a cancelled negotiation of the player is waiting to be replaced. */
    public synchronized boolean awaitsReplacement(Habbo habbo) {
        return habbo != null
                && this.pendingCancels.containsKey(habbo.getHabboInfo().getId());
    }

    private static boolean startedFromAny(Negotiation negotiation, Collection<Integer> contractIds) {
        for (Integer id : negotiation.contractIds()) {
            if (contractIds.contains(id)) return true;
        }
        return false;
    }

    /**
     * Put items on the table or take them off. Items that may not be offered here are refused with
     * the official trade error (0 not a valid item, 1 too many items, 2 too many kinds, 4 the requirements
     * are already met, 5 more than the contract asks, 9 more than the chest holds).
     *
     * @return how many items moved
     */
    public int offer(Habbo habbo, List<Integer> itemIds, boolean remove) {
        Negotiation negotiation = getNegotiation(habbo);
        if (negotiation == null || itemIds == null || itemIds.isEmpty()) return 0;

        WiredTradingSession session = negotiation.session();
        if (remove) return session.withdrawItems(itemIds);

        List<Integer> allowed = new ArrayList<>();
        List<OfferedItem> pending = new ArrayList<>();
        int error = -1;
        int already = session.getOfferedItems().size();
        Set<String> kinds = new HashSet<>();
        for (HabboItem held : negotiation.vault().heldItems()) kinds.add(tableKind(held));
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
            String kind = tableKind(item);
            if (!kinds.contains(kind) && kinds.size() >= WiredTradingSession.MAX_OFFERED_TYPES) {
                error = WiredTradeErrorComposer.TOO_MANY_DIFFERENT_ITEMS;
                continue;
            }
            if (negotiation.depositOnly() && exceedsDepositRoom(negotiation, already + allowed.size() + 1, item)) {
                error = WiredTradeErrorComposer.EXCEEDS_CAPACITY;
                break;
            }
            OfferedItem described = negotiation.vault().describe(itemId);
            int refusal = session.offerRefusal(pending, described);
            if (refusal == WiredTradingSession.OFFER_ALREADY_MET) {
                error = WiredTradeErrorComposer.ALREADY_MET;
                break;
            }
            if (refusal == WiredTradingSession.OFFER_EXCEEDS) {
                error = WiredTradeErrorComposer.EXCEEDS_REQUIREMENTS;
                continue;
            }
            kinds.add(kind);
            allowed.add(itemId);
            pending.add(described);
        }

        if (error >= 0 && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new WiredTradeErrorComposer(error));
        }
        return allowed.isEmpty() ? 0 : session.offerItems(allowed);
    }

    /**
     * The slot an item takes on the table, as the client groups them: one per furni type (a poster per
     * poster), and one of its own for furni that does not stack or is limited.
     */
    static String tableKind(HabboItem item) {
        if (item.getBaseItem() == null || !item.getBaseItem().allowInventoryStack() || item.isLimited()) {
            return "item:" + item.getId();
        }
        boolean wall = item.getBaseItem().getType() == FurnitureType.WALL;
        String kind = (wall ? "I" : "S") + item.getBaseItem().getId();
        return wall && "poster".equals(item.getBaseItem().getName()) ? kind + ":" + item.getExtradata() : kind;
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

        // The reward as it would be paid now: the contract times the multiplier or the offer's count.
        List<Term> reward = session.effectiveRules().getRule();
        List<ChestFurniStoredItem> rewardFurni = negotiation == null || negotiation.depositOnly()
                ? List.of()
                : WiredChestPayouts.preview(negotiation.chests(), reward);
        boolean plainTable = negotiation != null && (negotiation.depositOnly() || negotiation.donation());
        boolean canAccept = plainTable ? !session.getOfferedItems().isEmpty() : session.canAccept();
        // AIR reads the extra as the "met N times" count of the two multiplier modes.
        int extra = plainTable || session.getMultiplier().mode() == ContractMultiplier.MODE_NORMAL
                ? (canAccept ? 1 : 0)
                : session.timesMet();

        habbo.getClient()
                .sendResponse(new WiredTradeItemsComposer(
                        habbo.getHabboInfo().getId(),
                        offered,
                        credits,
                        rewardFurni,
                        WiredChestPayouts.coins(reward),
                        canAccept,
                        extra));
    }

    /**
     * The player left the room: whatever they had on a wired table goes back to them and the window closes ("trade
     * cancelled"); a contract's transaction failed triggers fire. Without this a negotiation without a timeout (the
     * Initiate Transaction default) held the offer until the room unloaded.
     */
    public void leftRoom(Habbo habbo) {
        if (habbo == null) return;
        synchronized (this) {
            this.pendingCancels.remove(habbo.getHabboInfo().getId());
        }
        cancel(habbo, WiredTradingSession.FAILURE_LEFT_ROOM);
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

        if (!negotiation.depositOnly()) {
            ChestTransactionFailures.noteOnMonitor(
                    this.room,
                    ChestTransactionFailures.fromSession(failureId),
                    MONITOR_SOURCE,
                    firstOf(negotiation.contractIds()));
            raiseFailure(habbo, ChestTransactionFailures.fromSession(failureId));
        }
    }

    /** End a negotiation that settled. The items it consumed are gone by then, by design. */
    public void complete(Habbo habbo) {
        complete(habbo, -1, null);
    }

    /**
     * {@code totals} is what the transaction moved, for {@code @event.transaction_complete.*}; null for a deposit, which
     * fires nothing.
     */
    private void complete(Habbo habbo, int successType, WiredEvent.TransactionTotals totals) {
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

        if (!negotiation.depositOnly()) {
            raise(habbo, WiredEvent.Type.TRANSACTION_COMPLETE, ChestTransactionFailures.NONE, totals);
        }
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

        if (negotiation.donation()) {
            if (!confirmation.settled()) {
                cancel(habbo, confirmation.failureId());
                return false;
            }
            return settleDonation(habbo, negotiation);
        }

        if (!confirmation.settled()) {
            cancel(habbo, confirmation.failureId());
            return false;
        }

        // The contract as many times over as the confirmation settled: the price, the reward and the
        // chest checks all use the multiplied amounts.
        Match match = confirmation.match();
        ContractRules settled =
                negotiation.session().getRules().times(negotiation.session().settledTimes());
        List<Term> paidRule = settled.giveRules().get(match.ruleIndex());
        List<Term> reward = settled.getRule();
        List<InteractionWiredChest> chests = this.usableHere(negotiation);
        WiredEvent.TransactionTotals[] totals = {null};

        int failure = WiredChestPayouts.holding(chests, () -> {
            if (chests.isEmpty()) return ChestTransactionFailures.NO_OR_LOCKED_CHESTS;

            int cover = WiredChestPayouts.coverFailure(chests, reward);
            if (cover != ChestTransactionFailures.NONE) return cover;

            // What pays: the furni the rule matched, plus enough credit furni for its coins.
            List<HabboItem> furniPayment = new ArrayList<>();
            for (Integer itemId : match.consumedItemIds()) {
                HabboItem item = negotiation.vault().peek(itemId);
                // The negotiation ended since the confirmation (timeout, cancel, leaving) and handed the table back.
                if (item == null) return ChestTransactionFailures.TRADE_CANCELLED;
                furniPayment.add(item);
            }
            List<HabboItem> creditPayment = pickCredits(
                    negotiation.vault().heldItems(), match.consumedItemIds(), WiredChestPayouts.coins(paidRule));
            if (creditPayment == null) return ChestTransactionFailures.INSUFFICIENT_FUNDS;

            int room = storeFailure(chests, furniPayment, creditPayment);
            if (room != ChestTransactionFailures.NONE) return room;

            Payment payment = pay(habbo, negotiation, chests, furniPayment, creditPayment);

            // Paid in part (a chest or the database refused some of it): the reward used to be handed out
            // anyway, and what was not taken went back to the player when the table closed. Now what
            // reached a chest comes back too, and nothing is given.
            if (payment.consumed().size() != furniPayment.size() + creditPayment.size()) {
                this.refundDeposits(habbo, payment);
                return ChestTransactionFailures.TRADE_CANCELLED;
            }
            negotiation.session().consume(payment.consumed());

            List<Term> given =
                    WiredChestPayouts.give(habbo, this.room, chests, reward, this.earningsCategory(negotiation));
            if (!given.isEmpty() && habbo.getClient() != null) {
                habbo.getClient()
                        .sendResponse(WiredTransactionSuccessComposer.reward(given, negotiation.rewardText(), false));
            }
            totals[0] = WiredTransactionVariables.totals(
                    negotiation.session().settledTimes(), furniPayment.size(), coinsOf(creditPayment), given);
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
                        : WiredTransactionSuccessComposer.TYPE_TRADE,
                totals[0]);
        return true;
    }

    /**
     * The earnings window row the reward's credits land in: the earnings category of the first reward contract the
     * negotiation started from (AIR {@code RewardContract}), else games.
     */
    private int earningsCategory(Negotiation negotiation) {
        if (this.room != null) {
            for (Integer id : negotiation.contractIds()) {
                if (this.room.getHabboItem(id) instanceof InteractionWiredContract contract
                        && contract.contractType() == InteractionWiredContract.TYPE_REWARD) {
                    return contract.getRewardCategory();
                }
            }
        }
        return WiredEarnings.CATEGORY_GAMES;
    }

    /**
     * The credit furni on the table that pay {@code coins}: all of them, worth exactly that. The table never holds more
     * than the contract asks (trade error 5) and only settles when it is the price exactly, so a 50 credit furni never
     * pays a 30 coin price. Null when they are not worth exactly {@code coins}.
     */
    static List<HabboItem> pickCredits(List<HabboItem> held, List<Integer> exclude, int coins) {
        List<HabboItem> credits = new ArrayList<>();
        long worth = 0;
        for (HabboItem item : held) {
            int value = ChestAirWire.creditValue(item);
            if (!exclude.contains(item.getId()) && value > 0) {
                credits.add(item);
                worth += value;
            }
        }
        return worth == Math.max(0, coins) ? credits : null;
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

    /** What a payment moved: every item id that left the table, and the furni ids each chest took. */
    record Payment(List<Integer> consumed, Map<InteractionWiredChest, List<Integer>> furniByChest) {}

    /** Gives back the furni a partly paid contract left in its chests. */
    private void refundDeposits(Habbo habbo, Payment payment) {
        for (Map.Entry<InteractionWiredChest, List<Integer>> deposit :
                payment.furniByChest().entrySet()) {
            List<Integer> ids = deposit.getValue();
            ChestWithdrawals.furni(
                    habbo,
                    deposit.getKey(),
                    this.room,
                    storage -> storage.removeFurniByInventoryIds(ids),
                    ChestTransactionLog.SOURCE_USER);
        }
    }

    /**
     * Move a payment off the table into the chests. The credit furni are redeemed last and only once all
     * the furni went in, so a partial payment never leaves coins behind that cannot be handed back.
     */
    private Payment pay(
            Habbo habbo,
            Negotiation negotiation,
            List<InteractionWiredChest> chests,
            List<HabboItem> furni,
            List<HabboItem> credits) {
        List<Integer> consumed = new ArrayList<>();
        Map<InteractionWiredChest, List<Integer>> furniByChest = new LinkedHashMap<>();
        int userId = habbo.getHabboInfo().getId();

        List<HabboItem> remaining = new ArrayList<>(furni);
        for (InteractionWiredChest chest : chests) {
            if (remaining.isEmpty()) break;
            if (chest.chestKind() != ChestStorage.KIND_FURNI) continue;

            List<HabboItem> accepted = chest.depositFurni(userId, remaining);
            for (HabboItem item : accepted) {
                negotiation.vault().release(item.getId());
                consumed.add(item.getId());
                furniByChest.computeIfAbsent(chest, key -> new ArrayList<>()).add(item.getId());
            }
            remaining.removeAll(accepted);
            if (!accepted.isEmpty()) this.afterDeposit(chest, habbo, accepted.size(), 0, accepted);
        }

        int coins = credits.stream().mapToInt(ChestAirWire::creditValue).sum();
        if (coins > 0 && remaining.isEmpty()) {
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
        return new Payment(consumed, furniByChest);
    }

    /**
     * Move everything on the table into the deposit's chest: furni into a furni chest, credit furni
     * into a coin chest as coins. What a full chest refuses goes back to the player.
     */
    private boolean settleDeposit(Habbo habbo, Negotiation negotiation) {
        InteractionWiredChest chest = negotiation.chests().get(0);
        if (!this.isInRoom(chest)) {
            cancelWithReason(habbo, ChestTransactionFailures.CHEST_NOT_IN_ROOM);
            return false;
        }
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
        complete(habbo, WiredTransactionSuccessComposer.TYPE_DEPOSIT, null);
        return true;
    }

    /**
     * Move everything on a donation's table into its chests: furni into the furni chests, credit furni into a coin
     * chest as coins. Unlike a deposit nothing is handed back: when the chests cannot take all of it, nothing moves
     * and the player is told why.
     */
    private boolean settleDonation(Habbo habbo, Negotiation negotiation) {
        List<HabboItem> offered = negotiation.vault().heldItems();
        if (offered.isEmpty()) {
            cancelWithReason(habbo, ChestTransactionFailures.EMPTY);
            return false;
        }

        List<HabboItem> credits = new ArrayList<>();
        List<HabboItem> furni = new ArrayList<>();
        for (HabboItem item : offered) (ChestAirWire.creditValue(item) > 0 ? credits : furni).add(item);

        List<InteractionWiredChest> chests = this.usableHere(negotiation);
        int failure = WiredChestPayouts.holding(chests, () -> {
            if (chests.isEmpty()) return ChestTransactionFailures.NO_OR_LOCKED_CHESTS;

            int room = storeFailure(chests, furni, credits);
            if (room != ChestTransactionFailures.NONE) return room;

            negotiation
                    .session()
                    .consume(pay(habbo, negotiation, chests, furni, credits).consumed());
            return ChestTransactionFailures.NONE;
        });

        if (failure != ChestTransactionFailures.NONE) {
            cancelWithReason(habbo, failure);
            return false;
        }

        complete(
                habbo,
                WiredTransactionSuccessComposer.TYPE_PAYMENT,
                WiredTransactionVariables.totals(1, furni.size(), coinsOf(credits), List.of()));
        return true;
    }

    /** The coins a set of credit furni is worth. */
    private static int coinsOf(List<HabboItem> credits) {
        long coins = 0;
        for (HabboItem item : credits) coins += ChestAirWire.creditValue(item);
        return (int) Math.min(coins, Integer.MAX_VALUE);
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
        if (!negotiation.depositOnly()) {
            ChestTransactionFailures.noteOnMonitor(
                    this.room, airReason, MONITOR_SOURCE, firstOf(negotiation.contractIds()));
            raiseFailure(habbo, airReason);
        }
    }

    /** The first contract a negotiation started from, for the monitor's detail line; 0 without one. */
    private static int firstOf(Collection<Integer> contractIds) {
        if (contractIds == null) return 0;
        for (Integer id : contractIds) {
            if (id != null) return id;
        }
        return 0;
    }

    /** The negotiation's chests that wired may still use and that are still in this room. */
    private List<InteractionWiredChest> usableHere(Negotiation negotiation) {
        List<InteractionWiredChest> chests = WiredChestPayouts.usable(negotiation.chests());
        chests.removeIf(chest -> !this.isInRoom(chest));
        return chests;
    }

    /** A chest picked up while a window was open is no longer a place to pay into or take a reward from. */
    private boolean isInRoom(InteractionWiredChest chest) {
        return this.room == null || chest.getRoomId() == this.room.getId();
    }

    private synchronized Negotiation getNegotiation(Habbo habbo) {
        return habbo == null ? null : this.sessions.get(habbo.getHabboInfo().getId());
    }

    /** TRANSACTION_FAIL with its reason, for {@code @event.transaction_failed.reason}. */
    private void raiseFailure(Habbo habbo, int reason) {
        this.raise(habbo, WiredEvent.Type.TRANSACTION_FAIL, reason, null);
    }

    /**
     * Fire the room's transaction triggers for how a contract trade ended. A negotiated contract does
     * not settle until the player confirms or walks away, so the triggers fire from here.
     */
    private void raise(Habbo habbo, WiredEvent.Type type, int failureReason, WiredEvent.TransactionTotals totals) {
        if (this.room == null || habbo == null) return;

        WiredEvent.Builder builder = WiredEvent.builder(type, this.room)
                .transactionFailureReason(failureReason)
                .transactionTotals(totals);
        if (habbo.getRoomUnit() != null) builder.actor(habbo.getRoomUnit());

        WiredManager.dispatchEffectTriggeredEvent(builder.build());
    }

    /** Drop every negotiation, handing every held item back. Called when the room lets go. */
    public void dispose() {
        Map<Integer, Negotiation> open;
        List<Integer> awaiting;
        synchronized (this) {
            open = new HashMap<>(this.sessions);
            this.sessions.clear();
            awaiting = new ArrayList<>(this.pendingCancels.keySet());
            this.pendingCancels.clear();
        }

        for (Integer userId : awaiting) {
            Habbo habbo = this.room == null ? null : this.room.getHabbo(userId);
            if (habbo != null && habbo.getClient() != null && !open.containsKey(userId)) {
                habbo.getClient()
                        .sendResponse(new WiredTradeCancelledComposer(ChestTransactionFailures.TRADE_CANCELLED));
            }
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

            boolean wall = item.getBaseItem().getType() == FurnitureType.WALL;
            String poster = wall && "poster".equals(item.getBaseItem().getName()) ? item.getExtradata() : "";
            return new OfferedItem(itemId, wall, item.getBaseItem().getId(), poster, ChestAirWire.creditValue(item));
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
