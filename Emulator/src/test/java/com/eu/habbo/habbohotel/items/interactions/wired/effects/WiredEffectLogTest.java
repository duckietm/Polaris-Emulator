package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredActionCodes;
import com.eu.habbo.messages.outgoing.wired.WiredEffectDataComposer;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Habbo's "write to logs": a message at a log level, saved, loaded and sent back as it was set. */
class WiredEffectLogTest {

    private static WiredEffectLog box() {
        return new WiredEffectLog(4, 1, base(), "", 0, 0);
    }

    private static WiredSettings settings(String message, int... params) {
        return new WiredSettings(params, message, new int[0], 0);
    }

    /** The int params and the type code the dialog receives. */
    private record Body(String message, int[] params, int code) {}

    private static Body body(WiredEffectLog box) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.getTriggers(anyInt(), anyInt())).thenReturn(Set.of());
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);

        ByteBuf packet = new WiredEffectDataComposer(box, room).compose().get();
        try {
            packet.skipBytes(6);
            // official layout: furniLimit, stuffIds, stuffIds2, spriteId, id
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.readInt();
            packet.readInt();
            byte[] text = new byte[packet.readShort()];
            packet.readBytes(text);
            int[] params = new int[packet.readInt()];
            for (int i = 0; i < params.length; i++) params[i] = packet.readInt();
            packet.readInt(); // variableIds
            packet.skipBytes(packet.readInt() * 4); // furni sources
            packet.skipBytes(packet.readInt() * 4); // user sources
            return new Body(new String(text, java.nio.charset.StandardCharsets.UTF_8), params, packet.readInt());
        } finally {
            packet.release();
        }
    }

    @Test
    void theDialogGetsTheLevelAndTheUserSourceUnderItsOwnCode() {
        WiredEffectLog box = box();

        WiredSettings settings = settings("  boss down  ", WiredEffectLog.LEVEL_DEBUG);
        settings.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_TRIGGER});
        assertTrue(box.saveData(settings, null));

        Body body = body(box);
        assertEquals("boss down", body.message());
        assertArrayEquals(new int[] {3}, body.params()); // official [level]; the source is the slot
        assertEquals(WiredActionCodes.official(WiredEffectType.WRITE_TO_LOGS.code), body.code());
    }

    @Test
    void anOutOfRangeLevelReadsAsInfoAndALongMessageIsCut() {
        WiredEffectLog box = box();

        box.saveData(settings("x".repeat(500), 9), null);

        assertEquals(WiredEffectLog.LEVEL_INFO, box.getLogLevel());
        assertEquals(WiredEffectLog.MAX_MESSAGE_LENGTH, body(box).message().length());
    }

    @Test
    void anEmptyMessageIsRefused() {
        assertFalse(box().saveData(settings("   ", 1), null));
    }

    @Test
    void theLevelSurvivesTheDatabaseAndOldBoxesReadAsInfo() throws Exception {
        WiredEffectLog saved = box();
        saved.saveData(settings("door opened", WiredEffectLog.LEVEL_DEBUG), null);

        WiredEffectLog loaded = box();
        loaded.loadWiredData(row(saved.getWiredData()), null);
        assertEquals(WiredEffectLog.LEVEL_DEBUG, loaded.getLogLevel());

        WiredEffectLog legacy = box();
        legacy.loadWiredData(row("{\"message\":\"hi\",\"delay\":0,\"userSource\":0}"), null);
        assertEquals(WiredEffectLog.LEVEL_INFO, legacy.getLogLevel());
    }

    @Test
    void theLevelsAreAirsAndTheOldNumbersMapOntoThem() {
        // AIR's dropdown and room log: 0 INFO, 1 WARN, 2 ERROR, 3 DEBUG.
        assertEquals(0, WiredEffectLog.LEVEL_INFO);
        assertEquals(1, WiredEffectLog.LEVEL_WARN);
        assertEquals(2, WiredEffectLog.LEVEL_ERROR);
        assertEquals(3, WiredEffectLog.LEVEL_DEBUG);

        // Before: 0 debug, 1 info, 2 warning, 3 error.
        assertEquals(WiredEffectLog.LEVEL_DEBUG, WiredEffectLog.legacyLevelToAir(0));
        assertEquals(WiredEffectLog.LEVEL_INFO, WiredEffectLog.legacyLevelToAir(1));
        assertEquals(WiredEffectLog.LEVEL_WARN, WiredEffectLog.legacyLevelToAir(2));
        assertEquals(WiredEffectLog.LEVEL_ERROR, WiredEffectLog.legacyLevelToAir(3));
        assertEquals(WiredEffectLog.LEVEL_INFO, WiredEffectLog.legacyLevelToAir(7));
    }

    @Test
    void theMigrationRewritesOnlyTheStoredLevel() throws Exception {
        String stored = "{\"message\":\"say ,\\\"logLevel\\\":3 here\",\"delay\":4,\"userSource\":1,\"logLevel\":0}";
        String migrated = WiredEffectLog.migrateLegacyLevel(stored);
        assertEquals(
                "{\"message\":\"say ,\\\"logLevel\\\":3 here\",\"delay\":4,\"userSource\":1,\"logLevel\":3}", migrated);

        WiredEffectLog loaded = box();
        loaded.loadWiredData(row(migrated), null);
        assertEquals(WiredEffectLog.LEVEL_DEBUG, loaded.getLogLevel());
        assertEquals(4, loaded.getDelay());

        assertEquals(null, WiredEffectLog.migrateLegacyLevel("{\"message\":\"x\",\"delay\":0,\"userSource\":0}"));
        assertEquals(null, WiredEffectLog.migrateLegacyLevel("0\tlegacy"));
        assertEquals(null, WiredEffectLog.migrateLegacyLevel("{broken"));
        assertEquals(null, WiredEffectLog.migrateLegacyLevel(null));
        assertEquals("{\"logLevel\":2}", WiredEffectLog.migrateLegacyLevel("{\"logLevel\":3}"));
    }

    @Test
    void aFreshBoxStartsAtInfoLikeTheOfficialDropdown() {
        assertEquals(WiredEffectLog.LEVEL_INFO, box().getLogLevel());
    }
}
