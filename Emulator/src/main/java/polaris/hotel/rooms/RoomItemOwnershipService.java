package polaris.hotel.rooms;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.items.interactions.InteractionPlant;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.InventoryUpdateItemComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemsComposer;
import polaris.messages.outgoing.rooms.items.RemoveWallItemComposer;
import polaris.messages.outgoing.rooms.items.RemoveWallItemsComposer;
import polaris.plugin.Event;
import polaris.plugin.events.furniture.FurniturePickedUpEvent;

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

    void add(UserItem item) {
        if (item == null) {
            return;
        }
        this.add(item, BuildersClubRoomSupport.isTrackedItem(item.getId()), true);
    }

    /**
     * Adds an item whose Builders Club tracking is already known. A room load passes the tracking it
     * read in one query and no name lookup, so it never asks the pool for a second connection.
     */
    void add(UserItem item, boolean trackedBuildersClub, boolean lookUpOwnerName) {
        if (item == null) {
            return;
        }

        synchronized (this.index.items()) {
            try {
                this.index.items().put(item.getId(), item);
                this.index.registerIncarnation(item);
                this.index.trackItem(item);
            } catch (Exception e) {
                LOGGER.error("Item ID ({}) could not be indexed in its room", item.getId(), e);
            }
        }

        if (trackedBuildersClub) {
            // From now on the client knows it by an id of the official Builders Club band.
            this.index.buildersClubIds().assign(item.getId());
        }

        if (trackedBuildersClub && item.getUserId() != BuildersClubRoomSupport.VIRTUAL_OWNER_ID) {
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
                this.addOwnerName(item, trackedBuildersClub, lookUpOwnerName);
            }
        }

        this.registry.register(item);
    }

    /**
     * Takes an item out of the room.
     *
     * @return whether this call is the one that removed it. Two threads can reach the same
     *     item - a pickup, a room eviction and a consumer such as a seed or a pet box - and
     *     only one of them may go on to delete the row and hand out what the item turns into.
     */
    boolean remove(UserItem item) {
        if (item == null) {
            return false;
        }

        boolean trackedBuildersClubItem = BuildersClubRoomSupport.isTrackedItem(item.getId());
        int trackedUserId =
                trackedBuildersClubItem ? BuildersClubRoomSupport.getTrackedUserId(item.getId()) : item.getUserId();

        UserItem removed;
        synchronized (this.index.items()) {
            removed = this.index.items().remove(item.getId());
        }

        if (removed != null) {
            this.index.buildersClubIds().release(removed.getId());
            this.index.untrackItem(removed);
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

        return removed != null;
    }

    int uniqueItemCount(int userId) {
        Set<Item> baseItems = new HashSet<>();
        synchronized (this.index.items()) {
            for (UserItem item : this.index.items().values()) {
                if (!baseItems.contains(item.getBaseItem()) && item.getUserId() == userId) {
                    baseItems.add(item.getBaseItem());
                }
            }
        }
        return baseItems.size();
    }

    void pickUp(UserItem item, User picker) {
        this.pickUp(item, picker, true, null);
    }

    /**
     * Picks a whole set up with the AIR 13 batched removals: one
     * {@code ObjectRemoveMultiple} (1451) for the floor items and one
     * {@code ItemRemoveMultiple} (2204) for the wall items, instead of one
     * {@code ObjectRemove} / {@code ItemRemove} per item. The client disposes
     * the batch and refreshes the tile object map once.
     */
    void pickUpAll(Collection<UserItem> items, User picker) {
        this.pickUpAll(items, picker, picker != null ? picker.getUserInfo().getId() : 0);
    }

    /**
     * Takes Place Furni's temporary copies out of the room in one {@code ObjectRemoveMultiple} with picker -1, as
     * Wired removal does in Habbo. Behaviour observed in plus-emulator/PlusEMU 55f02174, reimplemented.
     */
    void removeTemporaryItems(Collection<UserItem> items) {
        this.pickUpAll(items, null, -1);
    }

    private void pickUpAll(Collection<UserItem> items, User picker, int pickerId) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<Integer> floorItemIds = new ArrayList<>();
        List<Integer> wallItemIds = new ArrayList<>();
        // A whole room emptied at once: the owners get their furni in one delivery each and the freed floor is
        // recomputed once, instead of an inventory packet, a reload and a stack-height update per furni.
        PickUpBatch batch = new PickUpBatch();

        for (UserItem item : items) {
            if (item == null) {
                continue;
            }
            FurnitureType type = item.getBaseItem().getType();
            if (!this.pickUp(item, picker, false, batch)) {
                continue;
            }
            if (type == FurnitureType.FLOOR) {
                floorItemIds.add(item.getId());
            } else if (type == FurnitureType.WALL) {
                wallItemIds.add(item.getId());
            }
        }

        if (!floorItemIds.isEmpty()) {
            this.room.sendComposer(new RemoveFloorItemsComposer(floorItemIds, pickerId).compose());
        }
        if (!wallItemIds.isEmpty()) {
            this.room.sendComposer(new RemoveWallItemsComposer(wallItemIds, pickerId).compose());
        }

        if (!batch.freedTiles.isEmpty()) {
            this.refreshTiles(batch.freedTiles);
        }
        batch.deliveries.forEach((owner, delivered) -> deliver(owner, delivered, picker));
    }

    /** What a batched pick-up sends once at the end instead of once per furni. */
    private static final class PickUpBatch {
        private final Set<RoomTile> freedTiles = new HashSet<>();
        private final Map<User, List<UserItem>> deliveries = new HashMap<>();
    }

    /**
     * @param sendRemoveComposer when false the caller sends the batched removal
     *     composer itself; the room-side state changes are identical.
     * @param batch when not null, the freed floor and the owner's delivery are collected there and sent once by the
     *     caller.
     * @return whether the item was actually picked up (a plugin can cancel it).
     */
    private boolean pickUp(UserItem item, User picker, boolean sendRemoveComposer, PickUpBatch batch) {
        if (item == null) {
            return false;
        }

        // A gift being unwrapped stays in the room until OpenGift takes it out: picked up in the
        // meantime, its contents were delivered and the wrapper stayed in the inventory as well.
        if (item instanceof InteractionGift gift && gift.isOpening()) {
            return false;
        }

        boolean trackedBuildersClubItem = BuildersClubRoomSupport.isTrackedItem(item.getId());
        boolean destroyOnPickup = isDestroyedOnPickup(item);

        if (Emulator.getPluginManager().isRegistered(FurniturePickedUpEvent.class, true)) {
            Event furniturePickedUpEvent = new FurniturePickedUpEvent(item, picker);
            Emulator.getPluginManager().fireEvent(furniturePickedUpEvent);
            if (furniturePickedUpEvent.isCancelled()) {
                return false;
            }
        }

        this.room.removeUserItem(item.getId());
        item.onPickUp(this.room);
        item.setRoomId(0);
        item.needsUpdate(true);

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (sendRemoveComposer) {
                this.room.sendComposer(new RemoveFloorItemComposer(item).compose());
            }
            if (batch != null) {
                batch.freedTiles.addAll(this.floorArea(item));
            } else {
                this.refreshTiles(this.floorArea(item));
            }
            RoomConfigurationFurniSupport.onControllerRemoved(this.room, item);
        } else if (item.getBaseItem().getType() == FurnitureType.WALL && sendRemoveComposer) {
            this.room.sendComposer(new RemoveWallItemComposer(item).compose());
        }

        if (trackedBuildersClubItem) {
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            return true;
        }

        if (destroyOnPickup) {
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            return true;
        }

        User owner = picker != null && picker.getUserInfo().getId() == item.getUserId()
                ? picker
                : Emulator.getGameServer().getGameClientManager().getUser(item.getUserId());
        if (owner != null) {
            if (batch != null) {
                batch.deliveries
                        .computeIfAbsent(owner, ignored -> new ArrayList<>())
                        .add(item);
            } else {
                deliver(owner, List.of(item), picker);
            }
        }
        Emulator.getThreading().run(item);

        return true;
    }

    void ejectUserFurniture(int userId) {
        Set<UserItem> items = new HashSet<>();
        synchronized (this.index.items()) {
            for (UserItem item : this.index.items().values()) {
                if (item.getUserId() == userId) {
                    items.add(item);
                    item.setRoomId(0);
                }
            }
        }

        // The owner gets them back from pickUpAll, in one delivery; adding them here as well sent everything twice.
        this.pickUpAll(items, null);
    }

    void ejectAll(User exemptOwner) {
        this.ejectAll(exemptOwner, Set.of());
    }

    /** The owner's pickall: everything goes back to its owners except the Builders Club furni. */
    void ejectAllExceptBuildersClub() {
        this.ejectAll(null, BuildersClubRoomSupport.getTrackedItemIds(this.room.getId()));
    }

    /** Takes every Builders Club furni out of the room; they return to the Builders Club, not to an inventory. */
    void removeBuildersClubItems(User picker) {
        Set<Integer> trackedIds = BuildersClubRoomSupport.getTrackedItemIds(this.room.getId());
        if (trackedIds.isEmpty()) {
            return;
        }

        Set<UserItem> items = new HashSet<>();
        synchronized (this.index.items()) {
            for (Integer itemId : trackedIds) {
                UserItem item = this.index.items().get(itemId.intValue());
                if (item != null) {
                    items.add(item);
                }
            }
        }

        this.pickUpAll(items, picker);
    }

    private void ejectAll(User exemptOwner, Set<Integer> skippedItemIds) {
        Map<Integer, Set<UserItem>> itemsByOwner = new HashMap<>();
        synchronized (this.index.items()) {
            for (UserItem item : this.index.items().values()) {
                if (exemptOwner != null
                        && item.getUserId() == exemptOwner.getUserInfo().getId()) {
                    continue;
                }
                if (item instanceof InteractionPostIt || skippedItemIds.contains(item.getId())) {
                    continue;
                }
                itemsByOwner
                        .computeIfAbsent(item.getUserId(), ignored -> new HashSet<>())
                        .add(item);
            }
        }

        for (Set<UserItem> ownerItems : itemsByOwner.values()) {
            this.pickUpAll(ownerItems, null);
        }
    }

    private static boolean isDestroyedOnPickup(UserItem item) {
        // A temporary furni (wired "place furni") has nobody to go back to: picking it up removes it.
        return item.isTemporary() || (item instanceof InteractionPlant && ((InteractionPlant) item).isDead());
    }

    private void addOwnerName(UserItem item, boolean trackedBuildersClub, boolean lookUp) {
        if (item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID && trackedBuildersClub) {
            this.index.ownerNames().put(item.getUserId(), BuildersClubRoomSupport.DISPLAY_OWNER_NAME);
            return;
        }

        UserInfo owner = lookUp ? UserManager.getOfflineUserInfo(item.getUserId()) : null;
        if (owner != null) {
            this.index.ownerNames().put(item.getUserId(), owner.getUsername());
        } else {
            LOGGER.error("Failed to find username for item (ID: {}, UserID: {})", item.getId(), item.getUserId());
        }
    }

    private void removeOwnerIndex(UserItem item) {
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

    private Set<RoomTile> floorArea(UserItem item) {
        Set<RoomTile> tiles = new HashSet<>();
        Rectangle rectangle = RoomLayout.getRectangle(
                item.getX(),
                item.getY(),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());

        for (short x = (short) rectangle.x; x < rectangle.x + rectangle.getWidth(); x++) {
            for (short y = (short) rectangle.y; y < rectangle.y + rectangle.getHeight(); y++) {
                RoomTile tile = this.room.currentLayout().getTile(x, y);
                if (tile != null) {
                    tiles.add(tile);
                }
            }
        }

        return tiles;
    }

    /** Recomputes the stack height of tiles a pick-up freed and tells the room once. */
    private void refreshTiles(Set<RoomTile> updatedTiles) {
        for (RoomTile tile : updatedTiles) {
            tile.setStackHeight(this.room.getStackHeight(tile.x, tile.y, false));
        }

        this.room.sendComposer(new UpdateStackHeightComposer(this.room, updatedTiles).compose());
        this.room.updateTiles(updatedTiles);
        for (RoomTile tile : updatedTiles) {
            this.room.updateUsersAt(tile.x, tile.y);
            this.room.updateBotsAt(tile.x, tile.y);
        }
    }

    private static void deliver(User owner, List<UserItem> items, User picker) {
        // One furni keeps the single-item event plugins listen to; a batch fires the batch event once.
        if (items.size() == 1) {
            owner.getInventory().getItemsComponent().addItem(items.get(0));
            owner.getClient().sendResponse(new AddUserItemComposer(items.get(0)));
        } else {
            owner.getInventory().getItemsComponent().addItems(items);
            owner.getClient().sendResponse(new AddUserItemComposer(items));
        }
        if (owner == picker) {
            // Habbo sends the picker the item itself.
            for (UserItem item : items) {
                owner.getClient().sendResponse(new InventoryUpdateItemComposer(item));
            }
        } else {
            // An item returned to its absent owner keeps the reload, once per delivery.
            owner.getClient().sendResponse(new InventoryRefreshComposer());
        }
    }
}
