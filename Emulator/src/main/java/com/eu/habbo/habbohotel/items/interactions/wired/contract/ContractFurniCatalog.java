package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredWebApiOwnership;

/**
 * Translates between the furni a contract stores (our base item ids) and the furni the official contract editor
 * names (furnidata type ids, which are our sprite ids), and says which furni a contract may ask for at all.
 *
 * <p>An interface so the contract can be tested without an item table.
 */
public interface ContractFurniCatalog {
    /** The furnidata type id of a base item, or 0 when the base item is unknown. */
    int typeIdOf(int baseItemId);

    /**
     * The base item a furnidata type id names, or 0 when none does. {@code preferredBaseItemId} wins when it has
     * that type id, so a node the editor sends back unchanged keeps the exact base item it had.
     */
    int baseItemIdOf(boolean wall, int typeId, int preferredBaseItemId);

    /** AIR {@code item_not_allowed}: only tradeable furni, and never a chest, a contract or a web api box. */
    boolean allowedInContract(int baseItemId);

    /** The live item table. */
    static ContractFurniCatalog live() {
        return new ContractFurniCatalog() {
            private ItemManager items() {
                return WiredPlatform.gameEnvironment() == null
                        ? null
                        : WiredPlatform.gameEnvironment().getItemManager();
            }

            private Item item(int baseItemId) {
                ItemManager items = items();
                return items == null || baseItemId <= 0 ? null : items.getItem(baseItemId);
            }

            @Override
            public int typeIdOf(int baseItemId) {
                Item base = item(baseItemId);
                return base == null ? 0 : base.getSpriteId();
            }

            @Override
            public int baseItemIdOf(boolean wall, int typeId, int preferredBaseItemId) {
                Item preferred = item(preferredBaseItemId);
                if (matches(preferred, wall, typeId)) return preferred.getId();

                ItemManager items = items();
                if (items == null) return 0;

                // A tradeable one first, then the lowest id, so every lookup makes the same choice.
                int found = 0;
                boolean foundAllowed = false;
                for (Item base : items.getItems().values()) {
                    if (!matches(base, wall, typeId)) continue;
                    boolean allowed = allowedInContract(base.getId());
                    if (found == 0 || (allowed && !foundAllowed) || (allowed == foundAllowed && base.getId() < found)) {
                        found = base.getId();
                        foundAllowed = allowed;
                    }
                }
                return found;
            }

            private boolean matches(Item base, boolean wall, int typeId) {
                return base != null && base.getSpriteId() == typeId && (base.getType() == FurnitureType.WALL) == wall;
            }

            @Override
            public boolean allowedInContract(int baseItemId) {
                Item base = item(baseItemId);
                if (base == null || !base.allowTrade() || WiredWebApiOwnership.isWebApiItem(base)) return false;

                ItemInteraction interaction = base.getInteractionType();
                Class<?> type = interaction == null ? null : interaction.getType();
                return type == null
                        || !(InteractionWiredChest.class.isAssignableFrom(type)
                                || InteractionWiredContract.class.isAssignableFrom(type));
            }
        };
    }
}
