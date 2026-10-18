package com.eu.habbo.messages.incoming.rooms.items;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootballGate;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FootballGateSaveLookEventTest {
    private static final int GATE_ID = 44;
    private static final String LOOK = "ch-3109-92-1408.lg-3116-82-1408";

    @Test
    void aRightsHolderWhoIsNotTheOwnerSavesTheLook() throws Exception {
        InteractionFootballGate gate = mock(InteractionFootballGate.class);
        Room room = run(gate, true, "M");

        verify(gate).setFigureM(LOOK);
        verify(room).updateItem(gate);
    }

    @Test
    void aVisitorWithoutRightsChangesNothing() throws Exception {
        InteractionFootballGate gate = mock(InteractionFootballGate.class);
        Room room = run(gate, false, "F");

        verify(gate, never()).setFigureF(any());
        verify(room, never()).updateItem(any());
    }

    private static Room run(InteractionFootballGate gate, boolean hasRights, String gender) throws Exception {
        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(1);
        when(room.getHabboItem(GATE_ID)).thenReturn(gate);

        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(2);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.hasRights(habbo)).thenReturn(hasRights);
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);

        FootballGateSaveLookEvent handler = new FootballGateSaveLookEvent();
        handler.client = client;
        ByteBuf buffer = Unpooled.buffer().writeInt(GATE_ID);
        writeString(buffer, gender);
        writeString(buffer, LOOK);
        handler.packet = new ClientMessage(924, buffer);
        try {
            handler.handle();
        } finally {
            buffer.release();
        }
        return room;
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length).writeBytes(bytes);
    }
}
