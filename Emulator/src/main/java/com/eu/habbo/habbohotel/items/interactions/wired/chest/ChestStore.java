package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** What {@link #releaseChest} did. */
    enum ReleaseStatus {
        /** The item has no chest row: nothing was stored, nothing changed. */
        NO_CHEST,
        /** The chest held nothing; its empty row was removed. */
        EMPTY,
        /** Everything went back to the owner and the chest's rows were removed. */
        RELEASED,
        /** Nobody to give it back to: the rows were kept as they are. */
        OWNER_UNKNOWN,
        /** The database refused; the rows were kept as they are. */
        FAILED
    }

    /**
     * The result of releasing a chest.
     *
     * @param furni every stored id that went back mapped to the inventory id it has now
     * @param coins the coins paid to the owner as credits by this call (0 when an earlier call paid)
     * @param creditsAfter the owner's credits after the refund, or -1 when no refund was made
     */
    record Release(
            ReleaseStatus status, int chestId, int ownerId, Map<Integer, Integer> furni, int coins, int creditsAfter) {
        public Release {
            furni = furni == null ? Map.of() : Map.copyOf(furni);
        }

        public static Release of(ReleaseStatus status, int chestId, int ownerId) {
            return new Release(status, chestId, ownerId, Map.of(), 0, -1);
        }

        public boolean rowsRemoved() {
            return this.status == ReleaseStatus.EMPTY || this.status == ReleaseStatus.RELEASED;
        }
    }

    /** A chest row whose item no longer exists. {@code ownerId} is the owner last recorded, or 0. */
    record OrphanRow(int chestId, int ownerId) {}

    /**
     * Give everything a chest holds back to its owner and remove the chest's rows, before the chest
     * item itself is deleted: every stored furni goes back into the owner's inventory as itself (same
     * id unless taken, same state, limited number, group and expiry) and the coins are paid out as
     * credits through the economy ledger under {@code wired-chest-release:<chestId>}, all in one
     * transaction. Idempotent: a chest that was released already has no rows, and the ledger operation
     * id pays at most once.
     *
     * @param ownerId the owner as the caller knows it, or 0 to use the chest item's row and then the
     *     owner recorded on the chest
     */
    Release releaseChest(int chestId, int ownerId);

    /** Which of these item ids have a chest row. */
    Set<Integer> chestIdsAmong(Collection<Integer> itemIds);

    /** Chest rows whose item is gone from {@code items}. */
    List<OrphanRow> orphanedChests();
}
