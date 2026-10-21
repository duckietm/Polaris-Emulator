package com.eu.habbo.messages.incoming.unknown;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.resolution.AchievementResolution;
import com.eu.habbo.habbohotel.achievements.resolution.AchievementResolutionManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.events.resolution.NewYearResolutionComposer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

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
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Room room = mock(Room.class);
        HabboItem item = mock(HabboItem.class);
        ClientMessage packet = mock(ClientMessage.class);

        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(7);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.getHabboItem(ITEM_ID)).thenReturn(item);
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
