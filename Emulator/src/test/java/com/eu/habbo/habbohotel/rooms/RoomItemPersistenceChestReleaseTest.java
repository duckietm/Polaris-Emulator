package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStore;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStores;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InMemoryChestStore;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A room save that deletes a chest gives its contents back to the owner before the row goes. */
class RoomItemPersistenceChestReleaseTest {
    private static final int OWNER = 12;
    private static final int CHEST = 7000;

    private InMemoryChestStore store;
    private ChestStore previous;

    @BeforeEach
    void useMemoryStore() {
        this.store = new InMemoryChestStore();
        this.previous = ChestStores.use(this.store);
        this.store.createChest(CHEST, ChestStorage.KIND_CURRENCY, 90, 0, "{}");
    }

    @AfterEach
    void restoreStore() {
        ChestStores.use(this.previous);
    }

    @Test
    void deletedChestsAreReleasedBeforeTheirRowsAndOtherFurniAreNot() throws Exception {
        List<Boolean> chestRowAtDelete = new ArrayList<>();
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeBatch()).thenReturn(new int[0]);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("DELETE FROM items")) chestRowAtDelete.add(this.store.loadChest(CHEST) != null);
            return statement;
        });

        HabboItem chest = mock(InteractionWiredChestCurrency.class);
        when(chest.getId()).thenReturn(CHEST);
        when(chest.getUserId()).thenReturn(OWNER);
        when(chest.needsDelete()).thenReturn(true);
        HabboItem other = mock(HabboItem.class);
        when(other.getId()).thenReturn(7001);
        when(other.getUserId()).thenReturn(OWNER);
        when(other.needsDelete()).thenReturn(true);

        new RoomItemPersistence(() -> connection).save(List.of(other, chest));

        assertNull(this.store.loadChest(CHEST));
        assertEquals(90, this.store.creditsOf(OWNER));
        assertEquals(List.of(CHEST), this.store.releaseCalls());
        assertEquals(List.of(false), chestRowAtDelete);
    }
}
