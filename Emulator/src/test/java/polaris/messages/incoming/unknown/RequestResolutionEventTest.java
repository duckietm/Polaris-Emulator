package polaris.messages.incoming.unknown;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.achievements.resolution.AchievementResolution;
import polaris.hotel.achievements.resolution.AchievementResolutionManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.messages.ClientMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.events.resolution.NewYearResolutionComposer;

class RequestResolutionEventTest {
    private static final int ITEM_ID = 100;
    private static final int NOW = 1_000;

    @Test
    void aPromiseThatIsGoneWhenItIsReadBackOffersThePickerAgain() throws Exception {
        AchievementResolutionManager manager = mock(AchievementResolutionManager.class);
        AchievementResolution open = new AchievementResolution(ITEM_ID, 7, 3, 2, "ACH_Test2", 0, NOW + 5_000, 0);

        // The promise is open and kept, then it is no longer there when the handler reads it back.
        when(manager.resolution(ITEM_ID)).thenReturn(open, (AchievementResolution) null);
        when(manager.kept(any(), any())).thenReturn(true);
        when(manager.candidates(any())).thenReturn(List.of());
        when(manager.durationSeconds()).thenReturn(604_800);

        GameClient client = mock(GameClient.class);
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        Room room = mock(Room.class);
        UserItem item = mock(UserItem.class);
        ClientMessage packet = mock(ClientMessage.class);

        when(client.getUser()).thenReturn(user);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getId()).thenReturn(7);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.getUserItem(ITEM_ID)).thenReturn(item);
        when(item.getUserId()).thenReturn(7);
        when(packet.readInt()).thenReturn(ITEM_ID, 0);

        RequestResolutionEvent event = new RequestResolutionEvent();
        event.client = client;
        event.packet = packet;

        try (MockedStatic<AchievementResolutionManager> managers = mockStatic(AchievementResolutionManager.class);
                MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            managers.when(AchievementResolutionManager::getInstance).thenReturn(manager);
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(NOW);

            event.handle();
        }

        ArgumentCaptor<MessageComposer> answer = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client, times(1)).sendResponse(answer.capture());
        assertInstanceOf(NewYearResolutionComposer.class, answer.getValue());
        verify(manager, never()).progress(any(), any());
    }
}
