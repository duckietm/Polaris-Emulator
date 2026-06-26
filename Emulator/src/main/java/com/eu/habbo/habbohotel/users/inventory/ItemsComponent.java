package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.inventory.InventoryItemAddedEvent;
import com.eu.habbo.plugin.events.inventory.InventoryItemRemovedEvent;
import com.eu.habbo.plugin.events.inventory.InventoryItemsAddedEvent;
import gnu.trove.TCollections;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;

public class ItemsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemsComponent.class);

    private final TIntObjectMap<HabboItem> items = TCollections.synchronizedMap(new TIntObjectHashMap<>());

    private final HabboInventory inventory;

    public ItemsComponent(HabboInventory inventory, Habbo habbo) {
        this.inventory = inventory;
        this.items.putAll(loadItems(habbo));
    }

    public static THashMap<Integer, HabboItem> loadItems(Habbo habbo) {
        var itemsList = new THashMap<Integer, HabboItem>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT items.* FROM items LEFT JOIN builders_club_items ON builders_club_items.item_id = items.id WHERE items.room_id = ? AND items.user_id = ? AND builders_club_items.item_id IS NULL")) {
            statement.setInt(1, 0);
            statement.setInt(2, habbo.getHabboInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        HabboItem item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(set);

                        if (item != null) {
                            itemsList.put(set.getInt("id"), item);
                        } else {
                            LOGGER.error("Failed to load HabboItem: {}", set.getInt("id"));
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

    public void addItem(HabboItem item) {
        if (item == null) {
            return;
        }

        var event = new InventoryItemAddedEvent(this.inventory, item);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            this.items.put(event.item.getId(), event.item);
        }
    }

    public void addItems(THashSet<HabboItem> items) {
        var event = new InventoryItemsAddedEvent(this.inventory, items);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            for (HabboItem item : event.items) {
                if (item == null) {
                    continue;
                }

                this.items.put(item.getId(), item);
            }
        }
    }

    public HabboItem getHabboItem(int itemId) {
        return this.items.get(Math.abs(itemId));
    }

    public HabboItem getAndRemoveHabboItem(final Item item) {
        final HabboItem[] habboItem = {null};
        synchronized (this.items) {
            this.items.forEachValue(new TObjectProcedure<HabboItem>() {
                @Override
                public boolean execute(HabboItem object) {
                    if (object.getBaseItem() == item) {
                        habboItem[0] = object;
                        return false;
                    }

                    return true;
                }
            });
        }
        this.removeHabboItem(habboItem[0]);
        return habboItem[0];
    }

    public void removeHabboItem(int itemId) {
        this.items.remove(itemId);
    }

    public void removeHabboItem(HabboItem item) {
        var event = new InventoryItemRemovedEvent(this.inventory, item);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            this.items.remove(event.item.getId());
        }
    }

    public TIntObjectMap<HabboItem> getItems() {
        return this.items;
    }

    public THashSet<HabboItem> getItemsAsValueCollection() {
        var items = new THashSet<HabboItem>();
        items.addAll(this.items.valueCollection());

        return items;
    }

    public int itemCount() {
        return this.items.size();
    }

    public void dispose() {
        synchronized (this.items) {
            TIntObjectIterator<HabboItem> items = this.items.iterator();

            if (items == null) {
                LOGGER.error("Items is NULL!");
                return;
            }

            if (!this.items.isEmpty()) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                    try (PreparedStatement updateStmt = connection.prepareStatement(
                            "UPDATE items SET user_id = ?, room_id = ?, wall_pos = ?, x = ?, y = ?, z = ?, rot = ?, extra_data = ?, limited_data = ? WHERE id = ?")) {
                        try (PreparedStatement deleteStmt = connection.prepareStatement(
                                "DELETE FROM items WHERE id = ?")) {

                            int updateCount = 0;
                            int deleteCount = 0;

                            for (int i = this.items.size(); i-- > 0; ) {
                                try {
                                    items.advance();
                                } catch (NoSuchElementException e) {
                                    break;
                                }

                                HabboItem item = items.value();
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
