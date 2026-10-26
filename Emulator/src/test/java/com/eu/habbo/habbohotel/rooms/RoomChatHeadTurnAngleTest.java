package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** AIR: in view up to 2 steps from the body; at 2 the head turns one step, at 1 straight at the speaker. */
class RoomChatHeadTurnAngleTest {
    @Test
    void theHeadTurnsAtMostOneStepFromTheBody() {
        // Listener at (5,5) facing east (2).
        assertEquals(RoomUserRotation.EAST, RoomChatManager.headRotationTowardSpeaker(5, 5, 2, 8, 5));
        // Speaker to the north-east (1 step): looks straight at them.
        assertEquals(RoomUserRotation.NORTH_EAST, RoomChatManager.headRotationTowardSpeaker(5, 5, 2, 8, 2));
        // Speaker to the north (2 steps): one step toward them.
        assertEquals(RoomUserRotation.NORTH_EAST, RoomChatManager.headRotationTowardSpeaker(5, 5, 2, 5, 1));
        // Speaker to the south (2 steps the other way): one step toward them.
        assertEquals(RoomUserRotation.SOUTH_EAST, RoomChatManager.headRotationTowardSpeaker(5, 5, 2, 5, 9));
        // Behind (3 steps or more): no turn.
        assertNull(RoomChatManager.headRotationTowardSpeaker(5, 5, 2, 2, 2));
        assertNull(RoomChatManager.headRotationTowardSpeaker(5, 5, 2, 5, 5));
    }
}
