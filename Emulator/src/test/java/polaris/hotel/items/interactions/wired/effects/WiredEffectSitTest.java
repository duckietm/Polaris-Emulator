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

/** "Sit" has a message field that was saved, stored and sent back to the dialog, and never shown to anyone. */
class WiredEffectSitTest {

    @Test
    void theMessageIsWhisperedToTheUserWhoSat() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.canForcePosture()).thenReturn(true);
        User user = user("Frank", unit);
        when(room.getUser(unit)).thenReturn(user);
        WiredEffectSit box = box(room, "Sit down, %user%");

        box.execute(context(room, unit));

        verify(room).makeSit(user);
        ArgumentCaptor<RoomUserWhisperComposer> sent = ArgumentCaptor.forClass(RoomUserWhisperComposer.class);
        verify(user.getClient()).sendResponse(sent.capture());
        assertEquals("Sit down, Frank", sent.getValue().getRoomChatMessage().getMessage());
    }

    @Test
    void noMessageMeansNoWhisper() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.canForcePosture()).thenReturn(true);
        User user = user("Frank", unit);
        when(room.getUser(unit)).thenReturn(user);
        WiredEffectSit box = box(room, "");

        box.execute(context(room, unit));

        verify(room).makeSit(user);
        verify(user.getClient(), never()).sendResponse(any(MessageComposer.class));
    }

    private static WiredEffectSit box(Room room, String message) throws Exception {
        WiredEffectSit box = new WiredEffectSit(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"message\":\"" + message + "\",\"delay\":0,\"userSource\":0}"), room);
        return box;
    }
}
