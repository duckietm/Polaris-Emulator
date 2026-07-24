package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredEffectChangeOpacityTest {

    @Test
    void globalExecutionUpdatesRoomStateButOnlySendsToCapableClients() throws Exception {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = runtime(room);
        when(room.getId()).thenReturn(51);
        HabboItem target = item(5101, 51);
        when(room.getHabboItem(5101)).thenReturn(target);
        WiredOpacityState state = new WiredOpacityState(5101, false, 40, true);
        when(runtime.applyGlobalOpacity(any(), eq(40), eq(true))).thenReturn(List.of(state));
        when(runtime.effectiveOpacity(1, List.of(5101))).thenReturn(List.of(state));

        Habbo capable = habbo(1, true);
        Habbo legacy = habbo(2, false);
        when(room.getHabbos()).thenReturn(List.of(capable, legacy));
        WiredEffectChangeOpacity effect = effect(target, room, 1, 40, 2, 3, 100, 0, true);

        effect.execute(context(room, null, effect));

        verify(runtime).applyGlobalOpacity(any(), eq(40), eq(true));
        verify(capable.getClient()).sendResponse(any(MessageComposer.class));
        verify(legacy.getClient(), never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void perUserExecutionRemainsPrivateAndRequiresAnActorForTriggerSource() throws Exception {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = runtime(room);
        when(room.getId()).thenReturn(52);
        HabboItem target = item(5201, 52);
        when(room.getHabboItem(5201)).thenReturn(target);
        RoomUnit actor = mock(RoomUnit.class);
        Habbo recipient = habbo(7, true);
        when(room.getHabbo(actor)).thenReturn(recipient);
        WiredOpacityState state = new WiredOpacityState(5201, false, 15, false);
        when(runtime.applyUserOpacity(eq(7), any(), eq(15), eq(false))).thenReturn(List.of(state));
        WiredEffectChangeOpacity effect = effect(target, room, 0, 15, 0, 1, 100, 0, false);

        assertTrue(effect.requiresTriggeringUser());
        effect.execute(context(room, actor, effect));

        verify(runtime).applyUserOpacity(eq(7), any(), eq(15), eq(false));
        verify(recipient.getClient()).sendResponse(any(MessageComposer.class));
        verify(runtime, never()).applyGlobalOpacity(any(), anyInt(), anyBoolean());
    }

    @Test
    void defaultDurationResolvesTo400msAndExplicitDurationStaysExact() throws Exception {
        assertEquals(400, animatedDurationMs(0));
        assertEquals(400, animatedDurationMs(-5));
        assertEquals(3_000, animatedDurationMs(3));
        assertEquals(10_000, animatedDurationMs(44));
    }

    private static int animatedDurationMs(int savedDurationSeconds) throws Exception {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = runtime(room);
        when(room.getId()).thenReturn(54);
        HabboItem target = item(5401, 54);
        when(room.getHabboItem(5401)).thenReturn(target);
        WiredOpacityState state = new WiredOpacityState(5401, false, 40, false);
        when(runtime.applyGlobalOpacity(any(), eq(40), eq(false))).thenReturn(List.of(state));
        when(runtime.effectiveOpacity(1, List.of(5401))).thenReturn(List.of(state));
        Habbo capable = habbo(1, true);
        when(room.getHabbos()).thenReturn(List.of(capable));
        WiredEffectChangeOpacity effect = effect(target, room, 1, 40, 2, savedDurationSeconds, 100, 0, false);

        effect.execute(context(room, null, effect));

        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(capable.getClient()).sendResponse(captor.capture());
        Field durationField = captor.getValue().getClass().getDeclaredField("durationMs");
        durationField.setAccessible(true);
        return durationField.getInt(captor.getValue());
    }

    @Test
    void malformedPersistenceFailsClosed() throws Exception {
        Room room = mock(Room.class);
        runtime(room);
        when(room.getId()).thenReturn(53);
        HabboItem target = item(5301, 53);
        when(room.getHabboItem(5301)).thenReturn(target);
        WiredEffectChangeOpacity effect = effect(target, room, 1, 40, 0, 1, 100, 0, false);

        ResultSet malformed = mock(ResultSet.class);
        when(malformed.getString("wired_data")).thenReturn("{broken");
        assertDoesNotThrow(() -> effect.loadWiredData(malformed, room));
        assertTrue(effect.requiresTriggeringUser());
    }

    private static WiredEffectChangeOpacity effect(
            HabboItem target,
            Room room,
            int visibility,
            int opacity,
            int easing,
            int duration,
            int furniSource,
            int userSource,
            boolean clickThrough)
            throws Exception {
        Item boxBase = mock(Item.class);
        when(boxBase.getType()).thenReturn(FurnitureType.FLOOR);
        WiredEffectChangeOpacity effect = new WiredEffectChangeOpacity(5999, 1, boxBase, "0", 0, 0);
        effect.setRoomId(room.getId());
        ResultSet set = mock(ResultSet.class);
        int targetId = target.getId();
        when(set.getString("wired_data"))
                .thenReturn("{\"visibility\":" + visibility + ",\"opacity\":" + opacity
                        + ",\"easing\":" + easing + ",\"durationSeconds\":" + duration
                        + ",\"furniSource\":" + furniSource + ",\"userSource\":" + userSource
                        + ",\"clickThrough\":" + clickThrough + ",\"delay\":0,\"itemIds\":["
                        + targetId + "]}");
        effect.loadWiredData(set, room);
        return effect;
    }

    private static WiredContext context(Room room, RoomUnit actor, HabboItem trigger) {
        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.CUSTOM, room);
        if (actor != null) {
            builder.actor(actor);
        }
        return new WiredContext(builder.build(), trigger, mock(WiredServices.class), new WiredState(20));
    }

    private static RoomWiredRuntime runtime(Room room) {
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        return runtime;
    }

    private static HabboItem item(int itemId, int roomId) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getRoomId()).thenReturn(roomId);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }

    private static Habbo habbo(int userId, boolean supportsOpacity) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(client);
        when(info.getId()).thenReturn(userId);
        when(client.supportsWiredFeature(anyInt(), anyInt())).thenReturn(supportsOpacity);
        return habbo;
    }
}
