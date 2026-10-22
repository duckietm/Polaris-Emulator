package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionMuteArea;
import com.eu.habbo.habbohotel.items.interactions.InteractionTalkingFurniture;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.quests.QuestProgressEvents;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserShoutComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTalkComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTypingComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import com.eu.habbo.messages.outgoing.users.MutedWhisperComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.users.UsernameTalkEvent;
import com.eu.habbo.threading.ThreadPooling;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Records what {@link RoomChatManager#talk(Habbo, RoomChatMessage, RoomChatType, boolean)} does with one chat line:
 * the gates it passes or stops at, the packets each person in the room receives, the wired, command, quest, plugin
 * and bot calls, and the state it leaves the speaker in. The traces live in {@code rooms/room-chat-traces.txt};
 * regenerate them with {@code -Dpolaris.roomChatTraces.update=true}.
 */
class RoomChatManagerTalkTest {
    private final List<String> trace = new ArrayList<>();
    private final Map<String, Habbo> people = new LinkedHashMap<>();
    private final Map<Habbo, Set<String>> permissions = new HashMap<>();
    private final Set<Habbo> withRights = new HashSet<>();
    private final Set<Class<?>> registeredEvents = new HashSet<>();
    private final Map<String, Object> settings = new HashMap<>();
    private final Set<HabboItem> muteAreas = new LinkedHashSet<>();
    private final Set<HabboItem> talkingFurni = new LinkedHashSet<>();
    private final Int2ObjectOpenHashMap<Bot> bots = new Int2ObjectOpenHashMap<>();
    private boolean recording;
    private boolean commandHandled;
    private boolean suppressSays;
    private boolean raid;
    private boolean roomMuted;
    private int chatProtection;
    private Rectangle tent;
    private ServerMessage customPrefix;

    private Room room;
    private RoomChatManager chat;
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        this.room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomUnitManager units = mock(RoomUnitManager.class);
        when(this.room.getRoomSpecialTypes()).thenReturn(special);
        when(this.room.getLayout()).thenReturn(layout);
        when(this.room.getUnitManager()).thenReturn(units);
        when(units.getCurrentBots()).thenReturn(this.bots);
        when(layout.getTile(org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyShort()))
                .thenAnswer(invocation -> tile(invocation.getArgument(0), invocation.getArgument(1)));
        when(special.getItemsOfType(InteractionMuteArea.class)).thenReturn(this.muteAreas);
        when(special.getItemsOfType(InteractionTalkingFurniture.class)).thenReturn(this.talkingFurni);
        when(special.tentAt(any())).thenAnswer(invocation -> this.tent);
        when(this.room.getHabbos()).thenAnswer(invocation -> new ArrayList<>(this.people.values()));
        when(this.room.getCurrentHabbos()).thenAnswer(invocation -> {
            java.util.concurrent.ConcurrentHashMap<Integer, Habbo> map = new java.util.concurrent.ConcurrentHashMap<>();
            int id = 1;
            for (Habbo habbo : this.people.values()) map.put(id++, habbo);
            return map;
        });
        when(this.room.hasRights(any(Habbo.class)))
                .thenAnswer(invocation -> this.withRights.contains(invocation.getArgument(0)));
        when(this.room.isOwner(any(Habbo.class))).thenReturn(false);
        when(this.room.isMuted()).thenAnswer(invocation -> this.roomMuted);
        when(this.room.getChatProtection()).thenAnswer(invocation -> this.chatProtection);
        when(this.room.getChatDistance()).thenReturn(5);
        doAnswer(invocation -> this.record("room.unIdle(" + nameOf(invocation.getArgument(0)) + ")"))
                .when(this.room)
                .unIdle(any());
        doAnswer(invocation -> this.record("room <- " + packet(invocation.getArgument(0))))
                .when(this.room)
                .sendComposer(any());
        doAnswer(invocation -> this.record("room.updateItemState(" + nameOf(invocation.getArgument(0)) + ")"))
                .when(this.room)
                .updateItemState(any());

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString()))
                .thenAnswer(invocation -> (Boolean) this.settings.getOrDefault(invocation.getArgument(0), false));
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(invocation ->
                (Boolean) this.settings.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
        when(config.getInt(anyString()))
                .thenAnswer(invocation -> (Integer) this.settings.getOrDefault(invocation.getArgument(0), 0));
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation ->
                (Integer) this.settings.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
        when(config.getValue(anyString(), anyString()))
                .thenAnswer(invocation -> String.valueOf(
                        this.settings.getOrDefault(invocation.getArgument(0), invocation.getArgument(1))));
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(invocation -> "text:" + invocation.getArgument(0));
        when(texts.getValue(anyString(), anyString())).thenAnswer(invocation -> "text:" + invocation.getArgument(0));
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean()))
                .thenAnswer(invocation -> this.registeredEvents.contains(invocation.<Class<?>>getArgument(0)));
        when(plugins.fireEvent(any())).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof UsernameTalkEvent talkEvent && this.customPrefix != null) {
                talkEvent.setCustomComposer(this.customPrefix);
            }
            this.record("plugin event " + event.getClass().getSimpleName());
            return event;
        });
        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class))).thenAnswer(invocation -> {
            this.record("threading.run(" + invocation.getArgument(0).getClass().getSimpleName() + ")");
            return null;
        });
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            this.record("threading.run(task, " + invocation.getArgument(1) + ")");
            return null;
        });
        Random random = mock(Random.class);
        when(random.nextInt(anyInt())).thenReturn(1);

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getTexts).thenReturn(texts);
        emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        emulator.when(Emulator::getRandom).thenReturn(random);
        emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1000);
        this.statics.add(emulator);

        MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class);
        platform.when(WiredPlatform::configuration).thenReturn(config);
        this.statics.add(platform);

        MockedStatic<CommandHandler> commands = mockStatic(CommandHandler.class);
        commands.when(() -> CommandHandler.handleChatCommand(any(), anyString()))
                .thenAnswer(invocation -> {
                    this.record("CommandHandler.handleChatCommand(" + invocation.getArgument(1) + ")");
                    return this.commandHandled;
                });
        this.statics.add(commands);

        this.statics.add(mockStatic(WiredManager.class, invocation -> {
            this.record("WiredManager." + invocation.getMethod().getName() + "(" + describe(invocation.getArguments())
                    + ")");
            return invocation.getMethod().getName().equals("shouldSuppressUserSaysOutput") ? this.suppressSays : false;
        }));
        this.statics.add(mockStatic(QuestProgressEvents.class, invocation -> {
            this.record("QuestProgressEvents." + invocation.getMethod().getName() + "("
                    + describe(invocation.getArguments()) + ")");
            return null;
        }));

        RaidProtectionManager raids = mock(RaidProtectionManager.class);
        when(raids.onChat(any(), any())).thenAnswer(invocation -> this.raid);
        MockedStatic<RaidProtectionManager> raidStatic = mockStatic(RaidProtectionManager.class);
        raidStatic.when(RaidProtectionManager::get).thenReturn(raids);
        this.statics.add(raidStatic);

        this.composer(RoomUserTalkComposer.class, "Talk");
        this.composer(RoomUserShoutComposer.class, "Shout");
        this.composer(RoomUserWhisperComposer.class, "Whisper");
        this.composer(RoomUserTypingComposer.class, "Typing");
        this.composer(RoomUserNameChangedComposer.class, "NameChanged");
        this.composer(MutedWhisperComposer.class, "MutedWhisper");

        RoomChatManager.HABBO_CHAT_DELAY = false;
        RoomChatManager.MUTEAREA_CAN_WHISPER = false;
        this.chat = new RoomChatManager(this.room, 2, new Int2IntOpenHashMap());
    }

    @AfterEach
    void tearDown() {
        RoomChatManager.HABBO_CHAT_DELAY = false;
        RoomChatManager.MUTEAREA_CAN_WHISPER = false;
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- gates before the line

    @Test
    void aUserWhoMayNotTalk() {
        Habbo speaker = this.person("speaker", 3, 3);
        when(speaker.getHabboStats().allowTalk()).thenReturn(false);
        this.say("gate / may not talk", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void anInvisibleUserIsStopped() {
        Habbo speaker = this.person("speaker", 3, 3);
        when(speaker.getRoomUnit().isInvisible()).thenReturn(true);
        this.settings.put("invisible.prevent.chat", true);
        this.say("gate / invisible, a line", speaker, "hello", RoomChatType.TALK);
        this.commandHandled = true;
        this.say("gate / invisible, a command", speaker, ":sit", RoomChatType.TALK);
    }

    @Test
    void anInvisibleUserTalksWhenAllowed() {
        Habbo speaker = this.person("speaker", 3, 3);
        when(speaker.getRoomUnit().isInvisible()).thenReturn(true);
        this.say("gate / invisible, allowed", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void aUserInAnotherRoom() {
        Habbo speaker = this.person("speaker", 3, 3);
        when(speaker.getHabboInfo().getCurrentRoom()).thenReturn(mock(Room.class));
        this.say("gate / another room", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void theChatDelay() {
        Habbo speaker = this.person("speaker", 3, 3);
        RoomChatManager.HABBO_CHAT_DELAY = true;
        speaker.getHabboStats().lastChat = System.currentTimeMillis() + 60_000;
        this.say("gate / chat delay", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void anEmptyLineOnlyStopsTyping() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("listener", 4, 3);
        this.say("gate / empty line", speaker, "", RoomChatType.TALK);
    }

    @Test
    void anIdleUserWakesUp() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.say("gate / alone in the room, idle event", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void raidProtectionTakesTheLine() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.raid = true;
        this.say("gate / raid protection", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void aMuteArea() {
        Habbo speaker = this.person("speaker", 3, 3);
        HabboItem area = mock(InteractionMuteArea.class);
        when(((InteractionMuteArea) area).inSquare(any())).thenReturn(true);
        this.muteAreas.add(area);
        this.say("gate / mute area, talk", speaker, "hello", RoomChatType.TALK);
        this.say("gate / mute area, whisper", speaker, "hello", RoomChatType.WHISPER);
        RoomChatManager.MUTEAREA_CAN_WHISPER = true;
        this.say("gate / mute area, whisper allowed", speaker, "hello", RoomChatType.WHISPER);
        this.grant(speaker, Permission.ACC_NOMUTE);
        this.say("gate / mute area, no-mute permission", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void theRoomWordFilter() throws Exception {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("listener", 4, 3);
        filterWords(this.chat).add("darn");
        this.say("filter / masked", speaker, "Darn it, darn", RoomChatType.TALK);
        this.grant(speaker, Permission.ACC_CHAT_NO_FILTER);
        this.say("filter / speaker exempt", speaker, "darn it", RoomChatType.TALK);
    }

    @Test
    void mutes() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("listener", 4, 3);
        this.roomMuted = true;
        this.say("mute / room muted", speaker, "hello", RoomChatType.TALK);
        this.withRights.add(speaker);
        this.say("mute / room muted, speaker has rights", speaker, "hello", RoomChatType.TALK);
        this.withRights.remove(speaker);
        this.roomMuted = false;
        this.chat.getMutedHabbos().put(speaker.getHabboInfo().getId(), 1060);
        this.say("mute / speaker muted", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void floodProtection() {
        Habbo speaker = this.person("speaker", 3, 3);
        speaker.getHabboStats().chatCounter.set(3);
        this.say("flood / fourth line, protection 0", speaker, "hello", RoomChatType.TALK);

        Habbo second = this.person("second", 5, 5);
        this.chatProtection = 1;
        second.getHabboStats().chatCounter.set(3);
        this.say("flood / fourth line, protection 1", second, "hello", RoomChatType.TALK);
        this.say("flood / fifth line, protection 1", second, "hello", RoomChatType.TALK);

        Habbo third = this.person("third", 6, 6);
        this.withRights.add(third);
        this.chatProtection = 0;
        third.getHabboStats().chatCounter.set(3);
        this.say("flood / rights without flood.with.rights", third, "hello", RoomChatType.TALK);
    }

    @Test
    void floodBudgetMode() throws Exception {
        Habbo speaker = this.person("speaker", 3, 3);
        this.settings.put("hotel.chat.flood.mode", "budget");
        // The budget refills on a wall-clock grid; a scripted one keeps the trace independent of timing.
        ChatFloodBudget budget = mock(ChatFloodBudget.class);
        when(budget.tryTake(anyLong())).thenReturn(true, false);
        floodBudgets(this.chat).put(speaker.getHabboInfo().getId(), budget);
        this.say("flood budget / within the budget", speaker, "hello", RoomChatType.TALK);
        this.say("flood budget / over the budget", speaker, "hello", RoomChatType.TALK);

        Habbo owner = this.person("owner", 4, 4);
        this.withRights.add(owner);
        ChatFloodBudget empty = mock(ChatFloodBudget.class);
        floodBudgets(this.chat).put(owner.getHabboInfo().getId(), empty);
        this.say("flood budget / rights without flood.with.rights", owner, "hello", RoomChatType.TALK);
        this.settings.put("flood.with.rights", true);
        this.say("flood budget / rights with flood.with.rights", owner, "hello", RoomChatType.TALK);
    }

    @Test
    void thePirateSong() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.settings.put("easter_eggs.enabled", true);
        this.say("pirate / first", speaker, "I am a pirate", RoomChatType.TALK);
        this.say("pirate / while singing", speaker, "i am a pirate", RoomChatType.TALK);
    }

    // ---------------------------------------------------------------- commands and wired

    @Test
    void aCommand() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.commandHandled = true;
        this.say("command / talk", speaker, ":about", RoomChatType.TALK);
        this.say("command / whisper is not a command", speaker, ":about", RoomChatType.WHISPER);
    }

    @Test
    void wiredSuppressesTheLine() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("listener", 4, 3);
        this.suppressSays = true;
        this.say("wired / suppressed talk", speaker, "secret", RoomChatType.TALK);
        this.say("wired / suppressed shout", speaker, "secret", RoomChatType.SHOUT);
        this.say("wired / ignored by the caller", speaker, "secret", RoomChatType.TALK, true);
    }

    // ---------------------------------------------------------------- delivery

    @Test
    void talkReachesTheRightPeople() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("near", 4, 3);
        this.person("far", 20, 20);
        this.withRights.add(this.person("far with rights", 21, 21));
        when(this.person("ignoring", 4, 4).getHabboStats().userIgnored(anyInt()))
                .thenReturn(true);
        when(this.person("blocking", 2, 2).getHabboStats().userBlocked(anyInt()))
                .thenReturn(true);
        this.say("talk / plain", speaker, "hello   ", RoomChatType.TALK);
        this.say("talk / only spaces", speaker, "   ", RoomChatType.TALK);
    }

    @Test
    void talkWithoutAChatLimit() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("far", 20, 20);
        this.grant(speaker, Permission.ACC_CHAT_NO_LIMIT);
        this.say("talk / no chat limit", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void aRankPrefix() {
        Habbo speaker = this.person("speaker", 3, 3);
        when(speaker.getHabboInfo().getRank().hasPrefix()).thenReturn(true);
        this.person("near", 4, 3);
        this.person("old chat", 4, 4).getHabboStats().preferOldChat = true;
        this.say("prefix / talk", speaker, "hello", RoomChatType.TALK);
        this.say("prefix / shout", speaker, "hello", RoomChatType.SHOUT);
        this.say("prefix / whisper", speaker, "hello", RoomChatType.WHISPER, false, "old chat");
    }

    @Test
    void aPluginPrefix() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("near", 4, 3);
        this.registeredEvents.add(UsernameTalkEvent.class);
        this.customPrefix = message("PluginPrefix");
        this.say("prefix / plugin", speaker, "hello", RoomChatType.TALK);
    }

    @Test
    void aTent() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("inside", 4, 3);
        this.person("outside", 9, 9);
        this.grant(this.person("staff outside", 10, 10), Permission.ACC_SEE_TENTCHAT);
        this.tent = new Rectangle(2, 2, 3, 3);
        this.say("tent / talk", speaker, "hello", RoomChatType.TALK);
        this.say("tent / shout", speaker, "hello", RoomChatType.SHOUT);
    }

    @Test
    void shoutReachesEveryone() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("near", 4, 3);
        this.person("far", 20, 20);
        when(this.person("blocking", 2, 2).getHabboStats().userBlocked(anyInt()))
                .thenReturn(true);
        this.say("shout / plain", speaker, "HELLO", RoomChatType.SHOUT);
    }

    @Test
    void whispers() {
        Habbo speaker = this.person("speaker", 3, 3);
        this.person("target", 4, 3);
        this.person("bystander", 4, 4);
        this.grant(this.person("staff", 5, 5), Permission.ACC_SEE_WHISPERS);
        this.say("whisper / to the target", speaker, "psst", RoomChatType.WHISPER, false, "target");
        when(this.people.get("target").getHabboStats().userIgnored(anyInt())).thenReturn(true);
        this.say("whisper / target ignores", speaker, "psst", RoomChatType.WHISPER, false, "target");
        this.say("whisper / no target", speaker, "psst", RoomChatType.WHISPER);
    }

    @Test
    void listenersTurnTheirHeads() {
        Habbo speaker = this.person("speaker", 3, 3);
        Habbo facing = this.person("facing", 4, 3);
        when(facing.getRoomUnit().getBodyRotation()).thenReturn(RoomUserRotation.WEST);
        Habbo away = this.person("away", 5, 3);
        when(away.getRoomUnit().getBodyRotation()).thenReturn(RoomUserRotation.EAST);
        Habbo walking = this.person("walking", 4, 4);
        when(walking.getRoomUnit().getBodyRotation()).thenReturn(RoomUserRotation.NORTH_WEST);
        when(walking.getRoomUnit().isWalking()).thenReturn(true);
        this.say("heads / talk", speaker, "hello", RoomChatType.TALK);
        this.say("heads / shout", speaker, "hello", RoomChatType.SHOUT);
    }

    @Test
    void botsAndTalkingFurniListen() {
        Habbo speaker = this.person("speaker", 3, 3);
        Bot bot = mock(Bot.class, withSettings().name("bot"));
        doAnswer(invocation -> this.record("bot.onUserSay("
                        + invocation.<RoomChatMessage>getArgument(0).getMessage() + ")"))
                .when(bot)
                .onUserSay(any());
        this.bots.put(1, bot);
        this.talkingFurni.add(this.talkingFurni("parrot", 4, 3, "0"));
        this.talkingFurni.add(this.talkingFurni("busy parrot", 4, 4, "1"));
        this.talkingFurni.add(this.talkingFurni("far parrot", 20, 20, "0"));
        this.settings.put("furniture.talking.range", 3);
        this.settings.put("parrot.message.count", 2);
        this.say("listeners / talk", speaker, "hello", RoomChatType.TALK);
        this.say("listeners / whisper", speaker, "hello", RoomChatType.WHISPER);
    }

    // ---------------------------------------------------------------- the driver

    private void say(String label, Habbo speaker, String text, RoomChatType type) {
        this.say(label, speaker, text, type, false);
    }

    private void say(String label, Habbo speaker, String text, RoomChatType type, boolean ignoreWired) {
        this.say(label, speaker, text, type, ignoreWired, null);
    }

    private void say(String label, Habbo speaker, String text, RoomChatType type, boolean ignoreWired, String target) {
        RoomChatMessage line = new RoomChatMessage(
                text, speaker, target == null ? null : this.people.get(target), RoomChatMessageBubbles.NORMAL);
        this.trace.clear();
        this.recording = true;
        try {
            this.chat.talk(speaker, line, type, ignoreWired);
            this.trace.add("returned");
        } catch (Exception e) {
            this.trace.add("threw " + e.getClass().getSimpleName());
        } finally {
            this.recording = false;
        }
        this.trace.add("line \"" + line.getMessage() + "\" command " + line.isCommand + " delivered " + line.delivered);
        this.trace.add(nameOf(speaker) + " chat counter "
                + speaker.getHabboStats().chatCounter.get() + ", muted count " + speaker.getHabboStats().mutedCount
                + ", singing " + speaker.getHabboStats().singingPirate.get());
        this.check(label, String.join("\n", this.trace));
    }

    private Object record(String line) {
        if (this.recording) this.trace.add(line);
        return null;
    }

    private <T extends MessageComposer> void composer(Class<T> type, String name) {
        this.constructions.add(mockConstruction(type, (composer, context) -> {
            String detail = context.arguments().isEmpty()
                    ? ""
                    : describeComposerArgument(context.arguments().get(0));
            ServerMessage message = message(name + "(" + detail + ")");
            when(composer.compose()).thenReturn(message);
        }));
    }

    private String describeComposerArgument(Object argument) {
        if (argument instanceof RoomChatMessage line) return line.getMessage();
        if (argument instanceof Habbo habbo) return nameOf(habbo);
        if (argument instanceof RoomUnit) return "unit";
        return String.valueOf(argument);
    }

    private static ServerMessage message(String name) {
        return mock(ServerMessage.class, withSettings().name(name));
    }

    private static String packet(Object message) {
        return message == null ? "null" : mockName(message);
    }

    private static String mockName(Object mock) {
        return org.mockito.Mockito.mockingDetails(mock)
                .getMockCreationSettings()
                .getMockName()
                .toString();
    }

    private String nameOf(Object value) {
        if (value instanceof Habbo habbo) return habbo.getHabboInfo().getUsername();
        if (value != null && org.mockito.Mockito.mockingDetails(value).isMock()) return mockName(value);
        return String.valueOf(value);
    }

    private String describe(Object[] arguments) {
        List<String> parts = new ArrayList<>();
        for (Object argument : arguments) {
            if (argument == this.room) parts.add("room");
            else if (argument instanceof Habbo habbo) parts.add(nameOf(habbo));
            else if (argument instanceof RoomUnit) parts.add("unit");
            else if (argument instanceof Enum<?> value) parts.add(value.name());
            else parts.add(String.valueOf(argument));
        }
        return String.join(", ", parts);
    }

    private Habbo person(String name, int x, int y) {
        Habbo habbo = mock(Habbo.class, withSettings().name(name));
        HabboInfo info = mock(HabboInfo.class);
        Rank rank = mock(Rank.class);
        HabboStats stats = mock(HabboStats.class);
        setField(stats, "chatCounter", new AtomicInteger(0));
        setField(stats, "singingPirate", new AtomicBoolean(false));
        setField(stats, "chatColor", RoomChatMessageBubbles.NORMAL);
        when(stats.allowTalk()).thenReturn(true);
        when(info.getUsername()).thenReturn(name);
        when(info.getId()).thenReturn(100 + this.people.size());
        when(info.getRank()).thenReturn(rank);
        when(info.getCurrentRoom()).thenReturn(this.room);
        RoomUnit unit = mock(RoomUnit.class);
        RoomTile tile = tile(x, y);
        when(unit.getCurrentLocation()).thenReturn(tile);
        when(unit.getX()).thenReturn((short) x);
        when(unit.getY()).thenReturn((short) y);
        when(unit.getBodyRotation()).thenReturn(RoomUserRotation.NORTH);
        doAnswer(invocation -> this.record(name + " turns its head " + invocation.getArgument(0)))
                .when(unit)
                .setHeadRotation(any());
        GameClient client = mock(GameClient.class);
        doAnswer(invocation -> this.record(name + " <- " + packet(invocation.getArgument(0))))
                .when(client)
                .sendResponse(any(ServerMessage.class));
        doAnswer(invocation -> this.record(name + " <- "
                        + packet(invocation.<MessageComposer>getArgument(0).compose())))
                .when(client)
                .sendResponse(any(MessageComposer.class));
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getRoomUnit()).thenReturn(unit);
        when(habbo.getClient()).thenReturn(client);
        when(habbo.hasPermission(anyString()))
                .thenAnswer(invocation ->
                        this.permissions.getOrDefault(habbo, Set.of()).contains(invocation.<String>getArgument(0)));
        doAnswer(invocation -> this.record(name + " whispered \"" + invocation.getArgument(0) + "\""))
                .when(habbo)
                .whisper(anyString());
        doAnswer(invocation -> this.record(
                        name + " muted " + invocation.getArgument(0) + "s, flood " + invocation.getArgument(1)))
                .when(habbo)
                .mute(anyInt(), anyBoolean());
        this.people.put(name, habbo);
        return habbo;
    }

    private void grant(Habbo habbo, String permission) {
        this.permissions.computeIfAbsent(habbo, ignored -> new HashSet<>()).add(permission);
    }

    private HabboItem talkingFurni(String name, int x, int y, String state) {
        HabboItem item = mock(InteractionTalkingFurniture.class, withSettings().name(name));
        Item base = mock(Item.class);
        when(base.getName()).thenReturn(name.replace(' ', '_').equals("parrot") ? "parrot" : name.replace(' ', '_'));
        when(item.getBaseItem()).thenReturn(base);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        String[] extradata = {state};
        when(item.getExtradata()).thenAnswer(invocation -> extradata[0]);
        doAnswer(invocation -> {
                    extradata[0] = invocation.getArgument(0);
                    return this.record(name + ".setExtradata(" + extradata[0] + ")");
                })
                .when(item)
                .setExtradata(anyString());
        try {
            doAnswer(invocation -> this.record(name + ".onClick")).when(item).onClick(any(), any(), any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return item;
    }

    private static RoomTile tile(Object x, Object y) {
        return new RoomTile(((Number) x).shortValue(), ((Number) y).shortValue(), (short) 0, RoomTileState.OPEN, true);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, ChatFloodBudget> floodBudgets(RoomChatManager chat) throws Exception {
        Field field = RoomChatManager.class.getDeclaredField("floodBudgets");
        field.setAccessible(true);
        return (Map<Integer, ChatFloodBudget>) field.get(chat);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> filterWords(RoomChatManager chat) throws Exception {
        Field field = RoomChatManager.class.getDeclaredField("wordFilterWords");
        field.setAccessible(true);
        return (Set<String>) field.get(chat);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = HabboStats.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.roomChatTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/room-chat-traces.txt");
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
