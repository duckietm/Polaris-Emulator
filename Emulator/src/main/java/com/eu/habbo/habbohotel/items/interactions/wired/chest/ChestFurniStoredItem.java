package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;

/**
 * One furni row inside a wired furni chest. Wire layout matches official {@code gypyli.ChestStorage}
 * (client 6): inventoryId, lockState, transactionId, ChestItemType, groupable, specialType, stuffData, extra.
 */
public class ChestFurniStoredItem {
    public int inventoryId;
    public int lockState;
    public long transactionId;
    public boolean wallItem;
    public int baseItemId;
    /**
     * Client-facing furnidata classId ({@code items_base.sprite_id}). The wire always carries this
     * instead of {@link #baseItemId} — the client resolves icons and names against furnidata, which
     * knows nothing about the emulator's internal base item ids. 0 in pre-existing persisted payloads;
     * resolved on load (see {@link InteractionWiredChest#loadWiredData}).
     */
    public int spriteId;

    public String legacyPosterId = "";
    public boolean groupable = true;
    public int specialType;
    /** Nitro object-data format key ({@link com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniWireUtil#LEGACY_FORMAT}). */
    public int stuffDataFormat;

    public String extradata = "";
    public int limitedSells;
    public int limitedStack;
    /** Floor-item state (official {@code extra} int after stuffData). */
    public int extra;

    public ChestFurniStoredItem() {}

    /**
     * A row from {@code wired_chest_items}: the stored item itself, so its id is the inventory id the
     * client sees and echoes back.
     *
     * @param base the furni definition, or null when the furni no longer exists (the row is still
     *     shown, by its internal id, so nobody loses sight of it)
     */
    public static ChestFurniStoredItem fromStoredRow(ChestStore.StoredRow row, Item base) {
        ChestFurniStoredItem stored = new ChestFurniStoredItem();
        stored.inventoryId = row.itemId();
        stored.transactionId = row.sequence();
        stored.baseItemId = row.baseItemId();
        stored.extradata = row.extraData() == null ? "" : row.extraData();
        stored.stuffDataFormat = ChestFurniWireUtil.LEGACY_FORMAT;
        if (base != null) {
            stored.wallItem = base.getType() == FurnitureType.WALL;
            stored.spriteId = base.getSpriteId();
            stored.groupable = base.allowInventoryStack();
        }
        int[] limited = parseLimited(row.limitedData());
        stored.limitedStack = limited[0];
        stored.limitedSells = limited[1];
        if (stored.wallItem) {
            stored.legacyPosterId = base != null && "poster".equals(base.getName()) ? stored.extradata : "";
        } else {
            try {
                stored.extra = Integer.parseInt(stored.extradata);
            } catch (NumberFormatException ignored) {
                stored.extra = 0;
            }
        }
        return stored;
    }

    /** {@code items.limited_data} is {@code stack:sells}. */
    static int[] parseLimited(String limitedData) {
        int[] result = {0, 0};
        if (limitedData == null) return result;
        String[] parts = limitedData.split(":");
        try {
            if (parts.length > 0) result[0] = Integer.parseInt(parts[0].trim());
            if (parts.length > 1) result[1] = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {
            return new int[] {0, 0};
        }
        return result;
    }

    public static ChestFurniStoredItem fromHabboItem(HabboItem item, int inventoryId) {
        ChestFurniStoredItem stored = new ChestFurniStoredItem();
        Item base = item.getBaseItem();
        stored.inventoryId = inventoryId;
        stored.wallItem = base.getType() == FurnitureType.WALL;
        stored.baseItemId = base.getId();
        stored.spriteId = base.getSpriteId();
        stored.groupable = base.allowInventoryStack();
        stored.extradata = item.getExtradata() == null ? "" : item.getExtradata();
        stored.stuffDataFormat = ChestFurniWireUtil.LEGACY_FORMAT;
        if (item.isLimited()) {
            stored.limitedSells = item.getLimitedSells();
            stored.limitedStack = item.getLimitedStack();
        }
        if (stored.wallItem) {
            // The same identity a row read back from the store gets: a poster is its poster id, so a
            // freshly deposited poster groups, withdraws and pays out like one stored before.
            stored.legacyPosterId = "poster".equals(base.getName()) ? stored.extradata : "";
        } else {
            try {
                stored.extra = Integer.parseInt(stored.extradata);
            } catch (NumberFormatException ignored) {
                stored.extra = 0;
            }
        }
        return stored;
    }

    /**
     * The type id the client sees (and echoes back on withdraw). Sprite id when known, with the
     * internal base item id as fallback for unresolvable legacy rows.
     */
    public int wireTypeId() {
        return this.spriteId > 0 ? this.spriteId : this.baseItemId;
    }

    public void appendToMessage(ServerMessage message) {
        ChestFurniWireUtil.appendStoredItem(message, this);
    }
}
