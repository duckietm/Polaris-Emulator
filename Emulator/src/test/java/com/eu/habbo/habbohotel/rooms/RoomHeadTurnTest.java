package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.PluginManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomHeadTurnTest {

    private PluginManager originalPlugins;

    @BeforeEach
    void installPlugins() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        this.originalPlugins = (PluginManager) field.get(null);
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);
        field.set(null, plugins);
    }

    @AfterEach
    void restorePlugins() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        field.set(null, this.originalPlugins);
    }

    @Test
    void listenerOnTheSpeakersTileKeepsItsHead() {
        // Without the guard the rotation of a zero distance is north, one step from north-east.
        assertNull(RoomChatManager.headRotationTowardSpeaker(3, 3, RoomUserRotation.NORTH_EAST.getValue(), 3, 3));
        assertNull(RoomChatManager.headRotationTowardSpeaker(3, 3, RoomUserRotation.NORTH.getValue(), 3, 3));
    }

    @Test
    void listenerTurnsTowardASpeakerWithinPeripheralVision() {
        // Speaker straight south, body facing south-east: one step away.
        assertEquals(
                RoomUserRotation.SOUTH,
                RoomChatManager.headRotationTowardSpeaker(3, 3, RoomUserRotation.SOUTH_EAST.getValue(), 3, 5));
        // Wraps around: body north (0), speaker north-west (7).
        assertEquals(
                RoomUserRotation.NORTH_WEST,
                RoomChatManager.headRotationTowardSpeaker(3, 3, RoomUserRotation.NORTH.getValue(), 2, 2));
    }

    @Test
    void listenerIgnoresASpeakerBehindIt() {
        assertNull(RoomChatManager.headRotationTowardSpeaker(3, 3, RoomUserRotation.NORTH.getValue(), 3, 5));
    }

    @Test
    void seatedUserFacingNorthCanLookNorthWest() {
        RoomUnit unit = seatedUnit(RoomUserRotation.NORTH);

        unit.lookAtPoint(tile(2, 2));

        assertEquals(RoomUserRotation.NORTH_WEST, unit.getHeadRotation());
        assertEquals(RoomUserRotation.NORTH, unit.getBodyRotation());
    }

    @Test
    void seatedUserFacingNorthWestCanLookNorth() {
        RoomUnit unit = seatedUnit(RoomUserRotation.NORTH_WEST);

        unit.lookAtPoint(tile(3, 1));

        assertEquals(RoomUserRotation.NORTH, unit.getHeadRotation());
        assertEquals(RoomUserRotation.NORTH_WEST, unit.getBodyRotation());
    }

    @Test
    void seatedUserStillCannotLookBehindIt() {
        RoomUnit unit = seatedUnit(RoomUserRotation.NORTH);

        unit.lookAtPoint(tile(3, 5));

        assertEquals(RoomUserRotation.NORTH, unit.getHeadRotation());
    }

    private static RoomUnit seatedUnit(RoomUserRotation body) {
        RoomUnit unit = new RoomUnit();
        unit.setLocation(tile(3, 3));
        unit.setBodyRotation(body);
        unit.setHeadRotation(body);
        unit.setStatus(RoomUnitStatus.SIT, "0.5");
        return unit;
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }
}
