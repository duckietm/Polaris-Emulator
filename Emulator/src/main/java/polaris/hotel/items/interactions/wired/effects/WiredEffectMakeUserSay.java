package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomChatType;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.ChatStyleRepository;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.rooms.users.RoomUserTalkComposer;

public class WiredEffectMakeUserSay extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;
    protected static final int VISIBILITY_SOURCE_USERS = 0;
    protected static final int VISIBILITY_ALL_ROOM_USERS = 1;
    // The official show message editor takes 400 characters.
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LENGTH = 400;
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LINES = 8;

    protected String message = "";
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int visibilitySelection = VISIBILITY_SOURCE_USERS;
    protected int bubbleStyle = RoomChatMessageBubbles.WIRED.getType();
    protected int bubbleWidthOverride = RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE;

    public WiredEffectMakeUserSay(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMakeUserSay(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.message);
        message.appendInt(3);
        message.appendInt(this.visibilitySelection);
        message.appendInt(this.bubbleStyle);
        message.appendInt(this.bubbleWidthOverride);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    /**
     * The bubble this box may speak in, judged on its editor the way a user's own chat is: a banned
     * bubble, a catalog style the editor does not own, or a bubble behind a permission they lack falls
     * back to the wired bubble. The message is sent without a speaking Habbo, so the checks chat applies
     * at send time were all skipped, and an owner could make a visitor say anything in the staff or
     * ambassador bubble.
     */
    static int allowedBubble(int style, User editor) {
        int fallback = RoomChatMessageBubbles.WIRED.getType();
        RoomChatMessageBubbles bubble = RoomChatMessageBubbles.getBubble(style);
        if (bubble == null || bubble.getType() != style) return fallback;
        if (editor == null) return fallback;

        if (!bubble.getPermission().isEmpty() && !editor.hasPermission(bubble.getPermission())) return fallback;
        if (editor.hasPermission(Permission.ACC_ANYCHATCOLOR)) return style;

        for (Integer banned : RoomChatMessage.BANNED_BUBBLES) {
            if (banned == style) return fallback;
        }
        if (ChatStyleRepository.isPurchasable(style)
                && !ChatStyleRepository.owns(editor.getUserInfo().getId(), style)) {
            return fallback;
        }
        return style;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String message = settings.getStringParam();
        int[] params = settings.getIntParams();
        // Official show message layout: [visibility, style, bubble width]; the user source is the slot.
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.visibilitySelection = (params.length > 0 && params[0] == VISIBILITY_ALL_ROOM_USERS)
                ? VISIBILITY_ALL_ROOM_USERS
                : VISIBILITY_SOURCE_USERS;
        this.bubbleStyle = allowedBubble(
                (params.length > 1) ? params[1] : RoomChatMessageBubbles.WIRED.getType(), gameClient.getUser());
        this.bubbleWidthOverride = RoomChatMessage.normalizeBubbleWidthOverride(
                (params.length > 2) ? params[2] : RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE);

        if (gameClient.getUser() == null || !gameClient.getUser().hasPermission(Permission.ACC_SUPERWIRED)) {
            message = Emulator.getGameEnvironment().getWordFilter().filter(message, null);
        }

        int maxLength =
                Emulator.getConfig().getInt("hotel.wired.show_message.max_length", DEFAULT_SHOW_MESSAGE_MAX_LENGTH);
        int maxLines =
                Emulator.getConfig().getInt("hotel.wired.show_message.max_lines", DEFAULT_SHOW_MESSAGE_MAX_LINES);
        message = clampMessage(message, maxLength, maxLines);

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.message = message;
        this.setDelay(delay);
        return true;
    }

    private static String clampMessage(String value, int maxLength, int maxLines) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        int safeMaxLength = Math.max(1, maxLength);
        int safeMaxLines = Math.max(1, maxLines);

        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        var builder = new StringBuilder();
        int linesToWrite = Math.min(lines.length, safeMaxLines);

        for (int index = 0; index < linesToWrite; index++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }

            builder.append(lines[index]);
        }

        if (builder.length() > safeMaxLength) {
            builder.setLength(safeMaxLength);
        }

        return builder.toString();
    }

    protected List<RoomUnit> resolveUsers(WiredContext ctx) {
        return WiredSourceUtil.resolveUsers(ctx, this.userSource);
    }

    protected String buildMessage(WiredContext ctx, User referenceUser) {
        String username = "";

        if (referenceUser != null && referenceUser.getUserInfo() != null) {
            username = referenceUser.getUserInfo().getUsername();
        }

        String msg = this.message
                .replace("%user%", username)
                .replace(
                        "%online_count%",
                        Emulator.getGameEnvironment().getUserManager().getOnlineCount() + "")
                .replace(
                        "%room_count%",
                        Emulator.getGameEnvironment()
                                        .getRoomManager()
                                        .getActiveRooms()
                                        .size() + "");

        return WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, msg);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (this.message.length() > 0) {
            Room room = ctx.room();
            if (room == null) {
                return;
            }

            List<RoomUnit> sourceUsers = resolveUsers(ctx);
            boolean toEveryone = this.visibilitySelection == VISIBILITY_ALL_ROOM_USERS;

            for (RoomUnit unit : sourceUsers) {
                User h = room.getUser(unit);
                if (h == null) {
                    continue;
                }

                RoomChatMessage chatMessage = this.chatMessage(buildMessage(ctx, h), unit);

                if (toEveryone) {
                    room.talk(h, chatMessage, RoomChatType.TALK, true);
                } else {
                    sayToSpeakerOnly(h, chatMessage);
                }

                if (h.getRoomUnit().isIdle()) {
                    h.getRoomUnit().getRoom().unIdle(h);
                }
            }
        }
    }

    /**
     * The dialog's "message visibility" choice, which this box stored and never read. Everyone goes
     * through the room's own chat path, so distance, ignore lists and tents keep applying; the narrow
     * choice puts the bubble on the speaker's own screen only, and needs none of that filtering because
     * the speaker is the single recipient.
     */
    private void sayToSpeakerOnly(User speaker, RoomChatMessage chatMessage) {
        GameClient client = speaker.getClient();
        if (client == null) {
            return;
        }

        client.sendResponse(new RoomUserTalkComposer(chatMessage));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    public int getBubbleWidthOverride() {
        return this.bubbleWidthOverride;
    }

    private RoomChatMessage chatMessage(String text, RoomUnit unit) {
        RoomChatMessage chatMessage =
                new RoomChatMessage(text, unit, RoomChatMessageBubbles.getBubble(this.bubbleStyle));
        chatMessage.setBubbleWidthOverride(this.bubbleWidthOverride);
        return chatMessage;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.message,
                        this.getDelay(),
                        this.userSource,
                        this.visibilitySelection,
                        this.bubbleStyle,
                        this.bubbleWidthOverride));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(jsonData.delay));
            this.message = WiredUtilityPayloadGuard.text(jsonData.message);
            this.userSource = (jsonData.userSource != null)
                    ? WiredUtilityPayloadGuard.userSource(jsonData.userSource)
                    : WiredSourceUtil.SOURCE_TRIGGER;
            this.visibilitySelection =
                    (jsonData.visibilitySelection != null && jsonData.visibilitySelection == VISIBILITY_ALL_ROOM_USERS)
                            ? VISIBILITY_ALL_ROOM_USERS
                            : VISIBILITY_SOURCE_USERS;
            this.bubbleStyle =
                    (jsonData.bubbleStyle != null) ? jsonData.bubbleStyle : RoomChatMessageBubbles.WIRED.getType();
            this.bubbleWidthOverride = RoomChatMessage.normalizeBubbleWidthOverride(
                    (jsonData.bubbleWidthOverride != null)
                            ? jsonData.bubbleWidthOverride
                            : RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE);
        } else {
            this.message = "";

            String[] wiredDataSplit = wiredData != null ? wiredData.split("\t") : new String[0];
            if (wiredDataSplit.length >= 2) {
                super.setDelay(WiredUtilityPayloadGuard.parseDelay(wiredDataSplit[0]));
                this.message = wiredDataSplit[1];
            }

            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.visibilitySelection = VISIBILITY_SOURCE_USERS;
            this.bubbleStyle = RoomChatMessageBubbles.WIRED.getType();
            this.bubbleWidthOverride = RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE;
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.message = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.visibilitySelection = VISIBILITY_SOURCE_USERS;
        this.bubbleStyle = RoomChatMessageBubbles.WIRED.getType();
        this.bubbleWidthOverride = RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return (this.userSource == WiredSourceUtil.SOURCE_TRIGGER)
                || WiredTextPlaceholderUtil.requiresActor(this.getRoom(), this);
    }

    static class JsonData {
        String message;
        int delay;
        Integer userSource;
        Integer visibilitySelection;
        Integer bubbleStyle;
        Integer bubbleWidthOverride;

        public JsonData(
                String message,
                int delay,
                int userSource,
                int visibilitySelection,
                int bubbleStyle,
                int bubbleWidthOverride) {
            this.message = message;
            this.delay = delay;
            this.userSource = userSource;
            this.visibilitySelection = visibilitySelection;
            this.bubbleStyle = bubbleStyle;
            this.bubbleWidthOverride = bubbleWidthOverride;
        }
    }
}
