package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A snapshot of what a contract asks for and hands back, taken when a negotiation opens.
 *
 * <p>The grammar is an OR of ANDs, the way the official requirements bubble reads it: the give side is
 * a set of <strong>alternatives</strong>, each alternative a set of terms that must all be met — "two
 * chairs and five coins, <em>or</em> one sofa". The get side is a single rule.
 *
 * <p>A snapshot rather than a live view on purpose. A negotiation can stay open for a minute while its
 * owner edits the contract furni; the price a player was shown has to be the price they pay.
 *
 * <p>Persistence belongs to {@link InteractionWiredContract}, which stores the same grammar in its own
 * payload. This type only carries it around and enforces the ceilings.
 */
public final class ContractRules {
    /** Caps taken from the official element editor. */
    public static final int MAX_COINS = 100_000;

    public static final int MAX_FURNI = 500;

    /** Bounds on the grammar itself, matching what the contract will accept on save. */
    public static final int MAX_RULES = 8;

    public static final int MAX_TERMS_PER_RULE = 8;

    private final List<List<Term>> giveRules;
    private final List<Term> getRule;

    private ContractRules(List<List<Term>> giveRules, List<Term> getRule) {
        this.giveRules = giveRules;
        this.getRule = getRule;
    }

    /** The alternatives a player may satisfy, in the owner's order. Never empty. */
    public List<List<Term>> giveRules() {
        return this.giveRules;
    }

    /** What the contract hands back. All of it, together. */
    public List<Term> getRule() {
        return this.getRule;
    }

    /** True when the contract asks for nothing, so the window has no requirements to show. */
    public boolean asksForNothing() {
        for (List<Term> rule : this.giveRules) {
            if (!rule.isEmpty()) return false;
        }
        return true;
    }

    /** True when the contract hands back nothing, so there is no reward half. */
    public boolean givesNothing() {
        return this.getRule.isEmpty();
    }

    /** Take the snapshot from a contract furni. */
    public static ContractRules from(InteractionWiredContract contract) {
        return contract == null ? of(List.of(), List.of()) : of(contract.getGiveRules(), contract.getGetRule());
    }

    /**
     * Merge several contracts into one negotiation. The give sides are concatenated as further
     * alternatives and the rewards are pooled, which is what selecting more than one contract on a
     * single Init Transaction has always meant.
     */
    public static ContractRules from(List<InteractionWiredContract> contracts) {
        if (contracts == null || contracts.isEmpty()) return of(List.of(), List.of());

        List<List<Term>> give = new ArrayList<>();
        List<Term> get = new ArrayList<>();
        for (InteractionWiredContract contract : contracts) {
            if (contract == null) continue;
            give.addAll(contract.getGiveRules());
            get.addAll(contract.getGetRule());
        }
        return of(give, get);
    }

    /** Build from an explicit grammar, clamping to the ceilings and copying every term. */
    public static ContractRules of(List<List<Term>> giveRules, List<Term> getRule) {
        List<List<Term>> rules = new ArrayList<>();

        if (giveRules != null) {
            for (List<Term> rule : giveRules) {
                if (rules.size() >= MAX_RULES) break;
                rules.add(clamp(rule, InteractionWiredContract.DIR_PAY));
            }
        }
        if (rules.isEmpty()) rules.add(List.of());

        return new ContractRules(
                Collections.unmodifiableList(rules), clamp(getRule, InteractionWiredContract.DIR_RECEIVE));
    }

    /**
     * Copy a rule, dropping what cannot be paid and holding amounts to the official ceilings.
     *
     * <p>Copying matters as much as clamping: the terms belong to a furni whose owner can edit it, and
     * a negotiation must not silently change price underneath the player looking at it.
     */
    private static List<Term> clamp(List<Term> rule, int direction) {
        List<Term> out = new ArrayList<>();
        if (rule == null) return List.copyOf(out);

        for (Term term : rule) {
            if (term == null || term.amount <= 0) continue;
            if (out.size() >= MAX_TERMS_PER_RULE) break;

            int amount = Math.min(term.amount, term.isFurni() ? MAX_FURNI : MAX_COINS);
            out.add(
                    term.isFurni()
                            ? Term.furni(direction, term.wallItem, term.baseItemId, term.posterId(), amount)
                            : Term.currency(direction, term.currencyType, amount));
        }
        return List.copyOf(out);
    }
}
