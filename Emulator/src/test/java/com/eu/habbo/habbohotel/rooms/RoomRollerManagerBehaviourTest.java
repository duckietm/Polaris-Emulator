package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionPyramid;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.furniture.FurnitureRolledEvent;
import com.eu.habbo.plugin.events.users.UserRolledEvent;
import com.eu.habbo.threading.ThreadPooling;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

/**
 * Characterizes one roller cycle: which units and furniture a roller carries, to which tile and height, which
 * composers are built and sent, which plugin events fire, which walk-on is scheduled, and what is left marked on the
 * units. Every scenario pins the whole trace, so the roller manager can be taken apart without changing what a roller
 * does.
 */
class RoomRollerManagerBehaviourTest {
    private static final int WIDTH = 8;
    private static final int HEIGHT = 6;

    private final List<String> trace = new ArrayList<>();
    private final Map<Long, RoomTile> tiles = new HashMap<>();
    private final Map<Long, Double> floorAltitudes = new HashMap<>();
    private final Map<Long, List<HabboItem>> itemsByTile = new HashMap<>();
    private final Map<Integer, InteractionRoller> rollers = new LinkedHashMap<>();
    private final Set<HabboItem> pyramids = new LinkedHashSet<>();
    private final Map<RoomUnit, Habbo> habbos = new HashMap<>();
    private final Map<RoomUnit, RideablePet> pets = new HashMap<>();
    private final Set<String> cancelled = new HashSet<>();
    private final Set<Class<?>> registeredEvents = new HashSet<>();
    private final Map<Object, String> names = new java.util.IdentityHashMap<>();

    private Room room;
    private RoomLayout layout;
    private MockedStatic<Emulator> emulator;
    private MockedStatic<RoomQueueSpeedControlSupport> speed;
    private MockedConstruction<RoomUnitOnRollerComposer> unitComposers;
    private MockedConstruction<FloorItemOnRollerComposer> furniComposers;
    private int nextId = 1;

    @BeforeEach
    void setUp() {
        InteractionRoller.NO_RULES = false;
        Room.ROLLERS_MAXIMUM_ROLL_AVATARS = 1;

        for (short x = 0; x < WIDTH; x++) {
            for (short y = 0; y < HEIGHT; y++) {
                this.tiles.put(key(x, y), new RoomTile(x, y, (short) 0, RoomTileState.OPEN, true));
            }
        }

        this.layout = mock(RoomLayout.class);
        when(this.layout.getTile(anyShort(), anyShort()))
                .thenAnswer(call -> this.tiles.get(key(call.<Short>getArgument(0), call.<Short>getArgument(1))));
        when(this.layout.tileExists(anyShort(), anyShort()))
                .thenAnswer(
                        call -> this.tiles.containsKey(key(call.<Short>getArgument(0), call.<Short>getArgument(1))));
        when(this.layout.getHeightAtSquare(anyInt(), anyInt()))
                .thenAnswer(call -> this.tiles.get(key(call.<Integer>getArgument(0), call.<Integer>getArgument(1))).z);
        when(this.layout.getFloorAltitude(anyInt(), anyInt())).thenAnswer(call -> {
            long tileKey = key(call.<Integer>getArgument(0), call.<Integer>getArgument(1));
            return this.floorAltitudes.getOrDefault(tileKey, (double) this.tiles.get(tileKey).z);
        });
        when(this.layout.getTileInFront(any(RoomTile.class), anyInt())).thenAnswer(call -> {
            RoomTile tile = call.getArgument(0);
            int rotation = call.getArgument(1);
            int x = tile.x + (rotation == 2 ? 1 : rotation == 6 ? -1 : 0);
            int y = tile.y + (rotation == 4 ? 1 : rotation == 0 ? -1 : 0);
            return this.tiles.get(key(x, y));
        });

        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(specialTypes.rollerSnapshot()).thenAnswer(call -> new LinkedHashMap<>(this.rollers));
        when(specialTypes.getItemsOfType(InteractionPyramid.class))
                .thenAnswer(call -> new LinkedHashSet<>(this.pyramids));

        this.room = mock(Room.class);
        when(this.room.getLayout()).thenReturn(this.layout);
        when(this.room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(this.room.getItemsAt(any(RoomTile.class)))
                .thenAnswer(call -> new LinkedHashSet<>(this.itemsAt(call.getArgument(0))));
        when(this.room.getTopItemAt(anyInt(), anyInt()))
                .thenAnswer(call -> this.topItemAt(
                        this.tiles.get(key(call.<Integer>getArgument(0), call.<Integer>getArgument(1)))));
        when(this.room.getHabbo(any(RoomUnit.class)))
                .thenAnswer(call -> this.habbos.get(call.<RoomUnit>getArgument(0)));
        when(this.room.getPet(any(RoomUnit.class))).thenAnswer(call -> this.pets.get(call.<RoomUnit>getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> {
                    ServerMessage message = call.getArgument(0);
                    this.trace.add("send " + (message == null ? "null" : this.names.get(message)));
                    return null;
                })
                .when(this.room)
                .sendComposer(any());

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean()))
                .thenAnswer(call -> this.registeredEvents.contains(call.<Class<?>>getArgument(0)));
        when(plugins.fireEvent(any())).thenAnswer(call -> {
            Event event = call.getArgument(0);
            String label = this.describeEvent(event);
            boolean cancel = this.cancelled.contains(label);
            this.trace.add("event " + label + (cancel ? " -> cancelled" : ""));
            if (cancel) event.setCancelled(true);
            return event;
        });

        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(call -> {
            this.trace.add("schedule after " + call.<Long>getArgument(1) + " ms");
            call.<Runnable>getArgument(0).run();
            return null;
        });

        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        this.emulator.when(Emulator::getThreading).thenReturn(threading);

        this.speed = mockStatic(RoomQueueSpeedControlSupport.class);
        this.speed
                .when(() -> RoomQueueSpeedControlSupport.getEffectiveRollerIntervalMs(any()))
                .thenReturn(1000);

        this.unitComposers = mockConstruction(RoomUnitOnRollerComposer.class, (composer, context) -> {
            String label = "unitOnRoller#" + this.nextComposer();
            this.trace.add("compose " + label + "(" + this.describe(context.arguments()) + ")");
            ServerMessage message = mock(ServerMessage.class);
            this.names.put(message, label);
            when(composer.compose()).thenReturn(message);
        });
        this.furniComposers = mockConstruction(FloorItemOnRollerComposer.class, (composer, context) -> {
            String label = "furniOnRoller#" + this.nextComposer();
            this.trace.add("compose " + label + "(" + this.describe(context.arguments()) + ")");
            ServerMessage message = mock(ServerMessage.class);
            this.names.put(message, label);
            when(composer.compose()).thenReturn(message);
        });
    }

    @AfterEach
    void tearDown() {
        InteractionRoller.NO_RULES = false;
        Room.ROLLERS_MAXIMUM_ROLL_AVATARS = 1;
        this.furniComposers.close();
        this.unitComposers.close();
        this.speed.close();
        this.emulator.close();
    }

    private int composerCount = 0;

    private int nextComposer() {
        return ++this.composerCount;
    }

    // ---------------------------------------------------------------- the scenarios

    @Test
    void aRollerCarriesTheFurniOnItToAnEmptyTile() {
        InteractionRoller roller = this.roller("roller", 1, 1, 2, 0.5);
        this.item("chair", 1, 1, 0.5, 1.0);

        assertCycle(this.cycle());
    }

    @Test
    void aStackIsCarriedTopFirstAndAMultiTileItemStays() {
        this.roller("roller", 1, 1, 2, 0.5);
        this.item("low", 1, 1, 0.5, 1.0);
        this.item("high", 1, 1, 1.5, 1.0);
        HabboItem wide = this.item("wide", 0, 1, 0.5, 1.0);
        this.itemsAt(this.tile(1, 1)).add(wide);
        this.item("under the top", 1, 1, 0.2, 0.2);

        assertCycle(this.cycle());
    }

    @Test
    void aRollerCarriesAUserAndSchedulesTheWalkOnOfTheFurniInFront() {
        this.roller("roller", 1, 1, 2, 0.5);
        RoomUnit user = this.user("alice", 1, 1, 0.5);
        this.item("rug", 2, 1, 0.0, 0.1, true, false, false);
        when(user.getGoal()).thenReturn(this.tile(1, 1));

        assertCycle(this.cycle());
        assertCycleAfter("goal elsewhere", () -> when(user.getGoal()).thenReturn(this.tile(5, 5)));
    }

    @Test
    void aUserLandsOnTheSeatOfAChairInFront() {
        this.roller("roller", 1, 1, 4, 0.5);
        RoomUnit user = this.user("bob", 1, 1, 0.5);
        when(user.hasStatus(RoomUnitStatus.SIT)).thenReturn(true);
        this.item("chair", 1, 2, 0.0, 1.0, true, true, false);

        assertCycle(this.cycle());
    }

    @Test
    void aUserWhoIsWalkingOrJustRolledOrAlreadyCarriedStays() {
        this.roller("roller", 1, 1, 2, 0.5);
        RoomUnit walking = this.user("walking", 1, 1, 0.5);
        when(walking.hasStatus(RoomUnitStatus.MOVE)).thenReturn(true);
        assertCycleAfter("walking", () -> {});

        when(walking.hasStatus(RoomUnitStatus.MOVE)).thenReturn(false);
        when(walking.canBeRolled()).thenReturn(false);
        assertCycleAfter("cooling down", () -> {});
    }

    @Test
    void onlyAsManyUsersAsTheLimitAllowsAreCarriedPerTile() {
        this.roller("roller", 1, 1, 2, 0.5);
        // The tile keeps its units in a HashSet, so which of two users goes first is not fixed: both carry one name.
        this.user("a user", 1, 1, 0.5);
        this.user("a user", 1, 1, 0.5);

        assertCycleAfter("limit 1", () -> {});
        assertCycleAfter("limit 2", () -> Room.ROLLERS_MAXIMUM_ROLL_AVATARS = 2);
    }

    @Test
    void aBlockedOrOccupiedOrMissingTileInFrontStopsTheRoller() {
        this.roller("roller", 1, 1, 2, 0.5);
        this.item("box", 1, 1, 0.5, 1.0);
        this.user("carol", 1, 1, 0.5);

        assertCycleAfter("invalid", () -> this.tile(2, 1).state = RoomTileState.INVALID);
        assertCycleAfter("blocked, no stacking", () -> {
            this.tile(2, 1).state = RoomTileState.BLOCKED;
            this.tile(2, 1).setAllowStack(false);
        });
        assertCycleAfter("occupied", () -> {
            this.tile(2, 1).state = RoomTileState.OPEN;
            this.tile(2, 1).setAllowStack(true);
            this.user("dave", 2, 1, 0.0);
        });
    }

    @Test
    void theEdgeOfTheRoomStopsTheRoller() {
        this.roller("roller", 7, 1, 2, 0.5);
        this.item("box", 7, 1, 0.5, 1.0);

        assertCycle(this.cycle());
    }

    @Test
    void aSolidFurniInFrontLetsFurnitureOnButNotUsers() {
        this.roller("roller", 1, 1, 2, 0.5);
        this.item("box", 1, 1, 0.5, 1.0);
        this.user("erin", 1, 1, 0.5);
        this.item("table", 2, 1, 0.0, 1.0, false, false, false);

        assertCycle(this.cycle());
    }

    @Test
    void anOpenGateInFrontLetsUsersThrough() {
        this.roller("roller", 1, 1, 2, 0.5);
        this.user("frank", 1, 1, 0.5);
        HabboItem gate = this.typedItem(InteractionGate.class, "gate", 2, 1, 0.0, 0.0, false, false, false);
        when(gate.getExtradata()).thenReturn("1");

        assertCycleAfter("open", () -> {});
        assertCycleAfter("closed", () -> when(gate.getExtradata()).thenReturn("0"));
    }

    @Test
    void aChainOfRollersAtTheSameHeightPassesItemsAndUsersOn() {
        this.roller("first", 1, 1, 2, 0.5);
        this.roller("second", 2, 1, 2, 0.5);
        this.item("box", 1, 1, 0.5, 1.0);
        this.user("gina", 1, 1, 0.5);

        assertCycle(this.cycle());
    }

    @Test
    void aRollerInFrontAtAnotherHeightStopsTheChainUnlessRulesAreOff() {
        this.roller("first", 1, 1, 2, 0.5);
        this.roller("raised", 2, 1, 2, 0.5);
        when(this.rollers.get(2).getZ()).thenReturn(1.0);
        this.item("box", 1, 1, 0.5, 1.0);
        this.user("hank", 1, 1, 0.5);

        assertCycleAfter("rules on", () -> {});
        assertCycleAfter("rules off", () -> InteractionRoller.NO_RULES = true);
    }

    @Test
    void aRollerUnderOtherFurniInFrontStopsTheChain() {
        this.roller("first", 1, 1, 2, 0.5);
        this.roller("second", 2, 1, 2, 0.5);
        this.item("on the second", 2, 1, 0.5, 1.0);
        this.item("box", 1, 1, 0.5, 1.0);

        assertCycle(this.cycle());
    }

    @Test
    void aRiderAndTheirHorseRollTogether() {
        this.roller("roller", 1, 1, 2, 0.5);
        RoomUnit rider = this.user("ivy", 1, 1, 1.5);
        RoomUnit horse = this.unit("horse", RoomUnitType.PET, 1, 1, 0.5);
        RideablePet pet = mock(RideablePet.class);
        when(pet.getRoomUnit()).thenReturn(horse);
        when(pet.getRider()).thenReturn(this.habbos.get(rider));
        this.pets.put(horse, pet);
        when(this.habbos.get(rider).getHabboInfo().getRiding()).thenReturn(pet);

        assertCycle(this.cycle());
    }

    @Test
    void pluginsCanCancelARollForAUserOrAFurni() {
        this.registeredEvents.add(UserRolledEvent.class);
        this.registeredEvents.add(FurnitureRolledEvent.class);
        this.roller("roller", 1, 1, 2, 0.5);
        this.item("box", 1, 1, 0.5, 1.0);
        this.user("jack", 1, 1, 0.5);

        assertCycleAfter("allowed", () -> {});
        assertCycleAfter("cancelled", () -> {
            this.cancelled.add("UserRolledEvent(jack, roller, (2,1))");
            this.cancelled.add("FurnitureRolledEvent(box, roller, (2,1))");
        });
    }

    @Test
    void aSlopedTileIsSkippedInTheDirectionsThatClimbOrFallOverIt() {
        this.roller("east", 1, 1, 2, 0.5);
        this.item("box", 1, 1, 0.5, 1.0);
        this.setZ(3, 1, 1);
        this.floorAltitudes.put(key(2, 1), 0.5);

        assertCycleAfter("east, uphill", () -> {});
        assertCycleAfter("east, downhill", () -> this.setZ(3, 1, -1));
    }

    @Test
    void anEmptyRollerDoesNothingAndPyramidsChangeWhenDue() {
        this.roller("roller", 1, 1, 2, 0.5);
        InteractionPyramid due = mock(InteractionPyramid.class, withSettings().name("pyramid due"));
        InteractionPyramid later = mock(InteractionPyramid.class, withSettings().name("pyramid later"));
        when(due.getNextChange()).thenReturn(999);
        when(later.getNextChange()).thenReturn(1001);
        org.mockito.Mockito.doAnswer(call -> this.trace.add("change pyramid due"))
                .when(due)
                .change(this.room);
        org.mockito.Mockito.doAnswer(call -> this.trace.add("change pyramid later"))
                .when(later)
                .change(this.room);
        this.pyramids.add(due);
        this.pyramids.add(later);

        assertCycle(this.cycle());
    }

    // ---------------------------------------------------------------- the driver

    private String[] cycle() {
        return this.cycle(new LinkedHashSet<>());
    }

    private String[] cycle(Set<RoomUnit> updated) {
        this.trace.clear();
        this.composerCount = 0;
        for (RoomTile tile : this.tiles.values()) {
            for (RoomUnit unit : tile.getUnits()) {
                updated.add(unit);
            }
        }
        for (RoomTile tile : this.tiles.values()) {
            for (RoomUnit unit : tile.getUnits()) {
                unit.sitUpdate = false;
            }
        }

        boolean result = new RoomRollerManager(this.room).processRollerCycle(updated, 1_000_000L);

        this.trace.add("returned " + result);
        this.trace.add("still to update: "
                + listed(updated.stream().map(this::name).sorted().toList()));
        List<String> sitting = new ArrayList<>();
        for (RoomTile tile : this.tiles.values()) {
            for (RoomUnit unit : tile.getUnits()) {
                if (unit.sitUpdate) sitting.add(this.name(unit));
            }
        }
        sitting.sort(null);
        this.trace.add("sit update: " + listed(sitting));

        return this.trace.toArray(String[]::new);
    }

    private static String listed(List<String> names) {
        return names.isEmpty() ? "-" : String.join(", ", names);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.rollerTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/roller-traces.txt");
    private static final Map<String, String> RECORDING = new ConcurrentSkipListMap<>();

    private int expectation = 0;

    private void assertCycle(String[] actual) {
        this.check(Thread.currentThread().getStackTrace()[2].getMethodName(), actual);
    }

    /** Changes the room, then runs and checks one cycle under the given label. */
    private void assertCycleAfter(String label, Runnable change) {
        change.run();
        this.check(Thread.currentThread().getStackTrace()[2].getMethodName() + " / " + label, this.cycle());
    }

    private void check(String test, String[] actual) {
        String name = test + " #" + (this.expectation++);
        String trace = String.join("\n", actual);

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            RECORDING.put(name, trace);
            return;
        }

        assertEquals(expectedTraces().get(name), trace, name);
    }

    @AfterAll
    static void writeRecording() throws Exception {
        if (!Boolean.getBoolean(UPDATE_PROPERTY)) return;

        Map<String, String> traces = new TreeMap<>(RECORDING);
        StringBuilder text = new StringBuilder();
        traces.forEach((name, lines) ->
                text.append("== ").append(name).append('\n').append(lines).append("\n\n"));
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

    private void setZ(int x, int y, int z) {
        RoomTile old = this.tile(x, y);
        RoomTile replaced = new RoomTile((short) x, (short) y, (short) z, old.state, old.getAllowStack());
        this.tiles.put(key(x, y), replaced);
    }

    private List<HabboItem> itemsAt(RoomTile tile) {
        return this.itemsByTile.computeIfAbsent(key(tile.x, tile.y), k -> new ArrayList<>());
    }

    private HabboItem topItemAt(RoomTile tile) {
        HabboItem top = null;
        for (HabboItem item : this.itemsAt(tile)) {
            if (top == null || item.getZ() + Item.getCurrentHeight(item) > top.getZ() + Item.getCurrentHeight(top)) {
                top = item;
            }
        }
        return top;
    }

    private InteractionRoller roller(String name, int x, int y, int rotation, double height) {
        InteractionRoller roller = this.typedItem(InteractionRoller.class, name, x, y, 0.0, height, true, false, false);
        when(roller.getRotation()).thenReturn(rotation);
        this.rollers.put(this.rollers.size() + 1, roller);
        return roller;
    }

    private HabboItem item(String name, int x, int y, double z, double height) {
        return this.item(name, x, y, z, height, false, false, false);
    }

    private HabboItem item(String name, int x, int y, double z, double height, boolean walk, boolean sit, boolean lay) {
        return this.typedItem(HabboItem.class, name, x, y, z, height, walk, sit, lay);
    }

    private <T extends HabboItem> T typedItem(
            Class<T> type, String name, int x, int y, double z, double height, boolean walk, boolean sit, boolean lay) {
        Item base = mock(Item.class);
        when(base.getHeight()).thenReturn(height);
        when(base.allowWalk()).thenReturn(walk);
        when(base.allowSit()).thenReturn(sit);
        when(base.allowLay()).thenReturn(lay);
        T item = mock(type, withSettings().name(name));
        int id = this.nextId++;
        when(item.getId()).thenReturn(id);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getZ()).thenReturn(z);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getExtradata()).thenReturn("");
        when(item.isWalkable()).thenReturn(walk);
        try {
            org.mockito.Mockito.doAnswer(call -> {
                        Object[] data = call.getArgument(2);
                        this.trace.add(
                                "walkOn " + name + " by " + this.name(call.getArgument(0)) + " " + this.describe(data));
                        return null;
                    })
                    .when(item)
                    .onWalkOn(any(), any(), any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.itemsAt(this.tile(x, y)).add(item);
        return item;
    }

    private RoomUnit user(String name, int x, int y, double z) {
        RoomUnit unit = this.unit(name, RoomUnitType.USER, x, y, z);
        Habbo habbo = mock(Habbo.class, withSettings().name("habbo " + name));
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        this.habbos.put(unit, habbo);
        return unit;
    }

    private RoomUnit unit(String name, RoomUnitType type, int x, int y, double z) {
        RoomUnit unit = mock(RoomUnit.class, withSettings().name(name));
        RoomTile tile = this.tile(x, y);
        when(unit.getId()).thenReturn(this.nextId++);
        when(unit.getRoomUnitType()).thenReturn(type);
        when(unit.getCurrentLocation()).thenAnswer(call -> this.tile(x, y));
        when(unit.getZ()).thenReturn(z);
        when(unit.canBeRolled()).thenReturn(true);
        tile.addUnit(unit);
        return unit;
    }

    // ---------------------------------------------------------------- rendering

    private String name(Object value) {
        if (value == null) return "null";
        if (mockingDetails(value).isMock())
            return mockingDetails(value).getMockCreationSettings().getMockName().toString();
        return String.valueOf(value);
    }

    private String describe(Object value) {
        if (value == null) return "null";
        if (value instanceof RoomTile tile) return "(" + tile.x + "," + tile.y + ")";
        if (value instanceof Room) return "room";
        if (value instanceof Double number) return String.format(Locale.ROOT, "%.2f", number);
        if (value instanceof Object[] array)
            return Arrays.stream(array).map(this::describe).collect(Collectors.joining(", ", "[", "]"));
        if (value instanceof List<?> list)
            return list.stream().map(this::describe).collect(Collectors.joining(", "));
        return this.name(value);
    }

    private String describeEvent(Event event) {
        if (event instanceof UserRolledEvent rolled) {
            String unit = rolled.habbo == null
                    ? "null"
                    : this.name(this.habbos.entrySet().stream()
                            .filter(entry -> entry.getValue() == rolled.habbo)
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(null));
            return "UserRolledEvent(" + unit + ", " + this.name(rolled.roller) + ", " + this.describe(rolled.location)
                    + ")";
        }
        if (event instanceof FurnitureRolledEvent rolled) {
            return "FurnitureRolledEvent(" + this.name(rolled.furniture) + ", " + this.name(rolled.roller) + ", "
                    + this.describe(rolled.newLocation) + ")";
        }
        return event.getClass().getSimpleName();
    }
}
