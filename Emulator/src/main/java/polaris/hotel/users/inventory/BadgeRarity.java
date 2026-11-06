package polaris.hotel.users.inventory;

/**
 * Badge rarity tiers as the official client numbers them
 * (com.sulake.user.communication.enum BadgeRarity): the UserCurrentBadges
 * packet carries the tier id per worn slot. The tier is derived from the
 * number of users holding the badge with the same thresholds the badge
 * leaderboard endpoint uses ({@code BadgeLeaderboardHttpHandler}), so the
 * infostand bubble and the leaderboard agree.
 */
public final class BadgeRarity {

    public static final int COMMON = 0;
    public static final int UNCOMMON = 1;
    public static final int RARE = 2;
    public static final int EPIC = 3;
    public static final int MYTHICAL = 4;
    public static final int LEGENDARY = 5;
    public static final int UNIQUE = 6;

    private BadgeRarity() {}

    /** Tier for a badge held by {@code ownerCount} users; unknown badges (0 owners) are common. */
    public static int tierForOwnerCount(int ownerCount) {
        if (ownerCount > 50) return COMMON;
        if (ownerCount > 10) return RARE;
        if (ownerCount > 6) return EPIC;
        if (ownerCount > 3) return LEGENDARY;
        if (ownerCount > 1) return MYTHICAL;
        if (ownerCount > 0) return UNIQUE;
        return COMMON;
    }
}
