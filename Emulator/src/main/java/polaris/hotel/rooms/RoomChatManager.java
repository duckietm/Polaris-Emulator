package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.awt.Rectangle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.commands.CommandHandler;
import polaris.hotel.items.interactions.InteractionMuteArea;
import polaris.hotel.items.interactions.InteractionTalkingFurniture;
import polaris.hotel.modtool.WordFilter;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.raidprotection.RaidProtectionManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserWordFilter;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import polaris.messages.outgoing.rooms.users.RoomUserShoutComposer;
import polaris.messages.outgoing.rooms.users.RoomUserTalkComposer;
import polaris.messages.outgoing.rooms.users.RoomUserTypingComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.messages.outgoing.users.MutedWhisperComposer;
import polaris.plugin.events.users.UserIdleEvent;
import polaris.plugin.events.users.UsernameTalkEvent;
import polaris.threading.runnables.YouAreAPirate;
import polaris.util.pathfinding.Rotation;

/**
 * Manages all chat functionality within a room.
 * Handles talking, shouting, whispering, word filtering, flood protection, and muting.
 */
public class RoomChatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomChatManager.class);
    static final int DEFAULT_MUTE_TIME_SECONDS = 30;
    /** Every word is checked against every message in the room. */
    static final int MAX_WORD_FILTER_WORDS = 200;

    private final Room room;

    // Word filter
    private final Set<String> wordFilterWords;

    // Muted Habbos: userId -> unmute timestamp
    private final Int2IntMap mutedUsers;

    // Flood protection settings
    private final int muteTime;

    // The speakers' chat budgets in the budget flood mode; a full one is forgotten once many pile up.
    private static final int FLOOD_BUDGET_PRUNE_SIZE = 64;
    private final Map<Integer, ChatFloodBudget> floodBudgets = new ConcurrentHashMap<>();

    // Global chat delay setting
    public static boolean USER_CHAT_DELAY = false;

    // Mute area can whisper setting
    public static boolean MUTEAREA_CAN_WHISPER = false;

    public RoomChatManager(Room room) {
        this(room, configuredMuteTime());
    }

    RoomChatManager(Room room, Int2IntMap mutedUsers) {
        this(room, configuredMuteTime(), mutedUsers);
    }

    RoomChatManager(Room room, int muteTime) {
        this(room, muteTime, new Int2IntOpenHashMap());
    }

    RoomChatManager(Room room, int muteTime, Int2IntMap mutedUsers) {
        this.room = room;
        this.wordFilterWords = new HashSet<>(0);
        this.mutedUsers = mutedUsers;
        this.muteTime = muteTime;
    }

    private static int configuredMuteTime() {
        return Emulator.getConfig().getInt("hotel.flood.mute.time", DEFAULT_MUTE_TIME_SECONDS);
    }

    // ==================== WORD FILTER ====================

    /**
     * Loads word filter from the database.
     */
    public void loadWordFilter(Connection connection) {
        synchronized (this.wordFilterWords) {
            this.wordFilterWords.clear();

            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT word FROM room_wordfilter WHERE room_id = ?")) {
                statement.setInt(1, this.room.getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        this.wordFilterWords.add(set.getString("word"));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    /**
     * Adds a word to the filter.
     */
    public void addToWordFilter(String word) {
        synchronized (this.wordFilterWords) {
            if (this.wordFilterWords.contains(word) || this.wordFilterWords.size() >= MAX_WORD_FILTER_WORDS) {
                return;
            }

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("INSERT IGNORE INTO room_wordfilter VALUES (?, ?)")) {
                statement.setInt(1, this.room.getId());
                statement.setString(2, word);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                return;
            }

            this.wordFilterWords.add(word);
        }
    }

    /**
     * Removes a word from the filter.
     */
    public void removeFromWordFilter(String word) {
        synchronized (this.wordFilterWords) {
            this.wordFilterWords.remove(word);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM room_wordfilter WHERE room_id = ? AND word = ?")) {
                statement.setInt(1, this.room.getId());
                statement.setString(2, word);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    /**
     * Gets the word filter words.
     */
    public Set<String> getWordFilterWords() {
        return this.wordFilterWords;
    }

    /**
     * Checks if word filter is empty.
     */
    public boolean hasWordFilter() {
        return !this.wordFilterWords.isEmpty();
    }

    // ==================== MUTING ====================

    /**
     * Mutes a Habbo for a specified number of minutes.
     */
    public void muteUser(User user, int minutes) {
        // Compute the expiry in long arithmetic and clamp to Integer.MAX_VALUE:
        // the map stores an int timestamp, so a large `minutes` (e.g. from a
        // wired mute) would otherwise overflow `minutes * 60` to a negative /
        // unpredictable value instead of a far-future expiry.
        long unmuteAt = (long) this.room.currentUnixTimestamp() + ((long) Math.max(0, minutes) * 60L);
        synchronized (this.mutedUsers) {
            this.mutedUsers.put(user.getUserInfo().getId(), (int) Math.min(unmuteAt, Integer.MAX_VALUE));
        }

        // AIR tells the muted user alone, with RemainingMutePeriod. The room is not told: an
        // IgnoreResult would put the user on everybody else's own ignore list.
        if (this.isMuted(user) && user.getClient() != null) {
            user.getClient().sendResponse(new MutedWhisperComposer(this.getMuteTimeRemaining(user)));
        }
    }

    /**
     * Removes a room mute from a Habbo.
     */
    public void unmuteUser(User user) {
        synchronized (this.mutedUsers) {
            this.mutedUsers.remove(user.getUserInfo().getId());
        }
    }

    /**
     * Checks if a Habbo is muted.
     */
    public boolean isMuted(User user) {
        if (this.room.isOwner(user) || this.room.hasRights(user)) {
            return false;
        }

        if (this.mutedUsers.containsKey(user.getUserInfo().getId())) {
            boolean time = this.mutedUsers.get(user.getUserInfo().getId()) > this.room.currentUnixTimestamp();

            if (!time) {
                this.mutedUsers.remove(user.getUserInfo().getId());
            }

            return time;
        }

        return false;
    }

    /**
     * Gets remaining mute time for a Habbo.
     */
    public int getMuteTimeRemaining(User user) {
        if (this.mutedUsers.containsKey(user.getUserInfo().getId())) {
            return Math.max(0, this.mutedUsers.get(user.getUserInfo().getId()) - this.room.currentUnixTimestamp());
        }
        return 0;
    }

    /**
     * Gets the muted Habbos map.
     */
    public Int2IntMap getMutedUsers() {
        return this.mutedUsers;
    }

    /**
     * Applies flood mute to a Habbo.
     */
    /**
     * Whether a habbicon of this Habbo may go out: in the budget flood mode it spends a chat line, as
     * in Habbo; in the mute mode habbicons are not counted.
     */
    public boolean allowsHabbicon(User user) {
        if (user == null || user.hasPermission(Permission.ACC_CHAT_NO_FLOOD) || !ChatFloodBudget.isBudgetMode()) {
            return true;
        }
        return this.takeFloodBudget(user, ChatFloodBudget.floodWithRights());
    }

    /** Spends a line of the Habbo's flood budget; false when it is empty. Room rights exempt unless flood.with.rights. */
    private boolean takeFloodBudget(User user, boolean floodWithRights) {
        if (!floodWithRights && this.room.hasRights(user)) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (this.floodBudgets.size() > FLOOD_BUDGET_PRUNE_SIZE) {
            this.floodBudgets.values().removeIf(budget -> budget.isRefilled(now));
        }
        return this.floodBudgets
                .computeIfAbsent(user.getUserInfo().getId(), id -> new ChatFloodBudget())
                .tryTake(now);
    }

    public void floodMuteUser(User user, int timeOut) {
        user.getUserStats().mutedCount++;
        timeOut += (timeOut * (int) Math.ceil(Math.pow(user.getUserStats().mutedCount, 2)));
        user.getUserStats().chatCounter.set(0);
        user.mute(timeOut, true);
    }

    // ==================== CHAT METHODS ====================

    /**
     * Handles talking in the room.
     */
    public void talk(User user, RoomChatMessage roomChatMessage, RoomChatType chatType) {
        this.talk(user, roomChatMessage, chatType, false);
    }

    /**
     * Handles talking in the room with wired ignore option.
     */
    public void talk(
            final User user, final RoomChatMessage roomChatMessage, RoomChatType chatType, boolean ignoreWired) {
        if (!this.mayTalkNow(user, roomChatMessage)) {
            return;
        }

        this.noteActivity(user);

        if (roomChatMessage == null
                || roomChatMessage.getMessage() == null
                || roomChatMessage.getMessage().equals("")) {
            return;
        }

        // AIR raid protection: a chat flood of newcomers is a raid; a raider acted on loses the line.
        if (RaidProtectionManager.get().onChat(this.room, user)) {
            return;
        }

        if (this.inMuteArea(user, chatType)) {
            return;
        }

        this.applyRoomWordFilter(user, roomChatMessage);

        // Check room/user mute
        if (!user.hasPermission(Permission.ACC_NOMUTE)) {
            if (this.room.isMuted() && !this.room.hasRights(user)) {
                return;
            }

            if (this.isMuted(user)) {
                user.getClient().sendResponse(new MutedWhisperComposer(Math.max(1, this.getMuteTimeRemaining(user))));
                return;
            }
        }

        // Flood protection
        if (!user.hasPermission(Permission.ACC_CHAT_NO_FLOOD) && ChatFloodBudget.isBudgetMode()) {
            // Budget mode: a line over the speaker's budget is dropped, without a mute.
            if (!this.takeFloodBudget(user, ChatFloodBudget.floodWithRights())) {
                return;
            }
        } else if (!user.hasPermission(Permission.ACC_CHAT_NO_FLOOD)) {
            final int chatCounter = user.getUserStats().chatCounter.addAndGet(1);

            if (chatCounter > 3) {
                final boolean floodRights = Emulator.getConfig().getBoolean("flood.with.rights");
                final boolean hasRights = this.room.hasRights(user);

                if (floodRights || !hasRights) {
                    if (this.room.getChatProtection() == 0) {
                        this.floodMuteUser(user, this.muteTime);
                        return;
                    } else if (this.room.getChatProtection() == 1 && chatCounter > 4) {
                        this.floodMuteUser(user, this.muteTime);
                        return;
                    } else if (this.room.getChatProtection() == 2 && chatCounter > 5) {
                        this.floodMuteUser(user, this.muteTime);
                        return;
                    }
                }
            }
        }

        // Easter egg. Must stay below the mute and flood guards above: it consumes the
        // message and returns, so anything placed before it is trivially bypassed by
        // repeating the trigger. One song per Habbo at a time, otherwise a single client
        // can stack unbounded singing tasks on the scheduler.
        if (Emulator.getConfig().getBoolean("easter_eggs.enabled")
                && roomChatMessage.getMessage().equalsIgnoreCase("i am a pirate")) {
            if (user.getUserStats().singingPirate.compareAndSet(false, true)) {
                this.room.threading().run(new YouAreAPirate(user, this.room));
            }

            return;
        }

        String wiredSayMessage = roomChatMessage.getMessage();

        // Handle commands and wired
        if (chatType != RoomChatType.WHISPER && this.ranAsCommand(user, roomChatMessage, wiredSayMessage)) {
            return;
        }

        boolean suppressSaysOutput = chatType != RoomChatType.WHISPER
                && !ignoreWired
                && WiredManager.shouldSuppressUserSaysOutput(
                        user.getUserInfo().getCurrentRoom(),
                        user.getRoomUnit(),
                        wiredSayMessage,
                        chatType.ordinal(),
                        roomChatMessage.getBubble() != null
                                ? roomChatMessage.getBubble().getType()
                                : -1);

        ServerMessage prefixMessage = this.prefixFor(user, roomChatMessage, chatType);
        ServerMessage clearPrefixMessage =
                prefixMessage != null ? new RoomUserNameChangedComposer(user).compose() : null;

        Rectangle tentRectangle =
                this.room.getRoomSpecialTypes().tentAt(user.getRoomUnit().getCurrentLocation());

        trimTrailingWhitespace(roomChatMessage);

        // The line's wired runs first: its one flash, then its Show Message texts, then the speaker's own line (or
        // the echo of a hidden one), then the texts of stacks its signals reached (observed on AIR
        // WIN63-202610151745).
        boolean runsWired = chatType != RoomChatType.WHISPER && !ignoreWired && !roomChatMessage.isCommand;
        if (runsWired) {
            WiredBoxFlashes.runSayPass(() -> WiredManager.triggerUserSays(
                    user.getUserInfo().getCurrentRoom(),
                    user.getRoomUnit(),
                    wiredSayMessage,
                    chatType.ordinal(),
                    roomChatMessage.getBubble() != null
                            ? roomChatMessage.getBubble().getType()
                            : -1));
        }

        // Send chat based on type. The texts the line's wired queued go out even when sending the line fails, so
        // they never stay on this thread to follow the next line it handles.
        try {
            if (chatType == RoomChatType.WHISPER) {
                this.handleWhisper(user, roomChatMessage, prefixMessage, clearPrefixMessage);
            } else if (chatType == RoomChatType.TALK) {
                if (suppressSaysOutput) {
                    whisperBackToSpeaker(user, roomChatMessage);
                } else {
                    roomChatMessage.delivered = true;
                    this.handleTalk(user, roomChatMessage, prefixMessage, clearPrefixMessage, tentRectangle);
                }
            } else if (chatType == RoomChatType.SHOUT) {
                if (suppressSaysOutput) {
                    whisperBackToSpeaker(user, roomChatMessage);
                } else {
                    roomChatMessage.delivered = true;
                    this.handleShout(user, roomChatMessage, prefixMessage, clearPrefixMessage, tentRectangle);
                }
            }
        } finally {
            if (runsWired) {
                WiredBoxFlashes.releaseAfterSpeaker();
            }
        }

        // Notify bots and talking furniture
        if (chatType == RoomChatType.TALK || chatType == RoomChatType.SHOUT) {
            this.notifyBots(roomChatMessage);
            this.handleTalkingFurniture(user, roomChatMessage);
        }
    }

    /**
     * Whether the Habbo may say a line now: allowed to talk, in this room, visible (or the hotel lets invisible users
     * talk; a command still runs) and past the chat delay. Stamps the time of the line when it may.
     */
    private boolean mayTalkNow(User user, RoomChatMessage roomChatMessage) {
        if (!user.getUserStats().allowTalk()) {
            return false;
        }

        if (user.getRoomUnit().isInvisible() && Emulator.getConfig().getBoolean("invisible.prevent.chat", false)) {
            if (!CommandHandler.handleChatCommand(user.getClient(), roomChatMessage.getUnfilteredMessage())) {
                user.whisper(Emulator.getTexts().getValue("invisible.prevent.chat.error"));
            }

            return false;
        }

        if (user.getUserInfo().getCurrentRoom() != this.room) {
            return false;
        }

        long millis = System.currentTimeMillis();
        if (USER_CHAT_DELAY) {
            if (millis - user.getUserStats().lastChat < 750) {
                return false;
            }
        }
        user.getUserStats().lastChat = millis;
        return true;
    }

    /** Counts the talk quest when someone else is in the room, wakes the Habbo and ends its typing bubble. */
    private void noteActivity(User user) {
        if (this.room.getCurrentUsers().size() > 1) {
            polaris.hotel.quests.QuestProgressEvents.progress(user, polaris.hotel.quests.QuestGoalType.TALK_IN_ROOM, 1);
        }

        // Handle idle event
        UserIdleEvent event = new UserIdleEvent(user, UserIdleEvent.IdleReason.TALKED, false);
        this.room.pluginManager().fireEvent(event);

        if (!event.isCancelled()) {
            if (!event.idle) {
                this.room.unIdle(user);
            }
        }

        this.room.sendComposer(new RoomUserTypingComposer(user.getRoomUnit(), false).compose());
    }

    /** Whether the Habbo stands in a mute area; a whisper passes when the hotel allows it. */
    private boolean inMuteArea(User user, RoomChatType chatType) {
        if (user.hasPermission(Permission.ACC_NOMUTE) || (MUTEAREA_CAN_WHISPER && chatType == RoomChatType.WHISPER)) {
            return false;
        }

        for (UserItem area : this.room.getRoomSpecialTypes().getItemsOfType(InteractionMuteArea.class)) {
            if (((InteractionMuteArea) area).inSquare(user.getRoomUnit().getCurrentLocation())) {
                return true;
            }
        }

        return false;
    }

    /** Replaces the words of the room's filter with "bobba", unless the Habbo is exempt. */
    private void applyRoomWordFilter(User user, RoomChatMessage roomChatMessage) {
        if (this.wordFilterWords.isEmpty() || user.hasPermission(Permission.ACC_CHAT_NO_FILTER)) {
            return;
        }

        for (String string : this.wordFilterWords) {
            roomChatMessage.setMessage(
                    roomChatMessage.getMessage().replaceAll("(?i)" + Pattern.quote(string), "bobba"));
        }
    }

    /** Runs the line as a command when it is one; wired then hears it as said, and nobody sees it. */
    private boolean ranAsCommand(User user, RoomChatMessage roomChatMessage, String wiredSayMessage) {
        if (!CommandHandler.handleChatCommand(user.getClient(), roomChatMessage.getUnfilteredMessage())) {
            return false;
        }

        WiredManager.triggerUserSays(user.getUserInfo().getCurrentRoom(), user.getRoomUnit(), wiredSayMessage);
        roomChatMessage.isCommand = true;
        return true;
    }

    /** The packet shown before the line in place of the name: a plugin's, or the rank prefix; null for none. */
    private ServerMessage prefixFor(User user, RoomChatMessage roomChatMessage, RoomChatType chatType) {
        if (this.room.pluginManager().isRegistered(UsernameTalkEvent.class, true)) {
            UsernameTalkEvent usernameTalkEvent =
                    this.room.pluginManager().fireEvent(new UsernameTalkEvent(user, roomChatMessage, chatType));
            if (usernameTalkEvent.hasCustomComposer()) {
                return usernameTalkEvent.getCustomComposer();
            }
        }

        return roomChatMessage.getUser().getUserInfo().getRank().hasPrefix()
                ? new RoomUserNameChangedComposer(user, true).compose()
                : null;
    }

    /** Drops trailing whitespace; a line of spaces only becomes a single space. */
    private static void trimTrailingWhitespace(RoomChatMessage roomChatMessage) {
        String trimmedMessage = roomChatMessage.getMessage().replaceAll("\\s+$", "");

        if (trimmedMessage.isEmpty()) {
            trimmedMessage = " ";
        }

        roomChatMessage.setMessage(trimmedMessage);
    }

    /** A line wired keeps from the room: only the speaker sees it, as a whisper. */
    private static void whisperBackToSpeaker(User user, RoomChatMessage roomChatMessage) {
        user.getClient()
                .sendResponse(new RoomUserWhisperComposer(
                        new RoomChatMessage(roomChatMessage.getMessage(), user, user, roomChatMessage.getBubble())));
    }

    /**
     * Sends a talk or shout line to one listener: the prefix around it (not for the old chat), the line through their
     * own word filter, and turns their head toward the speaker.
     */
    private static void deliverTo(
            User listener,
            User speaker,
            RoomChatMessage roomChatMessage,
            ServerMessage message,
            Function<RoomChatMessage, MessageComposer> composer,
            ServerMessage prefixMessage,
            ServerMessage clearPrefixMessage) {
        if (prefixMessage != null && !listener.getUserStats().preferOldChat) {
            listener.getClient().sendResponse(prefixMessage);
        }
        listener.getClient().sendResponse(chatPacketFor(listener, speaker, roomChatMessage, message, composer));
        if (clearPrefixMessage != null && !listener.getUserStats().preferOldChat) {
            listener.getClient().sendResponse(clearPrefixMessage);
        }

        // Turn head toward speaker if conditions are met
        if (!listener.equals(speaker)) {
            turnHeadTowardSpeaker(listener.getRoomUnit(), speaker.getRoomUnit());
        }
    }

    /**
     * Handles whisper chat.
     */
    private void handleWhisper(
            User user, RoomChatMessage roomChatMessage, ServerMessage prefixMessage, ServerMessage clearPrefixMessage) {
        if (roomChatMessage.getTargetUser() == null) {
            return;
        }

        RoomChatMessage staffChatMessage = new RoomChatMessage(roomChatMessage);
        staffChatMessage.setMessage("To "
                + staffChatMessage.getTargetUser().getUserInfo().getUsername() + ": " + staffChatMessage.getMessage());

        final ServerMessage message = new RoomUserWhisperComposer(roomChatMessage).compose();
        final ServerMessage staffMessage = new RoomUserWhisperComposer(staffChatMessage).compose();

        for (User h : this.room.getUsers()) {
            if (h == roomChatMessage.getTargetUser() || h == user) {
                if (!h.getUserStats().userIgnored(user.getUserInfo().getId())
                        && !h.getUserStats().userBlocked(user.getUserInfo().getId())) {
                    if (prefixMessage != null) {
                        h.getClient().sendResponse(prefixMessage);
                    }
                    h.getClient()
                            .sendResponse(
                                    chatPacketFor(h, user, roomChatMessage, message, RoomUserWhisperComposer::new));

                    if (clearPrefixMessage != null) {
                        h.getClient().sendResponse(clearPrefixMessage);
                    }
                }

                continue;
            }
            if (h.hasPermission(Permission.ACC_SEE_WHISPERS)) {
                h.getClient().sendResponse(staffMessage);
            }
        }
    }

    /**
     * Handles normal talk.
     */
    private void handleTalk(
            User user,
            RoomChatMessage roomChatMessage,
            ServerMessage prefixMessage,
            ServerMessage clearPrefixMessage,
            Rectangle tentRectangle) {
        ServerMessage message = new RoomUserTalkComposer(roomChatMessage).compose();
        boolean noChatLimit = user.hasPermission(Permission.ACC_CHAT_NO_LIMIT);
        int chatDistance = this.room.getChatDistance();

        for (User h : this.room.getUsers()) {
            if ((h.getRoomUnit()
                                            .getCurrentLocation()
                                            .distance(user.getRoomUnit().getCurrentLocation())
                                    <= chatDistance
                            || h.equals(user)
                            || this.room.hasRights(h)
                            || noChatLimit)
                    && (tentRectangle == null
                            || RoomLayout.tileInSquare(
                                    tentRectangle, h.getRoomUnit().getCurrentLocation()))) {
                if (!h.getUserStats().userIgnored(user.getUserInfo().getId())
                        && !h.getUserStats().userBlocked(user.getUserInfo().getId())) {
                    deliverTo(
                            h,
                            user,
                            roomChatMessage,
                            message,
                            RoomUserTalkComposer::new,
                            prefixMessage,
                            clearPrefixMessage);
                }
                continue;
            }
            // Staff should be able to see the tent chat anyhow
            this.showTentChatMessageOutsideTentIfPermitted(h, roomChatMessage, tentRectangle);
        }
    }

    /**
     * Handles shout chat.
     */
    private void handleShout(
            User user,
            RoomChatMessage roomChatMessage,
            ServerMessage prefixMessage,
            ServerMessage clearPrefixMessage,
            Rectangle tentRectangle) {
        ServerMessage message = new RoomUserShoutComposer(roomChatMessage).compose();

        for (User h : this.room.getUsers()) {
            if (!h.getUserStats().userIgnored(user.getUserInfo().getId())
                    && !h.getUserStats().userBlocked(user.getUserInfo().getId())
                    && (tentRectangle == null
                            || RoomLayout.tileInSquare(
                                    tentRectangle, h.getRoomUnit().getCurrentLocation()))) {
                deliverTo(
                        h,
                        user,
                        roomChatMessage,
                        message,
                        RoomUserShoutComposer::new,
                        prefixMessage,
                        clearPrefixMessage);
                continue;
            }
            // Staff should be able to see the tent chat anyhow
            this.showTentChatMessageOutsideTentIfPermitted(h, roomChatMessage, tentRectangle);
        }
    }

    /** How long a listener looks at the speaker before the head returns to the body (AIR: 5 s). */
    static final long HEAD_TURN_MS = 5000L;

    private static final String HEAD_TURN_TOKEN = "chat.head_turn.token";

    /**
     * Turns the heads of the avatars in the room toward a bot that speaks aloud, as they turn toward a talking user
     * (AIR). Bots and pets do not turn.
     */
    public static void turnHeadsTowardSpeaker(Room room, RoomUnit speaker) {
        if (room == null || speaker == null) {
            return;
        }

        for (User user : room.getUsers()) {
            RoomUnit listener = user.getRoomUnit();
            if (listener != null && listener != speaker) {
                turnHeadTowardSpeaker(listener, speaker);
            }
        }
    }

    private static void turnHeadTowardSpeaker(RoomUnit roomUnit, RoomUnit speaker) {
        if (roomUnit.isWalking()
                || roomUnit.hasStatus(RoomUnitStatus.MOVE)
                || roomUnit.hasStatus(RoomUnitStatus.SIT)
                || roomUnit.hasStatus(RoomUnitStatus.LAY)
                || roomUnit.isIdle()
                || roomUnit.isInvisible()) {
            return;
        }

        RoomUserRotation targetRotation = headRotationTowardSpeaker(
                roomUnit.getX(),
                roomUnit.getY(),
                roomUnit.getBodyRotation().getValue(),
                speaker.getX(),
                speaker.getY());
        if (targetRotation == null) {
            return;
        }

        roomUnit.setHeadRotation(targetRotation);
        roomUnit.statusUpdate(true);

        // The head goes back 5 s later, unless a later line turned it again meanwhile (the later line wins).
        Object token = new Object();
        roomUnit.getCacheable().put(HEAD_TURN_TOKEN, token);
        Emulator.getThreading()
                .run(
                        () -> {
                            if (roomUnit.getCacheable().get(HEAD_TURN_TOKEN) == token
                                    && roomUnit.isInRoom()
                                    && !roomUnit.isWalking()
                                    && roomUnit.getHeadRotation() == targetRotation) {
                                roomUnit.setHeadRotation(roomUnit.getBodyRotation());
                                roomUnit.statusUpdate(true);
                            }
                        },
                        HEAD_TURN_MS);
    }

    /**
     * Head rotation a listener turns to when someone speaks, or null when the head stays put. AIR: a speaker more
     * than 2 steps from the body's direction is out of view; at 2 steps the head turns one step toward them, at 1 it
     * looks straight at them, straight ahead it stays with the body. A speaker on the listener's tile has no
     * direction.
     */
    static RoomUserRotation headRotationTowardSpeaker(
            int listenerX, int listenerY, int bodyRotation, int speakerX, int speakerY) {
        if (listenerX == speakerX && listenerY == speakerY) {
            return null;
        }

        int target = Rotation.Calculate(listenerX, listenerY, speakerX, speakerY);
        // Signed steps from the body to the speaker, -4..3.
        int steps = Math.floorMod(target - bodyRotation + 4, 8) - 4;
        if (Math.abs(steps) > 2) {
            return null;
        }

        return RoomUserRotation.values()[Math.floorMod(bodyRotation + Integer.signum(steps), 8)];
    }

    /**
     * The chat packet a recipient receives: the shared one, or a copy in which the words of their
     * personal word filter are masked. The speaker always sees their own text.
     */
    public static ServerMessage chatPacketFor(
            User recipient,
            User speaker,
            RoomChatMessage roomChatMessage,
            ServerMessage shared,
            Function<RoomChatMessage, MessageComposer> composer) {
        if (recipient == speaker || recipient.getUserStats() == null) {
            return shared;
        }

        UserWordFilter filter = recipient.getUserStats().getCustomWordFilter();
        if (filter == null || filter.isEmpty()) {
            return shared;
        }

        String masked = filter.apply(roomChatMessage.getMessage(), WordFilter.DEFAULT_REPLACEMENT);
        if (masked.equals(roomChatMessage.getMessage())) {
            return shared;
        }

        RoomChatMessage personal = new RoomChatMessage(roomChatMessage);
        personal.setMessage(masked);
        return composer.apply(personal).compose();
    }

    /**
     * Shows tent chat to staff outside the tent.
     */
    public void showTentChatMessageOutsideTentIfPermitted(
            User receivingUser, RoomChatMessage roomChatMessage, Rectangle tentRectangle) {
        if (receivingUser != null
                && receivingUser.hasPermission(Permission.ACC_SEE_TENTCHAT)
                && tentRectangle != null
                && !RoomLayout.tileInSquare(
                        tentRectangle, receivingUser.getRoomUnit().getCurrentLocation())) {
            RoomChatMessage staffChatMessage = new RoomChatMessage(roomChatMessage);
            staffChatMessage.setMessage("[" + Emulator.getTexts().getValue("hotel.room.tent.prefix") + "] "
                    + staffChatMessage.getMessage());
            final ServerMessage staffMessage = new RoomUserWhisperComposer(staffChatMessage).compose();
            receivingUser.getClient().sendResponse(staffMessage);
        }
    }

    /**
     * Notifies bots of a chat message.
     */
    private void notifyBots(RoomChatMessage roomChatMessage) {
        synchronized (this.room.getUnitManager().getCurrentBots()) {
            for (Bot bot : this.room.getUnitManager().getCurrentBots().values()) {
                try {
                    bot.onUserSay(roomChatMessage);

                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                    break;
                }
            }
        }
    }

    /**
     * Handles talking furniture responses.
     */
    private void handleTalkingFurniture(User user, RoomChatMessage roomChatMessage) {
        if (roomChatMessage.getBubble().triggersTalkingFurniture()) {
            Set<UserItem> items = this.room.getRoomSpecialTypes().getItemsOfType(InteractionTalkingFurniture.class);

            for (UserItem item : items) {
                if (item.getExtradata().equals("1")) {
                    continue;
                }
                if (this.room
                                .getLayout()
                                .getTile(item.getX(), item.getY())
                                .distance(user.getRoomUnit().getCurrentLocation())
                        <= Emulator.getConfig().getInt("furniture.talking.range")) {
                    int count = Emulator.getConfig().getInt(item.getBaseItem().getName() + ".message.count", 0);

                    if (count > 0) {
                        int randomValue = Emulator.getRandom().nextInt(count + 1);

                        RoomChatMessage itemMessage = new RoomChatMessage(
                                Emulator.getTexts()
                                        .getValue(
                                                item.getBaseItem().getName() + ".message." + randomValue,
                                                item.getBaseItem().getName() + ".message." + randomValue
                                                        + " not found!"),
                                user,
                                RoomChatMessageBubbles.getBubble(Emulator.getConfig()
                                        .getInt(
                                                item.getBaseItem().getName() + ".message.bubble",
                                                RoomChatMessageBubbles.PARROT.getType())));

                        this.room.sendComposer(new RoomUserTalkComposer(itemMessage).compose());

                        try {
                            item.onClick(user.getClient(), this.room, new Object[0]);
                            item.setExtradata("1");
                            this.room.updateItemState(item);

                            this.room
                                    .threading()
                                    .run(
                                            () -> {
                                                item.setExtradata("0");
                                                this.room.updateItemState(item);
                                            },
                                            2000);

                            break;
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }
                    }
                }
            }
        }
    }

    // ==================== DISPOSAL ====================

    /**
     * Clears chat manager state.
     */
    public void clear() {
        synchronized (this.wordFilterWords) {
            this.wordFilterWords.clear();
        }
        this.clearMutes();
    }

    void clearMutes() {
        synchronized (this.mutedUsers) {
            this.mutedUsers.clear();
        }
    }

    /**
     * Disposes the chat manager.
     */
    public void dispose() {
        this.clear();
    }
}
