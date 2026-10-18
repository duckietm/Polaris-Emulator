package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredProjectileFlight;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WiredProjectileFlightsTest {

    private static HabboItem furni(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(mock(Item.class));
        return item;
    }

    private static WiredProjectileFlight flight(long start) {
        return WiredProjectileFlight.launch(
                0, 0, 0, 4, 0, 0, start, 400, WiredProjectileFlight.ALL_VARIABLES, null, null);
    }

    @Test
    void aFurniNeverLaunchedHoldsNoneOfTheVariables() {
        AtomicLong clock = new AtomicLong(1_000);
        WiredProjectileFlights flights = new WiredProjectileFlights(clock::get);
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getProjectileFlights()).thenReturn(flights);
        HabboItem arrow = furni(7);
        HabboItem other = furni(8);

        flights.begin(7, flight(1_000));
        clock.set(1_200);

        assertTrue(WiredInternalVariableSupport.hasFurniValue(room, arrow, WiredProjectileFlight.TILES_TRAVELED));
        assertEquals(2, WiredInternalVariableSupport.readFurniValue(room, arrow, WiredProjectileFlight.POSITION_X));
        assertTrue(WiredInternalVariableSupport.hasFurniValue(room, arrow, WiredProjectileFlight.IS_TRAVELING));
        assertFalse(WiredInternalVariableSupport.hasFurniValue(room, other, WiredProjectileFlight.POSITION_X));
        assertNull(WiredInternalVariableSupport.readFurniValue(room, other, WiredProjectileFlight.POSITION_X));
        assertFalse(WiredInternalVariableSupport.hasFurniValue(arrow, WiredProjectileFlight.POSITION_X));

        clock.set(5_000);
        assertEquals(4, WiredInternalVariableSupport.readFurniValue(room, arrow, WiredProjectileFlight.TILES_TRAVELED));
        assertFalse(WiredInternalVariableSupport.hasFurniValue(room, arrow, WiredProjectileFlight.IS_TRAVELING));
    }

    @Test
    void theLastFlightWinsAndTheOldestGoFirstPastTheCap() {
        WiredProjectileFlights flights = new WiredProjectileFlights(() -> 0L);

        for (int id = 1; id <= WiredProjectileFlights.MAX_FLIGHTS + 10; id++) {
            flights.begin(id, flight(0));
        }
        flights.begin(11, flight(50));

        assertEquals(WiredProjectileFlights.MAX_FLIGHTS, flights.size());
        assertNull(flights.get(1));
        assertNull(flights.get(10));
        assertTrue(flights.get(11).isTraveling(300));

        // Launched again, 11 is now the newest: the next one over the cap evicts 12 instead.
        flights.begin(WiredProjectileFlights.MAX_FLIGHTS + 11, flight(0));
        assertTrue(flights.get(11) != null);
        assertNull(flights.get(12));
    }

    @Test
    void aFurniLeavingTheRoomOrTheRoomUnloadingForgetsTheFlights() {
        WiredProjectileFlights flights = new WiredProjectileFlights(() -> 0L);
        flights.begin(3, flight(0));
        flights.begin(4, flight(0));

        flights.forget(3);
        assertNull(flights.get(3));

        flights.clear();
        assertEquals(0, flights.size());
    }

    @Test
    void aFlightReportsItsCountsOnlyAfterLaunchAndItsFlagWhenItLands() {
        AtomicLong clock = new AtomicLong(1_000);
        WiredProjectileFlights flights = new WiredProjectileFlights(clock::get);

        // Four tiles in 400 ms: one tile every 100 ms.
        flights.begin(7, flight(1_000));
        assertTrue(flights.hasPending());
        assertTrue(flights.claimWatcher());
        assertFalse(flights.claimWatcher());

        assertTrue(flights.poll(1_050).isEmpty());
        java.util.List<WiredProjectileFlights.Change> crossed = flights.poll(1_250);
        assertEquals(
                java.util.List.of(new WiredProjectileFlights.Change(7, WiredProjectileFlight.TILES_TRAVELED, 0, 2)),
                crossed);
        assertFalse(flights.releaseWatcherIfIdle());

        java.util.List<WiredProjectileFlights.Change> landed = flights.poll(1_400);
        assertTrue(landed.contains(new WiredProjectileFlights.Change(7, WiredProjectileFlight.TILES_TRAVELED, 2, 4)));
        assertTrue(landed.contains(new WiredProjectileFlights.Change(7, WiredProjectileFlight.IS_TRAVELING, 1, null)));
        assertEquals(2, landed.size());
        assertFalse(flights.hasPending());
        assertTrue(flights.poll(1_500).isEmpty());
        assertTrue(flights.releaseWatcherIfIdle());
        assertTrue(flights.claimWatcher());
    }
}
