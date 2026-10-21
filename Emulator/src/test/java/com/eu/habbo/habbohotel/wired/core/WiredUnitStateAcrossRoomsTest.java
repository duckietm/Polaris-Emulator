package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * A unit id is a counter inside one room, so two units of two rooms can carry the same id. What the wired movement
 * helpers remember about a unit must not be shared between them.
 */
class WiredUnitStateAcrossRoomsTest {

    @Test
    void aFollowerOfOneUnitIsNotSeenOnTheUnitOfAnotherRoomWithTheSameId() {
        RoomUnit inFirstRoom = unit(3);
        RoomUnit inSecondRoom = unit(3);
        HabboItem moving = item(500);
        HabboItem stack = item(501);

        WiredMoveCarryHelper.registerUserFollower(room(1), stack, moving, inFirstRoom, null, null);
        WiredMoveCarryHelper.markUserFollowerProcessed(inFirstRoom, moving, 42L);

        assertTrue(WiredMoveCarryHelper.isUserFollowerProcessed(inFirstRoom, moving, 42L));
        assertFalse(WiredMoveCarryHelper.isUserFollowerProcessed(inSecondRoom, moving, 42L));

        WiredMoveCarryHelper.cleanupRoomUnit(inFirstRoom);
    }

    @Test
    void leavingARoomForgetsOnlyThatUnit() {
        RoomUnit leaving = unit(3);
        RoomUnit staying = unit(3);
        HabboItem moving = item(500);
        HabboItem stack = item(501);

        WiredMoveCarryHelper.registerUserFollower(room(1), stack, moving, leaving, null, null);
        WiredMoveCarryHelper.registerUserFollower(room(2), stack, moving, staying, null, null);
        WiredMoveCarryHelper.markUserFollowerProcessed(leaving, moving, 42L);
        WiredMoveCarryHelper.markUserFollowerProcessed(staying, moving, 42L);

        WiredMoveCarryHelper.cleanupRoomUnit(leaving);

        assertFalse(WiredMoveCarryHelper.isUserFollowerProcessed(leaving, moving, 42L));
        assertTrue(WiredMoveCarryHelper.isUserFollowerProcessed(staying, moving, 42L));

        WiredMoveCarryHelper.cleanupRoomUnit(staying);
    }

    @Test
    void aSuppressedStatusComposerStaysWithItsOwnUnit() throws Exception {
        for (Class<?> helper : new Class<?>[] {WiredMoveCarryHelper.class, WiredUserMovementHelper.class}) {
            RoomUnit suppressed = unit(3);
            RoomUnit other = unit(3);

            suppress(helper, suppressed);

            assertTrue(shouldSuppress(helper, suppressed), helper.getSimpleName());
            assertFalse(shouldSuppress(helper, other), helper.getSimpleName());

            clear(helper, suppressed);
            assertFalse(shouldSuppress(helper, suppressed), helper.getSimpleName());
        }
    }

    private static void suppress(Class<?> helper, RoomUnit unit) throws Exception {
        Method method = helper.getDeclaredMethod("suppressStatusComposer", RoomUnit.class, int.class);
        method.setAccessible(true);
        method.invoke(null, unit, 500);
    }

    private static boolean shouldSuppress(Class<?> helper, RoomUnit unit) throws Exception {
        return (boolean)
                helper.getMethod("shouldSuppressStatusComposer", RoomUnit.class).invoke(null, unit);
    }

    private static void clear(Class<?> helper, RoomUnit unit) throws Exception {
        helper.getMethod("clearStatusComposerSuppression", RoomUnit.class).invoke(null, unit);
    }

    private static RoomUnit unit(int id) {
        RoomUnit unit = new RoomUnit();
        unit.setId(id);
        return unit;
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        return room;
    }

    private static HabboItem item(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }
}
