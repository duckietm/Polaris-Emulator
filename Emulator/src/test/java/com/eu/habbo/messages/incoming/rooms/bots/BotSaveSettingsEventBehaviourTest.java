package com.eu.habbo.messages.incoming.rooms.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BotErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.BotForceOpenContextMenuComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDanceComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.eu.habbo.messages.outgoing.unknown.UnknownRoomDesktopComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.bots.BotSavedChatEvent;
import com.eu.habbo.plugin.events.bots.BotSavedLookEvent;
import com.eu.habbo.plugin.events.bots.BotSavedNameEvent;
import com.eu.habbo.threading.ThreadPooling;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import org.mockito.invocation.Invocation;
import org.mockito.listeners.InvocationListener;

/**
 * Records what {@link BotSaveSettingsEvent#handle} does with one bot setting: the owner and action gates, the look,
 * chat, walk, dance, name, motto and rotate actions, the plugin events that may cancel or change them, and the menu
 * the owner gets back. The traces live in {@code rooms/bots/bot-save-settings-traces.txt}; regenerate them with
 * {@code -Dpolaris.botSettingsTraces.update=true}.
 */
class BotSaveSettingsEventBehaviourTest {
    private static final int OWNER_ID = 7;
    private static final int BOT_ID = 12;

    private static final Set<String> RECORDED = Set.of(
            "setFigure",
            "setGender",
            "setEffect",
            "needsUpdate",
            "clearChat",
            "setChatAuto",
            "setChatRandom",
            "setChatDelay",
            "addChatLines",
            "setCanWalk",
            "setDanceType",
            "setName",
            "setMotto",
            "setRotation",
            "onPostOwnerAction",
            "sendComposer",
            "run");

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private final InvocationListener listener = report -> this.recordCall((Invocation) report.getInvocation());
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private final Set<String> permissions = new HashSet<>();
    private final Map<String, Boolean> flags = new HashMap<>();
    private boolean inRoom;
    private int roomOwner;
    private boolean botThere;
    private short[] actions;
    private boolean slotFree;
    private boolean needsUpdate;
    private boolean walks;
    private DanceType dance;
    private RoomUserRotation rotation;
    private boolean cancelEvents;
    private String renamedTo;

    private GameClient client;
    private Room room;
    private Bot bot;

    @BeforeEach
    void setUp() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        when(config.getBoolean(anyString(), anyBoolean()))
                .thenAnswer(i -> this.flags.getOrDefault(i.<String>getArgument(0), i.<Boolean>getArgument(1)));

        PluginManager plugins = mock(PluginManager.class);
        when(plugins.fireEvent(any())).thenAnswer(i -> {
            Event event = i.getArgument(0);
            if (event instanceof BotSavedLookEvent look) {
                this.record(
                        "plugins.fire(BotSavedLook " + look.newLook + ", " + look.gender + ", " + look.effect + ")");
            } else if (event instanceof BotSavedChatEvent chat) {
                this.record("plugins.fire(BotSavedChat auto " + chat.autoChat + ", random " + chat.randomChat
                        + ", delay " + chat.chatDelay + ", " + chat.chat + ")");
            } else if (event instanceof BotSavedNameEvent name) {
                this.record("plugins.fire(BotSavedName " + name.name + ")");
                if (this.renamedTo != null) name.name = this.renamedTo;
            }
            if (this.cancelEvents) event.setCancelled(true);
            return event;
        });

        WordFilter wordFilter = mock(WordFilter.class);
        when(wordFilter.filter(anyString(), any()))
                .thenAnswer(i -> i.<String>getArgument(0).replace("rude", "****"));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getWordFilter()).thenReturn(wordFilter);

        ThreadPooling threading = this.traced(ThreadPooling.class, "threading");

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        this.statics.add(emulator);

        this.composer(BotErrorComposer.class, "BotError");
        this.composer(BotForceOpenContextMenuComposer.class, "BotForceOpenContextMenu");
        this.composer(RoomUserDanceComposer.class, "RoomUserDance");
        this.composer(RoomUserNameChangedComposer.class, "RoomUserNameChanged");
        this.composer(RoomUserStatusComposer.class, "RoomUserStatus");
        this.composer(RoomUsersComposer.class, "RoomUsers");
        this.composer(UnknownRoomDesktopComposer.class, "BotSkillListUpdate");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- who may change what

    @Test
    void gates() throws Exception {
        this.fresh();
        this.inRoom = false;
        this.save("gate / not in a room", 3, null);

        this.fresh();
        this.roomOwner = 99;
        this.save("gate / someone else's room", 3, null);

        this.fresh();
        this.roomOwner = 99;
        this.permissions.add(Permission.ACC_ANYROOMOWNER);
        this.save("gate / staff in someone else's room", 3, null);

        this.fresh();
        this.botThere = false;
        this.save("gate / the bot is not in the room", 3, null);

        this.fresh();
        this.actions = new short[] {1, 2};
        this.save("gate / an action the bot does not offer", 3, null);

        this.fresh();
        this.slotFree = false;
        this.save("gate / too soon after the last action", 3, null);
    }

    // ---------------------------------------------------------------- look, walk, dance, rotate, menu

    @Test
    void simpleActions() throws Exception {
        this.fresh();
        this.save("look / copies the owner's look", 1, null);

        this.fresh();
        this.cancelEvents = true;
        this.save("look / a plugin cancels it", 1, null);

        this.fresh();
        this.save("walk / switched off", 3, null);

        this.fresh();
        this.walks = false;
        this.save("walk / switched on", 3, null);

        this.fresh();
        this.flags.put(BotSaveSettingsEvent.REOPEN_MENU_SETTING, false);
        this.save("walk / without reopening the menu", 3, null);

        this.fresh();
        this.save("dance / the next dance", 4, null);

        this.fresh();
        this.dance = DanceType.values()[DanceType.values().length - 1];
        this.save("dance / back to no dance", 4, null);

        this.fresh();
        this.save("rotate / two steps further", Bot.ACTION_ROTATE, null);
    }

    // ---------------------------------------------------------------- chat

    @Test
    void chat() throws Exception {
        this.fresh();
        this.save("chat / two lines, auto, random, delay 9", 2, "hello\rhow are you;#;true;#;9;#;true");

        this.fresh();
        this.save("chat / a filtered word", 2, "you are rude;#;false;#;7;#;false");

        this.fresh();
        this.permissions.add(Permission.ACC_CHAT_NO_FILTER);
        this.save("chat / an owner who may skip the filter", 2, "you are rude;#;false;#;7;#;false");

        this.fresh();
        this.save("chat / HTML is reduced to its text", 2, "<b>bold</b>;#;false;#;7;#;false");

        this.fresh();
        this.save("chat / HTML nested beyond five levels", 2, "&amp;amp;amp;amp;amp;amp;lt;;#;false;#;7;#;false");

        this.fresh();
        this.save(
                "chat / lines past the length budget",
                2,
                "a".repeat(100) + "\r" + "b".repeat(30) + "\rc;#;false;#;7;#;false");

        this.fresh();
        this.save("chat / a delay that is not a number", 2, "hi;#;false;#;fast;#;false");

        this.fresh();
        this.save("chat / a delay out of range", 2, "hi;#;false;#;100000;#;false");

        this.fresh();
        this.save("chat / fewer than three settings", 2, "hi;#;true");

        this.fresh();
        this.save("chat / over the packet limit", 2, "x".repeat(5113));

        this.fresh();
        this.cancelEvents = true;
        this.save("chat / a plugin cancels it", 2, "hi;#;false;#;7;#;false");
    }

    // ---------------------------------------------------------------- name and motto

    @Test
    void nameAndMotto() throws Exception {
        this.fresh();
        this.save("name / a new name", 5, "Robbie");

        this.fresh();
        this.save("name / too long", 5, "n".repeat(16));

        this.fresh();
        this.save("name / with a tag", 5, "<b>x");

        this.fresh();
        this.save("name / a filtered word", 5, "rudebot");

        this.fresh();
        this.save("name / empty", 5, "");

        this.fresh();
        this.renamedTo = "Plugged";
        this.save("name / a plugin renames it", 5, "Robbie");

        this.fresh();
        this.cancelEvents = true;
        this.save("name / a plugin cancels it", 5, "Robbie");

        this.fresh();
        this.save("motto / a new motto", 9, "Ask me anything");

        this.fresh();
        this.save("motto / too long", 9, "m".repeat(39));

        this.fresh();
        this.save("motto / a filtered word", 9, "so rude");

        this.fresh();
        this.save("motto / with a tag", 9, "<b>hi</b>");
    }

    // ---------------------------------------------------------------- the harness

    private void fresh() {
        this.trace.clear();
        this.permissions.clear();
        this.flags.clear();
        this.recording = false;
        this.inRoom = true;
        this.roomOwner = OWNER_ID;
        this.botThere = true;
        this.actions = new short[] {1, 2, 3, 4, 5, 9, (short) Bot.ACTION_ROTATE};
        this.slotFree = true;
        this.needsUpdate = false;
        this.walks = true;
        this.dance = DanceType.NONE;
        this.rotation = RoomUserRotation.NORTH;
        this.cancelEvents = false;
        this.renamedTo = null;

        RoomUnit botUnit = this.traced(RoomUnit.class, "botUnit");
        when(botUnit.getId()).thenReturn(40);
        when(botUnit.getDanceType()).thenAnswer(i -> this.dance);
        when(botUnit.getBodyRotation()).thenAnswer(i -> this.rotation);

        this.bot = this.traced(Bot.class, "bot");
        when(this.bot.getId()).thenReturn(BOT_ID);
        when(this.bot.getRoomUnit()).thenReturn(botUnit);
        when(this.bot.getOwnerActionIds()).thenAnswer(i -> this.actions);
        when(this.bot.tryAcquireOwnerActionSlot()).thenAnswer(i -> this.slotFree);
        when(this.bot.canWalk()).thenAnswer(i -> this.walks);
        when(this.bot.needsUpdate()).thenAnswer(i -> this.needsUpdate);
        doAnswer(i -> {
                    this.needsUpdate = i.getArgument(0);
                    return null;
                })
                .when(this.bot)
                .needsUpdate(anyBoolean());

        this.room = this.traced(Room.class, "room");
        when(this.room.getOwnerId()).thenAnswer(i -> this.roomOwner);
        when(this.room.getBot(anyInt()))
                .thenAnswer(i -> this.botThere && i.<Integer>getArgument(0) == BOT_ID ? this.bot : null);

        RoomUnit ownerUnit = mock(RoomUnit.class);
        when(ownerUnit.getEffectId()).thenReturn(33);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(OWNER_ID);
        when(info.getGender()).thenReturn(HabboGender.F);
        when(info.getLook()).thenReturn("hd-600-1");
        when(info.getCurrentRoom()).thenAnswer(i -> this.inRoom ? this.room : null);
        Habbo habbo = mock(Habbo.class, withSettings().name("ann"));
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getRoomUnit()).thenReturn(ownerUnit);
        when(habbo.hasPermission(anyString())).thenAnswer(i -> this.permissions.contains(i.<String>getArgument(0)));
        this.client = mock(GameClient.class);
        when(this.client.getHabbo()).thenReturn(habbo);
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(MessageComposer.class));
    }

    private void save(String name, int settingId, String text) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(-BOT_ID);
        buffer.writeInt(settingId);
        if (text != null) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            buffer.writeShort(bytes.length);
            buffer.writeBytes(bytes);
        }
        BotSaveSettingsEvent handler = new BotSaveSettingsEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.BotSaveSettingsEvent, buffer);

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName());
        }
        this.recording = false;

        this.check(name, String.join("\n", this.trace));
    }

    private <T> T traced(Class<T> type, String name) {
        T mock = mock(type, withSettings().name(name).invocationListeners(this.listener));
        this.labels.put(mock, name);
        return mock;
    }

    private void recordCall(Invocation invocation) {
        String method = invocation.getMethod().getName();
        if (!RECORDED.contains(method)) return;
        if (method.equals("needsUpdate") && invocation.getArguments().length == 0) return;

        String target = mockingDetails(invocation.getMock())
                .getMockCreationSettings()
                .getMockName()
                .toString();
        String arguments =
                Arrays.stream(invocation.getArguments()).map(this::label).collect(Collectors.joining(", "));
        this.record(target + "." + method + "(" + arguments + ")");
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
        if (value instanceof Map<?, ?> map) return new TreeMap<>(map).toString();
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::label).collect(Collectors.joining(", ", "[", "]"));
        }
        if (value instanceof Enum<?> constant) return constant.name();
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.botSettingsTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/bots/bot-save-settings-traces.txt");
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
