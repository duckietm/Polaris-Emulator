package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomWiredRuntime;
import polaris.hotel.rooms.WiredOpacityState;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;
import polaris.messages.outgoing.MessageComposer;

class WiredEffectChangeOpacityTest {

    @Test
    void globalExecutionUpdatesRoomStateButOnlySendsToCapableClients() throws Exception {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = runtime(room);
        when(room.getId()).thenReturn(51);
        UserItem target = item(5101, 51);
        when(room.getUserItem(5101)).thenReturn(target);
        WiredOpacityState state = new WiredOpacityState(5101, false, 40, true);
        when(runtime.applyGlobalOpacity(any(), eq(40), eq(true))).thenReturn(List.of(state));
        when(runtime.effectiveOpacity(1, List.of(5101))).thenReturn(List.of(state));

        User capable = user(1, true);
        User legacy = user(2, false);
        when(room.getUsers()).thenReturn(List.of(capable, legacy));
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
        UserItem target = item(5201, 52);
        when(room.getUserItem(5201)).thenReturn(target);
        RoomUnit actor = mock(RoomUnit.class);
        User recipient = user(7, true);
        when(room.getUser(actor)).thenReturn(recipient);
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
    void targetUsersMakesTheAvatarsTransparentAndLeavesTheFurniAlone() throws Exception {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = runtime(room);
        when(room.getId()).thenReturn(53);
        UserItem target = item(5301, 53);
        when(room.getUserItem(5301)).thenReturn(target);
        RoomUnit actor = mock(RoomUnit.class);
        WiredEffectChangeOpacity effect = effect(target, room, 1, 0, 0, 0, 100, 0, false, true);

        assertTrue(effect.requiresTriggeringUser());
        effect.execute(context(room, actor, effect));

        verify(runtime, never()).applyGlobalOpacity(any(), anyInt(), anyBoolean());
        verify(runtime, never()).applyUserOpacity(anyInt(), any(), anyInt(), anyBoolean());
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
        UserItem target = item(5401, 54);
        when(room.getUserItem(5401)).thenReturn(target);
        WiredOpacityState state = new WiredOpacityState(5401, false, 40, false);
        when(runtime.applyGlobalOpacity(any(), eq(40), eq(false))).thenReturn(List.of(state));
        when(runtime.effectiveOpacity(1, List.of(5401))).thenReturn(List.of(state));
        User capable = user(1, true);
        when(room.getUsers()).thenReturn(List.of(capable));
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
        UserItem target = item(5301, 53);
        when(room.getUserItem(5301)).thenReturn(target);
        WiredEffectChangeOpacity effect = effect(target, room, 1, 40, 0, 1, 100, 0, false);

        ResultSet malformed = mock(ResultSet.class);
        when(malformed.getString("wired_data")).thenReturn("{broken");
        assertDoesNotThrow(() -> effect.loadWiredData(malformed, room));
        assertTrue(effect.requiresTriggeringUser());
    }

    @Test
    void theDialogGetsTheOfficialLayoutWithTheTransparencyAndOurSecondsLast() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(55);
        UserItem target = item(5501, 55);
        when(room.getUserItem(5501)).thenReturn(target);
        WiredEffectChangeOpacity effect = effect(target, room, 1, 35, 2, 3, 100, 201, true);

        var body = WiredEffectProgressAchievementTest.body(effect);

        // official [audience, transparency (100 - our opacity), click through, easing, target users]
        assertArrayEquals(new int[] {1, 65, 1, 2, 0, 3}, body.params());
        assertArrayEquals(new int[] {100}, effect.officialFurniSources());
        assertArrayEquals(new int[] {201, 0}, effect.officialUserSources());
    }

    private static WiredEffectChangeOpacity effect(
            UserItem target,
            Room room,
            int visibility,
            int opacity,
            int easing,
            int duration,
            int furniSource,
            int userSource,
            boolean clickThrough)
            throws Exception {
        return effect(
                target, room, visibility, opacity, easing, duration, furniSource, userSource, clickThrough, false);
    }

    private static WiredEffectChangeOpacity effect(
            UserItem target,
            Room room,
            int visibility,
            int opacity,
            int easing,
            int duration,
            int furniSource,
            int userSource,
            boolean clickThrough,
            boolean targetUsers)
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
                        + ",\"clickThrough\":" + clickThrough + ",\"targetUsers\":" + targetUsers
                        + ",\"targetUserSource\":0,\"delay\":0,\"itemIds\":["
                        + targetId + "]}");
        effect.loadWiredData(set, room);
        return effect;
    }

    private static WiredContext context(Room room, RoomUnit actor, UserItem trigger) {
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

    private static UserItem item(int itemId, int roomId) {
        UserItem item = mock(UserItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getRoomId()).thenReturn(roomId);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }

    private static User user(int userId, boolean supportsOpacity) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GameClient client = mock(GameClient.class);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(client);
        when(info.getId()).thenReturn(userId);
        when(client.supportsWiredFeature(anyInt(), anyInt())).thenReturn(supportsOpacity);
        return user;
    }
}
