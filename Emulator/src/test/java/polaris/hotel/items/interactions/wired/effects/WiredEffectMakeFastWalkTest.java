package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;

/** "Fast walk" has a message field that was saved, stored and sent back to the dialog, and never shown to anyone. */
class WiredEffectMakeFastWalkTest {

    @Test
    void theMessageIsWhisperedToTheUserWhoSpedUp() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = user("Frank", unit);
        when(room.getUser(unit)).thenReturn(user);
        WiredEffectMakeFastWalk box = box(room, "Run, %user%");

        box.execute(context(room, unit));

        verify(unit).setFastWalk(true);
        ArgumentCaptor<RoomUserWhisperComposer> sent = ArgumentCaptor.forClass(RoomUserWhisperComposer.class);
        verify(user.getClient()).sendResponse(sent.capture());
        assertEquals("Run, Frank", sent.getValue().getRoomChatMessage().getMessage());
    }

    @Test
    void noMessageMeansNoWhisper() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = user("Frank", unit);
        when(room.getUser(unit)).thenReturn(user);
        WiredEffectMakeFastWalk box = box(room, "");

        box.execute(context(room, unit));

        verify(unit).setFastWalk(true);
        verify(user.getClient(), never()).sendResponse(any(MessageComposer.class));
    }

    private static WiredEffectMakeFastWalk box(Room room, String message) throws Exception {
        WiredEffectMakeFastWalk box = new WiredEffectMakeFastWalk(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"message\":\"" + message + "\",\"delay\":0,\"userSource\":0}"), room);
        return box;
    }
}
