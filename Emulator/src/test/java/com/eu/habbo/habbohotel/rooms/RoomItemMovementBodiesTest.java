package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.furniture.FurnitureRotatedEvent;
import com.eu.habbo.threading.ThreadPooling;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import org.mockito.invocation.Invocation;
import org.mockito.listeners.InvocationListener;

/**
 * Characterizes what the four move bodies of {@link RoomItemMovementService} do once a move is accepted: the writes on
 * the item, the wired spatial index and its invalidation, the save, the update packet, the tiles, users and bots
 * refreshed, the walk-on of users under the item, the topology notice, the plugin events and the result. Each scenario
 * runs through all four bodies, so their shared blocks can be taken out without changing any of them.
 */
class RoomItemMovementBodiesTest {
    private static final int SIZE = 6;

    private final List<String> trace = new ArrayList<>();
    private final InvocationListener listener = report -> this.record((Invocation) report.getInvocation());
    private final Map<Long, RoomTile> tiles = new HashMap<>();
    private final Map<Long, Set<Habbo>> habbosAt = new HashMap<>();
    private final Set<Class<?>> registeredEvents = new HashSet<>();

    private boolean recording;
    private boolean placeUnder;
    private Room room;
    private RoomLayout layout;
    private RoomItemMovementService movement;
    private MockedStatic<Emulator> emulator;
    private MockedStatic<WiredManager> wired;
    private MockedConstruction<FloorItemUpdateComposer> updates;

    @BeforeEach
    void setUp() {
        for (short x = 0; x < SIZE; x++) {
            for (short y = 0; y < SIZE; y++) {
                this.tiles.put(key(x, y), new RoomTile(x, y, (short) 0, RoomTileState.OPEN, true));
            }
        }

        this.layout = mock(RoomLayout.class);
        when(this.layout.getTile(anyShort(), anyShort()))
                .thenAnswer(call -> this.tiles.get(key(call.<Short>getArgument(0), call.<Short>getArgument(1))));
        when(this.layout.getTilesAt(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> call.getArgument(0) == null
                        ? new HashSet<>()
                        : new HashSet<>(List.of(call.<RoomTile>getArgument(0))));
        when(this.layout.fitsOnMap(any(), anyInt(), anyInt(), anyInt())).thenReturn(true);

        RoomSpecialTypes specialTypes = this.traced(RoomSpecialTypes.class, "specialTypes");

        this.room = this.traced(Room.class, "room");
        when(this.room.getLayout()).thenReturn(this.layout);
        when(this.room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(this.room.calculateTileState(any(), any())).thenReturn(RoomTileState.OPEN);
        when(this.room.getHabbosAt(anyShort(), anyShort()))
                .thenAnswer(call -> this.habbosAt.getOrDefault(
                        key(call.<Short>getArgument(0), call.<Short>getArgument(1)), new LinkedHashSet<>()));

        RoomItemManager facade = mock(RoomItemManager.class);
        when(facade.getItemsAt(any(RoomTile.class))).thenReturn(new HashSet<>());

        RoomItemPlacementService placement = mock(RoomItemPlacementService.class);
        when(placement.furnitureFitsAt(any(), any(), anyInt(), anyBoolean())).thenReturn(FurnitureMovementError.NONE);
        when(placement.furnitureFitsAt(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(FurnitureMovementError.NONE);

        this.movement = new RoomItemMovementService(this.room, facade, placement);

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean()))
                .thenAnswer(call -> this.registeredEvents.contains(call.<Class<?>>getArgument(0)));
        when(plugins.fireEvent(any())).thenAnswer(call -> {
            Event event = call.getArgument(0);
            this.trace.add("event " + event.getClass().getSimpleName());
            return event;
        });

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), anyBoolean()))
                .thenAnswer(call -> "wired.place.under".equals(call.getArgument(0))
                        ? this.placeUnder
                        : call.<Boolean>getArgument(1));

        ThreadPooling threading = this.traced(ThreadPooling.class, "threading");

        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        this.emulator.when(Emulator::getConfig).thenReturn(config);
        this.emulator.when(Emulator::getThreading).thenReturn(threading);

        this.wired = mockStatic(WiredManager.class);
        this.wired.when(() -> WiredManager.invalidateRoom(any())).thenAnswer(call -> {
            if (this.recording) this.trace.add("WiredManager.invalidateRoom(room)");
            return null;
        });

        this.updates = mockConstruction(FloorItemUpdateComposer.class, (composer, context) -> {
            if (this.recording)
                this.trace.add("compose FloorItemUpdateComposer(" + this.describe(context.arguments()) + ")");
            ServerMessage message = mock(ServerMessage.class, withSettings().name("floorItemUpdate"));
            when(composer.compose()).thenReturn(message);
        });
    }

    @AfterEach
    void tearDown() {
        this.updates.close();
        this.wired.close();
        this.emulator.close();
    }

    // ---------------------------------------------------------------- the scenarios

    @Test
    void aPlainItemIsMovedSavedSentAndTheTilesRefreshed() {
        this.everyBody("send", () -> this.item(HabboItem.class, "chair", 1, 1, 2), 3, 1, 2, true);
        this.everyBody("no send", () -> this.item(HabboItem.class, "chair", 1, 1, 2), 3, 1, 2, false);
    }

    @Test
    void wiredBoxesMoveTheirSpatialIndexAndInvalidateTheRoom() {
        this.everyBody("trigger", () -> this.item(InteractionWiredTrigger.class, "trigger", 1, 1, 0), 2, 2, 0, true);
        this.everyBody("effect", () -> this.item(InteractionWiredEffect.class, "effect", 1, 1, 0), 2, 2, 0, true);
        this.everyBody(
                "condition", () -> this.item(InteractionWiredCondition.class, "condition", 1, 1, 0), 2, 2, 0, true);
        this.everyBody("extra", () -> this.item(InteractionWiredExtra.class, "extra", 1, 1, 0), 2, 2, 0, true);
    }

    @Test
    void usersUnderTheItemWalkOnItWhenPlaceUnderIsOn() {
        Habbo habbo = mock(Habbo.class, withSettings().name("habbo alice"));
        RoomUnit unit = mock(RoomUnit.class, withSettings().name("alice"));
        when(habbo.getRoomUnit()).thenReturn(unit);
        this.habbosAt.put(key(4, 4), new LinkedHashSet<>(List.of(habbo)));
        this.placeUnder = true;

        this.everyBody("place under", () -> this.item(HabboItem.class, "rug", 1, 1, 0), 4, 4, 0, true);
    }

    @Test
    void aRotationIsAnnouncedToPlugins() {
        this.registeredEvents.add(FurnitureRotatedEvent.class);

        this.everyBody("rotate", () -> this.item(HabboItem.class, "chair", 1, 1, 0), 1, 1, 4, true);
    }

    @Test
    void aWiredBoxWithoutAnOldTileOnTheMap() {
        this.everyBody(
                "off the map", () -> this.item(InteractionWiredTrigger.class, "trigger", 40, 40, 0), 2, 2, 0, true);
    }

    // ---------------------------------------------------------------- the driver

    private interface ItemFactory {
        HabboItem make();
    }

    /** Runs the same move through the four bodies, each on a fresh item. */
    private void everyBody(String label, ItemFactory factory, int x, int y, int rotation, boolean sendUpdates) {
        WiredMovementPhysics physics = mock(WiredMovementPhysics.class);
        when(physics.isActive()).thenReturn(true);

        this.run(
                label + " / moveFurniTo with z",
                factory.make(),
                item -> this.movement.moveFurniTo(item, this.tile(x, y), rotation, 0.0, null, sendUpdates, true));
        this.run(
                label + " / moveFurniToWithPhysics with z",
                factory.make(),
                item -> this.movement.moveFurniToWithPhysics(
                        item, this.tile(x, y), rotation, 0.0, null, sendUpdates, true, physics));
        this.run(
                label + " / moveFurniTo",
                factory.make(),
                item -> this.movement.moveFurniTo(item, this.tile(x, y), rotation, null, sendUpdates, true));
        this.run(
                label + " / moveFurniToWithPhysics",
                factory.make(),
                item -> this.movement.moveFurniToWithPhysics(
                        item, this.tile(x, y), rotation, null, sendUpdates, true, physics));
    }

    private interface Move {
        FurnitureMovementError apply(HabboItem item);
    }

    private void run(String label, HabboItem item, Move move) {
        this.trace.clear();
        this.recording = true;
        try {
            this.trace.add("returned " + move.apply(item));
        } catch (Exception e) {
            this.trace.add("threw " + e.getClass().getSimpleName());
        } finally {
            this.recording = false;
        }
        this.check(label, String.join("\n", this.trace));
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.movementTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/movement-traces.txt");
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

    // ---------------------------------------------------------------- the room

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private RoomTile tile(int x, int y) {
        return this.tiles.get(key(x, y));
    }

    private <T extends HabboItem> T item(Class<T> type, String name, int x, int y, int rotation) {
        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(1);
        when(base.getLength()).thenReturn(1);
        when(base.getHeight()).thenReturn(1.0);
        when(base.allowStack()).thenReturn(true);
        T item = this.traced(type, name);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getZ()).thenReturn(0.0);
        when(item.getRotation()).thenReturn(rotation);
        when(item.getBaseItem()).thenReturn(base);
        when(item.canStackAt(any(), any())).thenReturn(true);
        return item;
    }

    private <T> T traced(Class<T> type, String name) {
        return mock(type, withSettings().name(name).invocationListeners(this.listener));
    }

    private static final Set<String> RECORDED = Set.of(
            "setRotation",
            "setX",
            "setY",
            "setZ",
            "setExtradata",
            "setAllowUnderpass",
            "onMove",
            "needsUpdate",
            "onWalkOn",
            "run",
            "sendComposer",
            "updateTiles",
            "updateHabbosAt",
            "updateBotsAt",
            "onFurnitureTopologyChanged",
            "updateTriggerLocation",
            "updateEffectLocation",
            "updateConditionLocation",
            "updateExtraLocation");

    private void record(Invocation invocation) {
        if (!this.recording) return;

        String method = invocation.getMethod().getName();
        if (!RECORDED.contains(method)) return;

        String target = this.name(invocation.getMock());
        this.trace.add(target + "." + method + "(" + this.describe(invocation.getArguments()) + ")");
    }

    private String name(Object value) {
        if (value == null) return "null";
        if (mockingDetails(value).isMock())
            return mockingDetails(value).getMockCreationSettings().getMockName().toString();
        return String.valueOf(value);
    }

    private String describe(Object value) {
        if (value == null) return "null";
        if (value instanceof RoomTile tile) return "(" + tile.x + "," + tile.y + ")";
        if (value instanceof Double number) return String.format(Locale.ROOT, "%.2f", number);
        if (value instanceof Object[] array) {
            return Arrays.stream(array).map(this::describe).collect(Collectors.joining(", "));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::describe).sorted().collect(Collectors.joining(", ", "[", "]"));
        }
        return this.name(value);
    }
}
