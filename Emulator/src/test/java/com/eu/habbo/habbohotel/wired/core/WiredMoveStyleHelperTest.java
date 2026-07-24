package com.eu.habbo.habbohotel.wired.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredMoveStyleHelperTest {

    @Test
    void onlyCapableClientsReceiveTheStyleHint() {
        Room room = mock(Room.class);
        Habbo capable = habbo(true);
        Habbo legacy = habbo(false);
        when(room.getHabbos()).thenReturn(List.of(capable, legacy));

        WiredMoveStyleHelper.broadcast(room, List.of(101), WiredMoveStyleHelper.STYLE_DROP, 100);

        verify(capable.getClient()).sendResponse(any(ServerMessage.class));
        verify(legacy.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    @Test
    void linearStyleAndEmptyTargetsSendNothing() {
        Room room = mock(Room.class);
        Habbo capable = habbo(true);
        when(room.getHabbos()).thenReturn(List.of(capable));

        WiredMoveStyleHelper.broadcast(room, List.of(101), WiredMoveStyleHelper.STYLE_LINEAR, 100);
        WiredMoveStyleHelper.broadcast(room, List.of(), WiredMoveStyleHelper.STYLE_DROP, 100);
        WiredMoveStyleHelper.broadcast(null, List.of(101), WiredMoveStyleHelper.STYLE_DROP, 100);

        verify(capable.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    private static Habbo habbo(boolean capable) {
        Habbo habbo = mock(Habbo.class);
        GameClient client = mock(GameClient.class);
        when(habbo.getClient()).thenReturn(client);
        when(client.supportsWiredFeature(
                        GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_MOVE_STYLE))
                .thenReturn(capable);
        return habbo;
    }
}
