package polaris.hotel.rooms;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.DatabaseLoggable;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.games.battlebanzai.BattleBanzaiGame;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.games.wired.WiredGame;
import polaris.hotel.modtool.WordFilter;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.ChatStyleRepository;
import polaris.hotel.users.User;
import polaris.hotel.users.UserCustomizationData;
import polaris.hotel.users.UserInfo;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.incoming.MessageHandler;

public class RoomChatMessage implements Runnable, ISerialize, DatabaseLoggable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomChatMessage.class);
    private static final String QUERY =
            "INSERT INTO chatlogs_room (user_from_id, user_to_id, message, timestamp, room_id) VALUES (?, ?, ?, ?, ?)";

    public static final int NO_BUBBLE_WIDTH_OVERRIDE = -1;
    private static final int WIDEST_BUBBLE_WIDTH = 0, THINNEST_BUBBLE_WIDTH = 2;
    private static final List<String> chatColors = Arrays.asList("@red@", "@cyan@", "@blue@", "@green@", "@purple@");
    public static volatile int MAXIMUM_LENGTH = 100;
    // Configuration. Loaded from database & updated accordingly.
    public static volatile boolean SAVE_ROOM_CHATS = false;
    public static volatile int[] BANNED_BUBBLES = {};
    private final User user;
    public int roomId;
    public boolean isCommand = false;
    /** Set by the room chat once the line has been sent to the room; a refused line stays false. */
    public boolean delivered = false;

    public boolean filtered = false;
    private int roomUnitId;
    private String message;
    private String unfilteredMessage;
    private int timestamp = 0;
    private RoomChatMessageBubbles bubble;
    private User targetUser;
    private byte emotion;
    private String RoomChatColour; // Added Chatcolor
    /** -1 follows the room setting; 0 wide, 1 normal, 2 thin as the room chat settings name them. */
    private int bubbleWidthOverride = NO_BUBBLE_WIDTH_OVERRIDE;
    /** The bubble shows the message without the speaker's name, as a wired bot may ask. */
    private boolean hideUsername = false;

    public RoomChatMessage(MessageHandler message) {
        if (message.packet.getMessageId() == Incoming.RoomUserWhisperEvent) {
            String data = message.packet.readString();
            this.targetUser =
                    message.client.getUser().getUserInfo().getCurrentRoom().getUser(data.split(" ")[0]);
            this.message = data.substring(data.split(" ")[0].length() + 1);
        } else {
            this.message = message.packet.readString();
        }
        this.message = truncateToMaximumLength(this.message);
        try {
            this.bubble = RoomChatMessageBubbles.getBubble(message.packet.readInt());
        } catch (Exception e) {
            this.bubble = RoomChatMessageBubbles.NORMAL;
        }

        this.RoomChatColour = message.packet.readString();

        if (!message.client.getUser().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            for (Integer i : RoomChatMessage.BANNED_BUBBLES) {
                if (i == this.bubble.getType()) {
                    this.bubble = RoomChatMessageBubbles.NORMAL;
                    break;
                }
            }

            // A catalog-sold style only for its owners; the ownership lookup only runs for those styles.
            if (ChatStyleRepository.isPurchasable(this.bubble.getType())
                    && !ChatStyleRepository.owns(
                            message.client.getUser().getUserInfo().getId(), this.bubble.getType())) {
                this.bubble = RoomChatMessageBubbles.NORMAL;
            }
        }

        this.user = message.client.getUser();
        this.bubble = resolveSpeakerBubble(this.bubble, this.user);
        this.roomUnitId = this.user.getRoomUnit().getId();
        this.unfilteredMessage = this.message;
        this.timestamp = now();

        this.checkEmotion();

        this.filter();
    }

    /**
     * The bubble a user's own talk, shout or whisper goes out in. A member of a Wired team speaks in the team's
     * bubble (AIR styles 130 red, 131 blue, 132 yellow, 133 green), whatever style they picked; so does a member of a
     * Battle Banzai or Freeze team, as on AIR. A client cannot ask for a team bubble itself.
     */
    static RoomChatMessageBubbles resolveSpeakerBubble(RoomChatMessageBubbles requested, User speaker) {
        RoomChatMessageBubbles bubble = requested;
        if (bubble == null || RoomChatMessageBubbles.isWiredTeamBubble(bubble.getType())) {
            bubble = RoomChatMessageBubbles.NORMAL;
        }

        RoomChatMessageBubbles team = wiredTeamBubble(speaker);
        return team != null ? team : bubble;
    }

    /** The team bubble of the speaker, or null when they are not in a coloured Wired, Banzai or Freeze team. */
    static RoomChatMessageBubbles wiredTeamBubble(User speaker) {
        if (speaker == null || speaker.getUserInfo() == null) {
            return null;
        }

        UserInfo info = speaker.getUserInfo();
        Class<?> game = info.getCurrentGame();
        if (game != WiredGame.class && game != BattleBanzaiGame.class && game != FreezeGame.class) {
            return null;
        }

        GamePlayer player = info.getGamePlayer();
        if (player == null || player.getTeamColor() == null) {
            return null;
        }

        switch (player.getTeamColor()) {
            case RED:
                return RoomChatMessageBubbles.WIRED_TEAM_RED;
            case BLUE:
                return RoomChatMessageBubbles.WIRED_TEAM_BLUE;
            case YELLOW:
                return RoomChatMessageBubbles.WIRED_TEAM_YELLOW;
            case GREEN:
                return RoomChatMessageBubbles.WIRED_TEAM_GREEN;
            default:
                return null;
        }
    }

    public RoomChatMessage(RoomChatMessage chatMessage) {
        this.message = chatMessage.getMessage();
        this.unfilteredMessage = chatMessage.getUnfilteredMessage();
        this.user = chatMessage.getUser();
        this.targetUser = chatMessage.getTargetUser();
        this.bubble = chatMessage.getBubble();
        this.roomUnitId = chatMessage.roomUnitId;
        this.emotion = (byte) chatMessage.getEmotion();
        this.bubbleWidthOverride = chatMessage.bubbleWidthOverride;
        this.hideUsername = chatMessage.hideUsername;
    }

    public RoomChatMessage(String message, RoomUnit roomUnit, RoomChatMessageBubbles bubble) {
        this.message = message;
        this.unfilteredMessage = message;
        this.user = null;
        this.bubble = bubble;
        this.roomUnitId = roomUnit.getId();
    }

    public RoomChatMessage(String message, User user, RoomChatMessageBubbles bubble) {
        this.message = message;
        this.unfilteredMessage = message;
        this.user = user;
        this.bubble = bubble;
        this.checkEmotion();
        this.roomUnitId = user.getRoomUnit().getId();
        this.message = this.message.replace("\r", "").replace("\n", "");

        if (this.bubble.isOverridable() && this.getUser().getUserStats().chatColor != RoomChatMessageBubbles.NORMAL)
            this.bubble = this.getUser().getUserStats().chatColor;
    }

    public RoomChatMessage(String message, User user, User targetUser, RoomChatMessageBubbles bubble) {
        this.message = message;
        this.unfilteredMessage = message;
        this.user = user;
        this.targetUser = targetUser;
        this.bubble = bubble;
        this.checkEmotion();
        this.roomUnitId = this.user.getRoomUnit().getId();
        this.message = this.message.replace("\r", "").replace("\n", "");

        if (this.bubble.isOverridable() && this.getUser().getUserStats().chatColor != RoomChatMessageBubbles.NORMAL)
            this.bubble = this.getUser().getUserStats().chatColor;
    }

    private void checkEmotion() {
        if (this.message.contains(":)") || this.message.contains(":-)") || this.message.contains(":]")) {
            this.emotion = 1;
        } else if (this.message.contains(":@") || this.message.contains(">:(")) {
            this.emotion = 2;
        } else if (this.message.contains(":o")
                || this.message.contains(":O")
                || this.message.contains(":0")
                || this.message.contains("O.o")
                || this.message.contains("o.O")
                || this.message.contains("O.O")) {
            this.emotion = 3;
        } else if (this.message.contains(":(") || this.message.contains(":-(") || this.message.contains(":[")) {
            this.emotion = 4;
        }
    }

    @Override
    public void run() {
        if (this.user == null) return;

        this.message = truncateToMaximumLength(this.message);

        Emulator.getDatabaseLogger().store(this);
    }

    /**
     * A longer line is cut to its first {@link #MAXIMUM_LENGTH} characters (a full-length line is kept whole), as in
     * Habbo, instead of being dropped. Behaviour observed in plus-emulator/PlusEMU 3a043f7d, reimplemented.
     */
    public static String truncateToMaximumLength(String message) {
        int maximum = Math.max(1, RoomChatMessage.MAXIMUM_LENGTH);
        if (message == null || message.length() <= maximum) {
            return message;
        }
        return message.substring(0, maximum);
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUnfilteredMessage() {
        return this.unfilteredMessage;
    }

    public RoomChatMessageBubbles getBubble() {
        return this.bubble;
    }

    public User getUser() {
        return this.user;
    }

    public User getTargetUser() {
        return this.targetUser;
    }

    public int getEmotion() {
        return this.emotion;
    }

    @Override
    public void serialize(ServerMessage message) {
        if (this.user != null && this.bubble.isOverridable()) {
            if (!this.user.hasPermission(Permission.ACC_ANYCHATCOLOR)) {
                for (Integer i : RoomChatMessage.BANNED_BUBBLES) {
                    if (i == this.bubble.getType()) {
                        this.bubble = RoomChatMessageBubbles.NORMAL;
                        break;
                    }
                }
            }
        }

        if (!this.getBubble().getPermission().isEmpty()) {
            if (this.user != null && !this.user.hasPermission(this.getBubble().getPermission())) {
                this.bubble = RoomChatMessageBubbles.NORMAL;
            }
        }

        try {
            message.appendInt(this.roomUnitId);
            message.appendString(this.getMessage());
            message.appendInt(this.getEmotion());
            message.appendInt(this.getBubble().getType());
            message.appendInt(0);
            message.appendString(this.RoomChatColour); // Added packet for room chat
            message.appendInt(this.getMessage().length());

            // Custom prefix data
            UserCustomizationData customizationData =
                    (this.user != null) ? UserCustomizationData.fromUser(this.user) : UserCustomizationData.empty();
            message.appendString(customizationData.prefixText);
            message.appendString(customizationData.prefixColor);
            message.appendString(customizationData.prefixIcon);
            message.appendString(customizationData.prefixEffect);
            message.appendString(customizationData.prefixFont);
            message.appendString(customizationData.nickIcon);
            message.appendString(customizationData.displayOrder);
            // Optional tail: an old client stops reading before it, a new one reads -1 as "room setting".
            message.appendInt(this.bubbleWidthOverride);
            // Optional tail after the width: an old client stops reading before it.
            message.appendBoolean(this.hideUsername);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    public int getBubbleWidthOverride() {
        return this.bubbleWidthOverride;
    }

    public void setBubbleWidthOverride(int bubbleWidthOverride) {
        this.bubbleWidthOverride = normalizeBubbleWidthOverride(bubbleWidthOverride);
    }

    public boolean isHideUsername() {
        return this.hideUsername;
    }

    public void setHideUsername(boolean hideUsername) {
        this.hideUsername = hideUsername;
    }

    /** Anything that is not one of the three widths means "no override". */
    public static int normalizeBubbleWidthOverride(int value) {
        return (value >= WIDEST_BUBBLE_WIDTH && value <= THINNEST_BUBBLE_WIDTH) ? value : NO_BUBBLE_WIDTH_OVERRIDE;
    }

    public void filter() {
        if (!this.user.getUserStats().hasActiveClub()) {
            for (String chatColor : chatColors) {
                this.message = this.message.replace(chatColor, "");
            }
        }

        if (roomWordFilterEnabled()) {
            if (!this.user.hasPermission(Permission.ACC_CHAT_NO_FILTER)) {
                if (!wordFilter().autoReportCheck(this)) {
                    if (!wordFilter().hideMessageCheck(this.message)) {
                        wordFilter().filter(this, this.user);
                        return;
                    }
                } else {
                    int muteTime = Emulator.getConfig().getInt("hotel.wordfilter.automute");
                    if (muteTime > 0) {
                        this.user.mute(muteTime, false);
                    } else {
                        LOGGER.error("Invalid hotel.wordfilter.automute defined in emulator_settings ({}).", muteTime);
                    }
                }

                this.message = "";
            }
        }
    }

    /**
     * The room word filter for a line someone else wrote and a command puts in this user's mouth, such
     * as :say or :shout. It is judged on the author: their filter permission, their word filter events.
     * It never auto-reports or mutes the user who is made to speak, who did not write it.
     */
    public void filterWrittenBy(User author) {
        if (!roomWordFilterEnabled() || author.hasPermission(Permission.ACC_CHAT_NO_FILTER)) {
            return;
        }

        if (wordFilter().hideMessageCheck(this.message)) {
            this.message = "";
            return;
        }

        wordFilter().filter(this, author);
    }

    /**
     * Logs a line a command put in this user's mouth, under this user and with the author's name and
     * the command appended, so the room chat log shows who really wrote it. Follows the same
     * save-room-chats setting as ordinary talk.
     */
    public void logWrittenBy(User author, String command) {
        if (!SAVE_ROOM_CHATS) {
            return;
        }

        RoomChatMessage logged = new RoomChatMessage(this);
        logged.unfilteredMessage = this.unfilteredMessage + " [:" + command + " "
                + author.getUserInfo().getUsername() + "]";
        logged.timestamp = now();
        logged.run();
    }

    private static boolean roomWordFilterEnabled() {
        return Emulator.getConfig().getBoolean("hotel.wordfilter.enabled")
                && Emulator.getConfig().getBoolean("hotel.wordfilter.rooms");
    }

    private static WordFilter wordFilter() {
        return Emulator.getGameEnvironment().getWordFilter();
    }

    private static int now() {
        return Emulator.getIntUnixTimestamp();
    }

    @Override
    public String getQuery() {
        return QUERY;
    }

    @Override
    public void log(PreparedStatement statement) throws SQLException {
        statement.setInt(1, this.user.getUserInfo().getId());

        if (this.targetUser != null)
            statement.setInt(2, this.targetUser.getUserInfo().getId());
        else statement.setInt(2, 0);

        statement.setString(3, this.unfilteredMessage);
        statement.setInt(4, this.timestamp);

        if (this.user.getUserInfo().getCurrentRoom() != null) {
            statement.setInt(5, this.user.getUserInfo().getCurrentRoom().getId());
        } else {
            statement.setInt(5, 0);
        }

        statement.addBatch();
    }
}
