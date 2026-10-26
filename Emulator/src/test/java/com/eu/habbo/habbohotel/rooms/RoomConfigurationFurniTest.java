package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHideControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionConfInvisControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionHanditemBlockControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionQueueSpeedControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionRemoteSwitchControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredDisableControl;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The five official room configuration furni: switches, queue speed modes and pickup refreshes. */
class RoomConfigurationFurniTest {

    @Test
    void aSwitchWithAModesCountOfOneStillTurnsOnAndOff() throws Exception {
        RecordingRoom room = roomWithLayout();
        InteractionHanditemBlockControl blocker =
                new InteractionHanditemBlockControl(1001, 7, baseItem("wf_conf_handitem_block", 1), "0", 0, 0);
        blocker.setRoomId(41);
        room.floorItems = Set.of(blocker);

        blocker.onClick(null, room, new Object[] {0});
        assertEquals("1", blocker.getExtradata());
        assertTrue(RoomHanditemBlockSupport.isHanditemBlocked(room));
        assertEquals(List.of(Outgoing.ItemStateComposer, Outgoing.HanditemBlockStateComposer), room.messageHeaders());

        blocker.onClick(null, room, new Object[] {0});
        assertEquals("0", blocker.getExtradata());
        assertFalse(RoomHanditemBlockSupport.isHanditemBlocked(room));
    }

    @Test
    void theSwitchStatesAreBinary() {
        assertEquals("1", InteractionRemoteSwitchControl.nextSwitchState("0"));
        assertEquals("1", InteractionRemoteSwitchControl.nextSwitchState(""));
        assertEquals("1", InteractionRemoteSwitchControl.nextSwitchState("2"));
        assertEquals("0", InteractionRemoteSwitchControl.nextSwitchState("1"));
    }

    @Test
    void aVisitorWithoutRightsCannotToggleASwitch() throws Exception {
        RecordingRoom room = roomWithLayout();
        InteractionWiredDisableControl disabler =
                new InteractionWiredDisableControl(1002, 7, baseItem("wf_conf_wired_disable", 1), "0", 0, 0);
        disabler.setRoomId(41);
        room.floorItems = Set.of(disabler);

        disabler.onClick(visitor(), room, new Object[] {0});

        assertEquals("0", disabler.getExtradata());
        assertEquals(List.of(), room.messageHeaders());
    }

    @Test
    void theWiredToggleStateEffectFlipsASwitchWithoutRights() throws Exception {
        RecordingRoom room = roomWithLayout();
        InteractionConfInvisControl invis =
                new InteractionConfInvisControl(1003, 7, baseItem("wf_conf_invis_control", 1), "0", 0, 0);
        invis.setRoomId(41);
        room.floorItems = Set.of(invis);

        invis.onClick(visitor(), room, new Object[] {0, WiredEffectType.TOGGLE_STATE});

        assertEquals("1", invis.getExtradata());
        assertTrue(RoomConfInvisSupport.hasActiveController(room));
        assertTrue(room.messageHeaders().contains(Outgoing.ConfInvisStateComposer));
    }

    @Test
    void theQueueSpeedControllerStepsWholeModesBothWays() {
        assertEquals(4, InteractionQueueSpeedControl.MODE_COUNT);
        assertEquals(12, InteractionQueueSpeedControl.STATE_COUNT);
        assertEquals(0, InteractionQueueSpeedControl.toRollerSpeed("2"));
        assertEquals(1, InteractionQueueSpeedControl.toRollerSpeed("5"));
        assertEquals(3, InteractionQueueSpeedControl.toRollerSpeed("11"));
        assertEquals(500, InteractionQueueSpeedControl.toRollerIntervalMs("0"));
        assertEquals(2000, InteractionQueueSpeedControl.toRollerIntervalMs("9"));
    }

    @Test
    void anActiveAreaHiderLeavingTheRoomLiftsItsArea() throws Exception {
        RecordingRoom room = roomWithLayout();
        InteractionAreaHideControl hider = new InteractionAreaHideControl(
                1004,
                7,
                baseItem("wf_conf_area_hide", 2),
                "state=1\trootX=2\trootY=3\twidth=4\tlength=5\tinvisibility=0\twallItems=1\tinvert=0",
                0,
                0);
        hider.values.put("state", "1");
        hider.values.put("rootX", "2");
        hider.values.put("rootY", "3");
        hider.values.put("width", "4");
        hider.values.put("length", "5");

        RoomConfigurationFurniSupport.onControllerRemoved(room, hider);

        assertEquals(List.of(Outgoing.AreaHideComposer), room.messageHeaders());
        ByteBuf buffer = room.messages.get(0).get();
        buffer.readerIndex(6);
        assertEquals(1004, buffer.readInt());
        assertFalse(buffer.readBoolean(), "the area goes off");
        assertEquals(2, buffer.readInt());
    }

    @Test
    void anActiveHanditemBlockerLeavingTheRoomUnblocksTheRoom() throws Exception {
        RecordingRoom room = roomWithLayout();
        HabboItem blocker = mockItem("wf_conf_handitem_block", "1");
        room.floorItems = Set.of();

        RoomConfigurationFurniSupport.onControllerRemoved(room, blocker);

        assertEquals(List.of(Outgoing.HanditemBlockStateComposer), room.messageHeaders());
    }

    @Test
    void anActiveInvisibleFurniControllerLeavingTheRoomShowsTheFurniAgain() throws Exception {
        RecordingRoom room = roomWithLayout();
        HabboItem controller = mockItem("wf_conf_invis_control", "1");
        room.floorItems = Set.of();

        RoomConfigurationFurniSupport.onControllerRemoved(room, controller);

        assertEquals(List.of(Outgoing.ConfInvisStateComposer), room.messageHeaders());
    }

    @Test
    void anOffControllerLeavingTheRoomSendsNothing() throws Exception {
        RecordingRoom room = roomWithLayout();

        RoomConfigurationFurniSupport.onControllerRemoved(room, mockItem("wf_conf_handitem_block", "0"));
        RoomConfigurationFurniSupport.onControllerRemoved(room, mockItem("wf_conf_invis_control", "0"));
        RoomConfigurationFurniSupport.onControllerRemoved(room, null);

        assertEquals(List.of(), room.messageHeaders());
    }

    @Test
    void pickupAndHandItemDropUseTheConfigurationRules() throws Exception {
        String pickup = source("com/eu/habbo/habbohotel/rooms/RoomItemOwnershipService.java");
        String drop = source("com/eu/habbo/messages/incoming/rooms/users/RoomUserDropHandItemEvent.java");
        String toggle = source("com/eu/habbo/habbohotel/items/interactions/wired/effects/WiredEffectToggleFurni.java");

        assertTrue(pickup.contains("RoomConfigurationFurniSupport.onControllerRemoved(this.room, item);"));
        assertTrue(drop.indexOf("RoomHanditemBlockSupport.isHanditemBlocked(room)") < drop.indexOf("setHandItem(0)"));
        assertTrue(toggle.contains("queueSpeed.wiredStepMode(room, this.toggleType != TOGGLE_TYPE_PREVIOUS)"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    private static GameClient visitor() {
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboStats stats = mock(HabboStats.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(stats.isRentingSpace()).thenReturn(false);
        return client;
    }

    private static Item baseItem(String interactionName, int stateCount) {
        Item baseItem = mock(Item.class);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(baseItem.getStateCount()).thenReturn(stateCount);
        when(baseItem.getInteractionType()).thenReturn(new ItemInteraction(interactionName, HabboItem.class));
        return baseItem;
    }

    private static HabboItem mockItem(String interactionName, String extradata) {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = baseItem(interactionName, 2);
        when(item.getId()).thenReturn(1005);
        when(item.getRoomId()).thenReturn(41);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(item.getExtradata()).thenReturn(extradata);
        return item;
    }

    private static RecordingRoom roomWithLayout() throws Exception {
        RecordingRoom room = new RecordingRoom();
        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTilesAt(nullable(RoomTile.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(Set.of());
        Field field = Room.class.getDeclaredField("layout");
        field.setAccessible(true);
        field.set(room, layout);
        return room;
    }

    private static final class RecordingRoom extends Room {
        private final List<ServerMessage> messages = new ArrayList<>();
        private Set<HabboItem> floorItems = Set.of();

        private RecordingRoom() {
            super(41, 7);
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public boolean hasRights(Habbo habbo) {
            return false;
        }

        @Override
        public Set<HabboItem> getFloorItems() {
            return this.floorItems;
        }

        @Override
        public Collection<Habbo> getHabbos() {
            return Set.of();
        }

        @Override
        public void updateTiles(Collection<RoomTile> tiles) {}

        @Override
        public void sendComposer(ServerMessage message) {
            this.messages.add(message);
        }

        private List<Integer> messageHeaders() {
            return this.messages.stream().map(ServerMessage::getHeader).toList();
        }
    }
}
