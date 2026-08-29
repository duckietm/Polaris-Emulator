package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The requirement grammar of a wired contract: what a player has to give, and what they get back.
 *
 * <p>A contract used to be one flat list of terms, all of which had to be met. The official client
 * asks a richer question: the <em>give</em> side is a set of <strong>alternatives</strong> and each
 * alternative is a set of terms that must all be met — "two chairs and five coins, <em>or</em> one
 * sofa". The requirements bubble renders exactly that, with {@code or} between rules and {@code &}
 * between the terms inside one. The <em>get</em> side stays a single rule, as it does officially.
 *
 * <p>Wire compatibility is kept by appending rather than replacing. {@link ContractTerm} still owns
 * the term encoding and still reads first; the rule partition rides in a tail after it, behind
 * {@link #RULES_MARKER}. A contract saved before this existed has no tail and reads back as one
 * alternative holding every PAY term — which is what it always meant.
 */
public final class ContractRules {
    /**
     * Sentinel that opens the rule tail. Chosen outside any plausible term value so a truncated or
     * hand-edited payload cannot be mistaken for a partition.
     */
    public static final int RULES_MARKER = 0x57524C31; // "WRL1"

    /** Caps taken from the official element editor. */
    public static final int MAX_COINS = 100_000;

    public static final int MAX_FURNI = 500;

    /** Bounds on the partition itself, so a crafted payload cannot allocate without limit. */
    public static final int MAX_RULES = 8;

    public static final int MAX_TERMS_PER_RULE = 10;

    private final List<List<ContractTerm>> giveRules;
    private final List<ContractTerm> getRule;

    private ContractRules(List<List<ContractTerm>> giveRules, List<ContractTerm> getRule) {
        this.giveRules = giveRules;
        this.getRule = getRule;
    }

    /** The alternatives a player may satisfy. Never empty for a contract that asks for anything. */
    public List<List<ContractTerm>> giveRules() {
        return this.giveRules;
    }

    /** What the contract hands back. All of it, together. */
    public List<ContractTerm> getRule() {
        return this.getRule;
    }

    /** True when the contract asks for nothing — the official "payment only" shape inverted. */
    public boolean asksForNothing() {
        for (List<ContractTerm> rule : this.giveRules) {
            if (!rule.isEmpty()) return false;
        }
        return true;
    }

    /** True when the contract hands back nothing, so there is no reward half to show. */
    public boolean givesNothing() {
        return this.getRule.isEmpty();
    }

    /**
     * Read the rules out of a saved contract. Falls back to a single alternative when the payload
     * predates the tail, which is the honest reading of a flat list.
     */
    public static ContractRules parse(int[] params, String stringParam) {
        List<ContractTerm> terms = ContractTerm.parse(params, stringParam);

        List<ContractTerm> pay = new ArrayList<>();
        List<ContractTerm> receive = new ArrayList<>();
        for (ContractTerm term : terms) {
            if (term.direction == ContractTerm.DIR_RECEIVE) receive.add(term);
            else pay.add(term);
        }

        List<Integer> partition = readPartition(params, terms.size());
        List<List<ContractTerm>> giveRules = new ArrayList<>();

        if (partition == null) {
            giveRules.add(pay);
        } else {
            int cursor = 0;
            for (int length : partition) {
                List<ContractTerm> rule = new ArrayList<>();
                for (int i = 0; i < length && cursor < pay.size(); i++) {
                    rule.add(pay.get(cursor++));
                }
                giveRules.add(rule);
            }
            // Terms the partition failed to account for still have to go somewhere: dropping them
            // would quietly make a contract cheaper than its owner set it.
            if (cursor < pay.size()) {
                List<ContractTerm> tail = new ArrayList<>(pay.subList(cursor, pay.size()));
                if (giveRules.isEmpty()) giveRules.add(tail);
                else giveRules.get(giveRules.size() - 1).addAll(tail);
            }
            if (giveRules.isEmpty()) giveRules.add(new ArrayList<>());
        }

        return new ContractRules(unmodifiable(giveRules), List.copyOf(receive));
    }

    /** Build from an explicit partition, clamping to the caps before anything is persisted. */
    public static ContractRules of(List<List<ContractTerm>> giveRules, List<ContractTerm> getRule) {
        List<List<ContractTerm>> rules = new ArrayList<>();

        if (giveRules != null) {
            for (List<ContractTerm> rule : giveRules) {
                if (rules.size() >= MAX_RULES) break;
                rules.add(clampRule(rule, ContractTerm.DIR_PAY));
            }
        }
        if (rules.isEmpty()) rules.add(new ArrayList<>());

        return new ContractRules(unmodifiable(rules), List.copyOf(clampRule(getRule, ContractTerm.DIR_RECEIVE)));
    }

    /**
     * Flatten back to the {@code intParams} a contract persists: every term in order, PAY grouped by
     * alternative, then the tail describing that grouping.
     */
    public int[] serialize() {
        List<ContractTerm> flat = flatten();
        int[] terms = ContractTerm.serialize(flat);

        int[] out = new int[terms.length + 2 + this.giveRules.size()];
        System.arraycopy(terms, 0, out, 0, terms.length);
        out[terms.length] = RULES_MARKER;
        out[terms.length + 1] = this.giveRules.size();
        for (int i = 0; i < this.giveRules.size(); i++) {
            out[terms.length + 2 + i] = this.giveRules.get(i).size();
        }
        return out;
    }

    /** Poster ids ride alongside, indexed against the same flattened order. */
    public String serializePosters() {
        return ContractTerm.serializePosters(flatten());
    }

    /** PAY terms first, grouped by alternative, then the RECEIVE rule. */
    public List<ContractTerm> flatten() {
        List<ContractTerm> flat = new ArrayList<>();
        for (List<ContractTerm> rule : this.giveRules) flat.addAll(rule);
        flat.addAll(this.getRule);
        return flat;
    }

    /**
     * Read the partition tail. Returns {@code null} when there is none, which means "one alternative"
     * rather than "no alternatives" — the difference matters for pre-existing contracts.
     */
    private static List<Integer> readPartition(int[] params, int termCount) {
        if (params == null || termCount < 0) return null;

        int start = 1 + termCount * ContractTerm.STRIDE_V2;
        if (start + 1 >= params.length || params[start] != RULES_MARKER) return null;

        int ruleCount = params[start + 1];
        if (ruleCount <= 0 || ruleCount > MAX_RULES) return null;
        if (start + 2 + ruleCount > params.length) return null;

        List<Integer> partition = new ArrayList<>(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            int length = params[start + 2 + i];
            if (length < 0 || length > MAX_TERMS_PER_RULE) return null;
            partition.add(length);
        }
        return partition;
    }

    private static List<ContractTerm> clampRule(List<ContractTerm> rule, int direction) {
        List<ContractTerm> out = new ArrayList<>();
        if (rule == null) return out;

        for (ContractTerm term : rule) {
            if (term == null || term.amount <= 0) continue;
            if (out.size() >= MAX_TERMS_PER_RULE) break;

            int cap = term.isFurni() ? MAX_FURNI : MAX_COINS;
            int amount = Math.min(term.amount, cap);
            out.add(
                    term.isFurni()
                            ? ContractTerm.furni(direction, term.wallItem, term.baseItemId, term.legacyPosterId, amount)
                            : ContractTerm.currency(direction, term.currencyType, amount));
        }
        return out;
    }

    private static List<List<ContractTerm>> unmodifiable(List<List<ContractTerm>> rules) {
        List<List<ContractTerm>> out = new ArrayList<>(rules.size());
        for (List<ContractTerm> rule : rules) out.add(List.copyOf(rule));
        return Collections.unmodifiableList(out);
    }
}
