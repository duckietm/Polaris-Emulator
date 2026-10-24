package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionCrackable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * "Place furni" puts down temporary copies: no database row, no owner. Breaking one
 * still ran the crackable reward path, so a repeater over a cheap egg minted real furni
 * - and the HC or Builders Club a crackable can carry - out of nothing. A crackable
 * cannot be a template, the same way a wired box cannot.
 */
class WiredEffectPlaceTemporaryFurniCrackableTest {

    private static final int FURNI_ID = 4242;

    private static Item floorBase() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getId()).thenReturn(1389);
        return base;
    }

    private static ConfigurationManager config() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(50);
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        return config;
    }

    /** Saves the box with {@code picked} as its single template and reports what happened. */
    private static WiredSaveException saveWith(HabboItem picked) {
        WiredEffectPlaceTemporaryFurni box = new WiredEffectPlaceTemporaryFurni(1, 1, floorBase(), "", 0, 0);
        box.setRoomId(7);

        Room room = mock(Room.class);
        when(room.getHabboItem(FURNI_ID)).thenReturn(picked);
        ConfigurationManager config = config();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            platform.when(WiredPlatform::configuration).thenReturn(config);
            WiredEffectTestFixtures.installPlatform(platform, room);

            WiredSettings settings = new WiredSettings(new int[] {0, 0, 0}, "", new int[] {FURNI_ID}, 0);
            settings.setRoom(room);

            try {
                assertTrue(box.saveData(settings, null));
                return null;
            } catch (WiredSaveException refused) {
                return refused;
            }
        }
    }

    @Test
    void aCrackableCannotBeATemplate() {
        // Built before the stubbing: Mockito reads a mock made inside a thenReturn(...)
        // argument as an unfinished stubbing.
        Item base = floorBase();
        InteractionCrackable egg = mock(InteractionCrackable.class);
        when(egg.getId()).thenReturn(FURNI_ID);
        when(egg.getBaseItem()).thenReturn(base);

        WiredSaveException refused = saveWith(egg);

        assertEquals(
                WiredEffectPlaceTemporaryFurni.FURNI_CAN_NOT_BE_CLONED, refused == null ? null : refused.getMessage());
    }

    @Test
    void anOrdinaryFurniStillIs() {
        Item base = floorBase();
        HabboItem lamp = mock(HabboItem.class);
        when(lamp.getId()).thenReturn(FURNI_ID);
        when(lamp.getBaseItem()).thenReturn(base);

        assertEquals(null, saveWith(lamp), "an ordinary furni is a valid template");
    }
}
