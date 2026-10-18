package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.installHotel;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredHighlight;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.protocol.WiredActionCodes;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WiredEffectSetHighlightTest {

    @Test
    void savingClampsTheParamsToTheEditorRanges() throws Exception {
        Room room = room(81, 8101);
        WiredEffectSetHighlight box = saved(room, new int[] {7, 9, 250, 0x7FFFFFFF, 3, 8, 1}, 8101);

        assertEquals(new WiredHighlight(5, 100, 0xFFFFFF, 1, 4), box.highlight());
        assertEquals(0, json(box).get("audience").getAsInt());
        assertTrue(json(box).get("targetUsers").getAsBoolean());

        box = saved(room, new int[] {1, -2, -5, -1, -1, -1, 0}, 8101);
        assertEquals(new WiredHighlight(0, 0, 0, 0, 0), box.highlight());
        assertEquals(1, json(box).get("audience").getAsInt());
    }

    @Test
    void darkenDropsTheColourAndBrightenIsWhiteAndPulsing() throws Exception {
        Room room = room(82, 8201);

        WiredEffectSetHighlight darken = saved(room, new int[] {1, 2, 60, 0xFF0000, 0, 2, 0}, 8201);
        assertEquals(new WiredHighlight(2, 60, 0, 0, 2), darken.highlight());

        WiredEffectSetHighlight brighten = saved(room, new int[] {1, 4, 38, 0x00FF00, 0, 0, 0}, 8201);
        assertEquals(0xFFFFFF, brighten.highlight().color());
        assertEquals(WiredHighlight.ANIMATION_PULSE, brighten.highlight().animation());
        // stored as the editor saved it, sent whole
        assertEquals(38, brighten.highlight().strength());
        assertEquals(76, brighten.highlight().wireStrength());
    }

    @Test
    void theDialogGetsTheSevenOfficialParamsTheSlotsAndActionCode62() throws Exception {
        Room room = room(83, 8301);
        WiredSettings settings = new WiredSettings(new int[] {1, 4, 38, 0, 0, 0, 1}, "", new int[] {8301}, 0);
        settings.setFurniSourceTypes(new int[] {100});
        settings.setUserSourceTypes(new int[] {0, 201});
        WiredEffectSetHighlight box = new WiredEffectSetHighlight(8399, 1, base(), "", 0, 0);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);
            box.saveData(settings, null);
        }

        var body = WiredEffectProgressAchievementTest.body(box);

        assertArrayEquals(new int[] {1, 4, 38, 0xFFFFFF, 1, 0, 1}, body.params());
        assertEquals(62, body.code());
        assertEquals(62, WiredActionCodes.official(WiredEffectType.SET_HIGHLIGHT.code));
        assertArrayEquals(new int[] {100}, box.officialFurniSources());
        assertArrayEquals(new int[] {0, 201}, box.officialUserSources());
    }

    @Test
    void whatIsSavedLoadsBackAndBrokenDataFailsClosed() throws Exception {
        Room room = room(84, 8401);
        WiredEffectSetHighlight saved = saved(room, new int[] {1, 3, 45, 0x3366CC, 1, 0, 1}, 8401);

        WiredEffectSetHighlight loaded = new WiredEffectSetHighlight(8499, 1, base(), "", 0, 0);
        loaded.loadWiredData(row(saved.getWiredData()), room);
        assertEquals(saved.highlight(), loaded.highlight());
        assertEquals(saved.getWiredData(), loaded.getWiredData());

        assertDoesNotThrow(() -> loaded.loadWiredData(row("{broken"), room));
        assertTrue(loaded.requiresTriggeringUser());
    }

    @Test
    void tooFewParamsAreRefused() {
        Room room = room(85, 8501);
        WiredEffectSetHighlight box = new WiredEffectSetHighlight(8599, 1, base(), "", 0, 0);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);
            assertThrows(
                    WiredSaveException.class,
                    () -> box.saveData(new WiredSettings(new int[] {1, 0}, "", new int[0], 0), null));
        }
    }

    private static WiredEffectSetHighlight saved(Room room, int[] params, int itemId) throws Exception {
        WiredEffectSetHighlight box = new WiredEffectSetHighlight(8999, 1, base(), "", 0, 0);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);
            box.saveData(new WiredSettings(params, "", new int[] {itemId}, 0), null);
        }
        return box;
    }

    private static Room room(int roomId, int itemId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getRoomId()).thenReturn(roomId);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(room.getHabboItem(itemId)).thenReturn(item);
        return room;
    }
}
