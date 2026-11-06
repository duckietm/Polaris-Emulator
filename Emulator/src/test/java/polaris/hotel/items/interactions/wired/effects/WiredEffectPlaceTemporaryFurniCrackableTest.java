package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionCrackable;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.wired.WiredSaveException;

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
    private static WiredSaveException saveWith(UserItem picked) {
        WiredEffectPlaceTemporaryFurni box = new WiredEffectPlaceTemporaryFurni(1, 1, floorBase(), "", 0, 0);
        box.setRoomId(7);

        Room room = mock(Room.class);
        when(room.getUserItem(FURNI_ID)).thenReturn(picked);
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
        UserItem lamp = mock(UserItem.class);
        when(lamp.getId()).thenReturn(FURNI_ID);
        when(lamp.getBaseItem()).thenReturn(base);

        assertEquals(null, saveWith(lamp), "an ordinary furni is a valid template");
    }
}
