package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WiredFurniRuntimeStateRequestEventTest {
    @Test
    void requesterWithoutInspectAccessGetsNoExistenceOracle() throws Exception {
        Fixture fixture = fixture(false, false);
        WiredFurniRuntimeStateRequestEvent event = event(fixture, 1, 0, "@gravity", 0);

        event.handle();

        verify(fixture.client, never()).sendResponse(any(MessageComposer.class));
        verify(fixture.room, never()).getHabboItem(1);
    }

    @Test
    void inspectOnlyRequesterCannotMutateRuntimeState() throws Exception {
        Fixture fixture = fixture(true, false);
        HabboItem item = floorItem(1);
        when(fixture.room.getHabboItem(1)).thenReturn(item);
        when(fixture.runtime.isGravityEnabled(item)).thenReturn(false);

        event(fixture, 1, 1, "@gravity", 1).handle();

        verify(fixture.runtime, never()).setGravityEnabled(any(HabboItem.class), anyBoolean());
        verify(fixture.client).sendResponse(any(MessageComposer.class));
    }

    @Test
    void modifyingRequesterWritesOnlyCurrentRoomFurniture() throws Exception {
        Fixture fixture = fixture(true, true);
        HabboItem item = floorItem(7);
        when(fixture.room.getHabboItem(7)).thenReturn(item);
        when(fixture.runtime.setGravityEnabled(item, true)).thenReturn(true);
        when(fixture.runtime.isGravityEnabled(item)).thenReturn(true);

        WiredFurniRuntimeStateRequestEvent event = event(fixture, 7, 1, "@gravity", 1);
        event.handle();

        verify(fixture.runtime).setGravityEnabled(item, true);
        verify(fixture.client).sendResponse(any(MessageComposer.class));
        assertEquals(100, event.getRatelimit());
    }

    @Test
    void malformedAndUnknownActionsDoNotMutate() throws Exception {
        Fixture fixture = fixture(true, true);
        HabboItem item = floorItem(1);
        when(fixture.room.getHabboItem(1)).thenReturn(item);

        event(fixture, 1, 99, "@gravity", 1).handle();
        WiredFurniRuntimeStateRequestEvent truncated = new WiredFurniRuntimeStateRequestEvent();
        truncated.client = fixture.client;
        truncated.packet = new ClientMessage(0, Unpooled.buffer().writeInt(1));
        truncated.handle();

        verify(fixture.runtime, never()).setGravityEnabled(any(HabboItem.class), anyBoolean());
    }

    private static WiredFurniRuntimeStateRequestEvent event(
            Fixture fixture, int itemId, int action, String key, int value) {
        WiredFurniRuntimeStateRequestEvent event = new WiredFurniRuntimeStateRequestEvent();
        event.client = fixture.client;
        event.packet = packet(itemId, action, key, value);
        return event;
    }

    private static ClientMessage packet(int itemId, int action, String key, int value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(itemId);
        buffer.writeInt(action);
        buffer.writeShort(keyBytes.length);
        buffer.writeBytes(keyBytes);
        buffer.writeInt(value);
        return new ClientMessage(0, buffer);
    }

    private static Fixture fixture(boolean canInspect, boolean canModify) {
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(room.canInspectWired(habbo)).thenReturn(canInspect);
        when(room.canModifyWired(habbo)).thenReturn(canModify);
        return new Fixture(client, room, runtime);
    }

    private static HabboItem floorItem(int id) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }

    private record Fixture(GameClient client, Room room, RoomWiredRuntime runtime) {}
}
