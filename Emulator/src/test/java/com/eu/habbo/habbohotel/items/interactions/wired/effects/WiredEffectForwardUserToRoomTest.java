package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionInformationTerminal;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredRoomLinker;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.PendingRoomEntry;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Teleport to room: a typed room, or the room a picked room link or teleporter leads to. */
class WiredEffectForwardUserToRoomTest {

    private static final int HERE = 1;

    private MockedStatic<WiredPlatform> platform;
    private final Map<Integer, Room> rooms = new HashMap<>();
    private ItemManager itemManager;
    private Room room;

    @BeforeEach
    void installHotel() {
        this.room = mock(Room.class);
        when(this.room.getId()).thenReturn(HERE);
        this.rooms.put(HERE, this.room);
        // Boxes and furni built here are in room 0, which is this room too.
        this.rooms.put(0, this.room);

        RoomManager roomManager = mock(RoomManager.class);
        when(roomManager.getRoom(anyInt())).thenAnswer(invocation -> this.rooms.get(invocation.getArgument(0)));
        when(roomManager.loadRoom(anyInt())).thenAnswer(invocation -> this.rooms.get(invocation.getArgument(0)));
        this.itemManager = mock(ItemManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(roomManager);
        when(environment.getItemManager()).thenReturn(this.itemManager);

        this.platform = mockStatic(WiredPlatform.class);
        this.platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
    }

    @AfterEach
    void closeHotel() {
        this.platform.close();
    }

    private Room target(int id, RoomState state) {
        Room target = mock(Room.class);
        when(target.getId()).thenReturn(id);
        when(target.getState()).thenReturn(state);
        this.rooms.put(id, target);
        return target;
    }

    private Habbo visitor(RoomUnit unit) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getCurrentRoom()).thenReturn(this.room);
        when(info.tryAcquireRoomForward(anyLong(), anyLong())).thenReturn(true);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        when(this.room.getHabbo(unit)).thenReturn(habbo);
        return habbo;
    }

    private static WiredEffectForwardUserToRoom box() {
        return new WiredEffectForwardUserToRoom(3, 1, base(), "", 0, 0);
    }

    private static WiredSettings settings(String roomId, int[] furniIds, int... params) {
        return new WiredSettings(params, roomId, furniIds, 0);
    }

    private static int forwardedRoom(Habbo habbo) {
        ArgumentCaptor<ForwardToRoomComposer> sent = ArgumentCaptor.forClass(ForwardToRoomComposer.class);
        verify(habbo.getClient()).sendResponse(sent.capture());
        return sent.getValue().compose().get().skipBytes(6).readInt();
    }

    private static PendingRoomEntry pendingEntry(Habbo habbo) {
        ArgumentCaptor<PendingRoomEntry> entry = ArgumentCaptor.forClass(PendingRoomEntry.class);
        verify(habbo.getHabboInfo()).setPendingRoomEntry(entry.capture());
        return entry.getValue();
    }

    @Test
    void aRoomIdIsPlainPositiveDigits() {
        assertEquals(123, WiredEffectForwardUserToRoom.parseRoomId(" 123 "));
        assertEquals(0, WiredEffectForwardUserToRoom.parseRoomId("0"));
        assertEquals(0, WiredEffectForwardUserToRoom.parseRoomId("-5"));
        assertEquals(0, WiredEffectForwardUserToRoom.parseRoomId("+5"));
        assertEquals(0, WiredEffectForwardUserToRoom.parseRoomId("12a"));
        assertEquals(0, WiredEffectForwardUserToRoom.parseRoomId("99999999999"));
        assertEquals(0, WiredEffectForwardUserToRoom.parseRoomId(null));
    }

    @Test
    void aBoxSavedBeforeTheFurniSourceStillSendsToItsTypedRoom() throws Exception {
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"77\",\"delay\":2,\"userSource\":0}"), this.room);

        assertEquals("77", json(box).get("roomIdText").getAsString());
        assertEquals(
                WiredSourceUtil.SOURCE_SELECTED, json(box).get("furniSource").getAsInt());
        assertEquals(2, box.getDelay());
        assertEquals(WiredEffectType.TELEPORT_TO_ROOM, box.getType());

        target(77, RoomState.OPEN);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = visitor(unit);
        box.execute(context(this.room, unit));

        assertEquals(77, forwardedRoom(habbo));
        assertNull(pendingEntry(habbo));
    }

    @Test
    void savesATypedRoomAndRefusesWhatIsNoRoom() throws Exception {
        WiredEffectForwardUserToRoom box = box();

        assertTrue(box.saveData(settings(" 42 ", new int[0], 0), null));
        assertEquals("42", json(box).get("roomIdText").getAsString());
        assertFalse(box.saveData(settings("lobby", new int[0], 0), null));
        assertFalse(box.saveData(settings("", new int[0], 0, WiredSourceUtil.SOURCE_SELECTED), null));
        assertTrue(box.saveData(settings("", new int[0], 0, WiredSourceUtil.SOURCE_SIGNAL), null));
        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, json(box).get("furniSource").getAsInt());
    }

    @Test
    void onlyRoomLinkersRoomLinksAndTeleportersCanBePicked() throws Exception {
        HabboItem chair = new InteractionDefault(5, 1, base(), "", 0, 0);
        InteractionTeleport teleport = new InteractionTeleport(6, 1, base(), "", 0, 0);
        InteractionWiredRoomLinker linker = new InteractionWiredRoomLinker(8, 1, base(), "", 0, 0);
        when(this.room.getHabboItem(5)).thenReturn(chair);
        when(this.room.getHabboItem(6)).thenReturn(teleport);
        when(this.room.getHabboItem(8)).thenReturn(linker);
        WiredEffectForwardUserToRoom box = box();

        WiredSaveException refused = assertThrows(
                WiredSaveException.class,
                () -> box.saveData(settings("", new int[] {5}, 0, WiredSourceUtil.SOURCE_SELECTED), null));
        assertEquals("wiredfurni.error.require_room_linker", refused.getMessage());
        assertTrue(box.saveData(settings("", new int[] {6}, 0, WiredSourceUtil.SOURCE_SELECTED), null));
        assertEquals(6, json(box).getAsJsonArray("itemIds").get(0).getAsInt());
        assertTrue(box.saveData(settings("", new int[] {8}, 0, WiredSourceUtil.SOURCE_SELECTED), null));
        assertEquals(8, json(box).getAsJsonArray("itemIds").get(0).getAsInt());
    }

    @Test
    void aPickedRoomLinkerSendsToTheRoomOfItsPairToArriveOnThePair() throws Exception {
        InteractionWiredRoomLinker linker = new InteractionWiredRoomLinker(12, 1, base(), "", 0, 0);
        when(this.room.getHabboItem(12)).thenReturn(linker);
        when(this.itemManager.getTargetTeleportRoomId(linker)).thenReturn(new int[] {88, 702});
        target(88, RoomState.OPEN);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"\",\"delay\":0,\"userSource\":0,\"itemIds\":[12]}"), this.room);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = visitor(unit);

        box.execute(context(this.room, unit));

        assertEquals(88, forwardedRoom(habbo));
        PendingRoomEntry entry = pendingEntry(habbo);
        assertEquals(PendingRoomEntry.METHOD_TELEPORT, entry.method());
        assertEquals(702, entry.teleportItemId());
    }

    @Test
    void aPickedRoomLinkSendsToItsRoomAsARoomNetworkEntry() throws Exception {
        InteractionInformationTerminal link = new InteractionInformationTerminal(9, 1, base(), "", 0, 0);
        link.values.put("internalLink", "55");
        when(this.room.getHabboItem(9)).thenReturn(link);
        target(55, RoomState.LOCKED);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(
                row("{\"roomIdText\":\"77\",\"delay\":0,\"userSource\":0,\"furniSource\":100,\"itemIds\":[9]}"),
                this.room);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = visitor(unit);

        box.execute(context(this.room, unit));

        assertEquals(55, forwardedRoom(habbo));
        PendingRoomEntry entry = pendingEntry(habbo);
        assertEquals(PendingRoomEntry.METHOD_ROOM_NETWORK, entry.method());
        assertEquals(55, entry.roomId());
    }

    @Test
    void aPickedTeleporterSendsToItsPairsRoomToArriveOnThePair() throws Exception {
        InteractionTeleport teleport = new InteractionTeleport(10, 1, base(), "", 0, 0);
        when(this.room.getHabboItem(10)).thenReturn(teleport);
        when(this.itemManager.getTargetTeleportRoomId(teleport)).thenReturn(new int[] {66, 501});
        target(66, RoomState.OPEN);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"\",\"delay\":0,\"userSource\":0,\"itemIds\":[10]}"), this.room);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = visitor(unit);

        box.execute(context(this.room, unit));

        assertEquals(66, forwardedRoom(habbo));
        PendingRoomEntry entry = pendingEntry(habbo);
        assertEquals(PendingRoomEntry.METHOD_TELEPORT, entry.method());
        assertEquals(501, entry.teleportItemId());

        // Straight after, the pair comes from the box's short cache instead of the database.
        box.execute(context(this.room, unit));
        verify(this.itemManager, times(1)).getTargetTeleportRoomId(any());
    }

    @Test
    void nobodyIsSentWhereTheNavigatorWouldNotLetThemInOrTooOften() throws Exception {
        Room banned = target(70, RoomState.OPEN);
        target(71, RoomState.INVISIBLE);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = visitor(unit);
        when(banned.isBanned(habbo)).thenReturn(true);
        when(habbo.hasPermission(anyString())).thenReturn(false);

        for (String roomId : new String[] {"70", "71", String.valueOf(HERE)}) {
            WiredEffectForwardUserToRoom box = box();
            box.loadWiredData(row("{\"roomIdText\":\"" + roomId + "\",\"delay\":0,\"userSource\":0}"), this.room);
            box.execute(context(this.room, unit));
        }
        verify(habbo.getClient(), never()).sendResponse(any(ForwardToRoomComposer.class));

        target(72, RoomState.OPEN);
        when(habbo.getHabboInfo().tryAcquireRoomForward(anyLong(), anyLong())).thenReturn(false);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"72\",\"delay\":0,\"userSource\":0}"), this.room);
        box.execute(context(this.room, unit));
        verify(habbo.getClient(), never()).sendResponse(any(ForwardToRoomComposer.class));
    }
}
