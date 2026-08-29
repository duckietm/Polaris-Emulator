package com.eu.habbo.habbohotel.items.interactions.wired.chest;

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
 * <p>Deliberately free of emulator types: it takes a wallet lookup and a flat list of offered items,
 * which keeps the whole decision unit-testable without a running hotel.
 */
public final class ContractRequirementEvaluator {
    private ContractRequirementEvaluator() {}

    /** One item a player has put on the table. */
    public record OfferedItem(int itemId, boolean wallItem, int baseItemId) {}

    /**
     * Which alternative was satisfied and exactly which offered items pay for it.
     *
     * @param ruleIndex index into {@link ContractRules#giveRules()}, or -1 when nothing is satisfied
     * @param consumedItemIds the offered items the rule claims, in the order they were matched
     * @param missing what is still short, for the "requirements not met" indicator; empty on a match
     */
    public record Match(int ruleIndex, List<Integer> consumedItemIds, List<ContractTerm> missing) {
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

        List<List<ContractTerm>> alternatives = rules.giveRules();
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
            int ruleIndex, List<ContractTerm> rule, IntUnaryOperator walletBalance, List<OfferedItem> offered) {
        List<Integer> consumed = new ArrayList<>();
        List<ContractTerm> missing = new ArrayList<>();

        // Offered furni is claimed as it is matched, so two terms asking for the same base item can
        // never both be paid by the same single item.
        boolean[] claimed = new boolean[offered == null ? 0 : offered.size()];

        for (ContractTerm term : rule) {
            if (term == null || term.amount <= 0) continue;

            // Currency is settled once, below, against the rule's total: checking term by term would
            // let "5 credits and 5 credits" pass on a balance of 5.
            if (term.isCurrency()) continue;

            int found = 0;
            for (int i = 0; i < claimed.length && found < term.amount; i++) {
                if (claimed[i]) continue;

                OfferedItem item = offered.get(i);
                if (item == null || item.wallItem() != term.wallItem || item.baseItemId() != term.baseItemId) continue;

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

    private static List<ContractTerm> totalCurrencyShortfall(List<ContractTerm> rule, IntUnaryOperator walletBalance) {
        Map<Integer, Integer> required = new LinkedHashMap<>();
        for (ContractTerm term : rule) {
            if (term != null && term.isCurrency() && term.amount > 0) {
                required.merge(term.currencyType, term.amount, Integer::sum);
            }
        }

        List<ContractTerm> shortfalls = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : required.entrySet()) {
            int held = walletBalance == null ? 0 : walletBalance.applyAsInt(entry.getKey());
            if (held < entry.getValue()) {
                shortfalls.add(ContractTerm.currency(ContractTerm.DIR_PAY, entry.getKey(), entry.getValue() - held));
            }
        }
        return shortfalls;
    }

    private static ContractTerm shortfall(ContractTerm term, int amount) {
        return ContractTerm.furni(ContractTerm.DIR_PAY, term.wallItem, term.baseItemId, term.legacyPosterId, amount);
    }
}
