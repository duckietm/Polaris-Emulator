package polaris.hotel.catalog;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;

/**
 * The official Furni-Matic achievements, by the names their badges carry in the client's texts (badge_name_ACH_*). An
 * achievement the hotel does not define is simply not progressed.
 *
 * <ul>
 *   <li>{@code RecycledItems}: one per furni put into an exchange.
 *   <li>{@code RecycleBoxOpener}: one per recycler box opened.
 *   <li>{@code RecycleEcoprizes}: one per prize that came out of a box.
 *   <li>{@code RecyclerRarePrize}: a prize of level {@link #RARE_LEVEL} or higher.
 *   <li>{@code RecyclerGrandPrize}: a prize of level {@link #GRAND_LEVEL} or higher.
 * </ul>
 */
public final class RecyclerAchievements {
    public static final String RECYCLED_ITEMS = "RecycledItems";
    public static final String BOX_OPENER = "RecycleBoxOpener";
    public static final String ECO_PRIZES = "RecycleEcoprizes";
    public static final String RARE_PRIZE = "RecyclerRarePrize";
    public static final String GRAND_PRIZE = "RecyclerGrandPrize";

    /** "Fairly rare" in the prize list (recycler.prizes.category.3) and up. */
    static final int RARE_LEVEL = 3;

    /** "Legendary" (recycler.prizes.category.5) and up. */
    static final int GRAND_LEVEL = 5;

    private RecyclerAchievements() {}

    /** The furni of one finished exchange. */
    public static void recycled(User user, int itemCount) {
        progress(user, RECYCLED_ITEMS, itemCount);
    }

    /** A box was opened and gave this prize. Only a prize out of a box counts, never a furni bought or traded. */
    public static void boxOpened(User user, Item prize) {
        GameEnvironment environment = Emulator.getGameEnvironment();
        CatalogManager catalog = environment == null ? null : environment.getCatalogManager();
        int level = catalog == null ? 0 : catalog.recyclerPrizeLevel(prize);

        for (String name : forOpenedBox(level)) {
            progress(user, name, 1);
        }
    }

    /** A box was opened and gave this chat style. */
    public static void chatStyleWon(User user, int chatStyleId) {
        GameEnvironment environment = Emulator.getGameEnvironment();
        CatalogManager catalog = environment == null ? null : environment.getCatalogManager();
        int level = catalog == null ? 0 : catalog.recyclerChatStyleLevel(chatStyleId);

        for (String name : forOpenedBox(level)) {
            progress(user, name, 1);
        }
    }

    /** The achievements an opened box counts for, by the level of the prize in the prize list (0: not listed). */
    static List<String> forOpenedBox(int prizeLevel) {
        List<String> names = new ArrayList<>(List.of(BOX_OPENER, ECO_PRIZES));

        if (prizeLevel >= RARE_LEVEL) names.add(RARE_PRIZE);
        if (prizeLevel >= GRAND_LEVEL) names.add(GRAND_PRIZE);

        return names;
    }

    /** Counting never stands in the way of the exchange or the opening itself: a hotel without the manager skips it. */
    private static void progress(User user, String name, int amount) {
        if (user == null || amount <= 0) return;

        GameEnvironment environment = Emulator.getGameEnvironment();
        AchievementManager achievements = environment == null ? null : environment.getAchievementManager();
        if (achievements == null) return;

        AchievementManager.progressAchievement(user, achievements.getAchievement(name), amount);
    }
}
