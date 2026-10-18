package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every place that deletes an item row releases a chest first, and only a chest: the stored furni and
 * coins are with the owner before the {@code DELETE FROM items} runs, and furni that are no chest
 * never reach the release.
 *
 * <p>Callers that only reach the database through these (the recycler, crackable explode, rentable
 * expiry, Builders Club cleanup, catalog rollbacks, pets, crafting, commands, ...) are covered by
 * covering these.
 */
class ChestDeletionPathsTest {
    private static final int OWNER = 4;
    private static final int CHEST = 2000;
    private static final int OTHER = 2001;

    private InMemoryChestStore store;
    private ChestStore previousStore;
    private Field databaseField;
    private Object previousDatabase;
    /** For every item delete prepared: whether the chest still had its row at that moment. */
    private final List<Boolean> chestRowAtDelete = new ArrayList<>();

    @BeforeEach
    void install() throws Exception {
        this.store = new InMemoryChestStore();
        this.previousStore = ChestStores.use(this.store);
        this.store.createChest(CHEST, ChestStorage.KIND_FURNI, 60, 0, "{}");
        this.store.storeItem(CHEST, new ChestStore.StoredRow(2100, 4100, "1", "", "2:9", 5, 0, false, 1L));

        this.databaseField = Emulator.class.getDeclaredField("database");
        this.databaseField.setAccessible(true);
        this.previousDatabase = this.databaseField.get(null);
        this.databaseField.set(null, this.recordingDatabase());
    }

    @AfterEach
    void restore() throws Exception {
        ChestStores.use(this.previousStore);
        this.databaseField.set(null, this.previousDatabase);
    }

    private Database recordingDatabase() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet empty = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(empty);
        when(statement.executeBatch()).thenReturn(new int[0]);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("DELETE FROM items")) this.chestRowAtDelete.add(this.store.loadChest(CHEST) != null);
            return statement;
        });
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(statement);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);
        return database;
    }

    private void assertChestReleasedBeforeTheDelete() {
        assertNull(this.store.loadChest(CHEST), "the chest's rows are gone");
        assertEquals(OWNER, this.store.inventoryItem(2100).ownerId(), "the stored furni is back with the owner");
        assertEquals(60, this.store.creditsOf(OWNER), "the coins are back as credits");
        assertFalse(this.chestRowAtDelete.isEmpty(), "the item row was deleted");
        assertFalse(this.chestRowAtDelete.getFirst(), "released before its item row was deleted");
        assertEquals(List.of(CHEST), this.store.releaseCalls());
    }

    private static HabboItem chest() {
        InteractionWiredChestFurni chest = mock(InteractionWiredChestFurni.class);
        when(chest.getId()).thenReturn(CHEST);
        when(chest.getUserId()).thenReturn(OWNER);
        return chest;
    }

    private static HabboItem otherFurni() {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(OTHER);
        when(item.getUserId()).thenReturn(OWNER);
        return item;
    }

    @Test
    void itemManagerDeleteItemReleasesAChest() {
        new ItemManager().deleteItem(chest());

        this.assertChestReleasedBeforeTheDelete();
    }

    @Test
    void itemManagerDeleteItemLeavesOtherFurniAlone() {
        new ItemManager().deleteItem(otherFurni());

        assertTrue(this.store.releaseCalls().isEmpty());
        assertNotNull(this.store.loadChest(CHEST));
    }

    @Test
    void queryDeleteByIdLeavesOtherIdsAlone() {
        new QueryDeleteHabboItem(OTHER).run();

        assertTrue(this.store.releaseCalls().isEmpty());
        assertNotNull(this.store.loadChest(CHEST));
    }

    @Test
    void queryDeleteByIdReleasesAChest() {
        this.store.giveInventoryItem(CHEST, OWNER, 3000); // the chest item's own row names its owner

        new QueryDeleteHabboItem(CHEST).run();

        this.assertChestReleasedBeforeTheDelete();
    }

    @Test
    void queryDeleteByItemReleasesAChestAndNothingElse() {
        new QueryDeleteHabboItem(otherFurni()).run();
        assertTrue(this.store.releaseCalls().isEmpty());

        new QueryDeleteHabboItem(chest()).run();
        this.chestRowAtDelete.removeFirst();
        this.assertChestReleasedBeforeTheDelete();
    }

    @Test
    void queryDeleteBatchReleasesOnlyItsChests() {
        new QueryDeleteHabboItems(List.of(otherFurni(), chest())).run();

        this.assertChestReleasedBeforeTheDelete();
    }

    @Test
    void habboItemSaveWithDeleteReleasesAChest() {
        InteractionWiredChestFurni chest = new InteractionWiredChestFurni(CHEST, OWNER, mock(Item.class), "0", 0, 0);
        chest.needsDelete(true);

        chest.run();

        this.assertChestReleasedBeforeTheDelete();
        assertEquals(0, chest.getContents().furniItemCount(), "the live chest forgets what it held");
        assertNull(this.store.loadChest(CHEST), "and does not create its row again");
    }

    @Test
    void habboItemSaveWithDeleteLeavesOtherFurniAlone() {
        // Even an item with the chest's id: a furni object that is no chest is never looked up.
        HabboItem item = new InteractionDefault(CHEST, OWNER, mock(Item.class), "0", 0, 0);
        item.needsDelete(true);

        item.run();

        assertTrue(this.store.releaseCalls().isEmpty());
        assertNotNull(this.store.loadChest(CHEST));
    }

    @Test
    void inventoryDisposeReleasesDeletedChestsOnly() {
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getHabboInfo().getId()).thenReturn(OWNER);
        ItemsComponent inventory = new ItemsComponent(mock(HabboInventory.class), habbo);
        HabboItem kept = chest();
        HabboItem deletedOther = otherFurni();
        when(deletedOther.needsDelete()).thenReturn(true);
        inventory.getItems().put(CHEST, kept);
        inventory.getItems().put(OTHER, deletedOther);

        inventory.dispose();
        assertTrue(this.store.releaseCalls().isEmpty(), "a chest that is only saved keeps its contents");

        HabboItem deletedChest = chest();
        when(deletedChest.needsDelete()).thenReturn(true);
        inventory.getItems().put(CHEST, deletedChest);
        this.chestRowAtDelete.clear();

        inventory.dispose();
        this.assertChestReleasedBeforeTheDelete();
    }

    @Test
    void aChestIsNeverRedeemedAsCreditFurniInATrade() {
        HabboItem chest = chest();
        Item base = mock(Item.class);
        when(base.getName()).thenReturn("CF_500_chest");
        when(chest.getBaseItem()).thenReturn(base);

        assertEquals(0, RoomTrade.getCreditsByItem(chest));
    }
}
