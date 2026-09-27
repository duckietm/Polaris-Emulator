package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredWebApiKeyResultComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredGenerateWebApiKeyEventTest {
    private static final int OWNER = 7;

    @Test
    void theOwnerGetsTheNewKeyInPacket59() throws Exception {
        WiredExtraVariableWebApi box = box();
        GameClient client = run(box, OWNER, OWNER, true);

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client).sendResponse(sent.capture());
        WiredWebApiKeyResultComposer composer = assertInstanceOf(WiredWebApiKeyResultComposer.class, sent.getValue());
        assertTrue(composer.carriesSecret());

        ByteBuf packet = composer.compose().get();
        packet.skipBytes(4);
        assertEquals(59, packet.readShort());
        assertEquals(box.getId(), packet.readInt());
        assertTrue(packet.readBoolean());
        byte[] key = new byte[packet.readUnsignedShort()];
        packet.readBytes(key);
        assertEquals(box.getReadKey(), new String(key, StandardCharsets.UTF_8));
    }

    @Test
    void anyoneElseGetsTheGenericErrorAndNoKey() throws Exception {
        WiredExtraVariableWebApi box = box();
        GameClient client = run(box, OWNER, OWNER + 1, false);

        verify(client).sendResponse(any(UpdateFailedComposer.class));
        verify(client, never()).sendResponse(any(WiredWebApiKeyResultComposer.class));
        assertEquals("", box.getWriteKey());
    }

    @Test
    void aBoxInSomeoneElsesRoomMintsNothing() throws Exception {
        WiredExtraVariableWebApi box = box();
        GameClient client = run(box, OWNER + 1, OWNER, false);

        verify(client, never()).sendResponse(any(WiredWebApiKeyResultComposer.class));
        assertEquals("", box.getWriteKey());
    }

    private static WiredExtraVariableWebApi box() {
        WiredExtraVariableWebApi box = new WiredExtraVariableWebApi(3, OWNER, mock(Item.class), "", 0, 0);
        box.setRoomId(90);
        return box;
    }

    private static GameClient run(WiredExtraVariableWebApi box, int roomOwner, int requester, boolean read)
            throws Exception {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getId()).thenReturn(90);
        when(room.getOwnerId()).thenReturn(roomOwner);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        when(types.getExtra(box.getId())).thenReturn(box);

        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(requester);
        when(info.getCurrentRoom()).thenReturn(room);
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(room.canInspectWired(habbo)).thenReturn(true);

        WiredGenerateWebApiKeyEvent handler = new WiredGenerateWebApiKeyEvent();
        handler.client = client;
        ByteBuf buffer = Unpooled.buffer().writeInt(box.getId()).writeBoolean(read);
        handler.packet = new ClientMessage(2819, buffer);
        try {
            handler.handle();
        } finally {
            buffer.release();
        }
        return client;
    }
}
