package polaris.hotel.users.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.chest.ChestRelease;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;
import polaris.plugin.events.inventory.InventoryItemAddedEvent;
import polaris.plugin.events.inventory.InventoryItemRemovedEvent;
import polaris.plugin.events.inventory.InventoryItemsAddedEvent;

public class ItemsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemsComponent.class);

    private final Int2ObjectMap<UserItem> items = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    private final UserInventory inventory;

    public ItemsComponent(UserInventory inventory, User user) {
        this.inventory = inventory;
        this.items.putAll(loadItems(user));
    }

    public static Int2ObjectMap<UserItem> loadItems(User user) {
        Int2ObjectMap<UserItem> itemsList = new Int2ObjectOpenHashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT items.* FROM items LEFT JOIN builders_club_items ON builders_club_items.item_id = items.id WHERE items.room_id = ? AND items.user_id = ? AND builders_club_items.item_id IS NULL")) {
            statement.setInt(1, 0);
            statement.setInt(2, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        UserItem item =
                                Emulator.getGameEnvironment().getItemManager().loadUserItem(set);

                        if (item != null) {
                            itemsList.put(set.getInt("id"), item);
                        } else {
                            LOGGER.error("Failed to load item: {}", set.getInt("id"));
                        }
                    } catch (SQLException e) {
                        LOGGER.error("Caught SQL exception", e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return itemsList;
    }

    public void addItem(UserItem item) {
        if (item == null) {
            return;
        }

        InventoryItemAddedEvent event = new InventoryItemAddedEvent(this.inventory, item);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            this.items.put(event.item.getId(), event.item);
        }

        this.inventory.getUnseenItemsComponent().markUnseen(unseenCategory(event.item), event.item.getId());
    }

    /** Official unseen categories: 1 for floor furni, 2 for wall furni. */
    private static int unseenCategory(UserItem item) {
        return item.getBaseItem() != null && item.getBaseItem().getType() == FurnitureType.WALL
                ? UnseenItemsComponent.CATEGORY_WALL_ITEM
                : UnseenItemsComponent.CATEGORY_FLOOR_ITEM;
    }

    public void addItems(Collection<UserItem> items) {
        InventoryItemsAddedEvent event = new InventoryItemsAddedEvent(this.inventory, new HashSet<>(items));
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            for (UserItem item : event.items) {
                if (item == null) {
                    continue;
                }

                this.items.put(item.getId(), item);
            }
        }
    }

    public UserItem getUserItem(int itemId) {
        return this.items.get(Math.abs(itemId));
    }

    /**
     * Takes one item of this base item out of the inventory, or null when there is none. A missing item used to reach
     * removeUserItem(null) and throw, so crafting and the wired take-furni effect never reached their own null check
     * and left the items already taken out of the inventory.
     */
    public UserItem getAndRemoveUserItem(final Item item) {
        UserItem found = null;
        synchronized (this.items) {
            for (UserItem object : this.items.values()) {
                if (object.getBaseItem() == item) {
                    found = object;
                    break;
                }
            }
        }
        if (found == null) {
            return null;
        }
        this.removeUserItem(found);
        return found;
    }

    /** The same, skipping rented furni: an ingredient is the owner's to spend, a rented furni is not. */
    public UserItem getAndRemoveUnrentedUserItem(final Item item) {
        UserItem found = null;
        synchronized (this.items) {
            for (UserItem object : this.items.values()) {
                if (object.getBaseItem() == item && !object.hasRentPeriod()) {
                    found = object;
                    break;
                }
            }
        }
        if (found == null) {
            return null;
        }
        this.removeUserItem(found);
        return found;
    }

    public void removeUserItem(int itemId) {
        this.items.remove(itemId);
    }

    public void removeUserItem(UserItem item) {
        InventoryItemRemovedEvent event = new InventoryItemRemovedEvent(this.inventory, item);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            this.items.remove(event.item.getId());
        }
    }

    public boolean takeUserItemsAtomically(Collection<UserItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) return false;

        Set<UserItem> uniqueItems = new HashSet<>(requestedItems);
        if (uniqueItems.size() != requestedItems.size()) return false;

        synchronized (this.items) {
            for (UserItem item : uniqueItems) {
                if (item == null || this.items.get(item.getId()) != item) return false;
            }

            for (UserItem item : uniqueItems) {
                InventoryItemRemovedEvent event = new InventoryItemRemovedEvent(this.inventory, item);

                if (Emulator.getPluginManager().fireEvent(event).isCancelled()
                        || event.item == null
                        || event.item.getId() != item.getId()) return false;
            }

            for (UserItem item : uniqueItems) this.items.remove(item.getId());

            return true;
        }
    }

    public void restoreUserItemsAfterFailedTake(Collection<UserItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) return;

        synchronized (this.items) {
            for (UserItem item : requestedItems) {
                if (item != null) this.items.put(item.getId(), item);
            }
        }
    }

    public Int2ObjectMap<UserItem> getItems() {
        return this.items;
    }

    public Set<UserItem> getItemsAsValueCollection() {
        return new HashSet<>(this.items.values());
    }

    public int itemCount() {
        return this.items.size();
    }

    public void dispose() {
        synchronized (this.items) {
            if (!this.items.isEmpty()) {
                // Wired chests give what they hold back to their owner before their rows go.
                ChestRelease.releaseAll(this.items.values().stream()
                        .filter(UserItem::needsDelete)
                        .toList());
                try (Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection()) {
                    try (PreparedStatement updateStmt = connection.prepareStatement(
                            "UPDATE items SET user_id = ?, room_id = ?, wall_pos = ?, x = ?, y = ?, z = ?, rot = ?, extra_data = ?, limited_data = ? WHERE id = ?")) {
                        try (PreparedStatement deleteStmt =
                                connection.prepareStatement("DELETE FROM items WHERE id = ?")) {

                            int updateCount = 0;
                            int deleteCount = 0;

                            for (UserItem item : this.items.values()) {
                                if (item.needsDelete()) {
                                    deleteStmt.setInt(1, item.getId());
                                    deleteStmt.addBatch();
                                    deleteCount++;
                                    item.needsUpdate(false);
                                    item.needsDelete(false);
                                } else if (item.needsUpdate()) {
                                    updateStmt.setInt(1, item.getUserId());
                                    updateStmt.setInt(2, item.getRoomId());
                                    updateStmt.setString(3, item.getWallPosition());
                                    updateStmt.setInt(4, item.getX());
                                    updateStmt.setInt(5, item.getY());
                                    updateStmt.setDouble(6, item.getZ());
                                    updateStmt.setInt(7, item.getRotation());
                                    updateStmt.setString(8, item.getExtradata());
                                    updateStmt.setString(9, item.getLimitedStack() + ":" + item.getLimitedSells());
                                    updateStmt.setInt(10, item.getId());
                                    updateStmt.addBatch();
                                    updateCount++;
                                    item.needsUpdate(false);
                                }

                                if (updateCount > 0 && updateCount % 100 == 0) {
                                    updateStmt.executeBatch();
                                }
                                if (deleteCount > 0 && deleteCount % 100 == 0) {
                                    deleteStmt.executeBatch();
                                }
                            }

                            if (deleteCount % 100 != 0) {
                                deleteStmt.executeBatch();
                            }
                            if (updateCount % 100 != 0) {
                                updateStmt.executeBatch();
                            }
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception during batch item save", e);
                }
            }

            this.items.clear();
        }
    }
}
