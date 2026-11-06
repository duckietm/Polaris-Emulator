package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.user;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;

/** "Lay" has a message field that was saved, stored and sent back to the dialog, and never shown to anyone. */
class WiredEffectLayTest {

    @Test
    void theMessageIsWhisperedToTheUserWhoLayDown() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = standingInTheOpen(room);
        User user = user("Frank", unit);
        when(room.getUser(unit)).thenReturn(user);
        WiredEffectLay box = box(room, "Lie down, %user%");

        box.execute(context(room, unit));

        verify(unit).setStatus(eq(RoomUnitStatus.LAY), any());
        ArgumentCaptor<RoomUserWhisperComposer> sent = ArgumentCaptor.forClass(RoomUserWhisperComposer.class);
        verify(user.getClient()).sendResponse(sent.capture());
        assertEquals("Lie down, Frank", sent.getValue().getRoomChatMessage().getMessage());
    }

    @Test
    void aUserWhoCouldNotLieDownGetsNoMessage() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = standingInTheOpen(room);
        when(unit.canForcePosture()).thenReturn(false);
        User user = user("Frank", unit);
        when(room.getUser(unit)).thenReturn(user);
        WiredEffectLay box = box(room, "Lie down, %user%");

        box.execute(context(room, unit));

        verify(unit, never()).setStatus(eq(RoomUnitStatus.LAY), any());
        verify(user.getClient(), never()).sendResponse(any(MessageComposer.class));
    }

    private static WiredEffectLay box(Room room, String message) throws Exception {
        WiredEffectLay box = new WiredEffectLay(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"message\":\"" + message + "\",\"delay\":0,\"userSource\":0}"), room);
        return box;
    }

    /** A unit facing north with three walkable tiles ahead, and enough state for the status packet to build. */
    private static RoomUnit standingInTheOpen(Room room) {
        RoomUnit unit = mock(RoomUnit.class);
        RoomTile here = mock(RoomTile.class);
        RoomTile ahead = mock(RoomTile.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(ahead.isWalkable()).thenReturn(true);
        when(layout.getTileInFront(eq(here), anyInt(), anyInt())).thenReturn(ahead);
        when(room.getLayout()).thenReturn(layout);
        when(unit.canForcePosture()).thenReturn(true);
        when(unit.getBodyRotation()).thenReturn(RoomUserRotation.NORTH);
        when(unit.getHeadRotation()).thenReturn(RoomUserRotation.NORTH);
        when(unit.getCurrentLocation()).thenReturn(here);
        when(unit.getPreviousLocation()).thenReturn(here);
        when(unit.getStatusMap()).thenReturn(new ConcurrentHashMap<>());
        return unit;
    }
}
