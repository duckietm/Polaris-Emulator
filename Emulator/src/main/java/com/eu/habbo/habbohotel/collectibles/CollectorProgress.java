package com.eu.habbo.habbohotel.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.BoxPrize;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Claim;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Collection;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectionState;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Entry;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.LevelReward;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/** The collector rules, without a database: XP, levels, set completion, claims and reward box draws. */
public final class CollectorProgress {
    private CollectorProgress() {}

    public static String rewardClaimKey(String collectionId) {
        return "reward:" + collectionId;
    }

    public static String bonusClaimKey(String collectionId) {
        return "bonus:" + collectionId;
    }

    /**
     * Collector XP: the score of every distinct product owned, plus the completion XP of every set the
     * user completed.
     */
    public static int score(
            Map<String, Integer> ownedCounts, Map<String, Product> products, List<Collection> collections) {
        long total = 0;
        for (Map.Entry<String, Integer> owned : ownedCounts.entrySet()) {
            Product product = products.get(owned.getKey());
            if (product != null && owned.getValue() > 0) total += Math.max(0, product.score());
        }
        for (Collection collection : collections) {
            if (isComplete(collectionProducts(collection, products), ownedCounts)) {
                total += Math.max(0, collection.completionXp());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Level 1 from 0 XP, one more level per {@code xpPerLevel} XP. */
    public static int level(int score, int xpPerLevel) {
        if (xpPerLevel <= 0) return 1;
        return 1 + Math.max(0, score) / xpPerLevel;
    }

    /** The products of a set, in the products' order. */
    public static List<Product> collectionProducts(Collection collection, Map<String, Product> products) {
        List<Product> result = new ArrayList<>();
        for (Product product : products.values()) {
            if (collection.collectionId().equals(product.collectionId())) result.add(product);
        }
        return result;
    }

    /** A set is complete when it has products and the user owns at least one of each. */
    public static boolean isComplete(List<Product> setProducts, Map<String, Integer> ownedCounts) {
        if (setProducts.isEmpty()) return false;
        for (Product product : setProducts) {
            if (ownedCounts.getOrDefault(product.productCode(), 0) <= 0) return false;
        }
        return true;
    }

    /** Whether the bonus window is open at {@code now}; a window without an end is never open. */
    public static boolean bonusWindowOpen(Collection collection, int now) {
        if (collection.bonusEnd() <= 0) return false;
        return now >= collection.bonusStart() && now <= collection.bonusEnd();
    }

    /** One set as the collections tab shows it for a user. */
    public static CollectionState state(
            Collection collection,
            Map<String, Product> products,
            Map<String, Integer> ownedCounts,
            Map<String, Integer> claimed,
            int now) {
        List<Product> setProducts = collectionProducts(collection, products);
        List<Entry> entries = new ArrayList<>();
        int score = 0;
        int goal = 0;
        for (Product product : setProducts) {
            int amount = ownedCounts.getOrDefault(product.productCode(), 0);
            entries.add(new Entry(product, amount));
            goal += Math.max(0, product.score());
            if (amount > 0) score += Math.max(0, product.score());
        }
        boolean complete = isComplete(setProducts, ownedCounts);

        Product bonusProduct = product(products, collection.bonusProductCode());
        Claim bonusClaim = null;
        if (bonusProduct != null) {
            String key = bonusClaimKey(collection.collectionId());
            int amount = claimed.getOrDefault(key, 0);
            boolean open = complete && bonusWindowOpen(collection, now) && amount < 1;
            bonusClaim =
                    new Claim(key, amount, 1, open ? CollectibleData.CLAIM_AVAILABLE : CollectibleData.CLAIM_LOCKED);
        }

        Product rewardProduct = product(products, collection.rewardProductCode());
        Claim rewardClaim = null;
        if (rewardProduct != null) {
            String key = rewardClaimKey(collection.collectionId());
            int amount = claimed.getOrDefault(key, 0);
            boolean open = complete && amount < 1;
            rewardClaim =
                    new Claim(key, amount, 1, open ? CollectibleData.CLAIM_AVAILABLE : CollectibleData.CLAIM_LOCKED);
        }

        return new CollectionState(
                collection, entries, score, goal, complete, bonusProduct, bonusClaim, rewardProduct, rewardClaim);
    }

    /** Whether a level reward is offered to a user of {@code level} at {@code now}. */
    public static boolean levelRewardOpen(LevelReward reward, int level, int now) {
        if (level < reward.minLevel()) return false;
        if (reward.validFrom() > 0 && now < reward.validFrom()) return false;
        return reward.validTo() <= 0 || now <= reward.validTo();
    }

    /**
     * Draws a reward box prize by weight. {@code random} gets the sum of the positive weights and returns
     * a number from 0 (inclusive) to it (exclusive). Null when nothing can be drawn.
     */
    public static BoxPrize draw(List<BoxPrize> prizes, IntUnaryOperator random) {
        long total = 0;
        for (BoxPrize prize : prizes) total += Math.max(0, prize.weight());
        if (total <= 0) return null;
        int roll = random.applyAsInt((int) Math.min(Integer.MAX_VALUE, total));
        long cursor = 0;
        for (BoxPrize prize : prizes) {
            if (prize.weight() <= 0) continue;
            cursor += prize.weight();
            if (roll < cursor) return prize;
        }
        return null;
    }

    /**
     * The emerald value of an emerald credit furni ({@code FurnitureNftCreditLogic}): the number at the end
     * of the class name, 15000 for {@code nft_emerald_emerhand} and 2000 for {@code nft_emerald_eggmerald};
     * 0 for anything else.
     */
    public static int emeraldCreditValue(String itemName) {
        if (itemName == null || !itemName.startsWith("nft_emerald")) return 0;
        if (itemName.equals("nft_emerald_emerhand")) return 15000;
        if (itemName.equals("nft_emerald_eggmerald")) return 2000;
        int end = itemName.length();
        int start = end;
        while (start > 0 && Character.isDigit(itemName.charAt(start - 1))) start--;
        if (start == end || end - start > 9) return 0;
        return Integer.parseInt(itemName.substring(start, end));
    }

    private static Product product(Map<String, Product> products, String code) {
        return code == null || code.isEmpty() ? null : products.get(code);
    }
}
