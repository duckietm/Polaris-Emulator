package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.room;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSaveRefusedException;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The chest conditions keep the picked chests, no more of them than the hotel lets a wired box select,
 * refuse a picked furni that is not a chest, and pass when there is no chest to check, as in Habbo.
 */
class WiredConditionChestSelectionTest {

    private static final String SELECTION_CAP = "hotel.wired.furni.selection.count";

    @Test
    void chestsAreKeptAndIdsTheRoomNoLongerKnowsAreLeftOut() {
        Room room = room(1);
        when(room.getHabboItem(1)).thenReturn(mock(InteractionWiredChest.class));
        // Id 3 resolves to nothing at all.

        for (InteractionWiredCondition box : boxes()) {
            assertTrue(save(box, room, 1, 3), box.getClass().getSimpleName());

            JsonArray chests = JsonParser.parseString(box.getWiredData())
                    .getAsJsonObject()
                    .get("chestIds")
                    .getAsJsonArray();
            assertEquals(1, chests.size(), box.getClass().getSimpleName());
            assertEquals(1, chests.get(0).getAsInt(), box.getClass().getSimpleName());
        }
    }

    @Test
    void aPickedFurniThatIsNotAChestIsRefusedWithHabbosText() {
        Room room = room(1);
        when(room.getHabboItem(1)).thenReturn(mock(InteractionWiredChest.class));
        when(room.getHabboItem(2)).thenReturn(mock(HabboItem.class));

        for (InteractionWiredCondition box : boxes()) {
            WiredSaveRefusedException refused =
                    assertThrows(WiredSaveRefusedException.class, () -> save(box, room, 1, 2));
            assertEquals("wiredfurni.error.require_chests", refused.getMessage());
        }
    }

    @Test
    void withNoChestToCheckBothConditionsPassWhateverTheComparison() {
        Room room = room(1);
        when(room.getHabboItem(1)).thenReturn(mock(InteractionWiredChest.class));

        for (InteractionWiredCondition box : boxes()) {
            assertTrue(save(box, room), box.getClass().getSimpleName());
            assertTrue(box.evaluate(context(room, null)), box.getClass().getSimpleName());

            // A picked chest that has been picked up since counts as no chest too.
            assertTrue(save(box, room, 1), box.getClass().getSimpleName());
            when(room.getHabboItem(1)).thenReturn(null);
            assertTrue(box.evaluate(context(room, null)), box.getClass().getSimpleName());
            when(room.getHabboItem(1)).thenReturn(mock(InteractionWiredChest.class));
        }
    }

    /** Saves [10, 5, more than or equal, 3] with these chest picks (Chest Has Item Types' second picks). */
    private static boolean save(InteractionWiredCondition box, Room room, int... picks) {
        WiredSettings settings = chestPicks(box, settings(new int[] {10, 5, WiredComparison.GREATER_EQUAL, 3}), picks);
        ConfigurationManager config = capOf(5);
        GameEnvironment environment = environmentWith(room);

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::configuration).thenReturn(config);
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            return box.saveData(settings);
        }
    }

    @Test
    void moreFurniThanTheHotelAllowsAreRefused() {
        Room room = room(1);

        for (InteractionWiredCondition box : boxes()) {
            WiredSettings settings =
                    chestPicks(box, settings(new int[] {10, 5, WiredComparison.GREATER_EQUAL, 3}), 1, 2, 3, 4, 5, 6);
            ConfigurationManager config = capOf(5);
            GameEnvironment environment = environmentWith(room);

            try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
                platform.when(WiredPlatform::configuration).thenReturn(config);
                platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
                assertFalse(box.saveData(settings), box.getClass().getSimpleName());
            }
        }
    }

    /** AIR picks the chests of Chest Has Item Types as its second furni selection, the item types as the first. */
    private static WiredSettings chestPicks(InteractionWiredCondition box, WiredSettings settings, int... picks) {
        if (box instanceof WiredConditionChestHasItemType) {
            settings.setFurniIds2(picks);
        } else {
            settings.setFurniIds(picks);
        }
        return settings;
    }

    private static ConfigurationManager capOf(int cap) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(SELECTION_CAP)).thenReturn(cap);
        return config;
    }

    private static List<InteractionWiredCondition> boxes() {
        return List.of(
                new WiredConditionChestHasItemType(1, 1, boxBase(), "", 0, 0),
                new WiredConditionChestHasItems(2, 1, boxBase(), "", 0, 0));
    }

    /** A game environment whose room manager answers this room for every id. */
    private static GameEnvironment environmentWith(Room room) {
        RoomManager rooms = mock(RoomManager.class);
        when(rooms.getRoom(anyInt())).thenReturn(room);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        return environment;
    }
}
