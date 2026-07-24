package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveWallItemComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurniturePickedUpEvent;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RoomItemOwnershipService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomItemOwnershipService.class);

    private final Room room;
    private final RoomItemIndex index;
    private final RoomItemRegistry registry;

    RoomItemOwnershipService(Room room, RoomItemIndex index, RoomItemRegistry registry) {
        this.room = room;
        this.index = index;
        this.registry = registry;
    }

    void add(HabboItem item) {
        if (item == null) {
            return;
        }

        synchronized (this.index.items()) {
            try {
                this.index.items().put(item.getId(), item);
                this.index.registerIncarnation(item);
            } catch (Exception ignored) {
                // Preserve the legacy best-effort registration behavior.
            }
        }

        if (BuildersClubRoomSupport.isTrackedItem(item.getId())
                && item.getUserId() != BuildersClubRoomSupport.VIRTUAL_OWNER_ID) {
            item.setVirtualUserId(BuildersClubRoomSupport.VIRTUAL_OWNER_ID);
            item.needsUpdate(true);
        }

        synchronized (this.index.ownerCounts()) {
            this.index
                    .ownerCounts()
                    .put(item.getUserId(), this.index.ownerCounts().get(item.getUserId()) + 1);
        }

        synchronized (this.index.ownerNames()) {
            if (!this.index.ownerNames().containsKey(item.getUserId())) {
                this.addOwnerName(item);
            }
        }

        this.registry.register(item);
    }

    void remove(HabboItem item) {
        if (item == null) {
            return;
        }

        boolean trackedBuildersClubItem = BuildersClubRoomSupport.isTrackedItem(item.getId());
        int trackedUserId =
                trackedBuildersClubItem ? BuildersClubRoomSupport.getTrackedUserId(item.getId()) : item.getUserId();

        HabboItem removed;
        synchronized (this.index.items()) {
            removed = this.index.items().remove(item.getId());
        }

        if (removed != null) {
            this.index.unregisterIncarnation(removed);
            this.removeOwnerIndex(removed);
            this.registry.unregister(item);
        }

        if (trackedBuildersClubItem) {
            BuildersClubRoomSupport.deleteTrackedItem(item.getId());
            if (BuildersClubRoomSupport.syncRoom(this.room) == BuildersClubRoomSupport.SyncResult.UNLOCKED) {
                BuildersClubRoomSupport.sendRoomUnlockedBubble(this.room.getOwnerId());
            }
            BuildersClubRoomSupport.sendPlacementStatusForPool(this.room, trackedUserId);
        }
    }

    int uniqueItemCount(int userId) {
        Set<Item> baseItems = new HashSet<>();
        synchronized (this.index.items()) {
            for (HabboItem item : this.index.items().values()) {
                if (!baseItems.contains(item.getBaseItem()) && item.getUserId() == userId) {
                    baseItems.add(item.getBaseItem());
                }
            }
        }
        return baseItems.size();
    }

    void pickUp(HabboItem item, Habbo picker) {
        if (item == null) {
            return;
        }

        boolean trackedBuildersClubItem = BuildersClubRoomSupport.isTrackedItem(item.getId());

        if (Emulator.getPluginManager().isRegistered(FurniturePickedUpEvent.class, true)) {
            Event furniturePickedUpEvent = new FurniturePickedUpEvent(item, picker);
            Emulator.getPluginManager().fireEvent(furniturePickedUpEvent);
            if (furniturePickedUpEvent.isCancelled()) {
                return;
            }
        }

        this.room.removeHabboItem(item.getId());
        item.onPickUp(this.room);
        item.setRoomId(0);
        item.needsUpdate(true);

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.sendComposer(new RemoveFloorItemComposer(item).compose());
            this.refreshRemovedFloorArea(item);
        } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.room.sendComposer(new RemoveWallItemComposer(item).compose());
        }

        if (trackedBuildersClubItem) {
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            return;
        }

        Habbo owner = picker != null && picker.getHabboInfo().getId() == item.getUserId()
                ? picker
                : Emulator.getGameServer().getGameClientManager().getHabbo(item.getUserId());
        if (owner != null) {
            owner.getInventory().getItemsComponent().addItem(item);
            owner.getClient().sendResponse(new AddHabboItemComposer(item));
            owner.getClient().sendResponse(new InventoryRefreshComposer());
        }
        Emulator.getThreading().run(item);
    }

    void ejectUserFurniture(int userId) {
        Set<HabboItem> items = new HashSet<>();
        Set<HabboItem> inventoryItems = new HashSet<>();
        synchronized (this.index.items()) {
            for (HabboItem item : this.index.items().values()) {
                if (item.getUserId() == userId) {
                    items.add(item);
                    if (!BuildersClubRoomSupport.isTrackedItem(item.getId())) {
                        inventoryItems.add(item);
                    }
                    item.setRoomId(0);
                }
            }
        }

        Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (owner != null && !inventoryItems.isEmpty()) {
            addInventoryItems(owner, inventoryItems);
        }
        for (HabboItem item : items) {
            this.pickUp(item, null);
        }
    }

    void ejectAll(Habbo exemptOwner) {
        Map<Integer, Set<HabboItem>> itemsByOwner = new HashMap<>();
        synchronized (this.index.items()) {
            for (HabboItem item : this.index.items().values()) {
                if (exemptOwner != null
                        && item.getUserId() == exemptOwner.getHabboInfo().getId()) {
                    continue;
                }
                if (item instanceof InteractionPostIt) {
                    continue;
                }
                itemsByOwner
                        .computeIfAbsent(item.getUserId(), ignored -> new HashSet<>())
                        .add(item);
            }
        }

        for (Map.Entry<Integer, Set<HabboItem>> entry : itemsByOwner.entrySet()) {
            Set<HabboItem> inventoryItems = new HashSet<>();
            for (HabboItem item : entry.getValue()) {
                if (!BuildersClubRoomSupport.isTrackedItem(item.getId())) {
                    inventoryItems.add(item);
                }
            }
            for (HabboItem item : entry.getValue()) {
                this.pickUp(item, null);
            }

            Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(entry.getKey());
            if (owner != null && !inventoryItems.isEmpty()) {
                addInventoryItems(owner, inventoryItems);
            }
        }
    }

    private void addOwnerName(HabboItem item) {
        if (item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID
                && BuildersClubRoomSupport.isTrackedItem(item.getId())) {
            this.index.ownerNames().put(item.getUserId(), BuildersClubRoomSupport.DISPLAY_OWNER_NAME);
            return;
        }

        HabboInfo owner = HabboManager.getOfflineHabboInfo(item.getUserId());
        if (owner != null) {
            this.index.ownerNames().put(item.getUserId(), owner.getUsername());
        } else {
            LOGGER.error("Failed to find username for item (ID: {}, UserID: {})", item.getId(), item.getUserId());
        }
    }

    private void removeOwnerIndex(HabboItem item) {
        synchronized (this.index.ownerCounts()) {
            synchronized (this.index.ownerNames()) {
                int count = this.index.ownerCounts().get(item.getUserId());
                if (count > 1) {
                    this.index.ownerCounts().put(item.getUserId(), count - 1);
                } else {
                    this.index.ownerCounts().remove(item.getUserId());
                    this.index.ownerNames().remove(item.getUserId());
                }
            }
        }
    }

    private void refreshRemovedFloorArea(HabboItem item) {
        Set<RoomTile> updatedTiles = new HashSet<>();
        Rectangle rectangle = RoomLayout.getRectangle(
                item.getX(),
                item.getY(),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());

        for (short x = (short) rectangle.x; x < rectangle.x + rectangle.getWidth(); x++) {
            for (short y = (short) rectangle.y; y < rectangle.y + rectangle.getHeight(); y++) {
                double stackHeight = this.room.getStackHeight(x, y, false);
                RoomTile tile = this.room.currentLayout().getTile(x, y);
                if (tile != null) {
                    tile.setStackHeight(stackHeight);
                    updatedTiles.add(tile);
                }
            }
        }

        this.room.sendComposer(new UpdateStackHeightComposer(this.room, updatedTiles).compose());
        this.room.updateTiles(updatedTiles);
        for (RoomTile tile : updatedTiles) {
            this.room.updateHabbosAt(tile.x, tile.y);
            this.room.updateBotsAt(tile.x, tile.y);
        }
    }

    private static void addInventoryItems(Habbo owner, Set<HabboItem> items) {
        owner.getInventory().getItemsComponent().addItems(items);
        owner.getClient().sendResponse(new AddHabboItemComposer(items));
        owner.getClient().sendResponse(new InventoryRefreshComposer());
    }
}
