package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;

/**
 * A signal and a stack call carry the whole sets they pass on, so "users/furni from signal" in the
 * receiving stack sees all of them rather than the one actor and the one furni it used to.
 */
class WiredSignalForwardingTest {

    private static RoomUnit unit(int id) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(id);
        return unit;
    }

    private static UserItem furni(int id) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static WiredContext context(WiredEvent event) {
        return new WiredContext(event, null, mock(WiredServices.class), new WiredState(20));
    }

    @Test
    void aSignalHandsOnEveryUserAndFurniItCarried() {
        Room room = mock(Room.class);
        RoomUnit first = unit(1);
        RoomUnit second = unit(2);
        UserItem ball = furni(10);
        UserItem goal = furni(11);

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .actor(first)
                .sourceItem(ball)
                .forwardedUsers(List.of(first, second))
                .forwardedItems(List.of(ball, goal))
                .build();

        WiredContext ctx = context(event);

        assertEquals(List.of(first, second), WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(List.of(ball, goal), WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SIGNAL, null));
    }

    @Test
    void aSignalThatCarriedNoSetsStillNamesItsActorAndFurni() {
        Room room = mock(Room.class);
        RoomUnit actor = unit(1);
        UserItem ball = furni(10);

        WiredContext ctx = context(WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .actor(actor)
                .sourceItem(ball)
                .build());

        assertEquals(List.of(actor), WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(List.of(ball), WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SIGNAL, null));
    }

    @Test
    void theReceivingStacksTriggeringFurniIsTheAntennaAndItsFurniComeFromTheSignal() {
        Room room = mock(Room.class);
        UserItem antenna = furni(42);
        UserItem ball = furni(10);

        WiredContext carried = context(WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .signalChannel(42)
                .sourceItem(antenna)
                .forwardedItems(List.of(ball))
                .build());

        assertEquals(List.of(antenna), WiredSourceUtil.resolveItems(carried, WiredSourceUtil.SOURCE_TRIGGER, null));
        assertEquals(List.of(ball), WiredSourceUtil.resolveItems(carried, WiredSourceUtil.SOURCE_SIGNAL, null));

        WiredContext empty = context(WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .signalChannel(42)
                .sourceItem(antenna)
                .build());

        assertEquals(List.of(), WiredSourceUtil.resolveItems(empty, WiredSourceUtil.SOURCE_SIGNAL, null));
    }

    @Test
    void onlyACallStacksBoxMarksItsEventAsAStackCall() {
        Room room = mock(Room.class);

        assertFalse(WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build().isStackCall());
        assertTrue(WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .stackCall(true)
                .build()
                .isStackCall());
    }
}
