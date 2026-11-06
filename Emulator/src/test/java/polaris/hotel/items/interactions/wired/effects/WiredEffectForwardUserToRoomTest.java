package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.items.interactions.InteractionInformationTerminal;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionWiredRoomLinker;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.PendingRoomEntry;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.rooms.RoomState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;
import polaris.threading.ThreadPooling;

/** Teleport to room: a typed room, or the room a picked room link or teleporter leads to. */
class WiredEffectForwardUserToRoomTest {

    private static final int HERE = 1;

    private MockedStatic<WiredPlatform> platform;
    private final Map<Integer, Room> rooms = new HashMap<>();
    private ItemManager itemManager;
    private RoomManager roomManager;
    private Room room;

    @BeforeEach
    void installHotel() {
        this.room = mock(Room.class);
        when(this.room.getId()).thenReturn(HERE);
        this.rooms.put(HERE, this.room);
        // Boxes and furni built here are in room 0, which is this room too.
        this.rooms.put(0, this.room);

        RoomManager roomManager = mock(RoomManager.class);
        this.roomManager = roomManager;
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

    private User visitor(RoomUnit unit) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getCurrentRoom()).thenReturn(this.room);
        when(info.tryAcquireRoomForward(anyLong(), anyLong())).thenReturn(true);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        when(this.room.getUser(unit)).thenReturn(user);
        return user;
    }

    private static WiredEffectForwardUserToRoom box() {
        return new WiredEffectForwardUserToRoom(3, 1, base(), "", 0, 0);
    }

    private static WiredSettings settings(String roomId, int[] furniIds, int... params) {
        return new WiredSettings(params, roomId, furniIds, 0);
    }

    /** The official furni source slot. */
    private static WiredSettings furniSlot(WiredSettings settings, int source) {
        settings.setFurniSourceTypes(new int[] {source});
        return settings;
    }

    private static int forwardedRoom(User user) {
        ArgumentCaptor<ForwardToRoomComposer> sent = ArgumentCaptor.forClass(ForwardToRoomComposer.class);
        verify(user.getClient()).sendResponse(sent.capture());
        return sent.getValue().compose().get().skipBytes(6).readInt();
    }

    private static PendingRoomEntry pendingEntry(User user) {
        ArgumentCaptor<PendingRoomEntry> entry = ArgumentCaptor.forClass(PendingRoomEntry.class);
        verify(user.getUserInfo()).setPendingRoomEntry(entry.capture());
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
        User user = visitor(unit);
        box.execute(context(this.room, unit));

        assertEquals(77, forwardedRoom(user));
        assertNull(pendingEntry(user));
    }

    @Test
    void theDestinationIsNotLoadedWhenNobodyMayBeForwardedYet() throws Exception {
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"77\",\"delay\":0,\"userSource\":0}"), this.room);

        RoomUnit unit = mock(RoomUnit.class);
        User user = visitor(unit);
        when(user.getUserInfo().tryAcquireRoomForward(anyLong(), anyLong())).thenReturn(false);
        box.execute(context(this.room, unit));

        verify(this.roomManager, never()).loadRoom(77);
    }

    @Test
    void theUserGlowsFromTheFiringAndIsForwardedAboutASecondLater() throws Exception {
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"77\",\"delay\":0,\"userSource\":0}"), this.room);
        target(77, RoomState.OPEN);
        RoomUnit unit = mock(RoomUnit.class);
        User user = visitor(unit);
        when(user.getRoomUnit()).thenReturn(unit);
        ThreadPooling threading = mock(ThreadPooling.class);
        this.platform.when(WiredPlatform::threading).thenReturn(threading);
        ArgumentCaptor<Runnable> forward = ArgumentCaptor.forClass(Runnable.class);

        box.execute(context(this.room, unit));

        verify(this.room).sendComposer(any(ServerMessage.class));
        verify(user.getClient(), never()).sendResponse(any(ForwardToRoomComposer.class));
        verify(threading).run(forward.capture(), eq(WiredEffectForwardUserToRoom.FORWARD_DELAY_MS));

        forward.getValue().run();
        assertEquals(77, forwardedRoom(user));
        verify(user.getUserInfo()).setRoomForwardGlow(eq(77), anyLong());
    }

    @Test
    void savesATypedRoomAndRefusesWhatIsNoRoom() throws Exception {
        WiredEffectForwardUserToRoom box = box();

        assertTrue(box.saveData(settings(" 42 ", new int[0], 0), null));
        assertEquals("42", json(box).get("roomIdText").getAsString());
        assertFalse(box.saveData(settings("lobby", new int[0], 0), null));
        assertFalse(box.saveData(furniSlot(settings("", new int[0]), WiredSourceUtil.SOURCE_SELECTED), null));
        assertTrue(box.saveData(furniSlot(settings("", new int[0]), WiredSourceUtil.SOURCE_SIGNAL), null));
        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, json(box).get("furniSource").getAsInt());
    }

    @Test
    void onlyRoomLinkersRoomLinksAndTeleportersCanBePicked() throws Exception {
        UserItem chair = new InteractionDefault(5, 1, base(), "", 0, 0);
        InteractionTeleport teleport = new InteractionTeleport(6, 1, base(), "", 0, 0);
        InteractionWiredRoomLinker linker = new InteractionWiredRoomLinker(8, 1, base(), "", 0, 0);
        when(this.room.getUserItem(5)).thenReturn(chair);
        when(this.room.getUserItem(6)).thenReturn(teleport);
        when(this.room.getUserItem(8)).thenReturn(linker);
        WiredEffectForwardUserToRoom box = box();

        polaris.hotel.items.interactions.wired.WiredSaveRefusedException refused =
                org.junit.jupiter.api.Assertions.assertThrows(
                        polaris.hotel.items.interactions.wired.WiredSaveRefusedException.class,
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
        when(this.room.getUserItem(12)).thenReturn(linker);
        when(this.itemManager.getTargetTeleportRoomId(linker)).thenReturn(new int[] {88, 702});
        target(88, RoomState.OPEN);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"\",\"delay\":0,\"userSource\":0,\"itemIds\":[12]}"), this.room);
        RoomUnit unit = mock(RoomUnit.class);
        User user = visitor(unit);

        box.execute(context(this.room, unit));

        assertEquals(88, forwardedRoom(user));
        PendingRoomEntry entry = pendingEntry(user);
        assertEquals(PendingRoomEntry.METHOD_TELEPORT, entry.method());
        assertEquals(702, entry.teleportItemId());
    }

    @Test
    void aPickedRoomLinkSendsToItsRoomAsARoomNetworkEntry() throws Exception {
        InteractionInformationTerminal link = new InteractionInformationTerminal(9, 1, base(), "", 0, 0);
        link.values.put("internalLink", "55");
        when(this.room.getUserItem(9)).thenReturn(link);
        target(55, RoomState.LOCKED);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(
                row("{\"roomIdText\":\"77\",\"delay\":0,\"userSource\":0,\"furniSource\":100,\"itemIds\":[9]}"),
                this.room);
        RoomUnit unit = mock(RoomUnit.class);
        User user = visitor(unit);

        box.execute(context(this.room, unit));

        assertEquals(55, forwardedRoom(user));
        PendingRoomEntry entry = pendingEntry(user);
        assertEquals(PendingRoomEntry.METHOD_ROOM_NETWORK, entry.method());
        assertEquals(55, entry.roomId());
    }

    @Test
    void aPickedTeleporterSendsToItsPairsRoomToArriveOnThePair() throws Exception {
        InteractionTeleport teleport = new InteractionTeleport(10, 1, base(), "", 0, 0);
        when(this.room.getUserItem(10)).thenReturn(teleport);
        when(this.itemManager.getTargetTeleportRoomId(teleport)).thenReturn(new int[] {66, 501});
        target(66, RoomState.OPEN);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"\",\"delay\":0,\"userSource\":0,\"itemIds\":[10]}"), this.room);
        RoomUnit unit = mock(RoomUnit.class);
        User user = visitor(unit);

        box.execute(context(this.room, unit));

        assertEquals(66, forwardedRoom(user));
        PendingRoomEntry entry = pendingEntry(user);
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
        User user = visitor(unit);
        when(banned.isBanned(user)).thenReturn(true);
        when(user.hasPermission(anyString())).thenReturn(false);

        for (String roomId : new String[] {"70", "71", String.valueOf(HERE)}) {
            WiredEffectForwardUserToRoom box = box();
            box.loadWiredData(row("{\"roomIdText\":\"" + roomId + "\",\"delay\":0,\"userSource\":0}"), this.room);
            box.execute(context(this.room, unit));
        }
        verify(user.getClient(), never()).sendResponse(any(ForwardToRoomComposer.class));

        target(72, RoomState.OPEN);
        when(user.getUserInfo().tryAcquireRoomForward(anyLong(), anyLong())).thenReturn(false);
        WiredEffectForwardUserToRoom box = box();
        box.loadWiredData(row("{\"roomIdText\":\"72\",\"delay\":0,\"userSource\":0}"), this.room);
        box.execute(context(this.room, unit));
        verify(user.getClient(), never()).sendResponse(any(ForwardToRoomComposer.class));
    }
}
