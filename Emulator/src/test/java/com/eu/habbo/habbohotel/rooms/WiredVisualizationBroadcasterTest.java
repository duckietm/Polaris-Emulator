package com.eu.habbo.habbohotel.rooms;

import static com.eu.habbo.habbohotel.rooms.WiredVisualizationServiceTest.item;
import static com.eu.habbo.habbohotel.rooms.WiredVisualizationServiceTest.room;
import static com.eu.habbo.habbohotel.rooms.WiredVisualizationServiceTest.unit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeOpacity;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSetHighlight;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.outgoing.MessageComposer;
import io.netty.buffer.ByteBuf;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredVisualizationBroadcasterTest {

    @Test
    void anEveryoneHighlightReachesTheWholeRoomForFurniAndTargetUsers() throws Exception {
        Room room = room(71);
        RoomWiredRuntime runtime = new RoomWiredRuntime(room);
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem wall = item(room, 7101, FurnitureType.WALL);
        RoomUnit actor = unit(room, 6);
        Habbo first = habbo(1);
        Habbo second = habbo(2);
        when(room.getHabbos()).thenReturn(List.of(first, second));

        WiredEffectSetHighlight box = highlightBox(room, 1, 4, 38, 0x123456, 0, 0, true, 7101);
        box.execute(context(room, actor));

        for (Habbo viewer : List.of(first, second)) {
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
        Habbo audience = habbo(5);
        Habbo bystander = habbo(6);
        when(room.getHabbo(actor)).thenReturn(audience);
        when(room.getHabbos()).thenReturn(List.of(audience, bystander));

        highlightBox(room, 0, 5, 40, 0x00FF00, 1, 0, false, 7201).execute(context(room, actor));

        assertEquals(1, sent(audience).size());
        verify(bystander.getClient(), never()).sendResponse(any(MessageComposer.class));

        Habbo rejoined = habbo(5);
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

        Habbo stranger = habbo(9);
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
        Habbo viewer = habbo(4);
        when(room.getHabbos()).thenReturn(List.of(viewer));

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

        Habbo entering = habbo(10);
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

    private static Habbo habbo(int userId) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(client);
        when(info.getId()).thenReturn(userId);
        return habbo;
    }

    /** Every packet the user was sent, each positioned just after its length. */
    private static List<ByteBuf> sent(Habbo habbo) {
        ArgumentCaptor<MessageComposer> captor = ArgumentCaptor.forClass(MessageComposer.class);
        verify(habbo.getClient(), atLeast(1)).sendResponse(captor.capture());
        List<ByteBuf> packets = new ArrayList<>();
        for (MessageComposer composer : captor.getAllValues()) {
            ByteBuf packet = composer.compose().get();
            packet.skipBytes(4);
            packets.add(packet);
        }
        return packets;
    }
}
