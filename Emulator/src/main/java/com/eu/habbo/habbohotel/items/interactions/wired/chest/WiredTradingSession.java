package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.Match;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * One player's open negotiation with a wired contract.
 *
 * <p>A contract used to settle in a single instant: validate everything, apply everything, done. The
 * official client instead opens a trade-shaped window and walks five states — the player puts items
 * on the table, accepts, waits out a short countdown, then confirms. This is the server half of that
 * walk. It owns the state, the offered items and the clock; it decides nothing about packets, which
 * keeps the whole thing testable without a running hotel.
 *
 * <p>Two rules carry the safety. Items placed on the table are <strong>taken out of the inventory
 * while they sit there</strong>, exactly as a normal trade does, so the same chair cannot be offered
 * here and sold in the marketplace at once. And <strong>any change to the offer clears the
 * acceptance</strong> and drops back to {@link #STATE_ADDING_ITEMS}, so nothing can be slipped in
 * under a player who has already agreed.
 */
public class WiredTradingSession {
    public static final int STATE_READY = 0;
    public static final int STATE_ADDING_ITEMS = 1;
    public static final int STATE_COUNTDOWN = 2;
    public static final int STATE_CONFIRMING = 3;
    public static final int STATE_CONFIRMED = 4;

    /** Same ceiling the room trade uses; the window has no more room than that either. */
    public static final int MAX_OFFERED_ITEMS = 100;

    /**
     * The client counts three seconds between accepting and being allowed to confirm. The server does
     * not trust that clock, it only refuses a confirmation that arrives implausibly early.
     */
    public static final int CONFIRM_DELAY_MILLIS = 2_500;

    /** Reason ids on the cancellation. Zero is deliberate: the client stays silent for it. */
    public static final int FAILURE_SILENT = 0;

    public static final int FAILURE_TIMEOUT = 1;
    public static final int FAILURE_REQUIREMENTS = 2;
    public static final int FAILURE_LEFT_ROOM = 3;
    public static final int FAILURE_CONTRACT_GONE = 4;
    public static final int FAILURE_REWARD_UNAVAILABLE = 5;

    /**
     * The inventory seen from the session's side. Narrow on purpose: the state machine has no reason
     * to know what a {@code HabboItem} is, and a fake makes every transition testable.
     */
    public interface ItemVault {
        /** Remove the item from the player's inventory and hold it. False when it is not available. */
        boolean take(int itemId);

        /** Hand a held item back, on removal or on any cancellation. */
        void giveBack(int itemId);

        /** What the item is, for matching against the contract. Null when it cannot be resolved. */
        OfferedItem describe(int itemId);
    }

    private final ContractRules rules;
    private final ItemVault vault;
    private final IntUnaryOperator walletBalance;
    private final int timeoutSeconds;
    private final long openedAtMillis;

    /** Insertion-ordered so the window lists items the way the player added them. */
    private final Map<Integer, OfferedItem> offered = new LinkedHashMap<>();

    private int state = STATE_READY;
    private long acceptedAtMillis = 0L;
    private boolean closed = false;

    public WiredTradingSession(
            ContractRules rules,
            ItemVault vault,
            IntUnaryOperator walletBalance,
            int timeoutSeconds,
            long openedAtMillis) {
        this.rules = rules;
        this.vault = vault;
        this.walletBalance = walletBalance;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.openedAtMillis = openedAtMillis;
        this.state = STATE_ADDING_ITEMS;
    }

    public int getState() {
        return this.state;
    }

    public boolean isClosed() {
        return this.closed;
    }

    public ContractRules getRules() {
        return this.rules;
    }

    /** The items on the table, in the order the player put them there. */
    public List<OfferedItem> getOfferedItems() {
        return List.copyOf(this.offered.values());
    }

    /** Seconds before the offer lapses. Never negative; zero means it has lapsed. */
    public int secondsLeft(long nowMillis) {
        long elapsed = (nowMillis - this.openedAtMillis) / 1000L;
        return (int) Math.max(0L, this.timeoutSeconds - elapsed);
    }

    public boolean hasExpired(long nowMillis) {
        return secondsLeft(nowMillis) <= 0;
    }

    /** Which alternative the current offer pays for, and what is still short. */
    public Match evaluate() {
        return ContractRequirementEvaluator.firstSatisfied(this.rules, this.walletBalance, getOfferedItems());
    }

    /** True when the player may press accept: the offer covers one of the alternatives. */
    public boolean canAccept() {
        return !this.closed && evaluate().satisfied();
    }

    /**
     * Put items on the table. Only meaningful while adding; anything already offered, unresolvable or
     * beyond the ceiling is skipped rather than failing the whole batch.
     *
     * @return how many were actually taken
     */
    public synchronized int offerItems(List<Integer> itemIds) {
        if (this.closed || this.state != STATE_ADDING_ITEMS || itemIds == null) return 0;

        int taken = 0;
        for (Integer itemId : itemIds) {
            if (itemId == null || this.offered.containsKey(itemId)) continue;
            if (this.offered.size() >= MAX_OFFERED_ITEMS) break;

            OfferedItem described = this.vault.describe(itemId);
            if (described == null) continue;
            if (!this.vault.take(itemId)) continue;

            this.offered.put(itemId, described);
            taken++;
        }

        if (taken > 0) clearAcceptance();
        return taken;
    }

    /** Take items back off the table and return them to the inventory. */
    public synchronized int withdrawItems(List<Integer> itemIds) {
        if (this.closed || itemIds == null) return 0;

        int returned = 0;
        for (Integer itemId : itemIds) {
            if (itemId == null || !this.offered.containsKey(itemId)) continue;

            this.offered.remove(itemId);
            this.vault.giveBack(itemId);
            returned++;
        }

        if (returned > 0) clearAcceptance();
        return returned;
    }

    /**
     * The first half of accepting: the player agrees, and the countdown starts. Refused unless the
     * offer actually covers a rule, so the countdown never runs on an offer that cannot settle.
     */
    public synchronized boolean accept(long nowMillis) {
        if (this.closed || this.state != STATE_ADDING_ITEMS || !canAccept()) return false;

        this.state = STATE_COUNTDOWN;
        this.acceptedAtMillis = nowMillis;
        return true;
    }

    /**
     * The countdown has run out and the player may now confirm. Driven by the client, so the delay is
     * checked rather than trusted.
     */
    public synchronized boolean readyToConfirm(long nowMillis) {
        if (this.closed || this.state != STATE_COUNTDOWN) return false;
        if (nowMillis - this.acceptedAtMillis < CONFIRM_DELAY_MILLIS) return false;

        this.state = STATE_CONFIRMING;
        return true;
    }

    /**
     * The second half: the player confirms. Answers with the alternative to settle, or a failure id
     * when the offer stopped covering anything between accepting and confirming.
     *
     * <p>Moving to {@link #STATE_CONFIRMED} here is what makes the settlement single-shot: a second
     * confirmation finds the wrong state and is refused, so nothing can be paid out twice.
     */
    public synchronized Confirmation confirm(long nowMillis) {
        if (this.closed) return Confirmation.refused(FAILURE_SILENT);
        if (this.state == STATE_COUNTDOWN) readyToConfirm(nowMillis);
        if (this.state != STATE_CONFIRMING) return Confirmation.refused(FAILURE_SILENT);
        if (hasExpired(nowMillis)) return Confirmation.refused(FAILURE_TIMEOUT);

        Match match = evaluate();
        if (!match.satisfied()) return Confirmation.refused(FAILURE_REQUIREMENTS);

        this.state = STATE_CONFIRMED;
        return Confirmation.settle(match);
    }

    /**
     * Close the session and hand every held item back. Safe to call twice; the second call returns
     * nothing, which is what keeps a timeout racing a cancellation from duplicating items.
     */
    public synchronized List<Integer> close() {
        if (this.closed) return List.of();

        this.closed = true;
        this.state = STATE_READY;

        List<Integer> returned = new ArrayList<>(this.offered.keySet());
        for (Integer itemId : returned) this.vault.giveBack(itemId);
        this.offered.clear();
        return returned;
    }

    /**
     * Consume the items a settled rule claimed. They are already out of the inventory; this only
     * stops {@link #close()} handing them back afterwards.
     */
    public synchronized void consume(List<Integer> itemIds) {
        if (itemIds == null) return;
        for (Integer itemId : itemIds) this.offered.remove(itemId);
    }

    private void clearAcceptance() {
        this.acceptedAtMillis = 0L;
        if (this.state == STATE_COUNTDOWN || this.state == STATE_CONFIRMING) {
            this.state = STATE_ADDING_ITEMS;
        }
    }

    /** The answer to a confirmation: settle this alternative, or fail for this reason. */
    public record Confirmation(boolean settled, Match match, int failureId) {
        static Confirmation settle(Match match) {
            return new Confirmation(true, match, FAILURE_SILENT);
        }

        static Confirmation refused(int failureId) {
            return new Confirmation(false, null, failureId);
        }
    }
}
