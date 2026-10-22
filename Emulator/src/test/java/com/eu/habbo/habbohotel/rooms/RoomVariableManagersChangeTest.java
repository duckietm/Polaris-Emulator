package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSharedVariableChanges;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.wired.WiredUserVariablesDataComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

/**
 * Records, in order, what the user, furni and room variable managers do when a value appears, changes, is written
 * again or goes: the wired triggers, the echo to other rooms, the derived parts, the debounced snapshot broadcast
 * and who receives it. The traces live in {@code rooms/variable-manager-traces.txt}; regenerate them with
 * {@code -Dpolaris.variableManagerTraces.update=true}.
 */
class RoomVariableManagersChangeTest {
    private static final int ROOM_ID = 44;
    private static final int USER = 7;
    private static final int FURNI = 300;

    private static final int USER_VARIABLE = 10;
    private static final int USER_FLAG = 11;
    private static final int USER_LEVELLED = 12;
    private static final int FURNI_VARIABLE = 20;
    private static final int FURNI_FLAG = 21;
    private static final int FURNI_LEVELLED = 22;
    private static final int ROOM_VARIABLE = 30;
    private static final int ROOM_LEVELLED = 32;

    /** The two parts every levelled variable gets: a level (value / 3) and an is-maxed presence (value >= 8). */
    private static final int LEVEL_PART = 1;

    private static final int MAXED_PART = 2;

    private final List<String> trace = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private final RoomJdbcTestSupport.RecordingDataSource database = new RoomJdbcTestSupport.RecordingDataSource();
    private boolean recording;

    private final InteractionWiredExtra levelBox = mock(InteractionWiredExtra.class);
    private Room room;
    private ThreadPooling threading;
    private boolean threadingAvailable;
    private RoomUserVariableManager users;
    private RoomFurniVariableManager furnis;
    private RoomVariableManager rooms;

    private MockedStatic<Emulator> emulator;
    private MockedStatic<WiredManager> wired;
    private MockedStatic<WiredSharedVariableChanges> shared;
    private MockedStatic<WiredVariableLevelSystemSupport> levels;
    private MockedConstruction<WiredUserVariablesDataComposer> composers;

    @BeforeEach
    void setUp() throws Exception {
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(
                userVariable(USER_VARIABLE, "points", true),
                userVariable(USER_FLAG, "flag", false),
                userVariable(USER_LEVELLED, "xp", true),
                furniVariable(FURNI_VARIABLE, "colour", true),
                furniVariable(FURNI_FLAG, "lit", false),
                furniVariable(FURNI_LEVELLED, "wear", true),
                roomVariable(ROOM_VARIABLE, "score"),
                roomVariable(ROOM_LEVELLED, "stage")));

        this.room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(this.room.getId()).thenReturn(ROOM_ID);
        when(this.room.getWiredTimezone()).thenReturn("UTC");
        when(this.room.getRoomSpecialTypes()).thenReturn(special);
        when(this.room.isLoaded()).thenReturn(true);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(0, 0)).thenReturn(extras);
        for (InteractionWiredExtra extra : extras) {
            when(special.getExtra(extra.getId())).thenReturn(extra);
        }
        when(this.room.getArrayVariableManager()).thenReturn(mock(RoomArrayVariableManager.class));

        ConcurrentHashMap<Integer, Habbo> habbos = new ConcurrentHashMap<>();
        habbos.put(1, this.habbo("inspector", true, true));
        habbos.put(2, this.habbo("visitor", true, false));
        habbos.put(3, this.habbo("disconnected", false, true));
        when(this.room.getCurrentHabbos()).thenReturn(habbos);

        this.threading = mock(ThreadPooling.class);
        when(this.threading.run(any(Runnable.class), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> {
                    if (this.recording) this.trace.add("threading.run(task, " + invocation.getArgument(1) + ")");
                    this.pending.add(invocation.getArgument(0));
                    return null;
                });
        this.threadingAvailable = true;

        this.emulator = mockStatic(Emulator.class);
        this.emulator
                .when(Emulator::getThreading)
                .thenAnswer(invocation -> this.threadingAvailable ? this.threading : null);
        this.emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1000);
        com.eu.habbo.database.Database db = mock(com.eu.habbo.database.Database.class);
        when(db.getDataSource()).thenReturn(this.database);
        this.emulator.when(Emulator::getDatabase).thenReturn(db);

        this.wired = mockStatic(WiredManager.class, this::recordStatic);
        this.shared = mockStatic(WiredSharedVariableChanges.class, this::recordStatic);
        this.levels =
                mockStatic(WiredVariableLevelSystemSupport.class, withSettings().defaultAnswer(this::levelSupport));

        this.composers = mockConstruction(WiredUserVariablesDataComposer.class, (composer, context) -> {
            if (this.recording) this.trace.add("compose " + this.describeSnapshots(context.arguments()));
        });

        this.users = new RoomUserVariableManager(this.room, new RoomUserVariableRepository(this.database), () -> 1000);
        this.furnis = new RoomFurniVariableManager(this.room);
        this.rooms = new RoomVariableManager(this.room);
        markLoaded(RoomFurniVariableManager.class, this.furnis, "permanentAssignmentsLoaded");
        markLoaded(RoomVariableManager.class, this.rooms, "persistentValuesLoaded");
        when(this.room.getUserVariableManager()).thenReturn(this.users);
        when(this.room.getFurniVariableManager()).thenReturn(this.furnis);
        when(this.room.getRoomVariableManager()).thenReturn(this.rooms);
    }

    @AfterEach
    void tearDown() {
        this.composers.close();
        this.levels.close();
        this.shared.close();
        this.wired.close();
        this.emulator.close();
    }

    // ---------------------------------------------------------------- user variables

    @Test
    void aUserValueAppearsChangesRepeatsAndGoes() {
        this.step("user / a value appears", () -> this.users.assignVariable(USER, USER_VARIABLE, 5, true));
        this.step("user / the value rises", () -> this.users.assignVariable(USER, USER_VARIABLE, 8, true));
        this.step("user / the same value again", () -> this.users.assignVariable(USER, USER_VARIABLE, 8, true));
        this.step("user / the value falls", () -> this.users.updateVariableValue(USER, USER_VARIABLE, 3));
        this.step("user / no override keeps the value", () -> this.users.assignVariable(USER, USER_VARIABLE, 9, false));
        this.step("user / the variable goes", () -> this.users.removeVariable(USER, USER_VARIABLE));
        this.step("user / removing it again", () -> this.users.removeVariable(USER, USER_VARIABLE));
    }

    @Test
    void aUserFlagWithoutValueAppearsAndGoes() {
        this.step("user flag / appears", () -> this.users.assignVariable(USER, USER_FLAG, 5, true));
        this.step("user flag / given again", () -> this.users.assignVariable(USER, USER_FLAG, 5, true));
        this.step("user flag / goes", () -> this.users.removeVariable(USER, USER_FLAG));
    }

    @Test
    void aLevelledUserValueMovesItsParts() {
        this.step("user levelled / appears", () -> this.users.assignVariable(USER, USER_LEVELLED, 5, true));
        this.step("user levelled / reaches the top", () -> this.users.assignVariable(USER, USER_LEVELLED, 9, true));
        this.step("user levelled / drops back", () -> this.users.assignVariable(USER, USER_LEVELLED, 2, true));
        this.step("user levelled / goes", () -> this.users.removeVariable(USER, USER_LEVELLED));
    }

    // ---------------------------------------------------------------- furni variables

    @Test
    void aFurniValueAppearsChangesRepeatsAndGoes() {
        HabboItem furni = furni();
        this.step("furni / a value appears", () -> this.furnis.assignVariable(furni, FURNI_VARIABLE, 5, true));
        this.step("furni / the value rises", () -> this.furnis.assignVariable(furni, FURNI_VARIABLE, 8, true));
        this.step("furni / the same value again", () -> this.furnis.assignVariable(furni, FURNI_VARIABLE, 8, true));
        this.step("furni / the value falls", () -> this.furnis.updateVariableValue(FURNI, FURNI_VARIABLE, 3));
        this.step(
                "furni / no override keeps the value",
                () -> this.furnis.assignVariable(furni, FURNI_VARIABLE, 9, false));
        this.step("furni / the variable goes", () -> this.furnis.removeVariable(FURNI, FURNI_VARIABLE));
        this.step("furni / removing it again", () -> this.furnis.removeVariable(FURNI, FURNI_VARIABLE));
    }

    @Test
    void aFurniFlagWithoutValueAppearsAndGoes() {
        HabboItem furni = furni();
        this.step("furni flag / appears", () -> this.furnis.assignVariable(furni, FURNI_FLAG, 5, true));
        this.step("furni flag / given again", () -> this.furnis.assignVariable(furni, FURNI_FLAG, 5, true));
        this.step("furni flag / goes", () -> this.furnis.removeVariable(FURNI, FURNI_FLAG));
    }

    @Test
    void aLevelledFurniValueMovesItsParts() {
        HabboItem furni = furni();
        this.step("furni levelled / appears", () -> this.furnis.assignVariable(furni, FURNI_LEVELLED, 5, true));
        this.step("furni levelled / reaches the top", () -> this.furnis.assignVariable(furni, FURNI_LEVELLED, 9, true));
        this.step("furni levelled / drops back", () -> this.furnis.assignVariable(furni, FURNI_LEVELLED, 2, true));
        this.step("furni levelled / goes", () -> this.furnis.removeVariable(FURNI, FURNI_LEVELLED));
    }

    // ---------------------------------------------------------------- room variables

    @Test
    void aRoomValueAppearsChangesRepeatsAndGoes() {
        this.step("room / a value appears", () -> this.rooms.updateVariableValue(ROOM_VARIABLE, 5));
        this.step("room / the value rises", () -> this.rooms.updateVariableValue(ROOM_VARIABLE, 8));
        this.step("room / the same value again", () -> this.rooms.updateVariableValue(ROOM_VARIABLE, 8));
        this.step("room / the value falls", () -> this.rooms.updateVariableValue(ROOM_VARIABLE, 3));
        this.step("room / the variable goes", () -> this.rooms.removeVariable(ROOM_VARIABLE));
        this.step("room / removing it again", () -> this.rooms.removeVariable(ROOM_VARIABLE));
    }

    @Test
    void aLevelledRoomValueMovesItsParts() {
        this.step("room levelled / appears", () -> this.rooms.updateVariableValue(ROOM_LEVELLED, 5));
        this.step("room levelled / reaches the top", () -> this.rooms.updateVariableValue(ROOM_LEVELLED, 9));
        this.step("room levelled / drops back", () -> this.rooms.updateVariableValue(ROOM_LEVELLED, 2));
        this.step("room levelled / goes", () -> this.rooms.removeVariable(ROOM_LEVELLED));
    }

    // ---------------------------------------------------------------- snapshots and broadcasts

    @Test
    void eachManagerSendsTheSameSnapshotToOneViewer() {
        this.users.assignVariable(USER, USER_VARIABLE, 5, true);
        this.furnis.assignVariable(furni(), FURNI_VARIABLE, 6, true);
        this.rooms.updateVariableValue(ROOM_VARIABLE, 7);
        this.pending.clear();

        Map<String, Habbo> habbos = this.room.getCurrentHabbos().values().stream()
                .collect(Collectors.toMap(h -> h.getHabboInfo().getUsername(), h -> h));
        for (String name : List.of("inspector", "visitor", "disconnected")) {
            Habbo habbo = habbos.get(name);
            this.step("snapshot / user manager to " + name, () -> {
                this.users.sendSnapshot(habbo);
                return null;
            });
            this.step("snapshot / furni manager to " + name, () -> {
                this.furnis.sendSnapshot(habbo);
                return null;
            });
            this.step("snapshot / room manager to " + name, () -> {
                this.rooms.sendSnapshot(habbo);
                return null;
            });
        }
        this.step("snapshot / user manager to nobody", () -> {
            this.users.sendSnapshot(null);
            return null;
        });
        this.step("snapshot / furni manager to nobody", () -> {
            this.furnis.sendSnapshot(null);
            return null;
        });
        this.step("snapshot / room manager to nobody", () -> {
            this.rooms.sendSnapshot(null);
            return null;
        });
    }

    @Test
    void aBroadcastIsDebouncedAndSkippedWhenTheRoomIsNotLoaded() {
        this.step("broadcast / user requests twice", () -> {
            this.users.requestBroadcast();
            this.users.requestBroadcast();
            return this.users.getChangeVersion();
        });
        this.step("broadcast / furni requests twice", () -> {
            this.furnis.requestBroadcast();
            this.furnis.requestBroadcast();
            return null;
        });
        this.step("broadcast / room requests twice", () -> {
            this.rooms.requestBroadcast();
            this.rooms.requestBroadcast();
            return null;
        });

        when(this.room.isLoaded()).thenReturn(false);
        this.step("broadcast / user while the room unloads", () -> {
            this.users.broadcastSnapshot();
            return null;
        });
        this.step("broadcast / furni while the room unloads", () -> {
            this.furnis.broadcastSnapshot();
            return null;
        });
        this.step("broadcast / room while the room unloads", () -> {
            this.rooms.broadcastSnapshot();
            return null;
        });
    }

    @Test
    void aBroadcastWithoutThreading() {
        this.threadingAvailable = false;
        this.step("no threading / user requests", () -> {
            this.users.requestBroadcast();
            return this.users.getChangeVersion();
        });
        this.step("no threading / furni requests", () -> {
            this.furnis.requestBroadcast();
            return null;
        });
        this.step("no threading / room requests", () -> {
            this.rooms.requestBroadcast();
            return null;
        });

        this.threadingAvailable = true;
        this.step("threading back / user requests", () -> {
            this.users.requestBroadcast();
            return this.users.getChangeVersion();
        });
        this.step("threading back / furni requests", () -> {
            this.furnis.requestBroadcast();
            return null;
        });
        this.step("threading back / room requests", () -> {
            this.rooms.requestBroadcast();
            return null;
        });
    }

    // ---------------------------------------------------------------- the driver

    /** Runs one call, then the broadcast it scheduled, and compares everything that happened with the trace. */
    private void step(String label, Supplier<Object> call) {
        this.trace.clear();
        int statements = this.database.calls().size();
        this.recording = true;
        try {
            this.trace.add("returned " + call.get());
        } catch (Exception e) {
            this.trace.add("threw " + e.getClass().getSimpleName());
        }
        this.database.calls().stream()
                .skip(statements)
                .forEach(statement -> this.trace.add("sql " + statement.sql().replaceAll("\s+", " ")));
        while (!this.pending.isEmpty()) {
            Runnable task = this.pending.remove(0);
            this.trace.add("-- the scheduled task runs");
            try {
                task.run();
            } catch (Exception e) {
                this.trace.add("task threw " + e.getClass().getSimpleName());
            }
        }
        this.recording = false;
        this.check(label, String.join("\n", this.trace));
    }

    private Object recordStatic(InvocationOnMock invocation) throws Throwable {
        if (this.recording) {
            this.trace.add(invocation.getMethod().getDeclaringClass().getSimpleName() + "."
                    + invocation.getMethod().getName() + "(" + this.describe(invocation.getArguments()) + ")");
        }
        return Answers.RETURNS_DEFAULTS.answer(invocation);
    }

    /** The real level-system support, except for the levelled variables, which get a level and an is-maxed part. */
    private Object levelSupport(InvocationOnMock invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        Object[] args = invocation.getArguments();

        if (name.equals("getDerivedDefinitions") && args.length == 4) {
            WiredVariableDefinitionInfo base = (WiredVariableDefinitionInfo) args[3];
            if (base != null && isLevelled(base.getItemId())) {
                return List.of(
                        new WiredVariableDefinitionInfo(
                                part(base.getItemId(), LEVEL_PART), base.getName() + ".level", true, 0, false, true),
                        new WiredVariableDefinitionInfo(
                                part(base.getItemId(), MAXED_PART),
                                base.getName() + ".is_maxed",
                                false,
                                0,
                                false,
                                true));
            }
        }

        if (name.equals("resolveDerivedDefinition") && args.length == 3) {
            int synthetic = (Integer) args[2];
            if (synthetic >= 9000) {
                int base = (synthetic - 9000) / 10;
                int sub = (synthetic - 9000) % 10;
                return new WiredVariableLevelSystemSupport.DerivedDefinition(
                        synthetic, base, sub, "part", null, this.levelBox);
            }
        }

        if (name.equals("isPresencePart") && args.length == 1) {
            WiredVariableLevelSystemSupport.DerivedDefinition derived =
                    (WiredVariableLevelSystemSupport.DerivedDefinition) args[0];
            if (derived != null && derived.getLevelSystem() == this.levelBox) {
                return derived.getSubvariableType() == MAXED_PART;
            }
        }

        if (name.equals("getDerivedValue") && args.length == 6 && args[1] == this.levelBox) {
            Integer value = (Integer) args[3];
            if (value == null) return null;
            return ((Integer) args[2] == MAXED_PART)
                    ? (value >= 8 ? Integer.valueOf(1) : null)
                    : Integer.valueOf(value / 3);
        }

        return invocation.callRealMethod();
    }

    private static boolean isLevelled(int definitionItemId) {
        return definitionItemId == USER_LEVELLED
                || definitionItemId == FURNI_LEVELLED
                || definitionItemId == ROOM_LEVELLED;
    }

    private static int part(int base, int sub) {
        return 9000 + base * 10 + sub;
    }

    private String describe(Object[] arguments) {
        return Arrays.stream(arguments).map(this::describe).collect(Collectors.joining(", "));
    }

    private String describe(Object argument) {
        if (argument == null) return "null";
        if (argument == this.room) return "room";
        if (argument instanceof InteractionWiredExtra extra) return "extra#" + extra.getId();
        if (argument instanceof Enum<?> value) return value.name();
        return String.valueOf(argument);
    }

    private String describeSnapshots(List<?> arguments) {
        RoomUserVariableManager.Snapshot users = (RoomUserVariableManager.Snapshot) arguments.get(0);
        RoomFurniVariableManager.Snapshot furnis = (RoomFurniVariableManager.Snapshot) arguments.get(1);
        RoomVariableManager.Snapshot rooms = (RoomVariableManager.Snapshot) arguments.get(2);
        return "WiredUserVariablesDataComposer(users " + users.getDefinitions().size() + " definitions / "
                + users.getUsers().size() + " holders, furni "
                + furnis.getDefinitions().size() + " definitions / "
                + furnis.getFurnis().size() + " holders, room "
                + rooms.getDefinitions().size() + " definitions / "
                + rooms.getAssignments().size() + " values)";
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.variableManagerTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/variable-manager-traces.txt");
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

    private Habbo habbo(String name, boolean connected, boolean inspector) {
        Habbo habbo = mock(Habbo.class);
        com.eu.habbo.habbohotel.users.HabboInfo info = mock(com.eu.habbo.habbohotel.users.HabboInfo.class);
        when(info.getUsername()).thenReturn(name);
        when(habbo.getHabboInfo()).thenReturn(info);
        if (connected) {
            GameClient client = mock(GameClient.class);
            org.mockito.Mockito.doAnswer(invocation -> {
                        if (this.recording) {
                            this.trace.add(name + " <- "
                                    + invocation
                                            .getArgument(0, MessageComposer.class)
                                            .getClass()
                                            .getSimpleName()
                                            .replaceAll("\\$MockitoMock\\$.*", ""));
                        }
                        return null;
                    })
                    .when(client)
                    .sendResponse(any(MessageComposer.class));
            when(habbo.getClient()).thenReturn(client);
        }
        when(this.room.canInspectWired(habbo)).thenReturn(inspector);
        return habbo;
    }

    private static HabboItem furni() {
        HabboItem furni = mock(HabboItem.class);
        when(furni.getId()).thenReturn(FURNI);
        return furni;
    }

    private static void markLoaded(Class<?> type, Object manager, String field) throws Exception {
        java.lang.reflect.Field loaded = type.getDeclaredField(field);
        loaded.setAccessible(true);
        loaded.set(manager, true);
    }

    private static WiredExtraUserVariable userVariable(int id, String name, boolean hasValue) throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(id, 1, mock(Item.class), "", 0, 0);
        definition.loadWiredData(wiredData(name, hasValue, WiredExtraUserVariable.AVAILABILITY_ROOM), null);
        return definition;
    }

    private static WiredExtraFurniVariable furniVariable(int id, String name, boolean hasValue) throws Exception {
        WiredExtraFurniVariable definition = new WiredExtraFurniVariable(id, 1, mock(Item.class), "", 0, 0);
        definition.loadWiredData(wiredData(name, hasValue, WiredExtraFurniVariable.AVAILABILITY_ROOM_ACTIVE), null);
        return definition;
    }

    private static WiredExtraRoomVariable roomVariable(int id, String name) throws Exception {
        WiredExtraRoomVariable definition = new WiredExtraRoomVariable(id, 1, mock(Item.class), "", 0, 0);
        definition.loadWiredData(wiredData(name, true, WiredExtraRoomVariable.AVAILABILITY_ROOM_ACTIVE), null);
        return definition;
    }

    private static ResultSet wiredData(String name, boolean hasValue, int availability) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"" + name + "\",\"hasValue\":" + hasValue + ",\"availability\":"
                        + availability + "}");
        return set;
    }
}
