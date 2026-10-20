package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Decides whether what a player has put on the table satisfies a contract, and which alternative it
 * satisfies.
 *
 * <p>The two halves of a rule are checked differently, the way the official window behaves. Furni has
 * to be <strong>offered</strong> — physically dragged into the trade grid, so the player sees exactly
 * which of their items is about to leave. Currency is not draggable; it is checked against the wallet
 * and taken at confirmation. A rule mixing both is satisfied only when both halves are.
 *
 * <p>Alternatives are tried in order and the first satisfied one wins, so the owner's ordering is the
 * priority: put the cheapest option first and that is the one a player pays.
 *
 * <p>A negotiation is judged the way AIR's trade errors describe it ({@code
 * wired_transactions.notification.trade_error.4} "you have already met the requirements", {@code .5} "exceeds the
 * offering requirements"): the table may never hold more than an alternative asks, so the offer is "met" only when it
 * is exactly one alternative, coins included ({@link #fits}, {@link #firstExact}). The older at-least check
 * ({@link #firstSatisfied}) still counts how many runs an offer covers.
 *
 * <p>Deliberately free of emulator types: it takes a wallet lookup and a flat list of offered items,
 * which keeps the whole decision unit-testable without a running hotel.
 */
public final class ContractRequirementEvaluator {
    private ContractRequirementEvaluator() {}

    /**
     * One item a player has put on the table. {@code posterId} is a poster's poster id (its extradata), empty for
     * anything else: posters share one base item, so a term naming a poster is paid only by that poster. {@code
     * creditValue} is what a credit furni is worth (0 for any other furni): those are the offer's coins, never one of
     * its furni.
     */
    public record OfferedItem(int itemId, boolean wallItem, int baseItemId, String posterId, int creditValue) {
        public OfferedItem {
            posterId = posterId == null ? "" : posterId;
            creditValue = Math.max(0, creditValue);
        }

        public OfferedItem(int itemId, boolean wallItem, int baseItemId, String posterId) {
            this(itemId, wallItem, baseItemId, posterId, 0);
        }

        public OfferedItem(int itemId, boolean wallItem, int baseItemId) {
            this(itemId, wallItem, baseItemId, "", 0);
        }

        /** A credit furni: it pays coins, not a furni term. */
        public boolean isCredit() {
            return this.creditValue > 0;
        }
    }

    /**
     * Which alternative was satisfied and exactly which offered items pay for it.
     *
     * @param ruleIndex index into {@link ContractRules#giveRules()}, or -1 when nothing is satisfied
     * @param consumedItemIds the offered items the rule claims, in the order they were matched
     * @param missing what is still short, for the "requirements not met" indicator; empty on a match
     */
    public record Match(int ruleIndex, List<Integer> consumedItemIds, List<Term> missing) {
        public boolean satisfied() {
            return this.ruleIndex >= 0;
        }
    }

    private static final Match NO_RULES = new Match(-1, List.of(), List.of());

    /**
     * Find the first alternative the player can pay right now.
     *
     * @param walletBalance currency type to the amount the player holds; a type it does not know
     *     should answer 0 rather than throw
     */
    public static Match firstSatisfied(ContractRules rules, IntUnaryOperator walletBalance, List<OfferedItem> offered) {
        if (rules == null) return NO_RULES;

        List<List<Term>> alternatives = rules.giveRules();
        if (alternatives.isEmpty()) return NO_RULES;

        Match closest = null;

        for (int index = 0; index < alternatives.size(); index++) {
            Match attempt = match(index, alternatives.get(index), walletBalance, offered);
            if (attempt.satisfied()) return attempt;

            // Report the shortfall of whichever alternative the player is nearest to completing,
            // otherwise a two-option contract would always nag about the first one.
            if (closest == null || attempt.missing().size() < closest.missing().size()) closest = attempt;
        }

        return closest == null ? NO_RULES : closest;
    }

    /** Check one alternative on its own. Exposed so a caller can explain a specific rule. */
    public static Match match(
            int ruleIndex, List<Term> rule, IntUnaryOperator walletBalance, List<OfferedItem> offered) {
        List<Integer> consumed = new ArrayList<>();
        List<Term> missing = new ArrayList<>();

        // Offered furni is claimed as it is matched, so two terms asking for the same base item can
        // never both be paid by the same single item.
        boolean[] claimed = new boolean[offered == null ? 0 : offered.size()];

        for (Term term : rule) {
            if (term == null || term.amount <= 0) continue;

            // Currency is settled once, below, against the rule's total: checking term by term would
            // let "5 credits and 5 credits" pass on a balance of 5.
            if (term.isCurrency()) continue;

            int found = 0;
            for (int i = 0; i < claimed.length && found < term.amount; i++) {
                if (claimed[i]) continue;

                OfferedItem item = offered.get(i);
                if (item == null || !pays(item, term)) continue;

                claimed[i] = true;
                consumed.add(item.itemId());
                found++;
            }

            if (found < term.amount) missing.add(shortfall(term, term.amount - found));
        }

        missing.addAll(totalCurrencyShortfall(rule, walletBalance));

        return missing.isEmpty()
                ? new Match(ruleIndex, List.copyOf(consumed), List.of())
                : new Match(-1, List.of(), List.copyOf(missing));
    }

    /**
     * Whether the offer stays within one alternative: every furni on the table is one the rule asks for, no type more
     * often than it asks, and the coins ({@code credits}, the credit furni on the table) no more than its coins. An
     * item that would break this is AIR's trade error 5 ("exceeds the offering requirements").
     */
    public static boolean fits(List<Term> rule, int credits, List<OfferedItem> offered) {
        if (rule == null) return false;
        Claim claim = claim(rule, offered);
        return claim.unclaimed == 0 && Math.max(0, credits) <= coins(rule);
    }

    /** Whether the offer is exactly one alternative: everything it asks, nothing more, the coins to the credit. */
    public static boolean matchesExactly(List<Term> rule, int credits, List<OfferedItem> offered) {
        if (rule == null) return false;
        Claim claim = claim(rule, offered);
        return claim.unclaimed == 0 && claim.missing.isEmpty() && Math.max(0, credits) == coins(rule);
    }

    /** True when the offer stays within at least one of the contract's alternatives. */
    public static boolean fitsAny(ContractRules rules, int credits, List<OfferedItem> offered) {
        if (rules == null) return false;
        for (List<Term> rule : rules.giveRules()) {
            if (fits(rule, credits, offered)) return true;
        }
        return false;
    }

    /**
     * The first alternative the offer is exactly, with the furni it claims (every non-credit item on the table; the
     * credit furni pay its coins). When none is, the shortfall of the closest one fitting the offer, for the "not met"
     * indicator.
     */
    public static Match firstExact(ContractRules rules, int credits, List<OfferedItem> offered) {
        if (rules == null || rules.giveRules().isEmpty()) return NO_RULES;

        List<List<Term>> alternatives = rules.giveRules();
        Match closest = null;
        for (int index = 0; index < alternatives.size(); index++) {
            List<Term> rule = alternatives.get(index);
            Claim claim = claim(rule, offered);
            int coins = coins(rule);
            int held = Math.max(0, credits);
            if (claim.unclaimed == 0 && claim.missing.isEmpty() && held == coins) {
                return new Match(index, List.copyOf(claim.consumed), List.of());
            }

            List<Term> missing = new ArrayList<>(claim.missing);
            if (held < coins) {
                missing.add(Term.currency(
                        InteractionWiredContract.DIR_PAY, InteractionWiredContract.CURRENCY_CREDITS, coins - held));
            }
            Match attempt = new Match(-1, List.of(), List.copyOf(missing));
            if (claim.unclaimed == 0
                    && held <= coins
                    && (closest == null || missing.size() < closest.missing().size())) {
                closest = attempt;
            }
        }
        return closest == null ? NO_RULES : closest;
    }

    /** Every coin the rule asks for, whatever the currency: coins are paid with credit furni. */
    static int coins(List<Term> rule) {
        long sum = 0;
        for (Term term : rule) {
            if (term != null && term.isCurrency() && term.amount > 0) sum += term.amount;
        }
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    /** How the non-credit furni on the table spread over a rule's furni terms. */
    private record Claim(List<Integer> consumed, List<Term> missing, int unclaimed) {}

    /**
     * Put every non-credit item on a furni term that pays it and still has room. A term naming a poster is filled
     * before one that takes any poster of its base item, so a named poster never sits on a generic term another poster
     * needed.
     */
    private static Claim claim(List<Term> rule, List<OfferedItem> offered) {
        List<Term> terms = new ArrayList<>();
        for (Term term : rule) {
            if (term != null && !term.isCurrency() && term.amount > 0) terms.add(term);
        }
        terms.sort((left, right) ->
                Boolean.compare(left.posterId().isEmpty(), right.posterId().isEmpty()));

        int[] filled = new int[terms.size()];
        List<Integer> consumed = new ArrayList<>();
        int unclaimed = 0;
        if (offered != null) {
            for (OfferedItem item : offered) {
                if (item == null || item.isCredit()) continue;
                boolean placed = false;
                for (int t = 0; t < terms.size() && !placed; t++) {
                    if (filled[t] < terms.get(t).amount && pays(item, terms.get(t))) {
                        filled[t]++;
                        placed = true;
                    }
                }
                if (placed) consumed.add(item.itemId());
                else unclaimed++;
            }
        }

        List<Term> missing = new ArrayList<>();
        for (int t = 0; t < terms.size(); t++) {
            if (filled[t] < terms.get(t).amount) missing.add(shortfall(terms.get(t), terms.get(t).amount - filled[t]));
        }
        return new Claim(consumed, missing, unclaimed);
    }

    /**
     * Whether an offered item is the furni a term asks for: the same placement and base item and, when the term names
     * a poster, that poster. A term without a poster id (saved before they were kept) takes any of its base item.
     */
    static boolean pays(OfferedItem item, Term term) {
        if (item.wallItem() != term.wallItem || item.baseItemId() != term.baseItemId) return false;
        String poster = term.wallItem ? term.posterId() : "";
        return poster.isEmpty() || poster.equals(item.posterId());
    }

    private static List<Term> totalCurrencyShortfall(List<Term> rule, IntUnaryOperator walletBalance) {
        Map<Integer, Integer> required = new LinkedHashMap<>();
        for (Term term : rule) {
            if (term != null && term.isCurrency() && term.amount > 0) {
                required.merge(term.currencyType, term.amount, Integer::sum);
            }
        }

        List<Term> shortfalls = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : required.entrySet()) {
            int held = walletBalance == null ? 0 : walletBalance.applyAsInt(entry.getKey());
            if (held < entry.getValue()) {
                shortfalls.add(
                        Term.currency(InteractionWiredContract.DIR_PAY, entry.getKey(), entry.getValue() - held));
            }
        }
        return shortfalls;
    }

    private static Term shortfall(Term term, int amount) {
        return Term.furni(InteractionWiredContract.DIR_PAY, term.wallItem, term.baseItemId, term.posterId(), amount);
    }
}
