package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.rooms.WiredVisualizationServiceTest.item;
import static polaris.hotel.rooms.WiredVisualizationServiceTest.room;
import static polaris.hotel.rooms.WiredVisualizationServiceTest.unit;

import io.netty.buffer.ByteBuf;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.effects.WiredEffectChangeOpacity;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSetHighlight;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;
import polaris.messages.outgoing.MessageComposer;

class WiredVisualizationBroadcasterTest {

    @Test
    void anEveryoneHighlightReachesTheWholeRoomForFurniAndTargetUsers() throws Exception {
        Room room = room(71);
        RoomWiredRuntime runtime = new RoomWiredRuntime(room);
        when(room.getWiredRuntime()).thenReturn(runtime);
        UserItem wall = item(room, 7101, FurnitureType.WALL);
        RoomUnit actor = unit(room, 6);
        User first = user(1);
        User second = user(2);
        when(room.getUsers()).thenReturn(List.of(first, second));

        WiredEffectSetHighlight box = highlightBox(room, 1, 4, 38, 0x123456, 0, 0, true, 7101);
        box.execute(context(room, actor));

        for (User viewer : List.of(first, second)) {
            List<ByteBuf> packets = sent(viewer);
            assertEquals(2, packets.size());
            ByteBuf furni = packets.get(0);
            assertEquals(9468, furni.readShort());
            assertEquals(1, furni.readInt());
            assertEquals(-7101, furni.readInt());
            assertEquals(4, furni.readInt());
            assertEquals(76, furni.readInt());
            assertEquals(0xFFFFFF, furni.readInt());
            assertEquals(1, furni.readInt());
            assertEquals(0, furni.readInt());
            ByteBuf users = packets.get(1);
            assertEquals(1684, users.readShort());
            assertEquals(1, users.readInt());
            assertEquals(6, users.readInt());
        }
    }

    @Test
    void aSourceUsersHighlightOnlyReachesTheAudienceAndIsReplayedToThemAlone() throws Exception {
        Room room = room(72);
        RoomWiredRuntime runtime = new RoomWiredRuntime(room);
        when(room.getWiredRuntime()).thenReturn(runtime);
        item(room, 7201, FurnitureType.FLOOR);
        RoomUnit actor = unit(room, 3);
        User audience = user(5);
        User bystander = user(6);
        when(room.getUser(actor)).thenReturn(audience);
        when(room.getUsers()).thenReturn(List.of(audience, bystander));

        highlightBox(room, 0, 5, 40, 0x00FF00, 1, 0, false, 7201).execute(context(room, actor));

        assertEquals(1, sent(audience).size());
        verify(bystander.getClient(), never()).sendResponse(any(MessageComposer.class));

        User rejoined = user(5);
        WiredVisualizationBroadcaster.sendState(room, rejoined);
        List<ByteBuf> replay = sent(rejoined);
        ByteBuf furni = replay.get(0);
        assertEquals(1826, furni.readShort());
        assertEquals(0, furni.readInt());
        assertEquals(1, furni.readInt());
        assertEquals(7201, furni.readInt());
        assertEquals(5, furni.readInt());
        assertEquals(40, furni.readInt());
        ByteBuf users = replay.get(1);
        assertEquals(1454, users.readShort());
        assertEquals(0, users.readInt());
        assertEquals(0, users.readInt());

        User stranger = user(9);
        WiredVisualizationBroadcaster.sendState(room, stranger);
        ByteBuf strangerFurni = sent(stranger).get(0);
        strangerFurni.readShort();
        assertEquals(0, strangerFurni.readInt());
        assertEquals(0, strangerFurni.readInt());
        assertFalse(strangerFurni.isReadable());
    }

    @Test
    void theTransparencyBoxNowMakesItsTargetUsersTransparentToo() throws Exception {
        Room room = room(73);
        RoomWiredRuntime runtime = new RoomWiredRuntime(room);
        when(room.getWiredRuntime()).thenReturn(runtime);
        RoomUnit actor = unit(room, 8);
        User viewer = user(4);
        when(room.getUsers()).thenReturn(List.of(viewer));

        WiredEffectChangeOpacity box = new WiredEffectChangeOpacity(7399, 1, boxBase(), "0", 0, 0);
        box.setRoomId(room.getId());
        box.loadWiredData(
                row("{\"visibility\":1,\"opacity\":30,\"easing\":2,\"clickThrough\":true,"
                        + "\"furniSource\":100,\"userSource\":0,\"targetUsers\":true,\"targetUserSource\":0}"),
                room);
        box.execute(context(room, actor));

        ByteBuf transparency = sent(viewer).get(0);
        assertEquals(3664, transparency.readShort());
        assertEquals(1, transparency.readInt());
        assertEquals(8, transparency.readInt());
        assertEquals(70, transparency.readInt());
        assertEquals(true, transparency.readBoolean());
        assertEquals(2, transparency.readInt());

        User entering = user(10);
        WiredVisualizationBroadcaster.sendState(room, entering);
        ByteBuf users = sent(entering).get(1);
        assertEquals(1454, users.readShort());
        assertEquals(1, users.readInt());
        assertEquals(8, users.readInt());
        assertEquals(70, users.readInt());
    }

    private static WiredEffectSetHighlight highlightBox(
            Room room,
            int audience,
            int type,
            int strength,
            int color,
            int animation,
            int easing,
            boolean targetUsers,
            int itemId)
            throws Exception {
        WiredEffectSetHighlight box = new WiredEffectSetHighlight(7999, 1, boxBase(), "0", 0, 0);
        box.setRoomId(room.getId());
        box.loadWiredData(
                row("{\"audience\":" + audience + ",\"highlightType\":" + type + ",\"strength\":" + strength
                        + ",\"color\":" + color + ",\"animation\":" + animation + ",\"easing\":" + easing
                        + ",\"targetUsers\":" + targetUsers + ",\"furniSource\":100,\"userSource\":0"
                        + ",\"targetUserSource\":0,\"itemIds\":[" + itemId + "]}"),
                room);
        return box;
    }

    private static Item boxBase() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        return base;
    }

    private static ResultSet row(String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        return set;
    }

    private static WiredContext context(Room room, RoomUnit actor) {
        WiredEvent event =
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build();
        return new WiredContext(event, null, mock(WiredServices.class), new WiredState(20));
    }

    private static User user(int userId) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GameClient client = mock(GameClient.class);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(client);
        when(info.getId()).thenReturn(userId);
        return user;
    }

    /** Every packet the user was sent, each positioned just after its length. */
    private static List<ByteBuf> sent(User user) {
        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(user.getClient(), atLeast(1)).sendResponse(captor.capture());
        List<ByteBuf> packets = new ArrayList<>();
        for (MessageComposer composer : captor.getAllValues()) {
            ByteBuf packet = composer.compose().get();
            packet.skipBytes(4);
            packets.add(packet);
        }
        return packets;
    }
}
