package com.eu.habbo.messages.incoming.floorplaneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.CustomRoomLayout;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.threading.ThreadPooling;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Records what {@link FloorPlanEditorSaveEvent#handle} does with one save packet: the permission and owner gates, the
 * map and field checks, the furni that block a change or are picked up, and how the new layout reaches the room. The
 * traces live in {@code floorplaneditor/floorplan-save-traces.txt}; regenerate them with
 * {@code -Dpolaris.floorplanSaveTraces.update=true}.
 *
 * <p>The room before the save is a 3 x 3 floor of height 0. Runs of consecutive lines of the same call are sorted,
 * because the handler collects the furni it picks up in hash sets.
 */
class FloorPlanEditorSaveEventBehaviourTest {
    private static final int NOW = 1_700_000_000;
    private static final int ROOM_ID = 50;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private final Map<String, Boolean> flags = new HashMap<>();
    private final Set<String> permissions = new LinkedHashSet<>();
    private final Map<String, Set<HabboItem>> items = new HashMap<>();
    private final Map<Integer, Habbo> online = new HashMap<>();
    private boolean inRoom;
    private int ownerId;
    private long lastSave;
    private boolean customLayout;
    private boolean doorTileFound;
    private boolean insertedLayout;
    private boolean roomLoadsBack;

    private int maximumWidth;
    private int maximumSize;

    private GameClient client;
    private Habbo habbo;
    private HabboStats stats;
    private Room room;
    private RoomTile[][] tiles;

    @BeforeEach
    void setUp() {
        this.maximumWidth = FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH;
        this.maximumSize = FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_SIZE;

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString())).thenAnswer(i -> this.flags.getOrDefault(i.<String>getArgument(0), false));
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> "text " + i.getArgument(0));

        HabboManager habbos = mock(HabboManager.class);
        when(habbos.getHabbo(anyInt())).thenAnswer(i -> this.online.get(i.<Integer>getArgument(0)));
        RoomManager rooms = mock(RoomManager.class);
        doAnswer(i -> this.record("rooms.unloadRoom(" + this.label(i.getArgument(0)) + ")"))
                .when(rooms)
                .unloadRoom(any(Room.class));
        when(rooms.loadRoom(anyInt())).thenAnswer(i -> {
            Room reloaded = this.roomLoadsBack ? this.room("reloaded room", false) : null;
            this.record("rooms.loadRoom(" + i.getArgument(0) + ") -> " + this.label(reloaded));
            return reloaded;
        });
        when(rooms.insertCustomLayout(any(Room.class), anyString(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> {
                    CustomRoomLayout inserted = this.insertedLayout ? this.customLayout("inserted layout") : null;
                    this.record("rooms.insertCustomLayout(" + this.label(i.getArgument(0)) + ", " + i.getArgument(1)
                            + ", door " + i.getArgument(2) + "," + i.getArgument(3) + " dir " + i.getArgument(4)
                            + ") -> " + this.label(inserted));
                    return inserted;
                });
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getHabboManager()).thenReturn(habbos);
        when(environment.getRoomManager()).thenReturn(rooms);

        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class)))
                .thenAnswer(i -> this.record("threading.run(" + this.label(i.getArgument(0)) + ")"));

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getTexts).thenReturn(texts);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        emulator.when(Emulator::getIntUnixTimestamp).thenReturn(NOW);
        emulator.when(() -> Emulator.isNumeric(anyString())).thenCallRealMethod();
        this.statics.add(emulator);

        this.composer(GenericAlertComposer.class, "GenericAlert");
        this.composer(BubbleAlertComposer.class, "BubbleAlert");
        this.composer(AddHabboItemComposer.class, "AddHabboItem");
        this.composer(InventoryRefreshComposer.class, "InventoryRefresh");
        this.composer(ForwardToRoomComposer.class, "ForwardToRoom");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH = this.maximumWidth;
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_SIZE = this.maximumSize;
    }

    // ---------------------------------------------------------------- who may save

    @Test
    void accessGates() throws Exception {
        this.fresh();
        this.permissions.remove(Permission.ACC_FLOORPLAN_EDITOR);
        this.save("gate / no floor plan editor permission", "000\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.inRoom = false;
        this.save("gate / not in a room", "000\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.ownerId = 99;
        this.save("gate / someone else's room", "000\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.ownerId = 99;
        this.permissions.add(Permission.ACC_ANYROOMOWNER);
        this.save("gate / staff saves someone else's room", "000\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.lastSave = NOW - 2;
        this.save("gate / saved again within the cooldown", "000\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.lastSave = NOW - 3;
        this.save("gate / saved again when the cooldown is over", "000\r000\r000", 0, 0, 2, 0, 0, 3, false);
    }

    // ---------------------------------------------------------------- the map itself

    @Test
    void mapShape() throws Exception {
        this.fresh();
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_SIZE = 10;
        this.save("map / longer than the size limit", "000\r000\r0000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("map / a character outside letters and digits", "000\r0-0\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH = 2;
        this.save("map / more rows than the width limit", "00\r00\r00", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("map / an empty first row", "\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH = 2;
        this.save("map / a first row wider than the width limit", "000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("map / rows of different widths", "000\r00\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("map / an upper case X is a void tile", "X00\r000\r000", 1, 1, 2, 0, 0, 3, false);

        this.fresh();
        this.save("map / letters are heights from 10", "0a0\r0z0\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("map / a trailing row break is dropped", "000\r000\r000\r", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("map / a larger map than the room", "0000\r0000\r0000\r0000", 0, 0, 2, 0, 0, 3, false);
    }

    // ---------------------------------------------------------------- the other fields

    @Test
    void fields() throws Exception {
        this.fresh();
        this.save("fields / door outside the map", "000\r000\r000", 3, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("fields / door above the map", "000\r000\r000", 0, -1, 2, 0, 0, 3, false);

        this.fresh();
        this.save("fields / door on a void tile", "x00\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.save("fields / door direction out of range", "000\r000\r000", 0, 0, 8, 0, 0, 3, false);

        this.fresh();
        this.save("fields / wall thickness out of range", "000\r000\r000", 0, 0, 2, 2, 0, 3, false);

        this.fresh();
        this.save("fields / floor thickness out of range", "000\r000\r000", 0, 0, 2, 0, -3, 3, false);

        this.fresh();
        this.save("fields / wall height out of range", "000\r000\r000", 0, 0, 2, 0, 0, 16, false);

        this.fresh();
        this.save("fields / every field out of range", "x00\r000\r000", 0, 0, -1, 5, 5, -2, false);

        this.fresh();
        this.save("fields / the lowest values in range", "000\r000\r000", 0, 0, 0, -2, -2, -1, false);

        this.fresh();
        this.saveOldClient("fields / a client without wall height or auto pickup", "000\r000\r000");

        this.fresh();
        this.save("fields / only void tiles, check disabled", "x0x\rxxx\rxxx", 1, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.flags.put("hotel.room.floorplan.check.enabled", true);
        this.save("fields / only void tiles, check enabled", "xxx\rxxx\rxxx", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.flags.put("hotel.room.floorplan.check.enabled", true);
        this.save("fields / one floor tile, check enabled", "x0x\rxxx\rxxx", 1, 0, 2, 0, 0, 3, false);
    }

    // ---------------------------------------------------------------- furni in the way

    @Test
    void furni() throws Exception {
        this.fresh();
        this.item(2, 2, "chair#11", 7);
        this.save("furni / on a tile that becomes void", "000\r000\r00x", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.item(2, 2, "chair#11", 7);
        this.save("furni / on a tile that changes height", "000\r000\r001", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.item(2, 2, "chair#11", 7);
        this.save("furni / on a tile that keeps its height", "000\r000\r000", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.item(2, 2, "chair#11", 7);
        this.save("furni / on a tile the new map leaves out", "00\r00", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.item(1, 1, "chair#11", 7);
        this.item(2, 2, "lamp#12", 7);
        this.save("furni / the first blocking tile is reported", "000\r0x0\r00x", 0, 0, 2, 0, 0, 3, false);

        this.fresh();
        this.item(2, 2, "chair#11", 7);
        this.save("furni / auto pickup on a tile that becomes void", "000\r000\r00x", 0, 0, 2, 0, 0, 3, true);

        this.fresh();
        this.item(2, 2, "chair#11", 7);
        this.item(2, 2, "rug#13", 7);
        this.save("furni / auto pickup on a tile that changes height", "000\r000\r001", 0, 0, 2, 0, 0, 3, true);

        this.fresh();
        this.item(2, 2, "chair#11", 7);
        this.save("furni / auto pickup on a tile the new map leaves out", "00\r00", 0, 0, 2, 0, 0, 3, true);

        this.fresh();
        this.item(0, 1, "chair#11", 7);
        this.item(1, 2, "sofa#21", 8);
        this.item(2, 2, "plant#31", 9);
        this.online.remove(9);
        this.save(
                "furni / auto pickup returns furni to every owner, online or not",
                "000\rx00\r0xx",
                0,
                0,
                2,
                0,
                0,
                3,
                true);

        this.fresh();
        for (int i = 0; i < 501; i++) this.item(2, 2, "coin#" + (1000 + i), 7);
        this.save("furni / auto pickup over the cap", "000\r000\r00x", 0, 0, 2, 0, 0, 3, true);
    }

    // ---------------------------------------------------------------- the new layout

    @Test
    void layout() throws Exception {
        this.fresh();
        this.save("layout / a custom layout is updated in place", "000\r010\r000", 1, 2, 4, -1, 1, 5, false);

        this.fresh();
        this.customLayout = false;
        this.save("layout / a default layout gets a custom one", "000\r010\r000", 1, 2, 4, -1, 1, 5, false);

        this.fresh();
        this.customLayout = false;
        this.insertedLayout = false;
        this.save("layout / no custom layout could be inserted", "000\r010\r000", 1, 2, 4, -1, 1, 5, false);

        this.fresh();
        this.doorTileFound = false;
        this.save("layout / the door tile is missing after the parse", "000\r010\r000", 1, 2, 4, -1, 1, 5, false);

        this.fresh();
        this.roomLoadsBack = false;
        this.save("layout / the room does not load back", "000\r010\r000", 1, 2, 4, -1, 1, 5, false);
    }

    // ---------------------------------------------------------------- the harness

    private void fresh() {
        this.trace.clear();
        this.flags.clear();
        this.permissions.clear();
        this.permissions.add(Permission.ACC_FLOORPLAN_EDITOR);
        this.items.clear();
        this.online.clear();
        this.recording = false;
        this.inRoom = true;
        this.ownerId = 7;
        this.lastSave = 0;
        this.customLayout = true;
        this.doorTileFound = true;
        this.insertedLayout = true;
        this.roomLoadsBack = true;
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_WIDTH_LENGTH = this.maximumWidth;
        FloorPlanEditorSaveEvent.MAXIMUM_FLOORPLAN_SIZE = this.maximumSize;

        this.tiles = new RoomTile[3][3];
        for (short y = 0; y < 3; y++) {
            for (short x = 0; x < 3; x++) {
                this.tiles[y][x] = new RoomTile(x, y, (short) 0, RoomTileState.OPEN, true);
            }
        }

        this.client = this.player("ann", 7);
        this.habbo = this.client.getHabbo();
        this.online.put(7, this.habbo);
        this.online.put(8, this.player("bob", 8).getHabbo());
        this.online.put(9, this.player("cid", 9).getHabbo());
        this.room = this.room("room", true);
    }

    private void save(
            String name,
            String map,
            int doorX,
            int doorY,
            int doorDirection,
            int wallSize,
            int floorSize,
            int wallHeight,
            boolean autoPickup)
            throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        writeString(buffer, map);
        buffer.writeInt(doorX);
        buffer.writeInt(doorY);
        buffer.writeInt(doorDirection);
        buffer.writeInt(wallSize);
        buffer.writeInt(floorSize);
        buffer.writeInt(wallHeight);
        buffer.writeBoolean(autoPickup);
        this.run(name, buffer);
    }

    private void saveOldClient(String name, String map) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        writeString(buffer, map);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(2);
        buffer.writeInt(0);
        buffer.writeInt(0);
        this.run(name, buffer);
    }

    private void run(String name, ByteBuf buffer) {
        this.stats.lastFloorplanSaveTimestamp = this.lastSave;
        FloorPlanEditorSaveEvent handler = new FloorPlanEditorSaveEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.FloorPlanEditorSaveEvent, buffer);

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName());
        }
        if (this.stats.lastFloorplanSaveTimestamp != this.lastSave) {
            this.record("ann's last floor plan save = " + this.stats.lastFloorplanSaveTimestamp);
        }
        this.recording = false;

        this.check(name, String.join("\n", sortedRuns(this.trace)));
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    private GameClient player(String name, int id) {
        GameClient client = mock(GameClient.class, withSettings().name(name + "'s client"));
        Habbo habbo = mock(Habbo.class, withSettings().name(name));
        HabboInfo info = mock(HabboInfo.class);
        HabboStats stats = mock(HabboStats.class);
        this.labels.put(client, name);
        this.labels.put(habbo, name);
        when(info.getId()).thenReturn(id);
        when(info.getCurrentRoom()).thenAnswer(i -> this.inRoom ? this.room : null);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getClient()).thenReturn(client);
        when(habbo.hasPermission(anyString())).thenAnswer(i -> this.permissions.contains(i.<String>getArgument(0)));
        doAnswer(i -> this.record(name + ".alert(" + i.getArgument(0) + ")"))
                .when(habbo)
                .alert(anyString());
        when(client.getHabbo()).thenReturn(habbo);
        doAnswer(i -> this.record(name + " <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(MessageComposer.class));
        doAnswer(i -> this.record(name + " <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(ServerMessage.class));
        if (id == 7) this.stats = stats;
        return client;
    }

    private Room room(String name, boolean current) {
        Room room = mock(Room.class, withSettings().name(name));
        this.labels.put(room, name);
        when(room.getId()).thenReturn(ROOM_ID);
        if (!current) return room;

        CustomRoomLayout custom = this.customLayout("layout");
        RoomLayout model = this.plainLayout();
        when(room.getOwnerId()).thenAnswer(i -> this.ownerId);
        when(room.getLayout()).thenAnswer(i -> this.customLayout ? custom : model);
        when(room.getTopItemAt(anyInt(), anyInt())).thenAnswer(i -> {
            Set<HabboItem> here = this.items.get(i.getArgument(0) + "," + i.getArgument(1));
            return here == null || here.isEmpty() ? null : here.iterator().next();
        });
        when(room.getItemsAt(anyInt(), anyInt())).thenAnswer(i -> this.itemsAt(i.getArgument(0), i.getArgument(1)));
        when(room.getItemsAt(any(RoomTile.class))).thenAnswer(i -> {
            RoomTile tile = i.getArgument(0);
            return this.itemsAt(tile.x, tile.y);
        });
        when(room.getLockedTiles()).thenAnswer(i -> {
            Set<RoomTile> locked = new HashSet<>();
            for (String position : this.items.keySet()) {
                String[] xy = position.split(",");
                locked.add(this.tiles[Integer.parseInt(xy[1])][Integer.parseInt(xy[0])]);
            }
            return locked;
        });
        when(room.getUserCount()).thenReturn(2);
        when(room.getHabbos()).thenAnswer(i -> List.of(this.habbo, this.online.get(8)));
        doAnswer(i -> this.record(
                        "room.pickUpItem(" + this.label(i.getArgument(0)) + ", " + this.label(i.getArgument(1)) + ")"))
                .when(room)
                .pickUpItem(any(), any());
        doAnswer(i -> this.record("room.setHasCustomLayout(" + i.getArgument(0) + ")"))
                .when(room)
                .setHasCustomLayout(anyBoolean());
        doAnswer(i -> this.record("room.setNeedsUpdate(" + i.getArgument(0) + ")"))
                .when(room)
                .setNeedsUpdate(anyBoolean());
        doAnswer(i -> this.record("room.setLayout(" + this.label(i.getArgument(0)) + ")"))
                .when(room)
                .setLayout(any());
        doAnswer(i -> this.record("room.setWallSize(" + i.getArgument(0) + ")"))
                .when(room)
                .setWallSize(anyInt());
        doAnswer(i -> this.record("room.setFloorSize(" + i.getArgument(0) + ")"))
                .when(room)
                .setFloorSize(anyInt());
        doAnswer(i -> this.record("room.setWallHeight(" + i.getArgument(0) + ")"))
                .when(room)
                .setWallHeight(anyInt());
        doAnswer(i -> this.record("room.save()")).when(room).save();
        return room;
    }

    private Set<HabboItem> itemsAt(int x, int y) {
        Set<HabboItem> here = this.items.get(x + "," + y);
        return here == null ? null : new HashSet<>(here);
    }

    private void item(int x, int y, String name, int ownerId) {
        HabboItem item = mock(HabboItem.class, withSettings().name(name));
        this.labels.put(item, name);
        when(item.getUserId()).thenReturn(ownerId);
        this.items.computeIfAbsent(x + "," + y, k -> new LinkedHashSet<>()).add(item);
    }

    private CustomRoomLayout customLayout(String name) {
        CustomRoomLayout layout = mock(CustomRoomLayout.class, withSettings().name(name));
        this.labels.put(layout, name);
        this.wireLayout(layout, name);
        doAnswer(i -> this.record(name + ".needsUpdate(" + i.getArgument(0) + ")"))
                .when(layout)
                .needsUpdate(anyBoolean());
        return layout;
    }

    private RoomLayout plainLayout() {
        RoomLayout layout = mock(RoomLayout.class, withSettings().name("model layout"));
        this.labels.put(layout, "model layout");
        this.wireLayout(layout, "model layout");
        return layout;
    }

    private void wireLayout(RoomLayout layout, String name) {
        when(layout.getTile(anyShort(), anyShort())).thenAnswer(i -> {
            short x = i.getArgument(0);
            short y = i.getArgument(1);
            return x < 0 || y < 0 || x >= 3 || y >= 3 ? null : this.tiles[y][x];
        });
        when(layout.getDoorTile()).thenAnswer(i -> this.doorTileFound ? this.tiles[0][0] : null);
        doAnswer(i -> this.record(name + ".setDoorX(" + i.getArgument(0) + ")"))
                .when(layout)
                .setDoorX(anyShort());
        doAnswer(i -> this.record(name + ".setDoorY(" + i.getArgument(0) + ")"))
                .when(layout)
                .setDoorY(anyShort());
        doAnswer(i -> this.record(name + ".setDoorDirection(" + i.getArgument(0) + ")"))
                .when(layout)
                .setDoorDirection(anyInt());
        doAnswer(i -> this.record(name + ".setHeightmap(" + i.getArgument(0) + ")"))
                .when(layout)
                .setHeightmap(anyString());
        doAnswer(i -> this.record(name + ".parse()")).when(layout).parse();
    }

    private Object record(String line) {
        if (this.recording) this.trace.add(line.replace("\r", "|"));
        return null;
    }

    private <T> void composer(Class<T> type, String name) {
        this.constructions.add(mockConstruction(type, (composer, context) -> {
            String text =
                    name + "(" + context.arguments().stream().map(this::label).collect(Collectors.joining(", ")) + ")";
            this.labels.put(composer, text);
            if (composer instanceof MessageComposer messageComposer) {
                ServerMessage message = mock(ServerMessage.class);
                this.labels.put(message, text);
                when(messageComposer.compose()).thenReturn(message);
            }
        }));
    }

    private String label(Object value) {
        if (value == null) return "null";
        String known = this.labels.get(value);
        if (known != null) return known;
        if (value instanceof Collection<?> collection)
            return collection.stream().map(this::label).collect(Collectors.joining(", ", "[", "]"));
        if (value instanceof Number || value instanceof String || value instanceof Boolean)
            return String.valueOf(value);
        return value.getClass().getSimpleName();
    }

    /** Sorts each run of consecutive lines that make the same call, so the order of a hash set does not show. */
    private static List<String> sortedRuns(List<String> lines) {
        List<String> sorted = new ArrayList<>();
        int start = 0;
        while (start < lines.size()) {
            String call = call(lines.get(start));
            int end = start + 1;
            while (end < lines.size() && call(lines.get(end)).equals(call)) end++;
            List<String> run = new ArrayList<>(lines.subList(start, end));
            run.sort(null);
            sorted.addAll(run);
            start = end;
        }
        return sorted;
    }

    private static String call(String line) {
        int open = line.indexOf('(');
        return open < 0 ? line : line.substring(0, open);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.floorplanSaveTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/floorplaneditor/floorplan-save-traces.txt");
    private static final Map<String, String> RECORDING = new ConcurrentSkipListMap<>();

    private void check(String name, String trace) {
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            RECORDING.put(name, trace);
            return;
        }

        assertEquals(expectedTraces().get(name), trace, name);
    }

    @AfterAll
    static void writeRecording() throws Exception {
        if (!Boolean.getBoolean(UPDATE_PROPERTY)) return;

        StringBuilder text = new StringBuilder();
        new TreeMap<>(RECORDING)
                .forEach((name, lines) -> text.append("== ")
                        .append(name)
                        .append('\n')
                        .append(lines)
                        .append("\n\n"));
        Files.createDirectories(TRACES.getParent());
        Files.writeString(TRACES, text.toString());
    }

    private static Map<String, String> expectedTraces() {
        try {
            Map<String, String> traces = new TreeMap<>();
            String name = null;
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(TRACES)) {
                if (line.startsWith("== ")) {
                    if (name != null) traces.put(name, String.join("\n", lines).strip());
                    name = line.substring(3);
                    lines.clear();
                } else {
                    lines.add(line);
                }
            }
            if (name != null) traces.put(name, String.join("\n", lines).strip());
            return traces;
        } catch (Exception e) {
            throw new IllegalStateException("Missing " + TRACES + "; record it with -D" + UPDATE_PROPERTY + "=true", e);
        }
    }
}
