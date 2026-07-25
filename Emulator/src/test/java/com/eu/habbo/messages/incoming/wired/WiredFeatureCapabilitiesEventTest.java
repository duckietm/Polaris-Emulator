package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredFeatureCapabilitiesEventTest {

    @Test
    void supportedClientReceivesItsAuthoritativeRoomSnapshot() throws Exception {
        Fixture fixture = fixture();
        when(fixture.runtime.opacitySnapshot(77)).thenReturn(List.of(new WiredOpacityState(901, false, 35, true)));

        WiredFeatureCapabilitiesEvent event =
                event(fixture, GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_OPACITY);
        event.handle();

        verify(fixture.runtime).opacitySnapshot(77);
        verify(fixture.client).sendResponse(any(MessageComposer.class));
        assertEquals(500, event.getRatelimit());
    }

    @Test
    void unsupportedClientNeverReceivesANewPacketFamily() throws Exception {
        Fixture unsupported = fixture();
        event(unsupported, 0, Integer.MAX_VALUE).handle();
        verify(unsupported.client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void truncatedPacketDoesNotChangeCapabilitiesOrReadRoomState() throws Exception {
        Fixture fixture = fixture();
        WiredFeatureCapabilitiesEvent event = new WiredFeatureCapabilitiesEvent();
        event.client = fixture.client;
        event.packet = new ClientMessage(0, Unpooled.buffer().writeInt(1));

        event.handle();

        verify(fixture.client, never()).setWiredFeatureCapabilities(1, 0);
        verify(fixture.runtime, never()).opacitySnapshot(anyInt());
    }

    private static WiredFeatureCapabilitiesEvent event(Fixture fixture, int version, int capabilities) {
        WiredFeatureCapabilitiesEvent event = new WiredFeatureCapabilitiesEvent();
        event.client = fixture.client;
        event.packet = new ClientMessage(0, Unpooled.buffer().writeInt(version).writeInt(capabilities));
        return event;
    }

    private static Fixture fixture() {
        GameClient client = mock(GameClient.class);
        doCallRealMethod().when(client).setWiredFeatureCapabilities(anyInt(), anyInt());
        doCallRealMethod().when(client).supportsWiredFeature(anyInt(), anyInt());
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        when(info.getId()).thenReturn(77);
        when(room.getWiredRuntime()).thenReturn(runtime);
        return new Fixture(client, room, runtime);
    }

    private record Fixture(GameClient client, Room room, RoomWiredRuntime runtime) {}
}
