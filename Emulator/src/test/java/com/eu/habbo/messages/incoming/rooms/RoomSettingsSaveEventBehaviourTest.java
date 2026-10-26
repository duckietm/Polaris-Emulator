package com.eu.habbo.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomChatSettingsComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomEditSettingsErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomSettingsSavedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomSettingsUpdatedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomThicknessComposer;
import com.eu.habbo.messages.outgoing.unknown.RoomCategoryUpdateMessageComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.mockito.invocation.Invocation;
import org.mockito.listeners.InvocationListener;

/**
 * Records what {@link RoomSettingsSaveEvent#handle} does with one settings packet: the owner gate, every field check
 * and its answer, which room fields are written and the messages that follow. The traces live in
 * {@code rooms/room-settings-save-traces.txt}; regenerate them with {@code -Dpolaris.roomSettingsTraces.update=true}.
 */
class RoomSettingsSaveEventBehaviourTest {
    private static final int ROOM_ID = 50;
    private static final int ALLOWED_CATEGORY = 3;
    private static final int STAFF_CATEGORY = 4;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private final InvocationListener roomListener = report -> this.recordRoom((Invocation) report.getInvocation());
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private final Map<String, String> config = new HashMap<>();
    private boolean roomLoaded;
    private boolean owner;
    private String existingPassword;

    private GameClient client;
    private Habbo habbo;
    private Room room;

    /** One settings packet; every field starts valid, a scenario changes what it is about. */
    private static final class Settings {
        int roomId = ROOM_ID;
        String name = "Lounge";
        String description = "A quiet room";
        int state = 0;
        String password = "";
        int usersMax = 25;
        int category = ALLOWED_CATEGORY;
        int tagCount = -1;
        List<String> tags = List.of("fun", "chat");
        int tradeMode = 2;
        boolean allowPets = true;
        boolean allowPetsEat = false;
        boolean allowWalkthrough = true;
        boolean hideWall = false;
        int wallSize = 0;
        int floorSize = -1;
        int muteOption = 1;
        int kickOption = 1;
        int banOption = 0;
        int chatMode = 0;
        int chatWeight = 1;
        int chatSpeed = 1;
        int chatDistance = 14;
        int chatProtection = 2;
        boolean withUnderpass = true;
        boolean allowUnderpass = true;
        boolean withIdle = true;
        boolean muteAllPets = false;
        boolean leaveOnDoorTile = true;
        boolean idleSleep = true;
        int idleSleepSeconds = 300;
        boolean idleAutokick = true;
        int idleAutokickSeconds = 900;

        ByteBuf write() {
            ByteBuf buffer = Unpooled.buffer();
            buffer.writeInt(this.roomId);
            writeString(buffer, this.name);
            writeString(buffer, this.description);
            buffer.writeInt(this.state);
            writeString(buffer, this.password);
            buffer.writeInt(this.usersMax);
            buffer.writeInt(this.category);
            buffer.writeInt(this.tagCount >= 0 ? this.tagCount : this.tags.size());
            for (String tag : this.tags) writeString(buffer, tag);
            buffer.writeInt(this.tradeMode);
            buffer.writeBoolean(this.allowPets);
            buffer.writeBoolean(this.allowPetsEat);
            buffer.writeBoolean(this.allowWalkthrough);
            buffer.writeBoolean(this.hideWall);
            buffer.writeInt(this.wallSize);
            buffer.writeInt(this.floorSize);
            buffer.writeInt(this.muteOption);
            buffer.writeInt(this.kickOption);
            buffer.writeInt(this.banOption);
            buffer.writeInt(this.chatMode);
            buffer.writeInt(this.chatWeight);
            buffer.writeInt(this.chatSpeed);
            buffer.writeInt(this.chatDistance);
            buffer.writeInt(this.chatProtection);
            if (this.withUnderpass) buffer.writeBoolean(this.allowUnderpass);
            if (this.withUnderpass && this.withIdle) {
                buffer.writeBoolean(this.muteAllPets);
                buffer.writeBoolean(this.leaveOnDoorTile);
                buffer.writeBoolean(this.idleSleep);
                buffer.writeInt(this.idleSleepSeconds);
                buffer.writeBoolean(this.idleAutokick);
                buffer.writeInt(this.idleAutokickSeconds);
            }
            return buffer;
        }
    }

    @BeforeEach
    void setUp() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getValue(anyString()))
                .thenAnswer(i -> this.config.getOrDefault(i.<String>getArgument(0), ""));
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> "%username% chose %category%");

        WordFilter wordFilter = mock(WordFilter.class);
        when(wordFilter.filter(anyString(), any()))
                .thenAnswer(i -> i.<String>getArgument(0).replace("rude", "****"));

        RoomCategory staffCategory = mock(RoomCategory.class, withSettings().name("staff category"));
        when(staffCategory.toString()).thenReturn("Staff picks");
        RoomManager rooms = mock(RoomManager.class);
        when(rooms.getRoom(anyInt()))
                .thenAnswer(i -> this.roomLoaded && i.<Integer>getArgument(0) == ROOM_ID ? this.room : null);
        when(rooms.hasCategory(anyInt(), any())).thenAnswer(i -> i.<Integer>getArgument(0) == ALLOWED_CATEGORY);
        when(rooms.getCategory(anyInt()))
                .thenAnswer(i -> i.<Integer>getArgument(0) == STAFF_CATEGORY ? staffCategory : null);

        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(environment.getWordFilter()).thenReturn(wordFilter);

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getConfig).thenReturn(configuration);
        emulator.when(Emulator::getTexts).thenReturn(texts);
        this.statics.add(emulator);

        MockedStatic<ScripterManager> scripters = mockStatic(ScripterManager.class);
        scripters
                .when(() -> ScripterManager.scripterDetected(any(), anyString()))
                .thenAnswer(i -> this.record("scripterDetected(" + i.getArgument(1) + ")"));
        this.statics.add(scripters);

        this.composer(RoomEditSettingsErrorComposer.class, "RoomEditSettingsError");
        this.composer(RoomThicknessComposer.class, "RoomThickness");
        this.composer(RoomChatSettingsComposer.class, "RoomChatSettings");
        this.composer(RoomSettingsUpdatedComposer.class, "RoomSettingsUpdated");
        this.composer(RoomSettingsSavedComposer.class, "RoomSettingsSaved");
        this.composer(RoomCategoryUpdateMessageComposer.class, "RoomCategoryUpdate");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- who may save

    @Test
    void gates() throws Exception {
        this.fresh();
        this.roomLoaded = false;
        this.save("gate / the room is not loaded", new Settings());

        this.fresh();
        this.owner = false;
        this.save("gate / not the owner", new Settings());

        this.fresh();
        this.save("gate / a valid save", new Settings());
    }

    // ---------------------------------------------------------------- name, description, state, password, capacity

    @Test
    void basics() throws Exception {
        this.fresh();
        Settings settings = new Settings();
        settings.name = "  ";
        this.save("name / blank", settings);

        this.fresh();
        settings = new Settings();
        settings.name = "n".repeat(61);
        this.save("name / 61 characters", settings);

        this.fresh();
        settings = new Settings();
        settings.name = "n".repeat(60);
        this.save("name / 60 characters", settings);

        this.fresh();
        settings = new Settings();
        settings.name = "rude room";
        this.save("name / a filtered word", settings);

        this.fresh();
        settings = new Settings();
        settings.description = "d".repeat(256);
        this.save("description / 256 characters", settings);

        this.fresh();
        settings = new Settings();
        settings.description = "a rude one";
        this.save("description / a filtered word", settings);

        this.fresh();
        settings = new Settings();
        settings.state = 4;
        this.save("state / out of range", settings);

        this.fresh();
        settings = new Settings();
        settings.state = 2;
        this.save("password / required for a new password room", settings);

        this.fresh();
        this.existingPassword = "secret";
        settings = new Settings();
        settings.state = 2;
        this.save("password / an existing one is kept", settings);

        this.fresh();
        settings = new Settings();
        settings.state = 2;
        settings.password = "hunter2";
        this.save("password / a new one is set", settings);

        this.fresh();
        settings = new Settings();
        settings.password = "p".repeat(65);
        this.save("password / 65 characters", settings);

        this.fresh();
        settings = new Settings();
        settings.usersMax = 0;
        this.save("capacity / 0", settings);

        this.fresh();
        settings = new Settings();
        settings.usersMax = 201;
        this.save("capacity / 201", settings);
    }

    // ---------------------------------------------------------------- tags and category

    @Test
    void tagsAndCategory() throws Exception {
        this.fresh();
        Settings settings = new Settings();
        settings.tagCount = 3;
        this.save("tags / three", settings);

        this.fresh();
        settings = new Settings();
        settings.tags = List.of("fun", "t".repeat(16));
        this.save("tags / one too long", settings);

        this.fresh();
        settings = new Settings();
        settings.tags = List.of("fun", "fun");
        this.save("tags / a duplicate is kept once", settings);

        this.fresh();
        settings = new Settings();
        settings.tags = List.of("fun", "rude");
        this.save("tags / a filtered word", settings);

        this.fresh();
        settings = new Settings();
        settings.tags = List.of("fun", "staffroom");
        this.save("tags / a staff tag inside another", settings);

        this.fresh();
        this.config.put("hotel.room.tags.staff", "");
        settings = new Settings();
        this.save("tags / no staff tags configured", settings);

        this.fresh();
        settings = new Settings();
        settings.tags = List.of();
        this.save("tags / none", settings);

        this.fresh();
        settings = new Settings();
        settings.category = 99;
        this.save("category / unknown", settings);

        this.fresh();
        settings = new Settings();
        settings.category = STAFF_CATEGORY;
        this.save("category / not allowed to the user", settings);
    }

    // ---------------------------------------------------------------- options and idle settings

    @Test
    void options() throws Exception {
        this.fresh();
        Settings settings = new Settings();
        settings.tradeMode = 3;
        this.save("options / trade mode out of range", settings);

        this.fresh();
        settings = new Settings();
        settings.wallSize = 2;
        this.save("options / wall thickness out of range", settings);

        this.fresh();
        settings = new Settings();
        settings.chatDistance = 0;
        this.save("options / chat distance out of range", settings);

        this.fresh();
        settings = new Settings();
        settings.idleSleepSeconds = 29;
        this.save("idle / sleep timeout too short", settings);

        this.fresh();
        settings = new Settings();
        settings.idleAutokickSeconds = 320;
        this.save("idle / kick too soon after sleep", settings);

        this.fresh();
        settings = new Settings();
        settings.idleSleep = false;
        settings.idleSleepSeconds = 5;
        settings.idleAutokick = false;
        settings.idleAutokickSeconds = 5;
        this.save("idle / switched off, timeouts ignored", settings);

        this.fresh();
        settings = new Settings();
        settings.withIdle = false;
        this.save("legacy / no idle settings", settings);

        this.fresh();
        settings = new Settings();
        settings.withUnderpass = false;
        this.save("legacy / no underpass or idle settings", settings);
    }

    // ---------------------------------------------------------------- the harness

    private void fresh() {
        this.trace.clear();
        this.config.clear();
        this.config.put("hotel.room.tags.staff", "staff;official;habbo");
        this.recording = false;
        this.roomLoaded = true;
        this.owner = true;
        this.existingPassword = "";

        this.habbo = mock(Habbo.class, withSettings().name("ann"));
        HabboInfo info = mock(HabboInfo.class);
        when(info.getUsername()).thenReturn("ann");
        when(this.habbo.getHabboInfo()).thenReturn(info);
        this.client = mock(GameClient.class, withSettings().name("client"));
        when(this.client.getHabbo()).thenReturn(this.habbo);
        doAnswer(i -> this.record("client <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(MessageComposer.class));

        this.room = mock(Room.class, withSettings().name("room").invocationListeners(this.roomListener));
        this.labels.put(this.room, "room");
        when(this.room.getId()).thenReturn(ROOM_ID);
        when(this.room.isOwner(any())).thenAnswer(i -> this.owner);
        when(this.room.getPassword()).thenAnswer(i -> this.existingPassword);
    }

    private void save(String name, Settings settings) throws Exception {
        RoomSettingsSaveEvent handler = new RoomSettingsSaveEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.RoomSettingsSaveEvent, settings.write());

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName());
        }
        this.recording = false;

        this.check(name, String.join("\n", this.trace));
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    /** Every room setter and every message sent to the room, in order. */
    private void recordRoom(Invocation invocation) {
        String method = invocation.getMethod().getName();
        if (!method.startsWith("set") && !method.equals("sendComposer")) return;

        String arguments =
                Arrays.stream(invocation.getArguments()).map(this::label).collect(Collectors.joining(", "));
        this.record("room." + method + "(" + arguments + ")");
    }

    private Object record(String line) {
        if (this.recording) this.trace.add(line);
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
        if (mockingDetails(value).isMock()) {
            return mockingDetails(value).getMockCreationSettings().getMockName().toString();
        }
        if (value instanceof Enum<?> constant) return constant.name();
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.roomSettingsTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/room-settings-save-traces.txt");
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
