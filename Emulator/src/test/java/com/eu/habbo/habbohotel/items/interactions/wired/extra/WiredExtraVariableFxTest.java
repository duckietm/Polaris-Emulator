package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxConfig;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxStatus;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxStyles;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * An fx box stores what the editor sent, gives it back the same way, and turns it into a config the
 * client can draw without checking anything itself.
 */
class WiredExtraVariableFxTest {

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4321);
        return base;
    }

    private static Room room() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(81);
        when(room.getRoomSpecialTypes()).thenReturn(mock(RoomSpecialTypes.class));
        return room;
    }

    private static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    /** The editor body: string slot, int params and, after the stuff-type slot, the layout code. */
    private record Body(int spriteId, int itemId, String text, int[] params, int code) {}

    private static Body body(WiredExtraVariableFx box, Room room) {
        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, room);
        ByteBuf buffer = message.get();
        try {
            buffer.readInt();
            buffer.readShort();
            buffer.readByte();
            buffer.readInt();
            int selected = buffer.readInt();
            for (int i = 0; i < selected; i++) buffer.readInt();
            int spriteId = buffer.readInt();
            int itemId = buffer.readInt();
            int length = buffer.readShort();
            String text =
                    buffer.readCharSequence(length, StandardCharsets.UTF_8).toString();
            int[] params = new int[buffer.readInt()];
            for (int i = 0; i < params.length; i++) params[i] = buffer.readInt();
            buffer.readInt();
            int code = buffer.readInt();
            return new Body(spriteId, itemId, text, params, code);
        } finally {
            buffer.release();
        }
    }

    private static final int[] PARAMS = {
        WiredExtraVariableFx.SOURCE_USER,
        WiredExtraVariableFx.VISIBILITY_GAME_TEAM,
        WiredExtraVariableFx.SHOW_WHEN_CHANGES,
        2500,
        1,
        WiredExtraVariableFx.COLOR_DYNAMIC_TEAM,
        3,
        5,
        10,
        250,
        1,
        0,
        WiredExtraVariableFx.OVERRIDE_TARGET_GLOBAL,
        WiredExtraVariableFx.OVERRIDE_TARGET_HOLDER,
        7,
        0
    };

    /** {@link #PARAMS} in the official 23-int layout the editor gets back. */
    private static final int[] OFFICIAL_PARAMS = {
        1, // user (official target)
        WiredExtraVariableFx.VISIBILITY_GAME_TEAM,
        WiredExtraVariableFx.SHOW_WHEN_CHANGES,
        0, // update mask
        0, // show on hover
        2500,
        1,
        WiredExtraVariableFx.COLOR_DYNAMIC_TEAM,
        3,
        0, // renderer
        0,
        10, // default min (long)
        0,
        250, // default max (long)
        1,
        0,
        -10, // override min: global
        1, // override max: the holder's (user)
        0,
        7, // audience value (long)
        5, // segments
        0, // category extra
        0 // stack order
    };

    @Test
    void whatTheEditorSavesComesBackToItUnchanged() throws Exception {
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        assertTrue(box.saveData(
                new WiredSettings(OFFICIAL_PARAMS, "custom:300\t\tcustom:400\tmisc_heart", new int[0], 0), null));

        Body body = body(box, room());

        assertEquals(4321, body.spriteId());
        assertEquals(7, body.itemId());
        assertArrayEquals(OFFICIAL_PARAMS, body.params());
        assertEquals("custom:300\t\tcustom:400\tmisc_heart", body.text());
        assertEquals(WiredExtraVariableFxProgressBar.CODE, body.code());
        assertEquals(131, body.code());
    }

    @Test
    void savedDataSurvivesTheDatabaseRoundTrip() throws Exception {
        WiredExtraVariableFxProgressBar saved = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        saved.saveData(new WiredSettings(PARAMS, "custom:300\t\tcustom:400\tmisc_heart", new int[0], 0), null);

        WiredExtraVariableFxProgressBar loaded = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        loaded.loadWiredData(row(saved.getWiredData()), room());

        // A save in the old 16-int layout still reads, and goes back out in the official one; it
        // shows on any real change, as "show when changes" did before the mask.
        Body body = body(loaded, room());
        int[] expected = OFFICIAL_PARAMS.clone();
        expected[3] = WiredExtraVariableFx.UPDATE_MASK_DEFAULT;
        assertArrayEquals(expected, body.params());
        assertEquals("custom:300\t\tcustom:400\tmisc_heart", body.text());
        assertEquals(400, loaded.getAudienceItemId());
        assertEquals("misc_heart", loaded.getIcon());
    }

    @Test
    void outOfRangeValuesAreClampedAndUnknownIconsDropped() throws Exception {
        int[] params = {9, 99, 99, 1, 99, 5000, 999, 999, 0, 100, 1, 1, 9, 9, 0, 0};
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        box.saveData(new WiredSettings(params, "custom:x\tcustom:-4\tnope\t<script>", new int[0], 0), null);

        Body body = body(box, room());
        int[] expected = {
            1,
            WiredExtraVariableFx.VISIBILITY_HAS_VARIABLE_WITH_VALUE,
            WiredExtraVariableFx.SHOW_NEVER,
            WiredExtraVariableFx.UPDATE_MASK_DEFAULT,
            0,
            WiredExtraVariableFx.SHOW_DURATION_MIN_MS,
            WiredVariableFxStyles.styleCount(WiredVariableFxStyles.CATEGORY_PROGRESS_BAR) - 1,
            1000,
            100,
            0,
            0,
            0,
            0,
            100,
            1,
            1,
            1,
            1,
            0,
            0,
            WiredExtraVariableFx.SEGMENTS_MAX,
            0,
            0
        };
        assertArrayEquals(expected, body.params());
        assertEquals("\t\t\t", body.text());
        assertEquals("", box.getIcon());
    }

    @Test
    void aRangeWithoutRoomInItIsRefused() {
        int[] params = {0, 2, 0, 3000, 0, -1, 2, 0, 50, 50, 0, 0, 0, 0, 0, 0};
        WiredExtraVariableFxHealthPoints box = new WiredExtraVariableFxHealthPoints(7, 1, base(), "", 0, 0);

        WiredSaveException error = assertThrows(
                WiredSaveException.class, () -> box.saveData(new WiredSettings(params, "", new int[0], 0), null));
        assertEquals("wiredfurni.params.variablefx.validation.range", error.getMessage());
    }

    @Test
    void theNumberDisplayHasNoRangeSoEqualEndsAreFine() throws Exception {
        int[] params = {0, 2, 0, 3000, 0, -1, 2, 0, 50, 50, 0, 0, 0, 0, 0, 0};
        WiredExtraVariableFxNumberDisplay box = new WiredExtraVariableFxNumberDisplay(7, 1, base(), "", 0, 0);

        assertTrue(box.saveData(new WiredSettings(params, "", new int[0], 0), null));
        assertEquals(135, body(box, room()).code());
    }

    @Test
    void theConfigCarriesTheStyleRendererAndOnlyApplicableSegments() throws Exception {
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        // Style 1 is the block bar, which draws segments; style 0 is plain and does not.
        box.saveData(
                new WiredSettings(new int[] {0, 2, 0, 3000, 1, 4, 2, 6, 0, 200, 0, 0, 0, 0, 0, 0}, "", new int[0], 0),
                null);

        WiredVariableFxConfig blocks = box.buildConfig();
        assertEquals(7, blocks.configId());
        assertTrue(blocks.userFx());
        assertEquals(WiredVariableFxStyles.CATEGORY_PROGRESS_BAR, blocks.category());
        assertEquals(1, blocks.styleId());
        assertEquals(WiredVariableFxStyles.RENDERER_BLOCK, blocks.rendererId());
        assertEquals(0L, blocks.defaultMinValue());
        assertEquals(200L, blocks.defaultMaxValue());
        assertEquals("6", blocks.extra().get(WiredVariableFxStyles.EXTRA_SEGMENTS));

        box.saveData(
                new WiredSettings(new int[] {0, 2, 0, 3000, 0, 4, 2, 6, 0, 200, 0, 0, 0, 0, 0, 0}, "", new int[0], 0),
                null);
        assertNull(box.buildConfig().extra().get(WiredVariableFxStyles.EXTRA_SEGMENTS));
    }

    @Test
    void theConfigCarriesTheOfficialVisibilityRendererAndStackOrder() throws Exception {
        WiredExtraVariableFxStatusBar box = new WiredExtraVariableFxStatusBar(7, 1, base(), "", 0, 0);
        int[] params = OFFICIAL_PARAMS.clone();
        params[3] = WiredExtraVariableFx.UPDATE_INCREASED | WiredExtraVariableFx.UPDATE_UNCHANGED;
        params[4] = 1;
        params[6] = 0;
        params[9] = WiredVariableFxStyles.RENDERER_ARROW;
        params[20] = 0;
        params[22] = -3;
        box.saveData(new WiredSettings(params, "", new int[0], 0), null);

        WiredVariableFxConfig config = box.buildConfig();
        assertEquals(
                WiredExtraVariableFx.UPDATE_INCREASED | WiredExtraVariableFx.UPDATE_UNCHANGED, config.updateMask());
        assertTrue(config.showOnMouseHover());
        assertEquals(WiredVariableFxStyles.RENDERER_ARROW, config.rendererId());
        assertEquals("-3", config.extra().get(WiredExtraVariableFx.EXTRA_STACK_ORDER));

        // A renderer the style does not offer falls back to its default; stack order 0 is not sent.
        params[9] = WiredVariableFxStyles.RENDERER_BOSS;
        params[22] = 0;
        box.saveData(new WiredSettings(params, "", new int[0], 0), null);
        assertEquals(WiredVariableFxStyles.RENDERER_BLOCK, box.buildConfig().rendererId());
        assertNull(box.buildConfig().extra().get(WiredExtraVariableFx.EXTRA_STACK_ORDER));
    }

    @Test
    void neverIsAirsVisibleOnHover() throws Exception {
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        int[] params = OFFICIAL_PARAMS.clone();
        params[2] = WiredExtraVariableFx.SHOW_NEVER;
        params[4] = 0;
        box.saveData(new WiredSettings(params, "", new int[0], 0), null);

        assertTrue(box.buildConfig().showOnMouseHover());
    }

    @Test
    void aRowFromBeforeTheMaskShowsOnAnyRealChange() throws Exception {
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"showMode\":1,\"showDurationMs\":3000,\"defaultMax\":100}"), room());

        assertEquals(WiredExtraVariableFx.UPDATE_MASK_DEFAULT, box.buildConfig().updateMask());
    }

    @Test
    void aStatusOnlyCarriesARangeWhenAVariableOverridesOneEnd() throws Exception {
        Room room = room();
        RoomUserVariableManager variables = mock(RoomUserVariableManager.class);
        when(room.getUserVariableManager()).thenReturn(variables);
        when(variables.hasVariable(42, 300)).thenReturn(true);
        when(variables.getCurrentValue(42, 300)).thenReturn(150);

        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        WiredVariableFxStatus.Key key = new WiredVariableFxStatus.Key(7, "user:9", true, 3);

        box.saveData(
                new WiredSettings(new int[] {0, 2, 0, 3000, 0, -1, 2, 0, 0, 100, 0, 0, 0, 0, 0, 0}, "", new int[0], 0),
                null);
        WiredVariableFxStatus plain = box.resolveStatus(room, null, key, 42, 60, null);
        assertEquals(60, plain.value());
        assertFalse(plain.hasOverrides());
        assertTrue(plain.extra().isEmpty());

        // Max from the holder's own variable 300; min stays the config's.
        box.saveData(
                new WiredSettings(
                        new int[] {0, 2, 0, 3000, 0, -1, 2, 0, 0, 100, 0, 1, 0, 0, 0, 0},
                        "\tcustom:300\t\t",
                        new int[0],
                        0),
                null);
        WiredVariableFxStatus overridden = box.resolveStatus(room, null, key, 42, 60, null);
        assertTrue(overridden.hasOverrides());
        assertEquals(0L, overridden.overrideMinValue());
        assertEquals(150L, overridden.overrideMaxValue());

        // A holder without the variable gets no override at all.
        assertFalse(box.resolveStatus(room, null, key, 43, 60, null).hasOverrides());
    }

    @Test
    void anImportedFxReadsItsOverridesThroughTheImportingRoomsIds() throws Exception {
        Room room = room();
        RoomUserVariableManager variables = mock(RoomUserVariableManager.class);
        when(room.getUserVariableManager()).thenReturn(variables);
        // The source room's variable 300 is reference 950 here.
        when(variables.hasVariable(42, 950)).thenReturn(true);
        when(variables.getCurrentValue(42, 950)).thenReturn(150);

        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        WiredVariableFxStatus.Key key = new WiredVariableFxStatus.Key(7, "user:9", true, 3);
        box.saveData(
                new WiredSettings(
                        new int[] {0, 2, 0, 3000, 0, -1, 2, 0, 0, 100, 0, 1, 0, 0, 0, 0},
                        "\tcustom:300\t\t",
                        new int[0],
                        0),
                null);

        assertFalse(box.resolveStatus(room, null, key, 42, 60, null).hasOverrides(), "300 does not exist here");
        WiredVariableFxStatus mapped = box.resolveStatus(room, null, key, 42, 60, null, id -> id == 300 ? 950 : id);
        assertTrue(mapped.hasOverrides());
        assertEquals(150L, mapped.overrideMaxValue());
    }

    @Test
    void theTeamColourOnlyTravelsWhenTheColourOptionAsksForIt() throws Exception {
        Room room = room();
        WiredExtraVariableFxHealthPoints box = new WiredExtraVariableFxHealthPoints(7, 1, base(), "", 0, 0);
        WiredVariableFxStatus.Key key = new WiredVariableFxStatus.Key(7, "user:9", true, 3);

        box.saveData(
                new WiredSettings(
                        new int[] {
                            0, 2, 0, 3000, 0, WiredExtraVariableFx.COLOR_DYNAMIC_TEAM, 2, 0, 0, 100, 0, 0, 0, 0, 0, 0
                        },
                        "",
                        new int[0],
                        0),
                null);
        assertEquals(
                "#df291e",
                box.resolveStatus(room, null, key, 42, 60, "#df291e").extra().get("delegated_color"));

        box.saveData(
                new WiredSettings(new int[] {0, 2, 0, 3000, 0, 3, 2, 0, 0, 100, 0, 0, 0, 0, 0, 0}, "", new int[0], 0),
                null);
        assertNull(box.resolveStatus(room, null, key, 42, 60, "#df291e").extra().get("delegated_color"));
    }

    @Test
    void theVariableBoxShownIsTheOneOnTheSameTileMatchingTheSource() throws Exception {
        Room room = room();
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        box.setX((short) 4);
        box.setY((short) 5);
        WiredExtraUserVariable userVariable = new WiredExtraUserVariable(20, 1, base(), "", 0, 0);
        WiredExtraFurniVariable furniVariable = new WiredExtraFurniVariable(21, 1, base(), "", 0, 0);
        when(room.getRoomSpecialTypes().getExtras(4, 5)).thenReturn(Set.of(box, userVariable, furniVariable));

        box.saveData(
                new WiredSettings(
                        new int[] {WiredExtraVariableFx.SOURCE_USER, 2, 0, 3000, 0, -1, 2, 0, 0, 100, 0, 0, 0, 0, 0, 0},
                        "",
                        new int[0],
                        0),
                null);
        assertEquals(userVariable, box.getShownVariableBox(room));

        box.saveData(
                new WiredSettings(
                        new int[] {WiredExtraVariableFx.SOURCE_FURNI, 2, 0, 3000, 0, -1, 2, 0, 0, 100, 0, 0, 0, 0, 0, 0
                        },
                        "",
                        new int[0],
                        0),
                null);
        assertEquals(furniVariable, box.getShownVariableBox(room));

        when(room.getRoomSpecialTypes().getExtras(4, 5)).thenReturn(Set.of(box));
        assertNull(box.getShownVariableBox(room));
    }

    @Test
    void anOfficialSaveReadsTheLongsAndTheVariableIdsApart() throws Exception {
        int[] params = OFFICIAL_PARAMS.clone();
        params[10] = -1; // default min -5, as hi/lo words
        params[11] = -5;
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        WiredSettings settings = new WiredSettings(params, "", new int[0], 0);
        settings.setVariableIds(new String[] {"300", "0", "400"});
        box.saveData(settings, null);

        Body body = body(box, room());
        assertEquals(-1, body.params()[10]);
        assertEquals(-5, body.params()[11]);
        assertEquals("custom:300\t\tcustom:400\t", body.text());
        // Sent back as the variable list's ids; a save in that form reads the same boxes.
        assertArrayEquals(new String[] {"room:300", "n", "user:400"}, box.officialVariableIds());
        settings.setVariableIds(box.officialVariableIds());
        box.saveData(settings, null);
        assertEquals("custom:300\t\tcustom:400\t", body(box, room()).text());
    }

    @Test
    void pickingUpResetsEverything() throws Exception {
        WiredExtraVariableFxProgressBar box = new WiredExtraVariableFxProgressBar(7, 1, base(), "", 0, 0);
        box.saveData(new WiredSettings(PARAMS, "custom:300\t\tcustom:400\tmisc_heart", new int[0], 0), null);

        box.onPickUp();

        Body body = body(box, room());
        assertEquals(WiredExtraVariableFx.VISIBILITY_EVERYONE, body.params()[1]);
        assertEquals(WiredExtraVariableFx.DEFAULT_MAX_VALUE, body.params()[13]);
        assertEquals("\t\t\t", body.text());
    }
}
