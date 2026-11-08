package polaris.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.messages.ClientMessage;
import polaris.messages.outgoing.MessageComposer;

/** Removing friends touches only real friends, and says nothing when there was nobody to remove. */
class RemoveFriendEventTest {

    private static final int FRIEND = 9;
    private static final int STRANGER = 5;

    private static GameClient run(int... ids) throws Exception {
        var buffer = Unpooled.buffer();
        buffer.writeInt(ids.length);
        for (int id : ids) {
            buffer.writeInt(id);
        }
        GameClient client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        when(client.getUser().getUserInfo().getId()).thenReturn(1);
        when(client.getUser().getMessenger().getFriend(FRIEND))
                .thenReturn(mock(MessengerBuddy.class), (MessengerBuddy) null);
        when(client.getUser().getMessenger().getFriend(STRANGER)).thenReturn(null);

        RemoveFriendEvent event = new RemoveFriendEvent();
        event.client = client;
        event.packet = new ClientMessage(0, buffer);
        try (MockedStatic<Messenger> messenger = mockStatic(Messenger.class);
                MockedStatic<Emulator> emulator = mockStatic(Emulator.class, RETURNS_DEEP_STUBS)) {
            event.handle();
            messenger.verify(
                    () -> Messenger.unfriend(anyInt(), anyInt()), times(ids.length == 1 && ids[0] == FRIEND ? 1 : 0));
        }
        return client;
    }

    @Test
    void aStrangerIsNeitherUnfriendedNorAnswered() throws Exception {
        GameClient client = run(STRANGER);

        verify(client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void aFriendIsRemovedAndTheListUpdated() throws Exception {
        GameClient client = run(FRIEND);

        verify(client, times(1)).sendResponse(any(MessageComposer.class));
    }

    @Test
    void theHandlerIsRateLimited() {
        assertTrue(new RemoveFriendEvent().getRatelimit() > 0);
    }
}
