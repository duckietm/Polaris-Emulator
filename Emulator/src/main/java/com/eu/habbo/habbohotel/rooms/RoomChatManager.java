package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.items.interactions.InteractionMuteArea;
import com.eu.habbo.habbohotel.items.interactions.InteractionTalkingFurniture;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserShoutComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTalkComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTypingComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import com.eu.habbo.messages.outgoing.users.MutedWhisperComposer;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import com.eu.habbo.plugin.events.users.UsernameTalkEvent;
import com.eu.habbo.threading.runnables.YouAreAPirate;
import com.eu.habbo.util.pathfinding.Rotation;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Manages all chat functionality within a room.
 * Handles talking, shouting, whispering, word filtering, flood protection, and muting.
 */
public class RoomChatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomChatManager.class);

    private final Room room;

    // Word filter
    private final THashSet<String> wordFilterWords;

    // Muted Habbos: userId -> unmute timestamp
    private final TIntIntHashMap mutedHabbos;

    // Flood protection settings
    private final int muteTime;

    // Global chat delay setting
    public static boolean HABBO_CHAT_DELAY = false;

    // Mute area can whisper setting
    public static boolean MUTEAREA_CAN_WHISPER = false;

    public RoomChatManager(Room room) {
        this.room = room;
        this.wordFilterWords = new THashSet<>(0);
        this.mutedHabbos = new TIntIntHashMap();
        this.muteTime = Emulator.getConfig().getInt("hotel.flood.mute.time", 30);
    }

    // ==================== WORD FILTER ====================

    /**
     * Loads word filter from the database.
     */
    public void loadWordFilter(Connection connection) {
        synchronized (this.wordFilterWords) {
            this.wordFilterWords.clear();

            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT word FROM room_wordfilter WHERE room_id = ?")) {
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
            if (this.wordFilterWords.contains(word)) {
                return;
            }

            try (Connection connection = Emulator.getDatabase().getDataSource()
                .getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO room_wordfilter VALUES (?, ?)")) {
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

            try (Connection connection = Emulator.getDatabase().getDataSource()
                .getConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wordfilter WHERE room_id = ? AND word = ?")) {
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
    public THashSet<String> getWordFilterWords() {
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
    public void muteHabbo(Habbo habbo, int minutes) {
        synchronized (this.mutedHabbos) {
            this.mutedHabbos.put(habbo.getHabboInfo().getId(),
                Emulator.getIntUnixTimestamp() + (minutes * 60));
        }
    }

    /**
     * Removes a room mute from a Habbo.
     */
    public void unmuteHabbo(Habbo habbo) {
        synchronized (this.mutedHabbos) {
            this.mutedHabbos.remove(habbo.getHabboInfo().getId());
        }
    }

    /**
     * Checks if a Habbo is muted.
     */
    public boolean isMuted(Habbo habbo) {
        if (this.room.isOwner(habbo) || this.room.hasRights(habbo)) {
            return false;
        }

        if (this.mutedHabbos.containsKey(habbo.getHabboInfo().getId())) {
            boolean time =
                this.mutedHabbos.get(habbo.getHabboInfo().getId()) > Emulator.getIntUnixTimestamp();

            if (!time) {
                this.mutedHabbos.remove(habbo.getHabboInfo().getId());
            }

            return time;
        }

        return false;
    }

    /**
     * Gets remaining mute time for a Habbo.
     */
    public int getMuteTimeRemaining(Habbo habbo) {
        if (this.mutedHabbos.containsKey(habbo.getHabboInfo().getId())) {
            return Math.max(0,
                this.mutedHabbos.get(habbo.getHabboInfo().getId()) - Emulator.getIntUnixTimestamp());
        }
        return 0;
    }

    /**
     * Gets the muted Habbos map.
     */
    public TIntIntHashMap getMutedHabbos() {
        return this.mutedHabbos;
    }

    /**
     * Applies flood mute to a Habbo.
     */
    public void floodMuteHabbo(Habbo habbo, int timeOut) {
        habbo.getHabboStats().mutedCount++;
        timeOut += (timeOut * (int) Math.ceil(Math.pow(habbo.getHabboStats().mutedCount, 2)));
        habbo.getHabboStats().chatCounter.set(0);
        habbo.mute(timeOut, true);
    }

    // ==================== CHAT METHODS ====================

    /**
     * Handles talking in the room.
     */
    public void talk(Habbo habbo, RoomChatMessage roomChatMessage, RoomChatType chatType) {
        this.talk(habbo, roomChatMessage, chatType, false);
    }

    /**
     * Handles talking in the room with wired ignore option.
     */
    public void talk(final Habbo habbo, final RoomChatMessage roomChatMessage, RoomChatType chatType,
        boolean ignoreWired) {
        if (!habbo.getHabboStats().allowTalk()) {
            return;
        }

        if (habbo.getRoomUnit().isInvisible() && Emulator.getConfig()
            .getBoolean("invisible.prevent.chat", false)) {
            if (!CommandHandler.handleCommand(habbo.getClient(),
                roomChatMessage.getUnfilteredMessage())) {
                habbo.whisper(Emulator.getTexts().getValue("invisible.prevent.chat.error"));
            }

            return;
        }

        if (habbo.getHabboInfo().getCurrentRoom() != this.room) {
            return;
        }

        long millis = System.currentTimeMillis();
        if (HABBO_CHAT_DELAY) {
            if (millis - habbo.getHabboStats().lastChat < 750) {
                return;
            }
        }
        habbo.getHabboStats().lastChat = millis;
        
        // Easter egg
        if (roomChatMessage != null && Emulator.getConfig().getBoolean("easter_eggs.enabled")
            && roomChatMessage.getMessage().equalsIgnoreCase("i am a pirate")) {
            habbo.getHabboStats().chatCounter.addAndGet(1);
            Emulator.getThreading().run(new YouAreAPirate(habbo, this.room));
            return;
        }

        // Handle idle event
        UserIdleEvent event = new UserIdleEvent(habbo, UserIdleEvent.IdleReason.TALKED, false);
        Emulator.getPluginManager().fireEvent(event);

        if (!event.isCancelled()) {
            if (!event.idle) {
                this.room.unIdle(habbo);
            }
        }

        this.room.sendComposer(new RoomUserTypingComposer(habbo.getRoomUnit(), false).compose());

        if (roomChatMessage == null || roomChatMessage.getMessage() == null
            || roomChatMessage.getMessage().equals("")) {
            return;
        }

        // Check mute area
        if (!habbo.hasPermission(Permission.ACC_NOMUTE) && (!MUTEAREA_CAN_WHISPER
            || chatType != RoomChatType.WHISPER)) {
            for (HabboItem area : this.room.getRoomSpecialTypes().getItemsOfType(InteractionMuteArea.class)) {
                if (((InteractionMuteArea) area).inSquare(habbo.getRoomUnit().getCurrentLocation())) {
                    return;
                }
            }
        }

        // Apply word filter
        if (!this.wordFilterWords.isEmpty()) {
            if (!habbo.hasPermission(Permission.ACC_CHAT_NO_FILTER)) {
                for (String string : this.wordFilterWords) {
                    roomChatMessage.setMessage(
                        roomChatMessage.getMessage().replaceAll("(?i)" + Pattern.quote(string), "bobba"));
                }
            }
        }

        // Check room/user mute
        if (!habbo.hasPermission(Permission.ACC_NOMUTE)) {
            if (this.room.isMuted() && !this.room.hasRights(habbo)) {
                return;
            }

            if (this.isMuted(habbo)) {
                habbo.getClient().sendResponse(new MutedWhisperComposer(
                    Math.max(1, this.getMuteTimeRemaining(habbo))));
                return;
            }
        }

        // Flood protection
        if (!habbo.hasPermission(Permission.ACC_CHAT_NO_FLOOD)) {
            final int chatCounter = habbo.getHabboStats().chatCounter.addAndGet(1);

            if (chatCounter > 3) {
                final boolean floodRights = Emulator.getConfig().getBoolean("flood.with.rights");
                final boolean hasRights = this.room.hasRights(habbo);

                if (floodRights || !hasRights) {
                    if (this.room.getChatProtection() == 0) {
                        this.floodMuteHabbo(habbo, this.muteTime);
                        return;
                    } else if (this.room.getChatProtection() == 1 && chatCounter > 4) {
                        this.floodMuteHabbo(habbo, this.muteTime);
                        return;
                    } else if (this.room.getChatProtection() == 2 && chatCounter > 5) {
                        this.floodMuteHabbo(habbo, this.muteTime);
                        return;
                    }
                }
            }
        }

        String wiredSayMessage = roomChatMessage.getMessage();

        // Handle commands and wired
        boolean suppressSaysOutput = false;
        if (chatType != RoomChatType.WHISPER) {
            if (CommandHandler.handleCommand(habbo.getClient(), roomChatMessage.getUnfilteredMessage())) {
                WiredManager.triggerUserSays(habbo.getHabboInfo().getCurrentRoom(), habbo.getRoomUnit(), wiredSayMessage);
                roomChatMessage.isCommand = true;
                return;
            }

            if (!ignoreWired) {
                suppressSaysOutput = WiredManager.shouldSuppressUserSaysOutput(
                    habbo.getHabboInfo().getCurrentRoom(),
                    habbo.getRoomUnit(),
                    wiredSayMessage,
                    chatType.ordinal(),
                    roomChatMessage.getBubble() != null ? roomChatMessage.getBubble().getType() : -1);
            }
        }

        // Build prefix messages
        ServerMessage prefixMessage = null;

        if (Emulator.getPluginManager().isRegistered(UsernameTalkEvent.class, true)) {
            UsernameTalkEvent usernameTalkEvent = Emulator.getPluginManager()
                .fireEvent(new UsernameTalkEvent(habbo, roomChatMessage, chatType));
            if (usernameTalkEvent.hasCustomComposer()) {
                prefixMessage = usernameTalkEvent.getCustomComposer();
            }
        }

        if (prefixMessage == null) {
            prefixMessage = roomChatMessage.getHabbo().getHabboInfo().getRank().hasPrefix()
                ? new RoomUserNameChangedComposer(habbo, true).compose() : null;
        }
        ServerMessage clearPrefixMessage =
            prefixMessage != null ? new RoomUserNameChangedComposer(habbo).compose() : null;

        Rectangle tentRectangle = this.room.getRoomSpecialTypes().tentAt(
            habbo.getRoomUnit().getCurrentLocation());

        // Trim message
        String trimmedMessage = roomChatMessage.getMessage().replaceAll("\\s+$", "");

        if (trimmedMessage.isEmpty()) {
            trimmedMessage = " ";
        }

        roomChatMessage.setMessage(trimmedMessage);

        // Send chat based on type
        if (chatType == RoomChatType.WHISPER) {
            this.handleWhisper(habbo, roomChatMessage, prefixMessage, clearPrefixMessage);
        } else if (chatType == RoomChatType.TALK) {
            if (suppressSaysOutput) {
                habbo.getClient().sendResponse(new RoomUserWhisperComposer(
                    new RoomChatMessage(roomChatMessage.getMessage(), habbo, habbo,
                        roomChatMessage.getBubble())));
            } else {
                this.handleTalk(habbo, roomChatMessage, prefixMessage, clearPrefixMessage, tentRectangle);
            }
        } else if (chatType == RoomChatType.SHOUT) {
            if (suppressSaysOutput) {
                habbo.getClient().sendResponse(new RoomUserWhisperComposer(
                    new RoomChatMessage(roomChatMessage.getMessage(), habbo, habbo,
                        roomChatMessage.getBubble())));
            } else {
                this.handleShout(habbo, roomChatMessage, prefixMessage, clearPrefixMessage, tentRectangle);
            }
        }

        if (chatType != RoomChatType.WHISPER && !ignoreWired && !roomChatMessage.isCommand) {
            WiredManager.triggerUserSays(
                    habbo.getHabboInfo().getCurrentRoom(),
                    habbo.getRoomUnit(),
                    wiredSayMessage,
                    chatType.ordinal(),
                    roomChatMessage.getBubble() != null ? roomChatMessage.getBubble().getType() : -1);
        }

        // Notify bots and talking furniture
        if (chatType == RoomChatType.TALK || chatType == RoomChatType.SHOUT) {
            this.notifyBots(roomChatMessage);
            this.handleTalkingFurniture(habbo, roomChatMessage);
        }
    }

    /**
     * Handles whisper chat.
     */
    private void handleWhisper(Habbo habbo, RoomChatMessage roomChatMessage,
        ServerMessage prefixMessage, ServerMessage clearPrefixMessage) {
        if (roomChatMessage.getTargetHabbo() == null) {
            return;
        }

        RoomChatMessage staffChatMessage = new RoomChatMessage(roomChatMessage);
        staffChatMessage.setMessage(
            "To " + staffChatMessage.getTargetHabbo().getHabboInfo().getUsername() + ": "
                + staffChatMessage.getMessage());

        final ServerMessage message = new RoomUserWhisperComposer(roomChatMessage).compose();
        final ServerMessage staffMessage = new RoomUserWhisperComposer(staffChatMessage).compose();

        for (Habbo h : this.room.getHabbos()) {
            if (h == roomChatMessage.getTargetHabbo() || h == habbo) {
                if (!h.getHabboStats().userIgnored(habbo.getHabboInfo().getId())) {
                    if (prefixMessage != null) {
                        h.getClient().sendResponse(prefixMessage);
                    }
                    h.getClient().sendResponse(message);

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
    private void handleTalk(Habbo habbo, RoomChatMessage roomChatMessage,
        ServerMessage prefixMessage, ServerMessage clearPrefixMessage, Rectangle tentRectangle) {
        ServerMessage message = new RoomUserTalkComposer(roomChatMessage).compose();
        boolean noChatLimit = habbo.hasPermission(Permission.ACC_CHAT_NO_LIMIT);
        int chatDistance = this.room.getChatDistance();

        for (Habbo h : this.room.getHabbos()) {
            if ((h.getRoomUnit().getCurrentLocation().distance(habbo.getRoomUnit().getCurrentLocation())
                <= chatDistance || h.equals(habbo) || this.room.hasRights(h) || noChatLimit) && (
                tentRectangle == null || RoomLayout.tileInSquare(tentRectangle,
                    h.getRoomUnit().getCurrentLocation()))) {
                if (!h.getHabboStats().userIgnored(habbo.getHabboInfo().getId())) {
                    if (prefixMessage != null && !h.getHabboStats().preferOldChat) {
                        h.getClient().sendResponse(prefixMessage);
                    }
                    h.getClient().sendResponse(message);
                    if (clearPrefixMessage != null && !h.getHabboStats().preferOldChat) {
                        h.getClient().sendResponse(clearPrefixMessage);
                    }
                    
                    // Turn head toward speaker if conditions are met
                    if (!h.equals(habbo)) {
                        RoomUnit roomUnit = h.getRoomUnit();
                        if (!roomUnit.isWalking() && !roomUnit.hasStatus(RoomUnitStatus.MOVE) 
                            && !roomUnit.hasStatus(RoomUnitStatus.LAY) && !roomUnit.isIdle() 
                            && !roomUnit.isInvisible()) {
                            RoomUserRotation targetRotation = RoomUserRotation.values()[
                                Rotation.Calculate(roomUnit.getX(), roomUnit.getY(), 
                                    habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY())];
                            // Only turn head if speaker is within peripheral vision (1 rotation step)
                            if (RoomUserRotation.rotationDistance(roomUnit.getBodyRotation().getValue(), 
                                targetRotation.getValue()) <= 1) {
                                roomUnit.setHeadRotation(targetRotation);
                                roomUnit.statusUpdate(true);
                                
                                // Schedule head reset after 2 seconds
                                Emulator.getThreading().run(() -> {
                                    if (roomUnit.isInRoom() && !roomUnit.isWalking() && !roomUnit.isIdle()) {
                                        roomUnit.setHeadRotation(roomUnit.getBodyRotation());
                                        roomUnit.statusUpdate(true);
                                    }
                                }, 2000);
                            }
                        }
                    }
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
    private void handleShout(Habbo habbo, RoomChatMessage roomChatMessage,
        ServerMessage prefixMessage, ServerMessage clearPrefixMessage, Rectangle tentRectangle) {
        ServerMessage message = new RoomUserShoutComposer(roomChatMessage).compose();

        for (Habbo h : this.room.getHabbos()) {
            if (!h.getHabboStats().userIgnored(habbo.getHabboInfo().getId()) && (tentRectangle == null
                || RoomLayout.tileInSquare(tentRectangle, h.getRoomUnit().getCurrentLocation()))) {
                if (prefixMessage != null && !h.getHabboStats().preferOldChat) {
                    h.getClient().sendResponse(prefixMessage);
                }
                h.getClient().sendResponse(message);
                if (clearPrefixMessage != null && !h.getHabboStats().preferOldChat) {
                    h.getClient().sendResponse(clearPrefixMessage);
                }
                
                // Turn head toward speaker if conditions are met
                if (!h.equals(habbo)) {
                    RoomUnit roomUnit = h.getRoomUnit();
                    if (!roomUnit.isWalking() && !roomUnit.hasStatus(RoomUnitStatus.MOVE) 
                        && !roomUnit.hasStatus(RoomUnitStatus.LAY) && !roomUnit.isIdle() 
                        && !roomUnit.isInvisible()) {
                        RoomUserRotation targetRotation = RoomUserRotation.values()[
                            Rotation.Calculate(roomUnit.getX(), roomUnit.getY(), 
                                habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY())];
                        // Only turn head if speaker is within peripheral vision (1 rotation step)
                        if (RoomUserRotation.rotationDistance(roomUnit.getBodyRotation().getValue(), 
                            targetRotation.getValue()) <= 1) {
                            roomUnit.setHeadRotation(targetRotation);
                            roomUnit.statusUpdate(true);
                            
                            // Schedule head reset after 2 seconds
                            Emulator.getThreading().run(() -> {
                                if (roomUnit.isInRoom() && !roomUnit.isWalking() && !roomUnit.isIdle()) {
                                    roomUnit.setHeadRotation(roomUnit.getBodyRotation());
                                    roomUnit.statusUpdate(true);
                                }
                            }, 2000);
                        }
                    }
                }
                continue;
            }
            // Staff should be able to see the tent chat anyhow
            this.showTentChatMessageOutsideTentIfPermitted(h, roomChatMessage, tentRectangle);
        }
    }

    /**
     * Shows tent chat to staff outside the tent.
     */
    public void showTentChatMessageOutsideTentIfPermitted(Habbo receivingHabbo,
        RoomChatMessage roomChatMessage, Rectangle tentRectangle) {
        if (receivingHabbo != null && receivingHabbo.hasPermission(Permission.ACC_SEE_TENTCHAT)
            && tentRectangle != null && !RoomLayout.tileInSquare(tentRectangle,
            receivingHabbo.getRoomUnit().getCurrentLocation())) {
            RoomChatMessage staffChatMessage = new RoomChatMessage(roomChatMessage);
            staffChatMessage.setMessage(
                "[" + Emulator.getTexts().getValue("hotel.room.tent.prefix") + "] "
                    + staffChatMessage.getMessage());
            final ServerMessage staffMessage = new RoomUserWhisperComposer(staffChatMessage).compose();
            receivingHabbo.getClient().sendResponse(staffMessage);
        }
    }

    /**
     * Notifies bots of a chat message.
     */
    private void notifyBots(RoomChatMessage roomChatMessage) {
        synchronized (this.room.getUnitManager().getCurrentBots()) {
            TIntObjectIterator<Bot> botIterator = this.room.getUnitManager().getCurrentBots().iterator();

            for (int i = this.room.getUnitManager().getCurrentBots().size(); i-- > 0; ) {
                try {
                    botIterator.advance();
                    Bot bot = botIterator.value();
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
    private void handleTalkingFurniture(Habbo habbo, RoomChatMessage roomChatMessage) {
        if (roomChatMessage.getBubble().triggersTalkingFurniture()) {
            THashSet<HabboItem> items = this.room.getRoomSpecialTypes().getItemsOfType(
                InteractionTalkingFurniture.class);

            for (HabboItem item : items) {
                if (item.getExtradata().equals("1")) {
                    continue;
                }
                if (this.room.getLayout().getTile(item.getX(), item.getY())
                    .distance(habbo.getRoomUnit().getCurrentLocation()) <= Emulator.getConfig()
                    .getInt("furniture.talking.range")) {
                    int count = Emulator.getConfig()
                        .getInt(item.getBaseItem().getName() + ".message.count", 0);

                    if (count > 0) {
                        int randomValue = Emulator.getRandom().nextInt(count + 1);

                        RoomChatMessage itemMessage = new RoomChatMessage(Emulator.getTexts()
                            .getValue(item.getBaseItem().getName() + ".message." + randomValue,
                                item.getBaseItem().getName() + ".message." + randomValue + " not found!"),
                            habbo, RoomChatMessageBubbles.getBubble(Emulator.getConfig()
                            .getInt(item.getBaseItem().getName() + ".message.bubble",
                                RoomChatMessageBubbles.PARROT.getType())));

                        this.room.sendComposer(new RoomUserTalkComposer(itemMessage).compose());

                        try {
                            item.onClick(habbo.getClient(), this.room, new Object[0]);
                            item.setExtradata("1");
                            this.room.updateItemState(item);

                            Emulator.getThreading().run(() -> {
                                item.setExtradata("0");
                                this.room.updateItemState(item);
                            }, 2000);

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
        synchronized (this.mutedHabbos) {
            this.mutedHabbos.clear();
        }
    }

    /**
     * Disposes the chat manager.
     */
    public void dispose() {
        this.clear();
    }
}
