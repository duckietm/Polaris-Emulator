package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
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
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.WiredBoxFlashes;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;

public class WiredEffectWhisper extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;
    protected static final int VISIBILITY_SOURCE_USERS = 0;
    protected static final int VISIBILITY_ALL_ROOM_USERS = 1;
    private static final long DELIVERY_DEDUP_TTL_MS = 60_000L;
    private static final int DELIVERY_DEDUP_CLEANUP_THRESHOLD = 512;
    private static final long DELIVERY_DEDUP_CLEANUP_INTERVAL_MS = 1_000L;
    private static final int DELIVERY_DEDUP_MAX = 50_000;
    private static final AtomicLong LAST_DEDUP_CLEANUP_MS = new AtomicLong();
    private static final ConcurrentHashMap<String, Long> DELIVERY_DEDUP = new ConcurrentHashMap<>();
    // The official show message editor takes 400 characters.
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LENGTH = 400;
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LINES = 8;

    protected String message = "";
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int visibilitySelection = VISIBILITY_SOURCE_USERS;
    protected int bubbleStyle = RoomChatMessageBubbles.WIRED.getType();
    protected int bubbleWidthOverride = RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE;

    public WiredEffectWhisper(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectWhisper(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        // Subclasses that answer a different type (give effect, give hand item, alert) advertise
        // their own dialog; the static field is always the show-message one.
        message.appendInt(this.getType().code);
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

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String message = settings.getStringParam();
        int[] params = settings.getIntParams();
        // Official layout: [visibility, style, bubble width]; the user source is the official slot.
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.visibilitySelection = (params.length > 0 && params[0] == VISIBILITY_ALL_ROOM_USERS)
                ? VISIBILITY_ALL_ROOM_USERS
                : VISIBILITY_SOURCE_USERS;
        this.bubbleStyle = (params.length > 1) ? params[1] : RoomChatMessageBubbles.WIRED.getType();
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

        StringBuilder builder = new StringBuilder();
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

    protected List<User> resolveRecipients(WiredContext ctx, List<RoomUnit> sourceUsers) {
        Room room = ctx.room();
        LinkedHashMap<Integer, User> recipients = new LinkedHashMap<>();

        if (room == null) {
            return Collections.emptyList();
        }

        if (this.visibilitySelection == VISIBILITY_ALL_ROOM_USERS) {
            for (User user : room.getCurrentUsers().values()) {
                addRecipient(recipients, user);
            }
        } else {
            for (RoomUnit roomUnit : sourceUsers) {
                addRecipient(recipients, room.getUser(roomUnit));
            }
        }

        return new ArrayList<>(recipients.values());
    }

    /** The bot behind the first source unit, when that unit is a bot; null otherwise. */
    static Bot sourceBot(Room room, List<RoomUnit> sourceUsers) {
        for (RoomUnit roomUnit : sourceUsers) {
            if (roomUnit == null) continue;
            if (room.getUser(roomUnit) != null) return null;
            return room.getBot(roomUnit);
        }
        return null;
    }

    protected User resolveMessageSourceUser(WiredContext ctx, List<RoomUnit> sourceUsers) {
        Room room = ctx.room();

        if (room != null) {
            for (RoomUnit roomUnit : sourceUsers) {
                User user = room.getUser(roomUnit);
                if (user != null) {
                    return user;
                }
            }
        }

        return (room == null)
                ? null
                : ctx.actor().map(roomUnit -> room.getUser(roomUnit)).orElse(null);
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

    private void addRecipient(LinkedHashMap<Integer, User> recipients, User user) {
        if (user == null || user.getUserInfo() == null || user.getClient() == null) {
            return;
        }

        recipients.putIfAbsent(user.getUserInfo().getId(), user);
    }

    protected boolean shouldDeliverToRecipient(WiredContext ctx, User user) {
        if (ctx == null || user == null || user.getUserInfo() == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        cleanupDeliveryDedup(now);

        String deliveryKey = buildDeliveryKey(ctx, user);

        return DELIVERY_DEDUP.putIfAbsent(deliveryKey, now) == null;
    }

    private String buildDeliveryKey(WiredContext ctx, User user) {
        return ctx.room().getId() + ":" + this.getId() + ":"
                + user.getUserInfo().getId() + ":" + ctx.event().getCreatedAtMs();
    }

    private static void cleanupDeliveryDedup(long now) {
        if (DELIVERY_DEDUP.size() < DELIVERY_DEDUP_CLEANUP_THRESHOLD) {
            return;
        }

        // The map is shared by the hotel: sweep it at most once a second, not once per recipient.
        long last = LAST_DEDUP_CLEANUP_MS.get();
        if (now - last < DELIVERY_DEDUP_CLEANUP_INTERVAL_MS && DELIVERY_DEDUP.size() < DELIVERY_DEDUP_MAX) {
            return;
        }
        if (!LAST_DEDUP_CLEANUP_MS.compareAndSet(last, now)) {
            return;
        }

        DELIVERY_DEDUP.entrySet().removeIf(entry -> (now - entry.getValue()) > DELIVERY_DEDUP_TTL_MS);
        if (DELIVERY_DEDUP.size() >= DELIVERY_DEDUP_MAX) {
            DELIVERY_DEDUP.clear();
        }
    }

    @Override
    public void execute(WiredContext ctx) {
        if (this.message.length() > 0) {
            List<RoomUnit> sourceUsers = resolveUsers(ctx);

            boolean shownToRoom = this.visibilitySelection == VISIBILITY_ALL_ROOM_USERS;

            // A message shown to everyone whose source is a bot is said aloud by that bot, as in Habbo; one chat line
            // for the room, so it uses the room's budget. Behaviour observed in plus-emulator/PlusEMU a8523821.
            if (shownToRoom && ctx.room() != null) {
                Bot speaker = sourceBot(ctx.room(), sourceUsers);
                if (speaker != null) {
                    if (!admitMessage(ctx.room(), null)) {
                        return;
                    }
                    String said = buildMessage(ctx, null);
                    WiredBoxFlashes.sendAfterFlash(() -> speaker.talk(said), reachedBySignal(ctx));
                    return;
                }
            }

            List<User> recipients = resolveRecipients(ctx, sourceUsers);
            User sharedSourceUser = shownToRoom ? resolveMessageSourceUser(ctx, sourceUsers) : null;

            for (User user : recipients) {
                // The user turned wired whispers off in their settings.
                if (user.getUserStats() != null && user.getUserStats().wiredWhisperDisabled) {
                    continue;
                }

                if (!shouldDeliverToRecipient(ctx, user)) {
                    continue;
                }

                // AIR: every receiver has their own budget, whoever's stack sends; one out of budget is skipped.
                if (!admitMessage(ctx.room(), user.getRoomUnit())) {
                    continue;
                }

                String msg = buildMessage(ctx, (sharedSourceUser != null) ? sharedSourceUser : user);
                RoomUserWhisperComposer whisper =
                        new RoomUserWhisperComposer(this.chatMessage(msg, user.getRoomUnit()));
                // The text follows the pass's flashes and movements; one a say reached through a signal follows the
                // speaker's own line (observed on AIR WIN63-202610151745).
                var client = user.getClient();
                WiredBoxFlashes.sendAfterFlash(() -> client.sendResponse(whisper), reachedBySignal(ctx));

                if (user.getRoomUnit().isIdle()) {
                    user.getRoomUnit().getRoom().unIdle(user);
                }
            }
        }
    }

    private static boolean reachedBySignal(WiredContext ctx) {
        return ctx.event() != null && ctx.event().getType() == WiredEvent.Type.SIGNAL_RECEIVED;
    }

    /** AIR shows a user at most 10 wired messages in any 5 s and 40 in any 30 s, and drops the rest. */
    private static boolean admitMessage(Room room, RoomUnit receiver) {
        if (room == null || room.getWiredRuntime() == null) {
            return true;
        }

        return room.getWiredRuntime().getMessageBudget(receiver).tryAdmit(System.currentTimeMillis());
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
