package com.eu.habbo.habbohotel.collectibles;

import java.util.List;

/** The collectibles data model: database rows and what the client screens are computed from. */
public final class CollectibleData {
    private CollectibleData() {}

    /** Official client product types ({@code class_3169}). */
    public static final int TYPE_WALL = 0;

    public static final int TYPE_FLOOR = 1;
    public static final int TYPE_EFFECT = 2;
    public static final int TYPE_BADGE = 4;
    public static final int TYPE_CHAT_STYLE = 9;
    public static final int TYPE_PET = 10;
    public static final int TYPE_CLOTHING = 11;

    /** A claim the user may make ({@code CollectibleItemClaim.status} 0). */
    public static final int CLAIM_AVAILABLE = 0;

    /** A claim the user cannot make yet or any more ({@code CollectibleItemClaim.status} 1). */
    public static final int CLAIM_LOCKED = 1;

    /** A {@code collectible_products} row. */
    public record Product(
            String productCode,
            int productType,
            String itemTypeId,
            int score,
            String rarity,
            String petFigure,
            List<Integer> figureSetIds,
            String collectionId,
            int emeraldPrice,
            boolean shopEnabled,
            int mintLimit,
            int mintedCount) {
        public Product {
            figureSetIds = List.copyOf(figureSetIds);
            rarity = rarity == null ? "common" : rarity;
            petFigure = petFigure == null ? "" : petFigure;
            itemTypeId = itemTypeId == null ? "" : itemTypeId;
        }

        /** Whether another serial can be minted: {@code mint_limit} below 0 means unlimited. */
        public boolean canMintMore() {
            return this.mintLimit < 0 || this.mintedCount < this.mintLimit;
        }
    }

    /** A {@code collectible_collections} row. */
    public record Collection(
            String collectionId,
            String name,
            int completionXp,
            String rewardProductCode,
            String bonusProductCode,
            int bonusStart,
            int bonusEnd) {}

    /** A {@code users_collectibles} row. */
    public record Owned(long assetId, int userId, String productCode, int serial) {}

    /** One claim as the client shows it: {@code claimedAmount / claimLimit} and its status. */
    public record Claim(String claimId, int claimedAmount, int claimLimit, int status) {}

    /** A collection product and how many the user owns. */
    public record Entry(Product product, int amount) {}

    /** A collection as the collections tab shows it for one user. */
    public record CollectionState(
            Collection collection,
            List<Entry> entries,
            int score,
            int goal,
            boolean complete,
            Product bonusProduct,
            Claim bonusClaim,
            Product rewardProduct,
            Claim rewardClaim) {
        public CollectionState {
            entries = List.copyOf(entries);
        }
    }

    /** Collector XP, the highest XP ever reached, and the collector level. */
    public record CollectorScore(int score, int highestScore, int level) {}

    /** A {@code collectible_level_rewards} row. */
    public record LevelReward(int id, int minLevel, String productCode, int claimLimit, int validFrom, int validTo) {
        public String claimKey() {
            return "level:" + this.id;
        }
    }

    /** A level reward the user can still claim, with its product and how often it was claimed. */
    public record OpenReward(LevelReward reward, Product product, int claimedAmount) {}

    /** A {@code collectible_reward_boxes} row. */
    public record BoxPrize(String productCode, int weight) {}

    /** A {@code collectible_mintables} row. */
    public record Mintable(int baseItemId, String productCode, int price, int startTime, int endTime) {}

    /** A {@code collectible_mint_token_offers} row. */
    public record MintTokenOffer(int offerId, String productCode, int silverPrice, int amount) {}

    /** A {@code collectible_avatar_outfits} row: the look a wearable product puts on. */
    public record AvatarOutfit(String productCode, String figure, String gender, String contractKey) {
        public AvatarOutfit {
            figure = figure == null ? "" : figure;
            gender = CollectibleAvatars.normalizeGender(gender);
            contractKey =
                    contractKey == null || contractKey.isBlank() ? CollectibleAvatars.CONTRACT_AVATAR : contractKey;
        }
    }

    /** An avatar collectible a user owns: the token and its look ({@code NftWardrobeItem} in AIR). */
    public record WardrobeOutfit(Owned token, AvatarOutfit outfit) {}

    /** The avatar collectible a user wears ({@code users_collectible_outfits}) and the look it replaced. */
    public record WornOutfit(long assetId, String fallbackFigure, String fallbackGender) {}
}
