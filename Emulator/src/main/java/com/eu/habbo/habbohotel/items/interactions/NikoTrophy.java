package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.users.HabboItem;

/**
 * The Niko trophies of the official client. {@code TrophyFurniWidgetHandler} (AIR 13) reads the
 * floor item's extra int ({@code furniture_extras}) as the trophy view: {@code 10} opens
 * {@code NikoTrophyView} in silver, {@code 20} in gold, anything else the plain trophy card. The
 * two Niko trophies are {@code prizetrophy_niko1} (silver) and {@code prizetrophy_niko2} (gold).
 */
public final class NikoTrophy {

    public static final int VIEW_NIKO_SILVER = 10;
    public static final int VIEW_NIKO_GOLD = 20;

    public static final String SILVER_ITEM_NAME = "prizetrophy_niko1";
    public static final String GOLD_ITEM_NAME = "prizetrophy_niko2";

    private NikoTrophy() {}

    /** The official trophy view for a base item name, or {@code 0} when it is not a Niko trophy. */
    public static int viewOf(String itemName) {
        if (SILVER_ITEM_NAME.equals(itemName)) {
            return VIEW_NIKO_SILVER;
        }
        if (GOLD_ITEM_NAME.equals(itemName)) {
            return VIEW_NIKO_GOLD;
        }
        return 0;
    }

    /** The official trophy view of a floor item, or {@code 0} when it is not a Niko trophy. */
    public static int viewOf(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return 0;
        }
        return viewOf(item.getBaseItem().getName());
    }

    /** The extra int a floor item packet carries: the Niko view for a Niko trophy, else {@code fallback}. */
    public static int extraOr(HabboItem item, int fallback) {
        int view = viewOf(item);
        return view > 0 ? view : fallback;
    }
}
