package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.PluginManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Records what {@link RoomCycleManager#cycleRoomUnit} does to one unit in one cycle: the sign, the walk step, the
 * horse of a rider, sitting on a chair and laying on a bed, the packets the room receives and the state the unit is
 * left in. The traces live in {@code rooms/room-cycle-unit-traces.txt}; regenerate them with
 * {@code -Dpolaris.roomCycleUnitTraces.update=true}.
 */
class RoomCycleUnitPostureTest {
    private final List<String> trace = new ArrayList<>();
    private final Map<Long, RoomTile> tiles = new HashMap<>();
    private final Map<Long, HabboItem> chairs = new HashMap<>();
    private final Map<Long, HabboItem> tops = new HashMap<>();
    private final Map<RoomUnit, String> names = new HashMap<>();
    private boolean recording;

    private Room room;
    private RoomCycleManager cycle;
    private MockedStatic<Emulator> emulator;
    private MockedConstruction<RoomUserStatusComposer> composers;

    @BeforeEach
    void setUp() {
        for (short x = 0; x < 8; x++) {
            for (short y = 0; y < 8; y++) {
                this.tiles.put(key(x, y), new RoomTile(x, y, (short) 0, RoomTileState.OPEN, true));
            }
        }

        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTile(anyShort(), anyShort()))
                .thenAnswer(invocation -> this.tiles.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        com.eu.habbo.habbohotel.rooms.pathfinding.Pathfinder pathfinder =
                mock(com.eu.habbo.habbohotel.rooms.pathfinding.Pathfinder.class);
        when(pathfinder.findPath(any(), any(), any(), any())).thenReturn(new ArrayDeque<>());
        when(layout.getPathfinder()).thenReturn(pathfinder);
        RoomUnitManager units = mock(RoomUnitManager.class);
        doAnswer(invocation -> this.record("room.checkBedLoveEffect(" + nameOf(invocation.getArgument(0)) + ")"))
                .when(units)
                .checkBedLoveEffect(any());

        this.room = mock(Room.class);
        when(this.room.getLayout()).thenReturn(layout);
        when(this.room.getUnitManager()).thenReturn(units);
        when(this.room.getTallestChair(any(RoomTile.class)))
                .thenAnswer(invocation -> this.chairs.get(key(invocation.<RoomTile>getArgument(0))));
        when(this.room.getTopItemAt(anyInt(), anyInt()))
                .thenAnswer(invocation -> this.tops.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(this.room.canLayAt(anyInt(), anyInt())).thenReturn(true);
        when(this.room.getRoomUnitsAt(any(RoomTile.class))).thenReturn(new ArrayList<>());
        doAnswer(invocation -> this.record("room.dance(" + this.unitName(invocation.getArgument(0)) + ", "
                        + invocation.<DanceType>getArgument(1) + ")"))
                .when(this.room)
                .dance(any(RoomUnit.class), any(DanceType.class));
        doAnswer(invocation -> this.record("room <- " + nameOf(invocation.getArgument(0))))
                .when(this.room)
                .sendComposer(any());

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);
        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getPluginManager).thenReturn(plugins);

        this.composers = mockConstruction(RoomUserStatusComposer.class, (composer, context) -> {
            String unit = this.unitName(context.arguments().get(0));
            when(composer.compose())
                    .thenReturn(mock(
                            com.eu.habbo.messages.ServerMessage.class,
                            withSettings().name("Status(" + unit + ")")));
        });

        this.cycle = new RoomCycleManager(this.room);
    }

    @AfterEach
    void tearDown() {
        this.composers.close();
        this.emulator.close();
    }

    // ---------------------------------------------------------------- sign, walk, move status

    @Test
    void aSignIsSentOnceAndDropped() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.SIGN, "7");
        this.cycleUnit("sign / shown once", unit, RoomUnitType.USER, null);
    }

    @Test
    void aWalkingUnitThatStepped() {
        RoomUnit unit = this.walking("walker", 3, 3, 4, 3);
        doReturn(false).when(unit).cycle(this.room);
        this.cycleUnit("walk / stepped", unit, RoomUnitType.USER, null);
    }

    @Test
    void aWalkingUnitWithNothingToDo() {
        RoomUnit unit = this.walking("walker", 3, 3, 4, 3);
        doReturn(true).when(unit).cycle(this.room);
        this.tops.put(key(3, 3), this.item("bed", 3, 3, 0.5, false, true));
        this.cycleUnit("walk / nothing to do", unit, RoomUnitType.USER, null);
    }

    @Test
    void aStoppedUnitDropsItsMoveStatus() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.MOVE, "3,3,0.0");
        this.cycleUnit("move / dropped", unit, RoomUnitType.USER, null);
    }

    @Test
    void anAnimatedWalkKeepsItsMoveStatus() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.MOVE, "3,3,0.0");
        unit.animateWalk = true;
        this.cycleUnit("move / animated walk", unit, RoomUnitType.USER, null);
    }

    @Test
    void aPendingStatusUpdateIsReported() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.statusUpdate(true);
        this.cycleUnit("update / pending", unit, RoomUnitType.USER, null);
    }

    // ---------------------------------------------------------------- riding

    @Test
    void aRidersHorseIsPutUnderIt() {
        RoomUnit rider = this.unit("rider", 4, 4);
        rider.setZ(2.0);
        RoomUnit horse = this.unit("horse", 3, 3);
        Habbo habbo = this.habbo(rider, horse);
        this.cycleUnit("riding / horse misplaced", rider, RoomUnitType.USER, habbo);
        this.dump("riding / the misplaced horse after", horse);
    }

    @Test
    void aRidersMovingHorseStops() {
        RoomUnit rider = this.unit("rider", 4, 4);
        rider.setZ(2.0);
        RoomUnit horse = this.unit("horse", 4, 4);
        horse.setStatus(RoomUnitStatus.MOVE, "4,4,1.0");
        Habbo habbo = this.habbo(rider, horse);
        this.cycleUnit("riding / horse moving", rider, RoomUnitType.USER, habbo);
        this.dump("riding / the moving horse after", horse);
    }

    @Test
    void aRidersHorseInPlace() {
        RoomUnit rider = this.unit("rider", 4, 4);
        RoomUnit horse = this.unit("horse", 4, 4);
        Habbo habbo = this.habbo(rider, horse);
        this.cycleUnit("riding / horse in place", rider, RoomUnitType.USER, habbo);
    }

    @Test
    void aRiderOnAChairAndABedNeitherSitsNorLays() {
        RoomUnit rider = this.unit("rider", 4, 4);
        rider.setStatus(RoomUnitStatus.SIT, "0.5");
        rider.setStatus(RoomUnitStatus.LAY, "0.5");
        this.tile(4, 4).state = RoomTileState.SIT;
        HabboItem seat = this.item("chair bed", 4, 4, 0.0, true, true);
        this.chairs.put(key(4, 4), seat);
        this.tops.put(key(4, 4), seat);
        Habbo habbo = this.habbo(rider, this.unit("horse", 4, 4));
        this.cycleUnit("riding / on a chair and a bed", rider, RoomUnitType.USER, habbo);
    }

    // ---------------------------------------------------------------- sitting

    @Test
    void aUnitOnAChairSits() {
        RoomUnit unit = this.unit("user", 3, 3);
        this.tile(3, 3).state = RoomTileState.SIT;
        this.chairs.put(key(3, 3), this.item("chair", 3, 3, 0.25, true, false, 4));
        this.cycleUnit("sit / on a chair", unit, RoomUnitType.USER, null);
    }

    @Test
    void aSittingUnitIsSeatedAgain() {
        RoomUnit unit = this.unit("user", 3, 3);
        this.tile(3, 3).state = RoomTileState.SIT;
        this.chairs.put(key(3, 3), this.item("chair", 3, 3, 0.25, true, false, 2));
        unit.setStatus(RoomUnitStatus.SIT, "0.5");
        unit.sitUpdate = true;
        this.cycleUnit("sit / again after a sit update", unit, RoomUnitType.USER, null);
    }

    @Test
    void aSittingUnitStaysSeated() {
        RoomUnit unit = this.unit("user", 3, 3);
        this.tile(3, 3).state = RoomTileState.SIT;
        this.chairs.put(key(3, 3), this.item("chair", 3, 3, 0.25, true, false, 2));
        unit.setStatus(RoomUnitStatus.SIT, "0.5");
        this.cycleUnit("sit / already seated", unit, RoomUnitType.USER, null);
    }

    @Test
    void aChairOnATileThatIsNotASeat() {
        RoomUnit unit = this.unit("user", 3, 3);
        this.chairs.put(key(3, 3), this.item("chair", 3, 3, 0.25, true, false, 2));
        this.cycleUnit("sit / chair on an open tile", unit, RoomUnitType.USER, null);
    }

    @Test
    void aFurniThatIsNotASeatStandsTheUnitUp() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.SIT, "0.5");
        this.chairs.put(key(3, 3), this.item("table", 3, 3, 0.0, false, false));
        this.cycleUnit("sit / not a seat", unit, RoomUnitType.USER, null);
    }

    @Test
    void noChairStandsTheUnitUp() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.SIT, "0.5");
        this.cycleUnit("sit / no chair", unit, RoomUnitType.USER, null);
    }

    @Test
    void aFloorSitIsKept() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.SIT, RoomPostureService.FLOOR_SIT_STATUS);
        unit.cmdSit = true;
        this.cycleUnit("sit / floor sit by command", unit, RoomUnitType.USER, null);
    }

    @Test
    void aPetWithASpecialStatusDoesNotSit() {
        RoomUnit unit = this.unit("pet", 3, 3);
        unit.setStatus(RoomUnitStatus.PLAY, "");
        this.tile(3, 3).state = RoomTileState.SIT;
        this.chairs.put(key(3, 3), this.item("chair", 3, 3, 0.25, true, false, 2));
        this.cycleUnit("sit / pet playing", unit, RoomUnitType.PET, null);
    }

    @Test
    void aBotOnAChairSits() {
        RoomUnit unit = this.unit("bot", 3, 3);
        this.tile(3, 3).state = RoomTileState.SIT;
        this.chairs.put(key(3, 3), this.item("chair", 3, 3, 0.25, true, false, 6));
        this.cycleUnit("sit / a bot", unit, RoomUnitType.BOT, null);
    }

    // ---------------------------------------------------------------- laying

    @Test
    void aUnitOnABedLays() {
        RoomUnit unit = this.unit("user", 3, 3);
        this.tops.put(key(3, 3), this.item("bed", 3, 3, 0.5, false, true, 2));
        this.cycleUnit("lay / on a bed", unit, RoomUnitType.USER, null);
    }

    @Test
    void aLayingUnitStays() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.LAY, "1.0");
        this.tops.put(key(3, 3), this.item("bed", 3, 3, 0.5, false, true, 2));
        this.cycleUnit("lay / already laying", unit, RoomUnitType.USER, null);
    }

    @Test
    void noBedGetsTheUnitUp() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.LAY, "1.0");
        this.cycleUnit("lay / no bed", unit, RoomUnitType.USER, null);
    }

    @Test
    void aFurniThatIsNotABedGetsTheUnitUp() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.LAY, "1.0");
        this.tops.put(key(3, 3), this.item("rug", 3, 3, 0.0, false, false));
        this.cycleUnit("lay / not a bed", unit, RoomUnitType.USER, null);
    }

    @Test
    void aLayCommandIsKept() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.setStatus(RoomUnitStatus.LAY, "0.5");
        unit.cmdLay = true;
        this.cycleUnit("lay / by command", unit, RoomUnitType.USER, null);
    }

    @Test
    void aChairThatIsAlsoABed() {
        RoomUnit unit = this.unit("user", 3, 3);
        this.tile(3, 3).state = RoomTileState.SIT;
        HabboItem seat = this.item("sofa bed", 3, 3, 0.25, true, true, 2);
        this.chairs.put(key(3, 3), seat);
        this.tops.put(key(3, 3), seat);
        this.cycleUnit("both / a chair that is also a bed", unit, RoomUnitType.USER, null);
    }

    @Test
    void aSitCommandOnABed() {
        RoomUnit unit = this.unit("user", 3, 3);
        unit.cmdSit = true;
        this.tops.put(key(3, 3), this.item("bed", 3, 3, 0.5, false, true, 2));
        this.cycleUnit("both / sit command on a bed", unit, RoomUnitType.USER, null);
    }

    // ---------------------------------------------------------------- the driver

    private void cycleUnit(String label, RoomUnit unit, RoomUnitType type, Habbo habbo) {
        this.trace.clear();
        this.recording = true;
        try {
            this.trace.add("returned " + this.cycle.cycleRoomUnit(unit, type, habbo));
        } catch (Exception e) {
            this.trace.add("threw " + e.getClass().getSimpleName());
        } finally {
            this.recording = false;
        }
        this.trace.addAll(this.state(unit));
        this.check(label, String.join("\n", this.trace));
    }

    private void dump(String label, RoomUnit unit) {
        this.check(label, String.join("\n", this.state(unit)));
    }

    private List<String> state(RoomUnit unit) {
        List<String> lines = new ArrayList<>();
        lines.add("at " + describe(unit.getCurrentLocation()) + " z " + unit.getZ() + " facing "
                + unit.getBodyRotation() + " / " + unit.getHeadRotation() + ", previous "
                + describe(unit.getPreviousLocation()) + " z " + unit.getPreviousLocationZ() + ", goal "
                + describe(unit.getGoal()));
        lines.add("statuses "
                + new TreeMap<>(unit.getStatusMap())
                        .entrySet().stream()
                                .map(e -> e.getKey().name() + "=" + e.getValue())
                                .collect(Collectors.joining(" ")));
        lines.add("status update " + unit.needsStatusUpdate() + ", sit update " + unit.sitUpdate + ", cmd sit "
                + unit.cmdSit + ", cmd lay " + unit.cmdLay + ", animate walk " + unit.animateWalk);
        return lines;
    }

    private Object record(String line) {
        if (this.recording) this.trace.add(line);
        return null;
    }

    private RoomUnit unit(String name, int x, int y) {
        RoomUnit unit = spy(new RoomUnit());
        unit.setRoom(this.room);
        unit.setLocation(this.tile(x, y));
        this.names.put(unit, name);
        return unit;
    }

    private RoomUnit walking(String name, int x, int y, int goalX, int goalY) {
        RoomUnit unit = this.unit(name, x, y);
        try {
            java.lang.reflect.Field goal = RoomUnit.class.getDeclaredField("goalLocation");
            goal.setAccessible(true);
            goal.set(unit, this.tile(goalX, goalY));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        unit.setPath(new ArrayDeque<>(List.of(this.tile(goalX, goalY))));
        return unit;
    }

    private Habbo habbo(RoomUnit rider, RoomUnit horse) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        RideablePet pet = mock(RideablePet.class);
        when(pet.getRoomUnit()).thenReturn(horse);
        when(info.getRiding()).thenReturn(pet);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getRoomUnit()).thenReturn(rider);
        return habbo;
    }

    private HabboItem item(String name, int x, int y, double z, boolean sit, boolean lay) {
        return this.item(name, x, y, z, sit, lay, 0);
    }

    private HabboItem item(String name, int x, int y, double z, boolean sit, boolean lay, int rotation) {
        HabboItem item = mock(HabboItem.class, withSettings().name(name));
        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(1);
        when(base.getLength()).thenReturn(1);
        when(base.getHeight()).thenReturn(1.0);
        when(base.allowSit()).thenReturn(sit);
        when(base.allowLay()).thenReturn(lay);
        when(base.getMultiHeights()).thenReturn(new double[0]);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getZ()).thenReturn(z);
        when(item.getRotation()).thenReturn(rotation);
        when(item.getExtradata()).thenReturn("");
        return item;
    }

    private String unitName(Object unit) {
        return unit instanceof RoomUnit roomUnit ? this.names.getOrDefault(roomUnit, "unit") : String.valueOf(unit);
    }

    private static String nameOf(Object value) {
        if (value != null && org.mockito.Mockito.mockingDetails(value).isMock()) {
            return org.mockito.Mockito.mockingDetails(value)
                    .getMockCreationSettings()
                    .getMockName()
                    .toString();
        }
        return String.valueOf(value);
    }

    private RoomTile tile(int x, int y) {
        return this.tiles.get(key(x, y));
    }

    private static long key(Object x, Object y) {
        return ((long) ((Number) x).intValue() << 32) | (((Number) y).intValue() & 0xFFFFFFFFL);
    }

    private static long key(RoomTile tile) {
        return key(tile.x, tile.y);
    }

    private static String describe(RoomTile tile) {
        return tile == null ? "-" : tile.x + "," + tile.y;
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.roomCycleUnitTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/room-cycle-unit-traces.txt");
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
