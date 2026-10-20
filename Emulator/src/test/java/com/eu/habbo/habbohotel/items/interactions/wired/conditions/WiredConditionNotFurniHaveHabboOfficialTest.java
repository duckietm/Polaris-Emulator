package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyWireReader;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Not Furnis Have Avatars (14) as AIR FurnisHaveNoAvatars: the first option is id 1 ("one of the picked furni has no
 * avatar", {@code not_requireall.2}), the second id 0 ("none of them has one", {@code not_requireall.3}).
 */
class WiredConditionNotFurniHaveHabboOfficialTest {

    private static WiredConditionNotFurniHaveHabbo box(Set<Integer> occupied) {
        return new WiredConditionNotFurniHaveHabbo(7, 1, WiredConditionTestSupport.boxBase(), "", 0, 0) {
            @Override
            protected boolean hasAvatarOnItem(
                    HabboItem item, Room room, Collection<Habbo> habbos, Collection<Bot> bots, Collection<Pet> pets) {
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
            HabboItem item = mock(HabboItem.class);
            when(item.getId()).thenReturn(id);
            when(room.getHabboItem(id)).thenReturn(item);
        }
        return room;
    }

    private static void load(WiredConditionNotFurniHaveHabbo box, Room room, String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        box.loadWiredData(set, room);
    }

    /** Saves the official param, then evaluates the stored box over picks 1 and 2. */
    private static boolean saveAndEvaluate(int official, Room room, Set<Integer> occupied) throws Exception {
        WiredConditionNotFurniHaveHabbo saved = box(occupied);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            assertTrue(saved.saveData(WiredConditionTestSupport.settings(new int[] {official})));
        }
        assertArrayEquals(new int[] {official}, WiredLegacyWireReader.read(saved, room).intParams);
        boolean all = saved.getWiredData().contains("\"all\":true");

        WiredConditionNotFurniHaveHabbo stored = box(occupied);
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
        WiredConditionNotFurniHaveHabbo every = box(Set.of(1));
        load(every, room, "{\"itemIds\":[1,2],\"furniSource\":100,\"all\":true}");
        assertArrayEquals(new int[] {0}, WiredLegacyWireReader.read(every, room).intParams);
        assertFalse(every.evaluate(WiredConditionTestSupport.context(room, null)));

        WiredConditionNotFurniHaveHabbo one = box(Set.of(1));
        load(one, room, "{\"itemIds\":[1,2],\"furniSource\":100,\"all\":false}");
        assertArrayEquals(new int[] {1}, WiredLegacyWireReader.read(one, room).intParams);
        assertTrue(one.evaluate(WiredConditionTestSupport.context(room, null)));

        // A fresh box opens on the first option.
        assertArrayEquals(new int[] {1}, WiredLegacyWireReader.read(box(Set.of()), room).intParams);
    }
}
