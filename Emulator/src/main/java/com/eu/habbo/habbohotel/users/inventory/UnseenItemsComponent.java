package com.eu.habbo.habbohotel.users.inventory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The server side of the official unseen-item tracker
 * ({@code com.sulake.habbo.inventory.UnseenItemTracker}). Categories are the official ones:
 * 1 floor furni, 2 wall furni, 3 pets, 4 badges, 5 bots, 7 collectibles, 8 habbicons.
 *
 * <p>{@code UnseenResetCategory} (3493) and {@code UnseenResetItems} (2343) are what the client
 * sends when a tab is opened or single items are looked at; without them the server keeps
 * counting items the user has already seen.
 */
public class UnseenItemsComponent {
    public static final int CATEGORY_FLOOR_ITEM = 1;
    public static final int CATEGORY_WALL_ITEM = 2;
    public static final int CATEGORY_PET = 3;
    public static final int CATEGORY_BADGE = 4;
    public static final int CATEGORY_BOT = 5;

    /** Guards against a client asking to clear thousands of ids in one packet. */
    public static final int MAX_RESET_ITEMS = 1000;

    private final Map<Integer, Set<Integer>> unseen = new HashMap<>();

    public void markUnseen(int category, int itemId) {
        if (category <= 0 || itemId <= 0) {
            return;
        }

        synchronized (this.unseen) {
            this.unseen.computeIfAbsent(category, key -> new HashSet<>()).add(itemId);
        }
    }

    /** @return true when the category actually held unseen items */
    public boolean resetCategory(int category) {
        synchronized (this.unseen) {
            Set<Integer> items = this.unseen.remove(category);
            return items != null && !items.isEmpty();
        }
    }

    /** @return the number of ids that were still marked unseen */
    public int resetItems(int category, Set<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return 0;
        }

        synchronized (this.unseen) {
            Set<Integer> items = this.unseen.get(category);

            if (items == null) {
                return 0;
            }

            int before = items.size();
            items.removeAll(itemIds);

            if (items.isEmpty()) {
                this.unseen.remove(category);
            }

            return before - items.size();
        }
    }

    public Set<Integer> getUnseen(int category) {
        synchronized (this.unseen) {
            Set<Integer> items = this.unseen.get(category);
            return items == null ? Collections.emptySet() : new HashSet<>(items);
        }
    }

    public int count(int category) {
        synchronized (this.unseen) {
            Set<Integer> items = this.unseen.get(category);
            return items == null ? 0 : items.size();
        }
    }
}
