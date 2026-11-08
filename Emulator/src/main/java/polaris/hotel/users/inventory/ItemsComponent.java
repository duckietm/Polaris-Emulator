package polaris.hotel.users.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
        return this.tryRemoveUserItem(found) ? found : null;
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
        return this.tryRemoveUserItem(found) ? found : null;
    }

    public void removeUserItem(int itemId) {
        this.items.remove(itemId);
    }

    public void removeUserItem(UserItem item) {
        this.tryRemoveUserItem(item);
    }

    /**
     * Removes an item unless a plugin cancels it, and says whether the item actually left the inventory. A caller
     * that deletes or hands over the item must check this: a cancelled removal keeps the item in the inventory.
     */
    public boolean tryRemoveUserItem(UserItem item) {
        if (item == null) return false;

        InventoryItemRemovedEvent event = new InventoryItemRemovedEvent(this.inventory, item);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled() || event.item == null) {
            return false;
        }

        synchronized (this.items) {
            return this.items.remove(event.item.getId()) != null;
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
                saveOnDispose(this.items.values());
            }

            this.items.clear();
        }
    }

    /**
     * Writes what the inventory still holds unsaved when its owner leaves. The batch used to write fewer columns than
     * a single save (no rent expiry, no underpass, the shown extra data instead of the stored one) and cleared every
     * flag before running, so a failed batch or a renewed rent was lost at logout. It now writes the same row as
     * {@link UserItem#run()}, clears a flag only once written, and retries the updates one by one if the batch fails.
     */
    static void saveOnDispose(Collection<UserItem> inventory) {
        List<UserItem> deletes = new ArrayList<>();
        List<UserItem> updates = new ArrayList<>();
        for (UserItem item : inventory) {
            if (item.needsDelete()) deletes.add(item);
            else if (item.needsUpdate()) updates.add(item);
        }
        if (deletes.isEmpty() && updates.isEmpty()) return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM items WHERE id = ?");
                PreparedStatement updateStmt = connection.prepareStatement(UserItem.UPDATE_ROW_SQL)) {
            for (UserItem item : deletes) {
                deleteStmt.setInt(1, item.getId());
                deleteStmt.addBatch();
            }
            long[] generations = new long[updates.size()];
            for (int i = 0; i < updates.size(); i++) {
                generations[i] = updates.get(i).bindRow(updateStmt);
                updateStmt.addBatch();
            }
            if (!deletes.isEmpty()) deleteStmt.executeBatch();
            if (!updates.isEmpty()) updateStmt.executeBatch();

            for (UserItem item : deletes) {
                item.needsUpdate(false);
                item.needsDelete(false);
            }
            for (int i = 0; i < updates.size(); i++) {
                updates.get(i).markSaved(generations[i]);
            }
        } catch (SQLException e) {
            // Deletions are not retried through run(): it would release a wired chest's content a second time.
            LOGGER.error("Batch item save failed at logout; saving the updated items one by one", e);
            for (UserItem item : updates) {
                if (item.needsUpdate()) item.run();
            }
        }
    }
}
