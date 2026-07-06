package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Chest ↔ inventory furni helpers for wired effects and contract transactions. */
public final class ChestWiredFurniUtil {
    private ChestWiredFurniUtil() {
    }

    public static int giveFromChest(Habbo habbo, InteractionWiredChest chest, int amount) {
        return giveFromChestByType(habbo, chest, false, 0, "", amount);
    }

    public static int giveFromChestByType(Habbo habbo, InteractionWiredChest chest, boolean wallItem, int baseItemId, String legacyPosterId, int amount) {
        if (habbo == null || chest == null || amount <= 0 || habbo.getClient() == null || baseItemId <= 0) {
            return 0;
        }

        List<ChestFurniStoredItem> removed = chest.getContents().removeFurniByType(wallItem, baseItemId, legacyPosterId, amount);
        int given = deliverStoredItems(habbo, removed);
        if (given > 0) {
            chest.persistContents();
        }
        return given;
    }

    public static int mintToInventory(Habbo habbo, boolean wallItem, int baseItemId, int amount) {
        if (habbo == null || habbo.getClient() == null || baseItemId <= 0 || amount <= 0) {
            return 0;
        }

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(baseItemId);
        if (baseItem == null) {
            return 0;
        }
        if ((baseItem.getType() == FurnitureType.WALL) != wallItem) {
            return 0;
        }

        int given = 0;
        for (int i = 0; i < amount; i++) {
            HabboItem created = Emulator.getGameEnvironment().getItemManager().createItem(
                    habbo.getHabboInfo().getId(), baseItem, 0, 0, "0");
            if (created == null) {
                continue;
            }
            habbo.getClient().sendResponse(new AddHabboItemComposer(created));
            habbo.getInventory().getItemsComponent().addItem(created);
            given++;
        }

        if (given > 0) {
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }
        return given;
    }

    public static int countInInventory(Habbo habbo, boolean wallItem, int baseItemId, String legacyPosterId) {
        if (habbo == null || baseItemId <= 0) {
            return 0;
        }
        int count = 0;
        for (HabboItem item : inventoryItems(habbo)) {
            if (matchesFurniType(item, wallItem, baseItemId, legacyPosterId)) {
                count++;
            }
        }
        return count;
    }

    public static List<ChestFurniStoredItem> takeFromInventory(Habbo habbo, boolean wallItem, int baseItemId, String legacyPosterId, int amount) {
        List<ChestFurniStoredItem> taken = new ArrayList<>();
        if (habbo == null || habbo.getClient() == null || baseItemId <= 0 || amount <= 0) {
            return taken;
        }

        List<HabboItem> removedItems = new ArrayList<>();
        for (HabboItem item : inventoryItems(habbo)) {
            if (taken.size() >= amount) {
                break;
            }
            if (!matchesFurniType(item, wallItem, baseItemId, legacyPosterId)) {
                continue;
            }
            HabboItem removed = habbo.getInventory().getItemsComponent().getHabboItem(item.getId());
            if (removed == null) {
                continue;
            }
            habbo.getInventory().getItemsComponent().removeHabboItem(removed);
            removedItems.add(removed);
            taken.add(ChestFurniStoredItem.fromHabboItem(removed, removed.getId()));
        }

        if (!removedItems.isEmpty()) {
            for (HabboItem removed : removedItems) {
                habbo.getClient().sendResponse(new RemoveHabboItemComposer(removed.getGiftAdjustedId()));
            }
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
            Emulator.getThreading().run(new QueryDeleteHabboItems(removedItems));
        }

        return taken;
    }

    public static void depositToChest(InteractionWiredChest chest, List<ChestFurniStoredItem> items) {
        if (chest == null || items == null || items.isEmpty()) {
            return;
        }
        ChestStorage contents = chest.getContents();
        for (ChestFurniStoredItem item : items) {
            if (item != null) {
                contents.addFurniItem(item);
            }
        }
        chest.persistContents();
    }

    private static int deliverStoredItems(Habbo habbo, List<ChestFurniStoredItem> removed) {
        int given = 0;
        for (ChestFurniStoredItem stored : removed) {
            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(stored.baseItemId);
            if (baseItem == null) {
                continue;
            }
            HabboItem created = Emulator.getGameEnvironment().getItemManager().createItem(
                    habbo.getHabboInfo().getId(), baseItem, stored.limitedStack, stored.limitedSells, stored.extradata);
            if (created == null) {
                continue;
            }
            habbo.getClient().sendResponse(new AddHabboItemComposer(created));
            habbo.getInventory().getItemsComponent().addItem(created);
            given++;
        }
        if (given > 0) {
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }
        return given;
    }

    private static Collection<HabboItem> inventoryItems(Habbo habbo) {
        return habbo.getInventory().getItemsComponent().getItemsAsValueCollection();
    }

    private static boolean matchesFurniType(HabboItem item, boolean wallItem, int baseItemId, String legacyPosterId) {
        if (item == null) {
            return false;
        }
        Item base = item.getBaseItem();
        if (base == null || base.getId() != baseItemId) {
            return false;
        }
        if ((base.getType() == FurnitureType.WALL) != wallItem) {
            return false;
        }
        if (!wallItem) {
            return true;
        }
        String poster = legacyPosterId == null ? "" : legacyPosterId;
        String extra = item.getExtradata() == null ? "" : item.getExtradata();
        return poster.isEmpty() || poster.equals(extra);
    }
}
