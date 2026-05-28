package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class FrankBot extends ButlerBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(FrankBot.class);
    public static final String BOT_TYPE = "frank";
    public static final String PERMISSION_USE = "acc_bot_frank";
    private static final String KEY_DOOR_LINES = "__door_lines";
    private static final String KEY_BUSY_WHISPER = "__busy_whisper";
    private static final String KEY_DOOR_TRIGGERS = "__door_triggers";
    private static final List<String> DEFAULT_DOOR_LINES = List.of(
            "Right this way - mind the step!",
            "And out you go. Come back soon!",
            "Allow me to escort you to the exit.",
            "There's the door. Farewell, true believer!"
    );
    private static final String DEFAULT_BUSY_WHISPER =
            "Sorry, I am currently busy. Please wait until I am available.";
    private static final Pattern DEFAULT_DOOR_PATTERN = Pattern.compile(
            "\\b(show me the door|kick me|i want to leave|let me out)\\b");

    private static final ConcurrentHashMap<Pattern, List<String>> chatResponses = new ConcurrentHashMap<>();
    private static volatile List<String> doorLines = DEFAULT_DOOR_LINES;
    private static volatile String busyWhisper = DEFAULT_BUSY_WHISPER;
    private static volatile Pattern doorTriggerPattern = DEFAULT_DOOR_PATTERN;

    private static final Random RANDOM = new Random();

    private static final int MAX_CHAT_KEYWORDS = 256;
    private static final int MAX_DOOR_TRIGGERS = 32;
    private static final int MAX_MESSAGE_LEN = 256;
    private static final long BUSY_WHISPER_COOLDOWN_MS = 5000L;

    private volatile RoomTile homeTile;
    private volatile RoomUserRotation homeRotation;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean returnScheduled = new AtomicBoolean(false);
    private final ConcurrentHashMap<Integer, Long> lastBusyWhisperAt = new ConcurrentHashMap<>();

    public FrankBot(ResultSet set) throws SQLException {
        super(set);
    }

    public FrankBot(Bot bot) {
        super(bot);
    }

    @Override
    public void onPlace(Habbo habbo, Room room) {
        super.onPlace(habbo, room);
        if (this.getRoomUnit() != null) {
            this.homeTile = this.getRoomUnit().getCurrentLocation();
            this.homeRotation = this.getRoomUnit().getBodyRotation();
        }
    }

    private static final short[] FRANK_OWNER_ACTIONS = { (short) Bot.ACTION_ROTATE };

    @Override
    public short[] getOwnerActionIds() {
        return FRANK_OWNER_ACTIONS;
    }

    @Override
    public void onPostOwnerAction(int actionId) {
        if (actionId == ACTION_ROTATE && this.getRoomUnit() != null) {
            this.homeRotation = this.getRoomUnit().getBodyRotation();
        }
    }

    public static void initialise() {
        chatResponses.clear();
        doorLines = DEFAULT_DOOR_LINES;
        busyWhisper = DEFAULT_BUSY_WHISPER;
        doorTriggerPattern = DEFAULT_DOOR_PATTERN;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet set = statement.executeQuery("SELECT `keys`, `responses` FROM bot_chat_responses WHERE bot_type = '" + BOT_TYPE + "'")) {
            while (set.next()) {
                String keysRaw = set.getString("keys");
                String responsesRaw = set.getString("responses");

                if (keysRaw == null || responsesRaw == null) continue;

                List<String> responses = new ArrayList<>();
                for (String line : responsesRaw.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) responses.add(trimmed);
                }

                if (responses.isEmpty()) continue;

                String firstKey = keysRaw.split(";", 2)[0].trim();
                if (firstKey.startsWith("__")) {
                    switch (firstKey) {
                        case KEY_DOOR_LINES:
                            doorLines = new CopyOnWriteArrayList<>(responses);
                            break;
                        case KEY_BUSY_WHISPER:
                            busyWhisper = responses.get(0);
                            break;
                        case KEY_DOOR_TRIGGERS:
                            doorTriggerPattern = buildDoorTriggerPattern(responses);
                            break;
                        default:
                            LOGGER.warn("FrankBot: unknown system key '{}', ignored", firstKey);
                    }
                    continue;
                }

                List<String> shared = new CopyOnWriteArrayList<>(responses);

                for (String key : keysRaw.split(";")) {
                    if (chatResponses.size() >= MAX_CHAT_KEYWORDS) {
                        LOGGER.warn("FrankBot: chat keyword cap ({}) reached, remaining rows ignored",
                                MAX_CHAT_KEYWORDS);
                        break;
                    }
                    String k = key == null ? "" : key.trim().toLowerCase();
                    if (k.isEmpty()) continue;
                    try {
                        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(k) + "\\b");
                        chatResponses.put(pattern, shared);
                    } catch (Exception e) {
                        LOGGER.error("Failed to compile Frank chat keyword pattern: {}", k, e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("FrankBot: could not load bot_chat_responses ({}). Frank will still serve items.", e.getMessage());
        }

        ButlerBot.initialise();
    }

    public static void dispose() {
        chatResponses.clear();
        doorLines = DEFAULT_DOOR_LINES;
        busyWhisper = DEFAULT_BUSY_WHISPER;
        doorTriggerPattern = DEFAULT_DOOR_PATTERN;
        ButlerBot.dispose();
    }

    private static Pattern buildDoorTriggerPattern(List<String> triggers) {
        StringBuilder sb = new StringBuilder("\\b(");
        boolean first = true;
        int count = 0;
        for (String trigger : triggers) {
            if (count >= MAX_DOOR_TRIGGERS) {
                LOGGER.warn("FrankBot: door trigger cap ({}) reached, extra entries ignored",
                        MAX_DOOR_TRIGGERS);
                break;
            }
            String t = trigger == null ? "" : trigger.trim().toLowerCase();
            if (t.isEmpty()) continue;
            if (!first) sb.append('|');
            sb.append(Pattern.quote(t));
            first = false;
            count++;
        }
        sb.append(")\\b");

        if (first) return DEFAULT_DOOR_PATTERN;

        try {
            return Pattern.compile(sb.toString());
        } catch (Exception e) {
            LOGGER.error("FrankBot: failed to compile door trigger pattern from {}, falling back to default", triggers, e);
            return DEFAULT_DOOR_PATTERN;
        }
    }

    @Override
    public void onUserSay(final RoomChatMessage message) {
        Room currentRoom = this.getRoom();
        if (currentRoom == null) return;

        Habbo asker = message.getHabbo();
        if (asker == null || asker.getClient() == null) return;

        if (this.getRoomUnit() == null) return;

        String raw = message.getUnfilteredMessage();
        if (raw != null && raw.length() > MAX_MESSAGE_LEN) return;

        if (this.homeTile == null) {
            this.homeTile = this.getRoomUnit().getCurrentLocation();
            this.homeRotation = this.getRoomUnit().getBodyRotation();
        }

        if (this.busy.get() || this.getRoomUnit().hasStatus(RoomUnitStatus.MOVE)) {
            this.whisperThrottled(asker, busyWhisper);
            return;
        }

        if (raw != null) {
            double distance = this.getRoomUnit().getCurrentLocation().distance(asker.getRoomUnit().getCurrentLocation());
            int commandDistance = Emulator.getConfig().getInt("hotel.bot.butler.commanddistance");

            if (distance <= commandDistance) {
                String lower = raw.toLowerCase();

                if (doorTriggerPattern.matcher(lower).find()) {
                    if (!this.busy.compareAndSet(false, true)) {
                        this.whisperThrottled(asker, busyWhisper);
                        return;
                    }
                    this.showToTheDoor(asker);
                    return;
                }

                for (java.util.Map.Entry<Pattern, List<String>> entry : chatResponses.entrySet()) {
                    if (entry.getKey().matcher(lower).find()) {
                        List<String> options = entry.getValue();
                        if (options.isEmpty()) continue;

                        String reply = options.get(RANDOM.nextInt(options.size()));
                        this.talk(reply);
                        return;
                    }
                }
            }
        }

        if (!this.busy.compareAndSet(false, true)) {
            this.whisperThrottled(asker, busyWhisper);
            return;
        }
        super.onUserSay(message);
        this.schedulePostServeReturn(currentRoom.getId(), 0);
    }

    private void whisperThrottled(Habbo target, String text) {
        if (target == null || text == null || text.isEmpty() || this.getRoomUnit() == null) return;
        int userId = target.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        Long last = lastBusyWhisperAt.get(userId);
        if (last != null && (now - last) < BUSY_WHISPER_COOLDOWN_MS) return;
        lastBusyWhisperAt.put(userId, now);
        RoomChatMessage msg = new RoomChatMessage(text, this.getRoomUnit(), RoomChatMessageBubbles.BOT);
        target.getClient().sendResponse(new RoomUserWhisperComposer(msg));
    }

    private void showToTheDoor(final Habbo target) {
        final Room room = this.getRoom();
        if (room == null || room.getLayout() == null || target == null) {
            this.busy.set(false);
            return;
        }

        final RoomTile doorTile = room.getLayout().getDoorTile();
        if (doorTile == null) {
            this.busy.set(false);
            return;
        }

        this.lookAt(target);
        List<String> lines = doorLines;
        String line = lines.isEmpty() ? DEFAULT_DOOR_LINES.get(RANDOM.nextInt(DEFAULT_DOOR_LINES.size()))
                : lines.get(RANDOM.nextInt(lines.size()));
        this.talk(line);

        final int targetId = target.getHabboInfo().getId();
        final int roomId = room.getId();
        final AtomicBoolean fired = new AtomicBoolean(false);

        final Runnable kickThenReturn = () -> {
            if (!fired.compareAndSet(false, true)) return;
            Room currentRoom = this.getRoom();
            if (currentRoom == null || currentRoom.getId() != roomId) {
                this.busy.set(false);
                return;
            }
            Habbo stillHere = currentRoom.getHabbo(targetId);
            if (stillHere != null) {
                currentRoom.kickHabbo(stillHere, false);
            }
            this.scheduleReturnHome(targetId, roomId, 0);
        };

        if (this.getRoomUnit().canWalk() && !this.getRoomUnit().getCurrentLocation().equals(doorTile)) {
            List<Runnable> onArrive = new ArrayList<>();
            onArrive.add(kickThenReturn);

            List<Runnable> onFail = new ArrayList<>();
            onFail.add(() -> Emulator.getThreading().run(kickThenReturn, 1500));

            this.getRoomUnit().setGoalLocation(doorTile);
            Emulator.getThreading().run(
                    new RoomUnitWalkToLocation(this.getRoomUnit(), doorTile, room, onArrive, onFail));
        } else {
            Emulator.getThreading().run(kickThenReturn, 1500);
        }
    }

    private static final int RETURN_HOME_POLL_MS = 500;
    private static final int RETURN_HOME_MAX_WAIT_MS = 8000;
    private static final int POST_SERVE_POLL_MS = 750;
    private static final int POST_SERVE_MAX_WAIT_MS = 30000;

    private void schedulePostServeReturn(final int roomId, final int waitedMs) {
        if (waitedMs == 0 && !this.returnScheduled.compareAndSet(false, true)) {
            return;
        }
        if (waitedMs >= POST_SERVE_MAX_WAIT_MS) {
            this.returnScheduled.set(false);
            this.busy.set(false);
            return;
        }
        if (this.homeTile == null) {
            this.returnScheduled.set(false);
            this.busy.set(false);
            return;
        }

        Emulator.getThreading().run(() -> {
            Room r = this.getRoom();
            if (r == null || r.getId() != roomId || this.getRoomUnit() == null || this.homeTile == null) {
                this.returnScheduled.set(false);
                this.busy.set(false);
                return;
            }

            if (this.getRoomUnit().getCurrentLocation().equals(this.homeTile)) {
                if (this.homeRotation != null && this.getRoomUnit().getBodyRotation() != this.homeRotation) {
                    this.getRoomUnit().setRotation(this.homeRotation);
                    r.sendComposer(new RoomUserStatusComposer(this.getRoomUnit()).compose());
                    this.persistPosition();
                } else {
                    this.busy.set(false);
                }
                this.returnScheduled.set(false);
                return;
            }

            boolean stillWalking = this.getRoomUnit().hasStatus(RoomUnitStatus.MOVE)
                    || (this.getRoomUnit().getPath() != null && !this.getRoomUnit().getPath().isEmpty());

            if (stillWalking) {
                this.schedulePostServeReturn(roomId, waitedMs + POST_SERVE_POLL_MS);
                return;
            }

            this.returnScheduled.set(false);
            this.returnHome(-1, false);
        }, POST_SERVE_POLL_MS);
    }

    private void scheduleReturnHome(final int kickedHabboId, final int roomId, final int waitedMs) {
        Room currentRoom = this.getRoom();
        if (currentRoom == null || currentRoom.getId() != roomId) return;

        boolean stillEscorting = currentRoom.getHabbo(kickedHabboId) != null;

        if (!stillEscorting || waitedMs >= RETURN_HOME_MAX_WAIT_MS) {
            this.returnHome(kickedHabboId, true);
            return;
        }

        Emulator.getThreading().run(
                () -> this.scheduleReturnHome(kickedHabboId, roomId, waitedMs + RETURN_HOME_POLL_MS),
                RETURN_HOME_POLL_MS);
    }

    private void returnHome(int kickedHabboId, boolean alwaysTeleport) {
        final Room room = this.getRoom();
        if (room == null || this.homeTile == null || this.getRoomUnit() == null) {
            this.busy.set(false);
            return;
        }

        final Runnable teleportHome = () -> {
            Room r = this.getRoom();
            if (r == null || this.getRoomUnit() == null) return;

            double homeZ = r.getTopHeightAt(this.homeTile.x, this.homeTile.y);

            this.getRoomUnit().stopWalking();
            this.getRoomUnit().setZ(homeZ);
            this.getRoomUnit().setLocation(this.homeTile);
            this.getRoomUnit().setPreviousLocationZ(homeZ);
            if (this.homeRotation != null) {
                this.getRoomUnit().setRotation(this.homeRotation);
            }
            this.getRoomUnit().statusUpdate(true);
            r.sendComposer(new RoomUserStatusComposer(this.getRoomUnit()).compose());
            this.persistPosition();
        };

        if (this.getRoomUnit().getCurrentLocation().equals(this.homeTile)) {
            if (this.homeRotation != null) {
                this.getRoomUnit().setRotation(this.homeRotation);
                room.sendComposer(new RoomUserStatusComposer(this.getRoomUnit()).compose());
            }
            this.persistPosition();
            return;
        }

        boolean hasOtherWatchers = false;
        for (Habbo h : room.getCurrentHabbos().values()) {
            if (h.getHabboInfo().getId() != kickedHabboId) {
                hasOtherWatchers = true;
                break;
            }
        }

        if (alwaysTeleport || !hasOtherWatchers || !this.getRoomUnit().canWalk()) {
            teleportHome.run();
            return;
        }

        List<Runnable> onArrive = new ArrayList<>();
        onArrive.add(() -> {
            if (this.homeRotation != null && this.getRoom() != null) {
                this.getRoomUnit().setRotation(this.homeRotation);
                this.getRoom().sendComposer(new RoomUserStatusComposer(this.getRoomUnit()).compose());
            }
            this.persistPosition();
        });

        List<Runnable> onFail = new ArrayList<>();
        onFail.add(teleportHome);

        this.getRoomUnit().setGoalLocation(this.homeTile);
        Emulator.getThreading().run(
                new RoomUnitWalkToLocation(this.getRoomUnit(), this.homeTile, room, onArrive, onFail));
    }

    private void persistPosition() {
        this.needsUpdate(true);
        this.run();
        this.busy.set(false);
    }
}
