package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureDirectionCatalog;
import com.eu.habbo.habbohotel.items.FurnitureDirectionsRuntime;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredProjectileDirections;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class WiredExtraProjectileTest {

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(5150);
        return base;
    }

    private static HabboItem furni(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static Room room(HabboItem... items) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(81);
        for (HabboItem item : items) {
            when(room.getHabboItem(item.getId())).thenReturn(item);
        }
        return room;
    }

    private static WiredSettings settings(int[] params, int[] furniIds, Room room) {
        WiredSettings settings = new WiredSettings(params, "", furniIds, 0);
        settings.setRoom(room);
        return settings;
    }

    private static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private static WiredExtraProjectile box() {
        return new WiredExtraProjectile(12, 1, base(), "", 0, 0);
    }

    private static final int[] PARAMS = {1, 3, 1, 1, 250, 2, 1, 1, 1, 75, 6, 5, 1, 1, 2, 1, -12, 3, 400};

    private static final int[] SAVED = {1, 3, 1, 1, 250, 2, 1, 1, 1, 75, 6, 5, 1, 1, 2, 1, -12, 3, 400, 0, 0, 0, 0, 0};

    private record Body(int[] params, int code, int[] selected, String string) {}

    private static Body body(WiredExtraProjectile box, Room room) {
        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, room);
        ByteBuf buffer = message.get();
        try {
            buffer.readInt();
            buffer.readShort();
            buffer.readByte();
            buffer.readInt();
            int[] selected = new int[buffer.readInt()];
            for (int i = 0; i < selected.length; i++) selected[i] = buffer.readInt();
            buffer.readInt();
            buffer.readInt();
            byte[] text = new byte[buffer.readShort()];
            buffer.readBytes(text);
            int[] params = new int[buffer.readInt()];
            for (int i = 0; i < params.length; i++) params[i] = buffer.readInt();
            buffer.readInt();
            return new Body(params, buffer.readInt(), selected, new String(text, StandardCharsets.UTF_8));
        } finally {
            buffer.release();
        }
    }

    @Test
    void whatTheEditorSavesComesBackToItUnchanged() {
        HabboItem arrow = furni(300);
        Room room = room(arrow);
        WiredExtraProjectile box = box();

        assertTrue(box.saveData(settings(PARAMS, new int[] {300}, room), null));

        Body body = body(box, room);
        assertArrayEquals(SAVED, body.params());
        assertArrayEquals(new int[] {300}, body.selected());
        assertEquals(136, body.code());
    }

    @Test
    void savedDataSurvivesTheDatabaseRoundTrip() throws Exception {
        HabboItem arrow = furni(300);
        Room room = room(arrow);
        WiredExtraProjectile saved = box();
        saved.saveData(settings(PARAMS, new int[] {300}, room), null);

        WiredExtraProjectile loaded = box();
        loaded.loadWiredData(row(saved.getWiredData()), room);

        assertArrayEquals(SAVED, body(loaded, room).params());
        assertTrue(loaded.appliesTo(arrow));
    }

    @Test
    void outOfRangeParamsAreClampedAndMissingOnesDefault() {
        int[] clamped = WiredExtraProjectile.normalizeParams(new int[] {7, 9, 0, 0, 0, 0, 0, 0, 0, 0, 30, 999});

        assertEquals(1, clamped[WiredExtraProjectile.PARAM_ROTATE]);
        assertEquals(3, clamped[WiredExtraProjectile.PARAM_DIRECTIONAL_SYSTEM]);
        assertEquals(7, clamped[WiredExtraProjectile.PARAM_ROTATION_OFFSET]);
        assertEquals(127, clamped[11]);
        assertEquals(WiredExtraProjectile.TOTAL_PARAM_COUNT, clamped.length);
        assertEquals(1, WiredExtraProjectile.normalizeParams(null)[WiredExtraProjectile.PARAM_ROTATE]);
    }

    @Test
    void aFreshBoxTurnsWhateverItsStackMoves() {
        WiredExtraProjectile box = box();

        assertTrue(box.appliesTo(furni(41)));
        assertEquals(WiredProjectileDirections.EAST, box.resolveRotation(2, 2, 6, 2, 4));
    }

    @Test
    void pickedFurniAreTheOnlyProjectiles() {
        HabboItem arrow = furni(300);
        Room room = room(arrow);
        WiredExtraProjectile box = box();
        box.saveData(settings(PARAMS, new int[] {300}, room), null);

        assertTrue(box.appliesTo(arrow));
        assertFalse(box.appliesTo(furni(301)));
        assertFalse(box.appliesTo(null));
    }

    @Test
    void theRotationFollowsTheSystemAndTheOffset() {
        Room room = room();
        WiredExtraProjectile box = box();
        int[] params = new int[19];
        params[WiredExtraProjectile.PARAM_ROTATE] = 1;
        params[WiredExtraProjectile.PARAM_DIRECTIONAL_SYSTEM] = WiredProjectileDirections.FOUR_PREFER_HORIZONTAL;
        params[WiredExtraProjectile.PARAM_ROTATION_OFFSET] = 4;
        box.saveData(settings(params, new int[0], room), null);

        assertEquals(WiredProjectileDirections.WEST, box.resolveRotation(0, 0, 3, 3, 2));
    }

    @Test
    void aDiagonalHeadingKeepsTheFurnisRotation() {
        // The fresh box turns with the move; a diagonal move cannot be a floor furni's heading.
        assertEquals(2, box().resolveRotation(0, 0, 3, 3, 2));
        assertEquals(0, box().resolveRotation(4, 4, 3, 3, 0));
    }

    @Test
    void aProjectileTurnsOnlyToADirectionItsBundleLists() {
        java.nio.file.Path furniture = java.nio.file.Path.of("../../Furniture");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.isDirectory(furniture), "no Furniture folder in this checkout");
        FurnitureDirectionsRuntime.install(
                new FurnitureDirectionCatalog(
                        new FurnitureDirectionCatalog.Settings(true, furniture, 16, 5000, 64L * 1024 * 1024)),
                name -> name.equals("bling_bed") ? 4 : -1);
        try {
            // bling_bed faces 2 or 4 (logic directions 90, 180); its furnidata defaultdir is 4 here.
            HabboItem bed = named("bling_bed");
            assertEquals(WiredProjectileDirections.EAST, box().resolveRotation(bed, 2, 2, 6, 2, 4));
            assertEquals(4, box().resolveRotation(bed, 2, 2, 2, 0, 2));
            assertEquals(4, box().resolveRotation(bed, 0, 0, 3, 3, 2));
            // Without a defaultdir it can face, the furni keeps its rotation.
            HabboItem polyfon = named("bed_polyfon");
            assertEquals(2, box().resolveRotation(polyfon, 2, 2, 2, 6, 2));
            assertEquals(0, box().resolveRotation(polyfon, 2, 2, 2, 0, 2));
            // A furni without bundle data keeps the old rule: even headings, diagonals keep the rotation.
            HabboItem unknown = named("no_such_bundle");
            assertEquals(WiredProjectileDirections.WEST, box().resolveRotation(unknown, 6, 2, 2, 2, 4));
            assertEquals(4, box().resolveRotation(unknown, 0, 0, 3, 3, 4));
        } finally {
            FurnitureDirectionsRuntime.uninstall();
        }
    }

    private static HabboItem named(String name) {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getName()).thenReturn(name);
        HabboItem item = mock(HabboItem.class);
        when(item.getBaseItem()).thenReturn(base);
        return item;
    }

    @Test
    void itLeavesTheRotationAloneWhenAskedToOrWhenNothingMoves() {
        Room room = room();
        WiredExtraProjectile off = box();
        int[] params = new int[19];
        off.saveData(settings(params, new int[0], room), null);

        assertEquals(4, off.resolveRotation(0, 0, 5, 0, 4));
        assertEquals(6, box().resolveRotation(3, 3, 3, 3, 6));
    }

    @Test
    void aSaveOutsideTheRoomIsRefused() {
        WiredExtraProjectile box = box();

        assertFalse(box.saveData(settings(PARAMS, new int[0], null), null));
    }

    private static WiredExtraProjectile configured(Room room, String tokens, int... pairs) {
        int[] params = new int[WiredExtraProjectile.TOTAL_PARAM_COUNT];
        params[WiredExtraProjectile.PARAM_TIME_PER_TILE] = WiredExtraProjectile.TIME_PER_TILE_DEFAULT;
        for (int i = 0; i < pairs.length; i += 2) params[pairs[i]] = pairs[i + 1];
        WiredExtraProjectile box = box();
        WiredSettings settings = new WiredSettings(params, tokens, new int[0], 0);
        settings.setRoom(room);
        box.saveData(settings, null);
        return box;
    }

    private static WiredContext firing(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));
    }

    @Test
    void aBoxSavedBeforeTheTimeWasAppliedGetsTheDefaultTimePerTile() throws Exception {
        WiredExtraProjectile loaded = box();
        loaded.loadWiredData(row("{\"params\":[1,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0],\"itemIds\":[]}"), room());

        int[] params = loaded.getParams();
        assertEquals(WiredExtraProjectile.TOTAL_PARAM_COUNT, params.length);
        assertEquals(WiredExtraProjectile.TIME_PER_TILE_DEFAULT, params[WiredExtraProjectile.PARAM_TIME_PER_TILE]);
        assertEquals(2, params[WiredExtraProjectile.PARAM_ROTATION_OFFSET]);
        assertEquals(1500, loaded.resolveDuration(null, null, 3, 0, 0, 400));
    }

    @Test
    void theVariableTokensAndSourcesSurviveTheDatabase() throws Exception {
        Room room = room();
        WiredExtraProjectile saved = configured(
                room,
                "custom:0012\tinternal:@position.x",
                WiredExtraProjectile.PARAM_TIME_IS_VARIABLE,
                1,
                WiredExtraProjectile.PARAM_TIME_FURNI_SOURCE,
                WiredVariableOperand.SOURCE_SECONDARY_SELECTED,
                WiredExtraProjectile.PARAM_SHOOTER_SOURCE,
                999);

        WiredExtraProjectile loaded = box();
        loaded.loadWiredData(row(saved.getWiredData()), room);

        Body body = body(loaded, room);
        assertEquals("custom:12\tinternal:@position_x", body.string());
        assertEquals(
                WiredVariableOperand.SOURCE_SECONDARY_SELECTED,
                body.params()[WiredExtraProjectile.PARAM_TIME_FURNI_SOURCE]);
        // Not a user source the box offers: the triggering user.
        assertEquals(0, body.params()[WiredExtraProjectile.PARAM_SHOOTER_SOURCE]);
    }

    @Test
    void theStacksOwnTimeStandsUnlessTheBoxScalesIt() {
        WiredExtraProjectile box = configured(room(), "");

        assertEquals(700, box.resolveDuration(null, null, 5, 0, 0, 700));
    }

    @Test
    void aScaledTimeIsTheTimePerTileForEveryTileOfTheLongestMeasuredAxis() {
        Room room = room();
        WiredExtraProjectile xy = configured(
                room, "", WiredExtraProjectile.PARAM_SCALE_TIME, 1, WiredExtraProjectile.PARAM_TIME_PER_TILE, 200);

        assertEquals(600, xy.resolveDuration(null, room, 3, -2, 5, 500));

        WiredExtraProjectile height = configured(
                room,
                "",
                WiredExtraProjectile.PARAM_SCALE_TIME,
                1,
                WiredExtraProjectile.PARAM_TIME_PER_TILE,
                200,
                WiredExtraProjectile.PARAM_DISTANCE_BY_HEIGHT,
                1);

        assertEquals(500, height.resolveDuration(null, room, 3, 0, 2.5, 500));
    }

    @Test
    void theSpeedIncreaseShortensEveryNextTileButNeverBelowAMillisecond() {
        assertEquals(450, WiredExtraProjectile.flightDuration(200, 50, 3));
        assertEquals(400, WiredExtraProjectile.flightDuration(200, 50, 2.5));
        assertEquals(WiredExtraProjectile.MIN_FLIGHT_MS, WiredExtraProjectile.flightDuration(40, 100_000, 10));
        assertEquals(WiredExtraProjectile.MAX_FLIGHT_MS, WiredExtraProjectile.flightDuration(100_000, 0, 64));
        assertEquals(WiredExtraProjectile.MIN_FLIGHT_MS, WiredExtraProjectile.flightDuration(500, 0, 0));
    }

    @Test
    void theTimePerTileCanComeFromAVariableReadOncePerFiring() {
        Room room = mock(Room.class);
        RoomVariableManager variables = mock(RoomVariableManager.class);
        WiredVariableDefinitionInfo definition = mock(WiredVariableDefinitionInfo.class);
        when(definition.hasValue()).thenReturn(true);
        when(room.getRoomVariableManager()).thenReturn(variables);
        when(variables.getDefinitionInfo(77)).thenReturn(definition);
        when(variables.getCurrentValue(77)).thenReturn(100);

        WiredExtraProjectile box = configured(
                room,
                "custom:77\t",
                WiredExtraProjectile.PARAM_SCALE_TIME,
                1,
                WiredExtraProjectile.PARAM_TIME_IS_VARIABLE,
                1,
                WiredExtraProjectile.PARAM_TIME_TARGET,
                WiredVariableOperand.TARGET_ROOM);

        WiredContext ctx = firing(room);
        assertEquals(400, box.resolveDuration(ctx, room, 4, 0, 0, 500));

        when(variables.getDefinitionInfo(77)).thenReturn(null);
        assertEquals(400, box.resolveDuration(ctx, room, 4, 0, 0, 500));
        assertEquals(2000, box.resolveDuration(firing(room), room, 4, 0, 0, 500));
    }

    @Test
    void anOvershootFliesPastAndAFixedDistanceMakesUpWhatIsMissing() {
        Room room = room();
        WiredExtraProjectile normal = configured(room, "", WiredExtraProjectile.PARAM_DISTANCE_TILES, 5);
        WiredExtraProjectile overshoot = configured(
                room,
                "",
                WiredExtraProjectile.PARAM_DISTANCE_MODE,
                WiredExtraProjectile.DISTANCE_OVERSHOOT,
                WiredExtraProjectile.PARAM_DISTANCE_TILES,
                3);
        WiredExtraProjectile fixed = configured(
                room,
                "",
                WiredExtraProjectile.PARAM_DISTANCE_MODE,
                WiredExtraProjectile.DISTANCE_FIXED,
                WiredExtraProjectile.PARAM_DISTANCE_TILES,
                5);

        assertEquals(0, normal.resolveOvershoot(null, room, 2, 0));
        assertEquals(3, overshoot.resolveOvershoot(null, room, 2, 0));
        assertEquals(3, fixed.resolveOvershoot(null, room, 2, -1));
        assertEquals(-2, fixed.resolveOvershoot(null, room, 7, 0));
    }

    @Test
    void aDistanceVariableNobodyHoldsLeavesTheFlightAsLongAsTheMove() {
        Room room = mock(Room.class);
        when(room.getRoomVariableManager()).thenReturn(mock(RoomVariableManager.class));
        WiredExtraProjectile box = configured(
                room,
                "\tcustom:5",
                WiredExtraProjectile.PARAM_DISTANCE_MODE,
                WiredExtraProjectile.DISTANCE_OVERSHOOT,
                WiredExtraProjectile.PARAM_DISTANCE_IS_VARIABLE,
                1,
                WiredExtraProjectile.PARAM_DISTANCE_TILES,
                4,
                WiredExtraProjectile.PARAM_DISTANCE_TARGET,
                WiredVariableOperand.TARGET_ROOM);

        assertEquals(0, box.resolveOvershoot(firing(room), room, 3, 0));
    }
}
