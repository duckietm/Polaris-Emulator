package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.habbohotel.wired.protocol.WiredActionCodes;
import org.junit.jupiter.api.Test;

/** Official give effect (52): [effect id, priority, type], and the rows the text editor left. */
class WiredEffectGiveEffectTest {

    @Test
    void savesAndSendsBackTheOfficialLayoutUnderCode52() throws Exception {
        WiredEffectGiveEffect box = new WiredEffectGiveEffect(1, 1, base(), "", 0, 0);

        box.saveData(new WiredSettings(new int[] {20_000, 7, 1}, "", new int[0], 0), null);

        var body = WiredEffectProgressAchievementTest.body(box);
        assertArrayEquals(new int[] {10_000, 2, 1}, body.params());
        assertEquals(52, body.code());
        assertEquals(52, WiredActionCodes.official(WiredEffectType.EFFECT_ID.code));
    }

    @Test
    void aRowFromTheTextEditorKeepsItsEffect() throws Exception {
        WiredEffectGiveEffect box = new WiredEffectGiveEffect(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"message\":\"108\",\"delay\":2,\"userSource\":200}"), null);

        assertEquals(108, box.getEffectId());
        assertArrayEquals(
                new int[] {108, 2, 0},
                WiredEffectProgressAchievementTest.body(box).params());
    }

    @Test
    void giveHandItemHasItsOwnCodeNow() {
        assertEquals(1157, WiredActionCodes.official(WiredEffectType.GIVE_HAND_ITEM.code));
    }

    @Test
    void aLowerPriorityBoxLeavesAHigherPriorityEffectOn() throws Exception {
        Room room = room();
        RoomUnit unit = new RoomUnit();

        run(room, unit, 10, 2, 0);
        run(room, unit, 20, 1, 0);
        assertEquals(10, unit.getEffectId());

        run(room, unit, 30, 2, 0);
        assertEquals(30, unit.getEffectId());
        assertEquals(2, unit.getWiredEffectPriority());
    }

    @Test
    void anEffectFromElsewhereHasNoWiredPriority() throws Exception {
        Room room = room();
        RoomUnit unit = new RoomUnit();

        run(room, unit, 10, 2, 0);
        unit.setEffectId(55, Integer.MAX_VALUE);
        assertEquals(-1, unit.getWiredEffectPriority());

        run(room, unit, 20, 0, 0);
        assertEquals(20, unit.getEffectId());
    }

    @Test
    void typeOneKeepsTheUsersOwnEffectButReplacesAWiredOne() throws Exception {
        Room room = room();
        RoomUnit unit = new RoomUnit();
        unit.setEffectId(55, Integer.MAX_VALUE);

        run(room, unit, 20, 2, 1);
        assertEquals(55, unit.getEffectId());

        unit.setEffectId(0, 0);
        run(room, unit, 20, 1, 1);
        assertEquals(20, unit.getEffectId());
        run(room, unit, 30, 1, 1);
        assertEquals(30, unit.getEffectId());
    }

    @Test
    void effectZeroTakesTheEffectOffAndForgetsThePriority() throws Exception {
        Room room = room();
        RoomUnit unit = new RoomUnit();

        run(room, unit, 10, 1, 0);
        run(room, unit, 0, 0, 0);
        assertEquals(10, unit.getEffectId());

        run(room, unit, 0, 1, 0);
        assertEquals(0, unit.getEffectId());
        assertEquals(-1, unit.getWiredEffectPriority());
    }

    private static void run(Room room, RoomUnit unit, int effectId, int priority, int type) throws Exception {
        WiredEffectGiveEffect box = new WiredEffectGiveEffect(1, 1, base(), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {effectId, priority, type}, "", new int[0], 0), null);
        WiredEvent event =
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(unit).build();
        box.execute(new WiredContext(event, box, mock(WiredServices.class), new WiredState(20)));
    }

    private static Room room() {
        Room room = mock(Room.class);
        doAnswer(invocation -> {
                    RoomUnit unit = invocation.getArgument(0);
                    unit.setEffectId(invocation.getArgument(1), invocation.getArgument(2));
                    return null;
                })
                .when(room)
                .giveEffect(any(RoomUnit.class), anyInt(), eq(Integer.MAX_VALUE));
        return room;
    }
}
