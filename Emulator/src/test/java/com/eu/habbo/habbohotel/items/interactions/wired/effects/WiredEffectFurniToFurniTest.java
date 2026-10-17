package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.config;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.installPlatform;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Picking furni to move with the dropdown left on "triggering furni" means the picked furni, as it
 * does for the sibling boxes and as this box's own loadWiredData already assumed.
 */
class WiredEffectFurniToFurniTest {

    @Test
    void pickedFurniPromoteTheTriggerSourceToSelected() throws Exception {
        Room room = mock(Room.class);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(301);
        when(room.getHabboItem(301)).thenReturn(item);
        WiredEffectFurniToFurni box = new WiredEffectFurniToFurni(1, 1, base(), "", 0, 0);
        ConfigurationManager config = config();

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class);
                MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installPlatform(platform, room);
            emulator.when(Emulator::getConfig).thenReturn(config);

            box.saveData(new WiredSettings(new int[0], "", new int[] {301}, 0), null);
        }

        assertEquals(
                WiredSourceUtil.SOURCE_SELECTED, json(box).get("moveSource").getAsInt());
        assertEquals(301, json(box).getAsJsonArray("itemIds").get(0).getAsInt());
    }

    @Test
    void theTargetPicksAreTheOfficialSecondPickList() throws Exception {
        Room room = mock(Room.class);
        HabboItem moved = mock(HabboItem.class);
        when(moved.getId()).thenReturn(301);
        when(room.getHabboItem(301)).thenReturn(moved);
        HabboItem target = mock(HabboItem.class);
        when(target.getId()).thenReturn(302);
        when(room.getHabboItem(302)).thenReturn(target);
        WiredEffectFurniToFurni box = new WiredEffectFurniToFurni(1, 1, base(), "", 0, 0);
        ConfigurationManager config = config();

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class);
                MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installPlatform(platform, room);
            emulator.when(Emulator::getConfig).thenReturn(config);

            WiredSettings settings = new WiredSettings(new int[0], "", new int[] {301}, 0);
            settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTED});
            settings.setFurniIds2(new int[] {302});
            box.saveData(settings, null);
        }

        assertEquals(302, json(box).getAsJsonArray("targetItemIds").get(0).getAsInt());
        assertArrayEquals(
                new int[] {WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTED},
                box.officialFurniSources());
        assertArrayEquals(new int[] {302}, box.officialFurniIds2());
    }

    @Test
    void aWallTargetSavesButMovesNothing() throws Exception {
        Room room = mock(Room.class);
        HabboItem moved = mock(HabboItem.class);
        when(moved.getId()).thenReturn(301);
        when(room.getHabboItem(301)).thenReturn(moved);
        HabboItem torch = mock(HabboItem.class);
        Item wallBase = mock(Item.class);
        when(wallBase.getType()).thenReturn(FurnitureType.WALL);
        when(torch.getBaseItem()).thenReturn(wallBase);
        when(torch.getId()).thenReturn(302);
        when(room.getHabboItem(302)).thenReturn(torch);
        WiredEffectFurniToFurni box = new WiredEffectFurniToFurni(1, 1, base(), "", 0, 0);
        ConfigurationManager config = config();

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class);
                MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installPlatform(platform, room);
            emulator.when(Emulator::getConfig).thenReturn(config);

            WiredSettings settings = new WiredSettings(new int[0], "", new int[] {301}, 0);
            settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTED});
            settings.setFurniIds2(new int[] {302});
            box.saveData(settings, null);
            box.execute(context(room));
        }

        assertEquals(302, json(box).getAsJsonArray("targetItemIds").get(0).getAsInt());
        // The wall target is dropped, so nothing reads where it stands.
        verify(torch, never()).getX();
        verify(torch, never()).getY();
    }
}
