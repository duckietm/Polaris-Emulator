package com.eu.habbo.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.habbohotel.guides.GuideManager;
import com.eu.habbo.habbohotel.modtool.CfhActionType;
import com.eu.habbo.habbohotel.modtool.CfhTopic;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolTicketState;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.modtool.BullyReportedMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.HelperRequestDisabledComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueHandledComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;
import com.eu.habbo.messages.outgoing.modtool.ReportRoomFormComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.InsertModToolIssue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Records what {@link ReportEvent#handle} does with one call for help: the gates, a report of a user (online, offline,
 * to the guardians) or of a room, the ticket it opens and what the delayed automatic answer does 30 seconds later.
 * The traces live in {@code modtool/report-traces.txt}; regenerate them with {@code -Dpolaris.reportTraces.update=true}.
 */
class ReportEventBehaviourTest {
    private static final int REPORTER_ID = 7;
    private static final int REPORTED_ID = 8;
    private static final int ROOM_ID = 50;
    private static final int TOPIC_MODS = 1;
    private static final int TOPIC_AUTO_REPLY = 2;
    private static final int TOPIC_AUTO_IGNORE = 3;
    private static final int TOPIC_GUARDIANS = 4;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private final List<Runnable> delayed = new ArrayList<>();
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private boolean allowTalk;
    private boolean pendingTicket;
    private boolean reportedOnline;
    private boolean reportedKnown;
    private boolean roomLoaded;
    private boolean guardiansActive;
    private boolean alreadyReported;
    private boolean roomChat;
    private boolean closeBeforeTimer;
    private boolean reporterLeftBeforeTimer;

    private GameClient client;
    private Habbo reporter;
    private Habbo reported;

    @BeforeEach
    void setUp() throws Exception {
        Map<Integer, CfhTopic> topics = Map.of(
                TOPIC_MODS, topic(CfhActionType.MODS, "Thanks, a moderator will look at it"),
                TOPIC_AUTO_REPLY, topic(CfhActionType.AUTO_REPLY, "Read the hotel way"),
                TOPIC_AUTO_IGNORE, topic(CfhActionType.AUTO_IGNORE, "We ignored them for you"),
                TOPIC_GUARDIANS, topic(CfhActionType.GUARDIANS, "The guardians are on it"));

        ModToolManager modTools = mock(ModToolManager.class);
        when(modTools.getCfhTopic(anyInt())).thenAnswer(i -> topics.get(i.<Integer>getArgument(0)));
        when(modTools.openTicketsForHabbo(any())).thenAnswer(i -> {
            List<ModToolIssue> open = new ArrayList<>();
            if (this.pendingTicket)
                open.add(mock(ModToolIssue.class, withSettings().name("pending ticket")));
            return open;
        });
        when(modTools.getRoomChatlog(anyInt())).thenAnswer(i -> {
            ArrayList<ModToolChatLog> log = new ArrayList<>();
            if (this.roomChat) log.add(mock(ModToolChatLog.class));
            return log;
        });
        doAnswer(i -> this.record("modtools.addTicket(" + this.label(i.getArgument(0)) + ")"))
                .when(modTools)
                .addTicket(any());
        doAnswer(i -> this.record("modtools.updateTicketToMods(" + this.label(i.getArgument(0)) + ")"))
                .when(modTools)
                .updateTicketToMods(any());
        doAnswer(i -> this.record("modtools.closeTicketAsHandled(" + this.label(i.getArgument(0)) + ")"))
                .when(modTools)
                .closeTicketAsHandled(any(), any());

        GuideManager guides = mock(GuideManager.class);
        when(guides.activeGuardians()).thenAnswer(i -> this.guardiansActive);
        when(guides.getOpenReportedHabboTicket(any()))
                .thenAnswer(i -> this.alreadyReported ? mock(GuardianTicket.class) : null);
        doAnswer(i -> this.record("guides.addGuardianTicket(" + this.label(i.getArgument(0)) + ")"))
                .when(guides)
                .addGuardianTicket(any());

        HabboManager habbos = mock(HabboManager.class);
        when(habbos.getHabbo(anyInt()))
                .thenAnswer(
                        i -> this.reportedOnline && i.<Integer>getArgument(0) == REPORTED_ID ? this.reported : null);

        Room room = mock(Room.class, withSettings().name("room"));
        when(room.getOwnerId()).thenReturn(REPORTED_ID);
        when(room.getOwnerName()).thenReturn("bob");
        RoomManager rooms = mock(RoomManager.class);
        when(rooms.getRoom(anyInt()))
                .thenAnswer(i -> this.roomLoaded && i.<Integer>getArgument(0) == ROOM_ID ? room : null);

        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getModToolManager()).thenReturn(modTools);
        when(environment.getGuideManager()).thenReturn(guides);
        when(environment.getHabboManager()).thenReturn(habbos);
        when(environment.getRoomManager()).thenReturn(rooms);

        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(i -> {
            this.record("threading.run(answer, " + i.getArgument(1) + " ms)");
            this.delayed.add(i.getArgument(0));
            return null;
        });

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1_700_000_000);
        this.statics.add(emulator);

        MockedStatic<HabboManager> offline = mockStatic(HabboManager.class);
        offline.when(() -> HabboManager.getOfflineHabboInfo(anyInt())).thenAnswer(i -> {
            if (!this.reportedKnown || i.<Integer>getArgument(0) != REPORTED_ID) return null;
            HabboInfo info = mock(HabboInfo.class, withSettings().name("bob's stored info"));
            when(info.getId()).thenReturn(REPORTED_ID);
            when(info.getUsername()).thenReturn("bob");
            return info;
        });
        this.statics.add(offline);

        this.constructions.add(mockConstruction(InsertModToolIssue.class, (insert, context) -> {
            ModToolIssue issue = (ModToolIssue) context.arguments().get(0);
            doAnswer(i -> this.record("InsertModToolIssue(" + this.label(issue) + ").run()"))
                    .when(insert)
                    .run();
        }));
        this.constructions.add(mockConstruction(
                GuardianTicket.class,
                (ticket, context) -> this.labels.put(
                        ticket,
                        "GuardianTicket("
                                + context.arguments().stream()
                                        .limit(2)
                                        .map(this::label)
                                        .collect(Collectors.joining(", "))
                                + ")")));
        this.composer(BullyReportedMessageComposer.class, "BullyReported");
        this.composer(HelperRequestDisabledComposer.class, "HelperRequestDisabled");
        this.composer(ModToolIssueHandledComposer.class, "ModToolIssueHandled");
        this.composer(ModToolReportReceivedAlertComposer.class, "ModToolReportReceivedAlert");
        this.composer(ReportRoomFormComposer.class, "ReportRoomForm");
        this.composer(RoomUserIgnoredComposer.class, "RoomUserIgnored");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- who may report what

    @Test
    void gates() throws Exception {
        this.fresh();
        this.allowTalk = false;
        this.report("gate / a muted reporter", "he spams", TOPIC_MODS, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.report("gate / an empty message", "   ", TOPIC_MODS, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.report("gate / no topic", "he spams", 0, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.report("gate / a negative user", "he spams", TOPIC_MODS, -5, ROOM_ID, "", "");

        this.fresh();
        this.report("gate / no room", "he spams", TOPIC_MODS, REPORTED_ID, 0, "", "");

        this.fresh();
        this.report("gate / oneself", "me", TOPIC_MODS, REPORTER_ID, ROOM_ID, "", "");

        this.fresh();
        this.report("gate / an unknown topic", "he spams", 99, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.pendingTicket = true;
        this.report("gate / a ticket still open", "he spams", TOPIC_MODS, REPORTED_ID, ROOM_ID, "", "");
    }

    // ---------------------------------------------------------------- reporting a user

    @Test
    void users() throws Exception {
        this.fresh();
        this.report("user / to the moderators", "he spams", TOPIC_MODS, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.report("user / with the reporter's contact", "he spams", TOPIC_MODS, REPORTED_ID, ROOM_ID, "Ann", "a@b.c");

        this.fresh();
        this.report("user / an automatic reply", "he spams", TOPIC_AUTO_REPLY, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.report("user / automatically ignored", "he spams", TOPIC_AUTO_IGNORE, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.closeBeforeTimer = true;
        this.report(
                "user / handled before the automatic answer",
                "he spams",
                TOPIC_AUTO_IGNORE,
                REPORTED_ID,
                ROOM_ID,
                "",
                "");

        this.fresh();
        this.reporterLeftBeforeTimer = true;
        this.report(
                "user / the reporter left before the automatic answer",
                "he spams",
                TOPIC_AUTO_IGNORE,
                REPORTED_ID,
                ROOM_ID,
                "",
                "");

        this.fresh();
        this.reportedOnline = false;
        this.report("user / offline", "he spams", TOPIC_MODS, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.reportedOnline = false;
        this.reportedKnown = false;
        this.report("user / nobody with that id", "he spams", TOPIC_MODS, REPORTED_ID, ROOM_ID, "", "");
    }

    // ---------------------------------------------------------------- guardians

    @Test
    void guardians() throws Exception {
        this.fresh();
        this.guardiansActive = true;
        this.report("guardians / a new case", "bully", TOPIC_GUARDIANS, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.guardiansActive = true;
        this.alreadyReported = true;
        this.report("guardians / already reported", "bully", TOPIC_GUARDIANS, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.guardiansActive = true;
        this.roomChat = false;
        this.report("guardians / no chat in the room", "bully", TOPIC_GUARDIANS, REPORTED_ID, ROOM_ID, "", "");

        this.fresh();
        this.report("guardians / none on duty", "bully", TOPIC_GUARDIANS, REPORTED_ID, ROOM_ID, "", "");
    }

    // ---------------------------------------------------------------- reporting a room

    @Test
    void rooms() throws Exception {
        this.fresh();
        this.report("room / to the moderators", "bad room", TOPIC_MODS, -1, ROOM_ID, "", "");

        this.fresh();
        this.report("room / automatically ignored", "bad room", TOPIC_AUTO_IGNORE, -1, ROOM_ID, "", "");

        this.fresh();
        this.roomLoaded = false;
        this.report("room / not loaded", "bad room", TOPIC_MODS, -1, ROOM_ID, "", "");

        this.fresh();
        this.reporterLeftBeforeTimer = true;
        this.report(
                "room / the reporter left before the automatic answer",
                "bad room",
                TOPIC_AUTO_REPLY,
                -1,
                ROOM_ID,
                "",
                "");
    }

    // ---------------------------------------------------------------- the harness

    private void fresh() {
        this.trace.clear();
        this.delayed.clear();
        this.issues.clear();
        this.recording = false;
        this.allowTalk = true;
        this.pendingTicket = false;
        this.reportedOnline = true;
        this.reportedKnown = true;
        this.roomLoaded = true;
        this.guardiansActive = false;
        this.alreadyReported = false;
        this.roomChat = true;
        this.closeBeforeTimer = false;
        this.reporterLeftBeforeTimer = false;

        HabboStats stats = mock(HabboStats.class);
        when(stats.allowTalk()).thenAnswer(i -> this.allowTalk);
        when(stats.ignoreUser(any(), anyInt())).thenAnswer(i -> {
            this.record("ann.ignoreUser(" + i.getArgument(1) + ")");
            return true;
        });
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(REPORTER_ID);
        when(info.getUsername()).thenReturn("ann");
        this.reporter = mock(Habbo.class, withSettings().name("ann"));
        when(this.reporter.getHabboInfo()).thenReturn(info);
        when(this.reporter.getHabboStats()).thenReturn(stats);

        HabboInfo reportedInfo = mock(HabboInfo.class);
        when(reportedInfo.getId()).thenReturn(REPORTED_ID);
        when(reportedInfo.getUsername()).thenReturn("bob");
        this.reported = mock(Habbo.class, withSettings().name("bob"));
        when(this.reported.getHabboInfo()).thenReturn(reportedInfo);

        this.client = mock(GameClient.class);
        Habbo[] attached = {this.reporter};
        when(this.client.getHabbo()).thenAnswer(i -> attached[0]);
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(MessageComposer.class));
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(ServerMessage.class));
        this.leave = () -> attached[0] = null;
    }

    private Runnable leave;

    private void report(
            String name, String message, int topic, int userId, int roomId, String contactName, String contactEmail) {
        ByteBuf buffer = Unpooled.buffer();
        writeString(buffer, message);
        buffer.writeInt(topic);
        buffer.writeInt(userId);
        buffer.writeInt(roomId);
        buffer.writeInt(0);
        if (!contactName.isEmpty() || !contactEmail.isEmpty()) {
            writeString(buffer, contactName);
            writeString(buffer, contactEmail);
        }
        ReportEvent handler = new ReportEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.ReportEvent, buffer);

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName());
        }
        if (this.closeBeforeTimer) this.openIssues().forEach(issue -> issue.state = ModToolTicketState.CLOSED);
        if (this.reporterLeftBeforeTimer) this.leave.run();
        for (Runnable task : new ArrayList<>(this.delayed)) {
            this.record("-- 30 seconds later");
            try {
                task.run();
            } catch (RuntimeException e) {
                this.record("the answer threw " + e.getClass().getSimpleName());
            }
        }
        this.recording = false;

        this.check(name, String.join("\n", this.trace));
    }

    private final List<ModToolIssue> issues = new ArrayList<>();

    private List<ModToolIssue> openIssues() {
        return this.issues;
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static CfhTopic topic(CfhActionType action, String reply) throws Exception {
        CfhTopic topic = mock(CfhTopic.class);
        set(topic, "action", action);
        set(topic, "reply", reply);
        return topic;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = CfhTopic.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
        if (value instanceof ModToolIssue issue && !mockingDetails(value).isMock()) {
            if (!this.issues.contains(issue)) this.issues.add(issue);
            return "ticket " + issue.type + " " + issue.senderUsername + "->" + issue.reportedUsername + "#"
                    + issue.reportedId + " room " + issue.roomId + " topic " + issue.category + " \"" + issue.message
                    + "\"";
        }
        if (mockingDetails(value).isMock()) {
            return mockingDetails(value).getMockCreationSettings().getMockName().toString();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::label).collect(Collectors.joining(", ", "[", "]"));
        }
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.reportTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/modtool/report-traces.txt");
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
