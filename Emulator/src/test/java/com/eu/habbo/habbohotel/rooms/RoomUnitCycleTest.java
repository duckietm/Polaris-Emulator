package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.interfaces.ConditionalGate;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.pathfinding.Pathfinder;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.eu.habbo.threading.ThreadPooling;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Records one tick of {@link RoomUnit#cycle(Room)} - a walking user, pet or bot - as what it does to the world
 * (plugin events, furni walk calls, status packets, door kick) and the state the unit is left in. The traces live in
 * {@code rooms/room-unit-cycle-traces.txt}; regenerate them with {@code -Dpolaris.roomUnitCycleTraces.update=true}.
 */
class RoomUnitCycleTest {
    private final List<String> trace = new ArrayList<>();
    private final Map<Long, RoomTile> tiles = new HashMap<>();
    private final Set<Class<?>> registeredEvents = new HashSet<>();
    private final Set<Class<?>> cancelledEvents = new HashSet<>();
    private final Map<Long, HabboItem> walkable = new HashMap<>();
    private final Map<Long, HabboItem> top = new HashMap<>();
    private final Map<Long, Set<HabboItem>> itemsAt = new HashMap<>();
    private final Set<Long> unwalkable = new HashSet<>();
    private final Set<Long> sitTiles = new HashSet<>();
    private final Set<Long> layTiles = new HashSet<>();
    private boolean recording;
    private boolean leaveOnDoor;
    private boolean publicRoom;
    private boolean publicDoorKicks;

    private Room room;
    private RoomLayout layout;
    private MockedStatic<Emulator> emulator;
    private MockedConstruction<RoomUserStatusComposer> composers;

    @BeforeEach
    void setUp() {
        for (short x = 0; x < 8; x++) {
            for (short y = 0; y < 8; y++) {
                this.tiles.put(key(x, y), new RoomTile(x, y, (short) 0, RoomTileState.OPEN, true));
            }
        }

        this.layout = mock(RoomLayout.class);
        when(this.layout.getTile(anyShortValue(), anyShortValue()))
                .thenAnswer(invocation -> this.tiles.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(this.layout.getHeightAtSquare(anyInt(), anyInt())).thenReturn((short) 0);
        when(this.layout.getDoorX()).thenReturn((short) 0);
        when(this.layout.getDoorY()).thenReturn((short) 0);
        Pathfinder pathfinder = mock(Pathfinder.class);
        when(pathfinder.findPath(any(), any(), any(), any())).thenReturn(new ArrayDeque<>());
        when(this.layout.getPathfinder()).thenReturn(pathfinder);

        this.room = mock(Room.class);
        RoomItemManager items = mock(RoomItemManager.class);
        when(this.room.getLayout()).thenReturn(this.layout);
        when(this.room.getItemManager()).thenReturn(items);
        when(items.getWalkableItemAt(anyInt(), anyInt()))
                .thenAnswer(invocation -> this.walkable.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(this.room.getTopItemAt(anyInt(), anyInt()))
                .thenAnswer(invocation -> this.top.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(this.room.getItemsAt(any(RoomTile.class)))
                .thenAnswer(invocation ->
                        new HashSet<>(this.itemsAt.getOrDefault(key(invocation.getArgument(0)), Set.of())));
        when(this.room.tileWalkable(any(RoomTile.class)))
                .thenAnswer(invocation -> !this.unwalkable.contains(key(invocation.getArgument(0))));
        when(this.room.canSitAt(anyInt(), anyInt()))
                .thenAnswer(invocation ->
                        this.sitTiles.contains(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(this.room.canLayAt(anyInt(), anyInt()))
                .thenAnswer(invocation ->
                        this.layTiles.contains(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(this.room.getTallestChair(any(RoomTile.class)))
                .thenAnswer(invocation -> this.walkable.get(key(invocation.getArgument(0))));
        when(this.room.isLeaveOnDoorTileEnabled()).thenAnswer(invocation -> this.leaveOnDoor);
        when(this.room.isPublicRoom()).thenAnswer(invocation -> this.publicRoom);
        doAnswer(invocation -> this.record("room.unIdle(" + name(invocation.getArgument(0)) + ")"))
                .when(this.room)
                .unIdle(any());
        doAnswer(invocation -> this.record("room.sendComposer(..)"))
                .when(this.room)
                .sendComposer(any());

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean()))
                .thenAnswer(invocation -> this.registeredEvents.contains(invocation.<Class<?>>getArgument(0)));
        when(plugins.fireEvent(any())).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (this.cancelledEvents.contains(event.getClass())) event.setCancelled(true);
            this.record(
                    "plugin event " + event.getClass().getSimpleName() + (event.isCancelled() ? " (cancelled)" : ""));
            return event;
        });
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(eq("hotel.room.public.doortile.kick"))).thenAnswer(invocation -> this.publicDoorKicks);
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(1));
        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            this.record("threading.run(" + invocation.getArgument(0).getClass().getSimpleName() + ", "
                    + invocation.getArgument(1) + ")");
            return null;
        });

        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        this.emulator.when(Emulator::getConfig).thenReturn(config);
        this.emulator.when(Emulator::getThreading).thenReturn(threading);

        this.composers = mockConstruction(RoomUserStatusComposer.class, (composer, context) -> {
            Object unit = context.arguments().get(0);
            this.record("compose RoomUserStatusComposer(" + this.unitName(unit) + ")");
        });
    }

    @AfterEach
    void tearDown() {
        this.composers.close();
        this.emulator.close();
    }

    // ---------------------------------------------------------------- not walking

    @Test
    void aUnitAtItsGoalWithoutAMoveStatusStops() {
        RoomUnit unit = this.unit(RoomUnitType.USER, 3, 3);
        this.tick("at goal / no move status", unit);
    }

    @Test
    void aUnitAtItsGoalWithAMoveStatusDropsItAndItsWalkingStatuses() {
        RoomUnit unit = this.unit(RoomUnitType.USER, 3, 3);
        unit.setStatus(RoomUnitStatus.MOVE, "4,3,0.0");
        unit.setStatus(RoomUnitStatus.SIT, "0.5");
        unit.setStatus(RoomUnitStatus.WAVE, "");
        unit.setStatus(RoomUnitStatus.JUMP, "");
        this.tick("at goal / with a move status", unit);
    }

    @Test
    void aRiderAtItsGoalAlsoStopsItsPet() {
        RoomUnit unit = this.unit(RoomUnitType.USER, 3, 3);
        RoomUnit petUnit = this.unit(RoomUnitType.PET, 3, 3);
        petUnit.setStatus(RoomUnitStatus.MOVE, "3,3,1.0");
        this.habbo("rider", unit, this.rideable(petUnit, null));
        this.tick("at goal / a rider stops its pet", unit);
        this.dump("at goal / the pet of the rider", petUnit);
    }

    @Test
    void aKickedUnitAtItsGoalStillCycles() {
        RoomUnit unit = this.unit(RoomUnitType.USER, 3, 3);
        unit.isKicked = true;
        this.tick("at goal / kicked", unit);
    }

    // ---------------------------------------------------------------- plain steps

    @Test
    void aPlainStep() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        unit.setStatus(RoomUnitStatus.LAY, "0.5");
        unit.setStatus(RoomUnitStatus.DEAD, "");
        unit.setStatus(RoomUnitStatus.SIGN, "3");
        this.habbo("walker", unit, null);
        this.tick("step / plain", unit);
    }

    @Test
    void aBotStep() {
        RoomUnit unit = this.walking(RoomUnitType.BOT, 3, 3, 4, 4);
        this.tick("step / a bot", unit);
    }

    @Test
    void anEmptyPathStops() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        unit.setPath(new ArrayDeque<>());
        this.tick("step / empty path", unit);
    }

    @Test
    void theLastStepOntoAnOccupiedTileWaits() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.tile(4, 3).addUnit(this.unit(RoomUnitType.USER, 4, 3));
        this.tick("step / last tile occupied", unit);
    }

    @Test
    void theLastStepOntoAnOccupiedTileTheUnitMayOverride() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.tile(4, 3).addUnit(this.unit(RoomUnitType.USER, 4, 3));
        unit.addOverrideTile(this.tile(4, 3));
        this.tick("step / last tile occupied, overridable", unit);
    }

    @Test
    void fastWalkSkipsATile() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3, 5, 3, 6, 3);
        unit.setFastWalk(true);
        this.habbo("runner", unit, null);
        this.tick("step / fast walk", unit);
    }

    @Test
    void fastWalkOnTheLastTwoTilesIsAPlainStep() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3, 5, 3);
        unit.setFastWalk(true);
        this.tick("step / fast walk, two tiles left", unit);
    }

    @Test
    void anUnwalkableTileStopsTheWalk() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3, 5, 3);
        this.unwalkable.add(key(4, 3));
        this.tick("step / unwalkable tile", unit);
    }

    @Test
    void aTileTooHighStopsTheWalk() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3, 5, 3);
        this.tile(4, 3).setStackHeight(3.0);
        this.tick("step / tile too high", unit);
    }

    @Test
    void anUnwalkableTileTheUnitMayOverride() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3, 5, 3);
        this.unwalkable.add(key(4, 3));
        unit.addOverrideTile(this.tile(4, 3));
        this.tick("step / unwalkable tile, overridable", unit);
    }

    // ---------------------------------------------------------------- plugins and idle

    @Test
    void anIdleUserWakesUpWhenWalking() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        unit.setIdle();
        this.habbo("sleeper", unit, null);
        this.tick("plugins / idle user walks", unit);
    }

    @Test
    void aPluginKeepsTheUserIdle() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        unit.setIdle();
        this.habbo("sleeper", unit, null);
        this.cancelledEvents.add(UserIdleEvent.class);
        this.tick("plugins / idle event cancelled", unit);
    }

    @Test
    void aPluginCancelsTheStep() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.habbo("walker", unit, null);
        this.registeredEvents.add(UserTakeStepEvent.class);
        this.cancelledEvents.add(UserTakeStepEvent.class);
        this.tick("plugins / step cancelled", unit);
    }

    @Test
    void aPluginHearsTheStep() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.habbo("walker", unit, null);
        this.registeredEvents.add(UserTakeStepEvent.class);
        this.tick("plugins / step heard", unit);
    }

    // ---------------------------------------------------------------- sitting and laying goals

    @Test
    void aChairGoalWithinReachIsSatOn() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.tile(4, 3).state = RoomTileState.SIT;
        this.sitTiles.add(key(4, 3));
        this.walkable.put(key(4, 3), this.item("chair", 4, 3, 0.0, true, false));
        this.tick("goal / a chair", unit);
    }

    @Test
    void aSitGoalWithoutAChairStops() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.tile(4, 3).state = RoomTileState.SIT;
        this.tick("goal / a sit tile without a chair", unit);
    }

    @Test
    void aChairTooHighStops() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.tile(4, 3).state = RoomTileState.SIT;
        this.sitTiles.add(key(4, 3));
        this.walkable.put(key(4, 3), this.item("tall chair", 4, 3, 3.0, true, false));
        this.tick("goal / a chair too high", unit);
    }

    @Test
    void aRiderDoesNotWalkOntoABed() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.tile(4, 3).state = RoomTileState.LAY;
        this.layTiles.add(key(4, 3));
        this.walkable.put(key(4, 3), this.item("bed", 4, 3, 0.0, false, true));
        this.habbo("rider", unit, this.rideable(this.unit(RoomUnitType.PET, 3, 3), null));
        this.tick("goal / a rider and a bed", unit);
    }

    // ---------------------------------------------------------------- furni underfoot

    @Test
    void walkingOffOneFurniOntoAnother() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        this.habbo("walker", unit, null);
        this.top.put(key(3, 3), this.item("rug", 3, 3, 0.0, false, false));
        this.walkable.put(key(4, 3), this.item("plate", 4, 3, 0.5, false, false));
        this.tick("furni / off one onto another", unit);
    }

    @Test
    void walkingAlongOneFurni() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        HabboItem carpet = this.item("carpet", 3, 3, 0.0, false, false);
        when(carpet.getBaseItem().getWidth()).thenReturn(2);
        this.top.put(key(3, 3), carpet);
        this.walkable.put(key(4, 3), carpet);
        this.tick("furni / along one", unit);
    }

    @Test
    void aGateRefusesTheUser() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3, 5, 3);
        this.habbo("walker", unit, null);
        HabboItem gate = mock(HabboItem.class, withSettings().name("gate").extraInterfaces(ConditionalGate.class));
        this.describeItem(gate, "gate", 4, 3, 0.0, false, false);
        when(gate.canWalkOn(any(), any(), any())).thenReturn(false);
        doAnswer(invocation -> this.record("gate.onRejected"))
                .when((ConditionalGate) gate)
                .onRejected(any(), any(), any());
        this.walkable.put(key(4, 3), gate);
        this.tick("furni / a gate refuses", unit);
    }

    @Test
    void aClosedFurniThatIsNotAGateIsWalkedThrough() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        HabboItem closed = this.item("closed", 4, 3, 0.0, false, false);
        when(closed.canWalkOn(any(), any(), any())).thenReturn(false);
        this.walkable.put(key(4, 3), closed);
        this.tick("furni / closed but not a gate", unit);
    }

    @Test
    void aStackHelperSetsTheHeight() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        InteractionStackWalkHelper helper = mock(InteractionStackWalkHelper.class);
        when(helper.getZ()).thenReturn(2.5);
        this.itemsAt.put(key(4, 3), Set.of(helper));
        this.tick("furni / stack helper", unit);
    }

    // ---------------------------------------------------------------- riding

    @Test
    void aRiderCarriesItsPet() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 4, 5, 5, 6, 6);
        unit.setFastWalk(true);
        RoomUnit petUnit = this.unit(RoomUnitType.PET, 3, 3);
        petUnit.setRoom(this.room);
        this.habbo("rider", unit, this.rideable(petUnit, null));
        this.tick("riding / the rider walks", unit);
        this.dump("riding / the pet after the rider walked", petUnit);
    }

    @Test
    void aPetFollowsItsRider() {
        RoomUnit petUnit = this.unit(RoomUnitType.PET, 3, 3);
        RoomUnit riderUnit = this.unit(RoomUnitType.USER, 4, 3);
        riderUnit.setPreviousLocation(this.tile(3, 3));
        riderUnit.setStatus(RoomUnitStatus.MOVE, "4,3,1.0");
        Habbo rider = this.habbo("rider", riderUnit, null);
        RideablePet pet = this.rideable(petUnit, rider);
        when(this.room.getPet(petUnit)).thenReturn(pet);
        this.tick("riding / the pet follows", petUnit);
    }

    @Test
    void aPetWithItsRiderInPlaceDropsTheMove() {
        RoomUnit petUnit = this.unit(RoomUnitType.PET, 3, 3);
        petUnit.setStatus(RoomUnitStatus.MOVE, "3,3,0.0");
        RoomUnit riderUnit = this.unit(RoomUnitType.USER, 3, 3);
        Habbo rider = this.habbo("rider", riderUnit, null);
        RideablePet pet = this.rideable(petUnit, rider);
        when(this.room.getPet(petUnit)).thenReturn(pet);
        this.tick("riding / the pet waits", petUnit);
    }

    // ---------------------------------------------------------------- the door

    @Test
    void stepOntoTheDoorKicks() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 1, 0, 0, 0);
        this.habbo("leaver", unit, null);
        this.leaveOnDoor = true;
        this.tick("door / kicks", unit);
    }

    @Test
    void aPublicRoomDoorKicksOnlyWithTheSetting() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 1, 0, 0, 0);
        this.habbo("leaver", unit, null);
        this.leaveOnDoor = true;
        this.publicRoom = true;
        this.tick("door / public room without the setting", unit);
    }

    @Test
    void aPublicRoomDoorKicksWithTheSetting() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 1, 0, 0, 0);
        this.habbo("leaver", unit, null);
        this.leaveOnDoor = true;
        this.publicRoom = true;
        this.publicDoorKicks = true;
        this.tick("door / public room with the setting", unit);
    }

    @Test
    void aUnitThatMayNotLeaveByTheDoorStays() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 1, 0, 0, 0);
        unit.setCanLeaveRoomByDoor(false);
        this.habbo("leaver", unit, null);
        this.leaveOnDoor = true;
        this.tick("door / may not leave", unit);
    }

    @Test
    void aFurniOnTheDoorThatInvalidatesTheKick() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 1, 0, 0, 0);
        this.habbo("leaver", unit, null);
        this.leaveOnDoor = true;
        HabboItem mat = this.item("mat", 0, 0, 0.0, false, false);
        when(mat.invalidatesToRoomKick()).thenReturn(true);
        this.top.put(key(0, 0), mat);
        this.tick("door / a furni keeps the user", unit);
    }

    // ---------------------------------------------------------------- failures

    @Test
    void anExceptionIsLoggedAndCountsAsAStep() {
        RoomUnit unit = this.walking(RoomUnitType.USER, 3, 3, 4, 3);
        when(this.room.getItemManager()).thenReturn(null);
        this.tick("failure / no item manager", unit);
    }

    // ---------------------------------------------------------------- the driver

    private final Map<RoomUnit, String> names = new HashMap<>();

    private void tick(String label, RoomUnit unit) {
        this.trace.clear();
        this.recording = true;
        try {
            this.trace.add("returned " + unit.cycle(this.room));
        } finally {
            this.recording = false;
        }
        this.trace.add("wired location " + describe(unit.getWiredLocation()));
        this.trace.addAll(this.state(unit));
        this.check(label, String.join("\n", this.trace));
    }

    private void dump(String label, RoomUnit unit) {
        this.check(label, String.join("\n", this.state(unit)));
    }

    private List<String> state(RoomUnit unit) {
        List<String> lines = new ArrayList<>();
        lines.add("at " + describe(unit.getCurrentLocation()) + " z " + unit.getZ() + " facing "
                + unit.getBodyRotation() + " / " + unit.getHeadRotation());
        lines.add("previous " + describe(unit.getPreviousLocation()) + " z " + unit.getPreviousLocationZ() + ", goal "
                + describe(unit.getGoal()));
        lines.add("path "
                + unit.getPath().stream().map(RoomUnitCycleTest::describe).collect(Collectors.joining(" ")));
        lines.add("statuses "
                + new TreeMap<>(unit.getStatusMap())
                        .entrySet().stream()
                                .map(e -> e.getKey().name() + "=" + e.getValue())
                                .collect(Collectors.joining(" ")));
        lines.add("tiles walked " + field(unit, "tilesWalked") + ", status update " + unit.needsStatusUpdate()
                + ", sit update " + unit.sitUpdate + ", idle " + unit.isIdle());
        return lines;
    }

    private Object record(String line) {
        if (this.recording) this.trace.add(line);
        return null;
    }

    private RoomUnit unit(RoomUnitType type, int x, int y) {
        RoomUnit unit = new RoomUnit();
        unit.setRoomUnitType(type);
        unit.setRoom(this.room);
        unit.setLocation(this.tile(x, y));
        this.names.put(unit, type.name().toLowerCase() + "@" + x + "," + y);
        return unit;
    }

    /** A unit at (x, y) whose path runs through the given tiles and whose goal is the last of them. */
    private RoomUnit walking(RoomUnitType type, int x, int y, int... steps) {
        RoomUnit unit = this.unit(type, x, y);
        ArrayDeque<RoomTile> path = new ArrayDeque<>();
        for (int i = 0; i < steps.length; i += 2) path.add(this.tile(steps[i], steps[i + 1]));
        setField(unit, "goalLocation", path.getLast());
        unit.setPath(path);
        return unit;
    }

    private Habbo habbo(String name, RoomUnit unit, RideablePet riding) {
        Habbo habbo = mock(Habbo.class, withSettings().name(name));
        HabboInfo info = mock(HabboInfo.class);
        when(info.getUsername()).thenReturn(name);
        when(info.getRiding()).thenReturn(riding);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getRoomUnit()).thenReturn(unit);
        when(this.room.getHabbo(unit)).thenReturn(habbo);
        return habbo;
    }

    private RideablePet rideable(RoomUnit unit, Habbo rider) {
        RideablePet pet = mock(RideablePet.class);
        when(pet.getRoomUnit()).thenReturn(unit);
        when(pet.getRider()).thenReturn(rider);
        return pet;
    }

    private HabboItem item(String name, int x, int y, double z, boolean sit, boolean lay) {
        HabboItem item = mock(HabboItem.class, withSettings().name(name));
        this.describeItem(item, name, x, y, z, sit, lay);
        when(item.canWalkOn(any(), any(), any())).thenReturn(true);
        return item;
    }

    private void describeItem(HabboItem item, String name, int x, int y, double z, boolean sit, boolean lay) {
        Item base = mock(Item.class);
        when(base.getWidth()).thenReturn(1);
        when(base.getLength()).thenReturn(1);
        when(base.getHeight()).thenReturn(1.0);
        when(base.allowSit()).thenReturn(sit);
        when(base.allowLay()).thenReturn(lay);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getZ()).thenReturn(z);
        Consumer<String> walk = call -> this.record(name + "." + call);
        try {
            this.recordWalks(item, walk);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void recordWalks(HabboItem item, Consumer<String> walk) throws Exception {
        doAnswer(invocation -> {
                    walk.accept("onWalkOn" + this.walkArguments(invocation.getArgument(2)));
                    return null;
                })
                .when(item)
                .onWalkOn(any(), any(), any());
        doAnswer(invocation -> {
                    walk.accept("onWalkOff" + this.walkArguments(invocation.getArgument(2)));
                    return null;
                })
                .when(item)
                .onWalkOff(any(), any(), any());
        doAnswer(invocation -> {
                    walk.accept("onWalk" + this.walkArguments(invocation.getArgument(2)));
                    return null;
                })
                .when(item)
                .onWalk(any(), any(), any());
    }

    private String walkArguments(Object[] arguments) {
        if (arguments == null) return "(null)";
        return "("
                + java.util.Arrays.stream(arguments)
                        .map(a -> a instanceof RoomTile tile ? describe(tile) : String.valueOf(a))
                        .collect(Collectors.joining(", "))
                + ")";
    }

    private String unitName(Object unit) {
        return unit instanceof RoomUnit roomUnit ? this.names.getOrDefault(roomUnit, "unit") : String.valueOf(unit);
    }

    private String name(Object habbo) {
        return habbo instanceof Habbo h ? h.getHabboInfo().getUsername() : String.valueOf(habbo);
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

    private static short anyShortValue() {
        return org.mockito.ArgumentMatchers.anyShort();
    }

    private static String describe(RoomTile tile) {
        return tile == null ? "-" : tile.x + "," + tile.y;
    }

    private static Object field(Object target, String name) {
        try {
            Field field = RoomUnit.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = RoomUnit.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.roomUnitCycleTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/room-unit-cycle-traces.txt");
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
