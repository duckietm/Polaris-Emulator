package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.List;
import java.util.Map;

/**
 * Durable storage behind the wired chests, after the official AIR model.
 *
 * <p>A chest is a row in {@code wired_chests} (its kind, its coins, its capacity level and its
 * settings) and the furni it holds are real items parked in {@code wired_chest_items}: the item keeps
 * its id, its state and its limited number while it sits in the chest, and leaves with them again.
 * Nothing is copied and nothing is minted, so a withdrawal can only hand out an item that was put
 * in, exactly once.
 *
 * <p>Every method that moves something is atomic on its own and answers with what it actually moved,
 * so a caller never credits a player for a move the database refused.
 */
public interface ChestStore {
    /** The chest's own row. {@code settings} is the JSON the chest window edits. */
    record ChestRow(int chestId, int kind, int coins, int capacityLevel, String settings) {}

    /** One furni inside a chest, as stored. {@code sequence} orders deposits (oldest first). */
    record StoredRow(
            int itemId,
            int baseItemId,
            String extraData,
            String wiredData,
            String limitedData,
            int guildId,
            int expires,
            boolean allowUnderpass,
            long sequence) {}

    /** The chest's row, or null when the chest has none yet. */
    ChestRow loadChest(int chestId);

    /** Create the chest's row when it has none. A row that already exists is left alone. */
    void createChest(int chestId, int kind, int coins, int capacityLevel, String settings);

    /** Save the chest's settings and capacity level. Coins and furni are never touched here. */
    void saveSettings(int chestId, int capacityLevel, String settings);

    /**
     * Add (or with a negative delta take) coins. Refused when the chest would go below zero.
     *
     * @return true when the change was stored
     */
    boolean addCoins(int chestId, int delta);

    /** The furni stored in a chest, oldest deposit first. */
    List<StoredRow> loadItems(int chestId);

    /**
     * Move items out of a player's inventory into the chest. Only items the player still owns and
     * has not placed are moved.
     *
     * @return the ids that were moved
     */
    List<Integer> depositItems(int chestId, int fromUserId, List<Integer> itemIds, long sequence);

    /**
     * Move stored items back into a player's inventory.
     *
     * @return for every item that left the chest, its stored id mapped to the id it has in the
     *     inventory now (the same id unless that id was taken in the meantime)
     */
    Map<Integer, Integer> withdrawItems(int chestId, int toUserId, List<Integer> itemIds);

    /**
     * Redeem credit furni from a player's inventory into a coin chest: the furni are destroyed and
     * their value is added to the chest's coins, together or not at all.
     *
     * @return true when every item was redeemed and the coins were added
     */
    boolean redeemCreditFurni(int chestId, int fromUserId, List<Integer> itemIds, int coins);
}
