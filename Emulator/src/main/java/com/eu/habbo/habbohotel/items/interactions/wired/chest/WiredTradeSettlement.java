package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.users.Habbo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Turns a confirmed negotiation into an actual exchange.
 *
 * <p>Deliberately separate from {@link WiredTransactionExecutor}, which settles the old instant path.
 * That one counts <em>matching</em> items in the inventory and takes any of them; here the player has
 * chosen specific pieces and watched them sit on the table, so the settlement has to move
 * <strong>those exact items</strong> and nothing else. Handing over an equivalent rare instead of the
 * one someone picked is the kind of substitution a player would rightly call theft.
 *
 * <p>The plan is built before anything moves, and refused whole if any part of it cannot be met, so a
 * player never pays into a reward that turns out not to exist.
 */
public final class WiredTradeSettlement {
    private WiredTradeSettlement() {}

    /** What a settlement will do, once it is known to be possible. */
    public record Plan(List<Integer> itemsToTake, List<ContractTerm> currencyToTake, List<ContractTerm> rewardToGive) {}

    /**
     * Work out whether the exchange can happen, without moving anything.
     *
     * @param paidRule the alternative the player satisfied
     * @param consumedItemIds the exact items that pay for it
     * @param reward what the contract hands back
     * @param chest the chest the contract draws from, or null when it mints
     * @return the plan, or null when the reward cannot be covered
     */
    public static Plan plan(
            IntUnaryOperator walletBalance,
            List<ContractTerm> paidRule,
            List<Integer> consumedItemIds,
            List<ContractTerm> reward,
            ChestStorage chestContents) {
        if (paidRule == null || consumedItemIds == null || reward == null) return null;

        // Settled on the rule's total, the same way the requirement was judged: two terms in one rule
        // asking for five each need ten, not five twice over.
        Map<Integer, Integer> currencyNeeded = new LinkedHashMap<>();
        for (ContractTerm term : paidRule) {
            if (term != null && term.isCurrency() && term.amount > 0) {
                currencyNeeded.merge(term.currencyType, term.amount, Integer::sum);
            }
        }

        List<ContractTerm> currencyToTake = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : currencyNeeded.entrySet()) {
            int held = walletBalance == null ? 0 : walletBalance.applyAsInt(entry.getKey());
            if (held < entry.getValue()) return null;
            currencyToTake.add(ContractTerm.currency(ContractTerm.DIR_PAY, entry.getKey(), entry.getValue()));
        }

        // A chest-backed contract may only hand out what the chest actually holds. Without this a
        // room could promise a rare it ran out of and quietly pay nothing for the coins it took.
        if (chestContents != null) {
            Map<Integer, Integer> currencyOwed = new LinkedHashMap<>();
            Map<Integer, Integer> furniOwed = new LinkedHashMap<>();
            for (ContractTerm term : reward) {
                if (term == null || term.amount <= 0) continue;
                if (term.isFurni()) furniOwed.merge(term.baseItemId, term.amount, Integer::sum);
                else currencyOwed.merge(term.currencyType, term.amount, Integer::sum);
            }

            for (Map.Entry<Integer, Integer> entry : currencyOwed.entrySet()) {
                if (chestContents.count(ChestStorage.KIND_CURRENCY, entry.getKey()) < entry.getValue()) return null;
            }
            for (Map.Entry<Integer, Integer> entry : furniOwed.entrySet()) {
                if (chestContents.count(ChestStorage.KIND_FURNI, entry.getKey()) < entry.getValue()) return null;
            }
        }

        return new Plan(List.copyOf(consumedItemIds), List.copyOf(currencyToTake), List.copyOf(reward));
    }

    /**
     * Carry the plan out. Payment moves first: taking before giving means a failure part-way leaves
     * the player short rather than the room, and the plan has already established that everything it
     * touches exists.
     *
     * @param release hands over an offered item so it can be stored or destroyed; null when the
     *     caller has nothing to release
     */
    public static void apply(Habbo habbo, Plan plan, InteractionWiredChest chest, ItemRelease release) {
        if (habbo == null || plan == null) return;

        for (ContractTerm term : plan.currencyToTake()) {
            ChestWiredCurrencyUtil.take(habbo, term.currencyType, term.amount);
            if (chest != null) {
                chest.getContents().add(ChestStorage.KIND_CURRENCY, term.currencyType, term.amount);
            }
        }

        for (Integer itemId : plan.itemsToTake()) {
            if (release != null) release.release(itemId, chest);
        }

        for (ContractTerm term : plan.rewardToGive()) {
            if (term.amount <= 0) continue;

            if (term.isCurrency()) {
                if (chest != null) {
                    chest.getContents().take(ChestStorage.KIND_CURRENCY, term.currencyType, term.amount);
                }
                ChestWiredCurrencyUtil.give(habbo, term.currencyType, term.amount);
                continue;
            }

            if (chest != null) {
                ChestWiredFurniUtil.giveFromChestByType(
                        habbo, chest, term.wallItem, term.baseItemId, term.legacyPosterId, term.amount);
            } else {
                ChestWiredFurniUtil.mintToInventory(habbo, term.wallItem, term.baseItemId, term.amount);
            }
        }

        if (chest != null) chest.persistContents();
    }

    /** How an offered item leaves the player: into the contract's chest, or out of existence. */
    public interface ItemRelease {
        void release(int itemId, InteractionWiredChest chest);
    }
}
