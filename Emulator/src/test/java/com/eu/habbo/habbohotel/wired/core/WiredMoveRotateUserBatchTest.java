package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.messages.ServerMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A Move/Rotate User box's turns join the firing's one WiredMovements; a seated user keeps the seat's direction. */
class WiredMoveRotateUserBatchTest {

    @AfterEach
    void clear() {
        WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
    }

    @Test
    void aBoxTurnJoinsTheOpenCollection() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        WiredMoveCarryHelper.beginMovementCollection(true);
        try {
            WiredUserMovementHelper.joiningFiringMovements(() -> WiredUserMovementHelper.updateUserDirection(
                    room, unit, RoomUserRotation.EAST, RoomUserRotation.EAST));
            assertEquals(1, WiredMoveCarryHelper.collectedMovementCount());
            verify(room, never()).sendComposer(any(ServerMessage.class));
        } finally {
            WiredMoveCarryHelper.finishMovementCollection();
        }
        assertEquals(RoomUserRotation.EAST, unit.getBodyRotation());
    }

    @Test
    void anotherTurnKeepsItsOwnPacket() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        WiredMoveCarryHelper.beginMovementCollection(true);
        try {
            WiredUserMovementHelper.updateUserDirection(room, unit, RoomUserRotation.EAST, RoomUserRotation.EAST);
        } finally {
            WiredMoveCarryHelper.finishMovementCollection();
        }
        verify(room, times(1)).sendComposer(any(ServerMessage.class));
    }

    @Test
    void aUserOnASeatGetsTheTurnButKeepsTheSeatsDirection() {
        Room room = mock(Room.class);
        RoomUnit seated = new RoomUnit();
        seated.setBodyRotation(RoomUserRotation.SOUTH);
        seated.setHeadRotation(RoomUserRotation.SOUTH);
        seated.setStatus(RoomUnitStatus.SIT, "0.5");

        WiredUserMovementHelper.joiningFiringMovements(() -> WiredUserMovementHelper.updateUserDirection(
                room, seated, RoomUserRotation.EAST, RoomUserRotation.EAST));

        verify(room, times(1)).sendComposer(any(ServerMessage.class));
        assertEquals(RoomUserRotation.SOUTH, seated.getBodyRotation());

        RoomUnit floorSitter = new RoomUnit();
        floorSitter.setStatus(RoomUnitStatus.SIT, "0.5");
        floorSitter.cmdSit = true;
        WiredUserMovementHelper.joiningFiringMovements(() -> WiredUserMovementHelper.updateUserDirection(
                room, floorSitter, RoomUserRotation.EAST, RoomUserRotation.EAST));
        assertEquals(RoomUserRotation.EAST, floorSitter.getBodyRotation());
    }
}
