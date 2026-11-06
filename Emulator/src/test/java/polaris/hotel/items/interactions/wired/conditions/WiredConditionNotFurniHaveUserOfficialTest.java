package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.interactions.wired.WiredLegacyWireReader;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;

/**
 * Not Furnis Have Avatars (14) as AIR FurnisHaveNoAvatars: the first option is id 1 ("one of the picked furni has no
 * avatar", {@code not_requireall.2}), the second id 0 ("none of them has one", {@code not_requireall.3}).
 */
class WiredConditionNotFurniHaveUserOfficialTest {

    private static WiredConditionNotFurniHaveUser box(Set<Integer> occupied) {
        return new WiredConditionNotFurniHaveUser(7, 1, WiredConditionTestSupport.boxBase(), "", 0, 0) {
            @Override
            protected boolean hasAvatarOnItem(
                    UserItem item, Room room, Collection<User> users, Collection<Bot> bots, Collection<Pet> pets) {
                return occupied.contains(item.getId());
            }
        };
    }

    private static Room roomWithFurni(int... ids) {
        Room room = WiredConditionTestSupport.room(3);
        when(room.getLayout()).thenReturn(mock(RoomLayout.class));
        when(room.getCurrentBots()).thenReturn(new Int2ObjectOpenHashMap<>());
        when(room.getCurrentPets()).thenReturn(new Int2ObjectOpenHashMap<>());
        for (int id : ids) {
            UserItem item = mock(UserItem.class);
            when(item.getId()).thenReturn(id);
            when(room.getUserItem(id)).thenReturn(item);
        }
        return room;
    }

    private static void load(WiredConditionNotFurniHaveUser box, Room room, String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        box.loadWiredData(set, room);
    }

    /** Saves the official param, then evaluates the stored box over picks 1 and 2. */
    private static boolean saveAndEvaluate(int official, Room room, Set<Integer> occupied) throws Exception {
        WiredConditionNotFurniHaveUser saved = box(occupied);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            assertTrue(saved.saveData(WiredConditionTestSupport.settings(new int[] {official})));
        }
        assertArrayEquals(new int[] {official}, WiredLegacyWireReader.read(saved, room).intParams);
        boolean all = saved.getWiredData().contains("\"all\":true");

        WiredConditionNotFurniHaveUser stored = box(occupied);
        load(stored, room, "{\"itemIds\":[1,2],\"furniSource\":100,\"all\":" + all + "}");
        WiredContext ctx = WiredConditionTestSupport.context(room, null);
        return stored.evaluate(ctx);
    }

    @Test
    void theFirstOptionPassesWhenOneFurniIsFree() throws Exception {
        Room room = roomWithFurni(1, 2);
        // Furni 1 has an avatar, furni 2 is free.
        assertTrue(saveAndEvaluate(1, room, Set.of(1)));
        assertFalse(saveAndEvaluate(1, room, Set.of(1, 2)));
    }

    @Test
    void theSecondOptionPassesOnlyWhenNoFurniHasAnAvatar() throws Exception {
        Room room = roomWithFurni(1, 2);
        assertFalse(saveAndEvaluate(0, room, Set.of(1)));
        assertTrue(saveAndEvaluate(0, room, Set.of()));
    }

    @Test
    void savedBoxesKeepTheirMeaningAndOpenOnTheMatchingOption() throws Exception {
        Room room = roomWithFurni(1, 2);
        // A box saved before the fix with "all": its label was .3, every furni free: official 0.
        WiredConditionNotFurniHaveUser every = box(Set.of(1));
        load(every, room, "{\"itemIds\":[1,2],\"furniSource\":100,\"all\":true}");
        assertArrayEquals(new int[] {0}, WiredLegacyWireReader.read(every, room).intParams);
        assertFalse(every.evaluate(WiredConditionTestSupport.context(room, null)));

        WiredConditionNotFurniHaveUser one = box(Set.of(1));
        load(one, room, "{\"itemIds\":[1,2],\"furniSource\":100,\"all\":false}");
        assertArrayEquals(new int[] {1}, WiredLegacyWireReader.read(one, room).intParams);
        assertTrue(one.evaluate(WiredConditionTestSupport.context(room, null)));

        // A fresh box opens on the first option.
        assertArrayEquals(new int[] {1}, WiredLegacyWireReader.read(box(Set.of()), room).intParams);
    }
}
