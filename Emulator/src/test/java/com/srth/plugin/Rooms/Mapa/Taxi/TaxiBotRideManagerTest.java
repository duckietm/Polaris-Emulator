package com.srth.plugin.Rooms.Mapa.Taxi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaxiBotRideManagerTest {

    @Test
    void destinationWalksUpToTwoTilesInTheArrivalDirection() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomUnit botUnit = mock(RoomUnit.class);
        RoomTile startingTile = tile(10, 10);
        RoomTile firstTile = tile(11, 10);
        RoomTile secondTile = tile(12, 10);
        when(room.getLayout()).thenReturn(layout);
        when(botUnit.getCurrentLocation()).thenReturn(startingTile);
        when(layout.getTileInFront(startingTile, RoomUserRotation.SOUTH.getValue()))
                .thenReturn(firstTile);
        when(layout.getTileInFront(firstTile, RoomUserRotation.SOUTH.getValue()))
                .thenReturn(secondTile);
        when(room.getRoomUnitsAt(firstTile)).thenReturn(List.of());
        when(room.getRoomUnitsAt(secondTile)).thenReturn(List.of());

        assertSame(secondTile, TaxiBotRideManager.findArrivalWalkGoal(room, botUnit, RoomUserRotation.SOUTH));
    }

    @Test
    void botRouteCanWaitWithoutStartingTheExistingArrowCycle() {
        RecorridoTaxi route = new RecorridoTaxi(mock(Habbo.class), 42, false, true, true);

        assertTrue(route.getStatus());
        assertTrue(route.isTaxiBotRideMode());
        assertFalse(route.isStarted());
    }

    @Test
    void destinationCanFinishWhileBotApproachesAfterRoomTransfer() {
        assertTrue(TaxiBotRideManager.canFinishAtDestination(TaxiBotRideManager.RideState.APPROACHING));
        assertTrue(TaxiBotRideManager.canFinishAtDestination(TaxiBotRideManager.RideState.ACTIVE));
        assertFalse(TaxiBotRideManager.canFinishAtDestination(TaxiBotRideManager.RideState.EXITING));
    }

    @Test
    void botKeepsItsDirectionWhenAddedToTheNextRoom() {
        Bot bot = new Bot(1, "Taxi", "", "", HabboGender.M, 1, "owner");
        Room room = mock(Room.class);
        RoomTile spawnTile = tile(3, 4);

        TaxiBotRideManager.addBotToRoom(bot, room, spawnTile, 634, RoomUserRotation.SOUTH);

        assertEquals(RoomUserRotation.SOUTH, bot.getRoomUnit().getBodyRotation());
    }

    @Test
    void botSpawnsAtAnAvailableTeleportTile() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomUnit passenger = mock(RoomUnit.class);
        RoomTile passengerTile = tile(10, 10);
        RoomTile arrowTile = tile(12, 10);
        HabboItem arrow = teleportArrow(12, 10);

        when(room.getLayout()).thenReturn(layout);
        when(room.getFloorItems()).thenReturn(Set.of(arrow));
        when(room.getRoomUnitsAt(arrowTile)).thenReturn(List.of());
        when(layout.getTile((short) 12, (short) 10)).thenReturn(arrowTile);
        when(passenger.getCurrentLocation()).thenReturn(passengerTile);

        assertSame(arrowTile, TaxiBotRideManager.findTeleportSpawnTile(room, passenger));
    }

    @Test
    void botUsesAnAdjacentTileWhenTheTeleportIsOccupied() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomUnit passenger = mock(RoomUnit.class);
        RoomTile passengerTile = tile(10, 10);
        RoomTile arrowTile = tile(12, 10);
        RoomTile adjacentTile = tile(12, 11);
        HabboItem arrow = teleportArrow(12, 10);

        when(room.getLayout()).thenReturn(layout);
        when(room.getFloorItems()).thenReturn(Set.of(arrow));
        when(room.getRoomUnitsAt(arrowTile)).thenReturn(List.of(passenger));
        when(room.getRoomUnitsAt(adjacentTile)).thenReturn(List.of());
        when(layout.getTile((short) 12, (short) 10)).thenReturn(arrowTile);
        when(layout.getTilesAround(arrowTile)).thenReturn(List.of(adjacentTile));
        when(passenger.getCurrentLocation()).thenReturn(passengerTile);

        assertSame(adjacentTile, TaxiBotRideManager.findTeleportSpawnTile(room, passenger));
    }

    @Test
    void nextRoomEntryUsesThePairedTeleportWhenAvailable() {
        Room room = mock(Room.class);
        HabboItem arrivalArrow = teleportArrow(12, 10);
        RoomTile userTile = tile(11, 10);

        when(room.getId()).thenReturn(42);
        when(room.getHabboItem(73)).thenReturn(arrivalArrow);

        assertSame(arrivalArrow, TaxiBotRideManager.findEntryArrow(room, 42, 73, userTile));
    }

    @Test
    void taxiDeparturePrefersTheTeleportTileItEnteredThrough() {
        Room room = mock(Room.class);
        HabboItem entryArrow = teleportArrow(12, 10);
        when(room.getHabboItem(73)).thenReturn(entryArrow);

        assertSame(entryArrow, TaxiBotRideManager.findDepartureArrow(room, 73, 0, 0, null));
    }

    @Test
    void nextRoomEntryFallsBackToNearbyArrowWhenDestinationWasResolvedDynamically() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        HabboItem nearbyArrow = teleportArrow(12, 10);
        RoomTile arrowTile = tile(12, 10);
        RoomTile userTile = tile(11, 10);

        when(room.getId()).thenReturn(43);
        when(room.getLayout()).thenReturn(layout);
        when(room.getFloorItems()).thenReturn(Set.of(nearbyArrow));
        when(layout.getTile((short) 12, (short) 10)).thenReturn(arrowTile);

        assertSame(nearbyArrow, TaxiBotRideManager.findEntryArrow(room, 42, 73, userTile));
    }

    @Test
    void botApproachesTheTileInFrontOfThePassenger() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomUnit passenger = mock(RoomUnit.class);
        RoomTile passengerTile = tile(10, 10);
        RoomTile frontTile = tile(10, 11);

        when(room.getLayout()).thenReturn(layout);
        when(room.getRoomUnitsAt(frontTile)).thenReturn(List.of());
        when(passenger.getCurrentLocation()).thenReturn(passengerTile);
        when(passenger.getBodyRotation()).thenReturn(RoomUserRotation.NORTH);
        when(layout.getTileInFront(passengerTile, RoomUserRotation.NORTH.getValue()))
                .thenReturn(frontTile);

        RoomTile approachTile = TaxiBotRideManager.findApproachTile(room, passenger);

        assertSame(frontTile, approachTile);
    }

    @Test
    void passengerFollowsOnTheBotsPreviousTileEvenWhenTheBotStopped() {
        Room room = mock(Room.class);
        RoomUnit botUnit = mock(RoomUnit.class);
        RoomUnit passengerUnit = mock(RoomUnit.class);
        RoomTile previousTile = tile(10, 10);
        RoomTile botTile = tile(11, 10);

        when(botUnit.getPreviousLocation()).thenReturn(previousTile);
        when(botUnit.getCurrentLocation()).thenReturn(botTile);
        when(room.getRoomUnitsAt(previousTile)).thenReturn(List.of());

        assertSame(
                previousTile, TaxiBotRideManager.findPassengerFollowTile(room, botUnit, passengerUnit, tile(15, 10)));
    }

    private static HabboItem teleportArrow(int x, int y) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(baseItem.getName()).thenReturn("room_switcher_back_left");
        when(item.getBaseItem()).thenReturn(baseItem);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getBaseItem().getInteractionType())
                .thenReturn(new ItemInteraction("teleport_tile", InteractionTeleportTile.class));
        return item;
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }
}
