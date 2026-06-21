package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class WiredEffectWhisper extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;
    protected static final int VISIBILITY_SOURCE_USERS = 0;
    protected static final int VISIBILITY_ALL_ROOM_USERS = 1;
    private static final long DELIVERY_DEDUP_TTL_MS = 60_000L;
    private static final int DELIVERY_DEDUP_CLEANUP_THRESHOLD = 512;
    private static final ConcurrentHashMap<String, Long> DELIVERY_DEDUP = new ConcurrentHashMap<>();
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LENGTH = 200;
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LINES = 8;

    protected String message = "";
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int visibilitySelection = VISIBILITY_SOURCE_USERS;
    protected int bubbleStyle = RoomChatMessageBubbles.WIRED.getType();

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
        message.appendInt(this.userSource);
        message.appendInt(this.visibilitySelection);
        message.appendInt(this.bubbleStyle);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
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
        this.userSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;
        this.visibilitySelection = (params.length > 1 && params[1] == VISIBILITY_ALL_ROOM_USERS)
                ? VISIBILITY_ALL_ROOM_USERS
                : VISIBILITY_SOURCE_USERS;
        this.bubbleStyle = (params.length > 2) ? params[2] : RoomChatMessageBubbles.WIRED.getType();

        if(gameClient.getHabbo() == null || !gameClient.getHabbo().hasPermission(Permission.ACC_SUPERWIRED)) {
            message = Emulator.getGameEnvironment().getWordFilter().filter(message, null);
        }

        int maxLength = Emulator.getConfig().getInt("hotel.wired.show_message.max_length", DEFAULT_SHOW_MESSAGE_MAX_LENGTH);
        int maxLines = Emulator.getConfig().getInt("hotel.wired.show_message.max_lines", DEFAULT_SHOW_MESSAGE_MAX_LINES);
        message = clampMessage(message, maxLength, maxLines);

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
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

    protected List<Habbo> resolveRecipients(WiredContext ctx, List<RoomUnit> sourceUsers) {
        Room room = ctx.room();
        LinkedHashMap<Integer, Habbo> recipients = new LinkedHashMap<>();

        if (room == null) {
            return Collections.emptyList();
        }

        if (this.visibilitySelection == VISIBILITY_ALL_ROOM_USERS) {
            for (Habbo habbo : room.getCurrentHabbos().values()) {
                addRecipient(recipients, habbo);
            }
        } else {
            for (RoomUnit roomUnit : sourceUsers) {
                addRecipient(recipients, room.getHabbo(roomUnit));
            }
        }

        return new ArrayList<>(recipients.values());
    }

    protected Habbo resolveMessageSourceHabbo(WiredContext ctx, List<RoomUnit> sourceUsers) {
        Room room = ctx.room();

        if (room != null) {
            for (RoomUnit roomUnit : sourceUsers) {
                Habbo habbo = room.getHabbo(roomUnit);
                if (habbo != null) {
                    return habbo;
                }
            }
        }

        return (room == null) ? null : ctx.actor().map(roomUnit -> room.getHabbo(roomUnit)).orElse(null);
    }

    protected String buildMessage(WiredContext ctx, Habbo referenceHabbo) {
        String username = "";

        if (referenceHabbo != null && referenceHabbo.getHabboInfo() != null) {
            username = referenceHabbo.getHabboInfo().getUsername();
        }

        String msg = this.message
                .replace("%user%", username)
                .replace("%online_count%", Emulator.getGameEnvironment().getHabboManager().getOnlineCount() + "")
                .replace("%room_count%", Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size() + "");

        return WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, msg);
    }

    private void addRecipient(LinkedHashMap<Integer, Habbo> recipients, Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getClient() == null) {
            return;
        }

        recipients.putIfAbsent(habbo.getHabboInfo().getId(), habbo);
    }

    protected boolean shouldDeliverToRecipient(WiredContext ctx, Habbo habbo) {
        if (ctx == null || habbo == null || habbo.getHabboInfo() == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        cleanupDeliveryDedup(now);

        String deliveryKey = buildDeliveryKey(ctx, habbo);

        return DELIVERY_DEDUP.putIfAbsent(deliveryKey, now) == null;
    }

    private String buildDeliveryKey(WiredContext ctx, Habbo habbo) {
        return ctx.room().getId() + ":" + this.getId() + ":" + habbo.getHabboInfo().getId() + ":" + ctx.event().getCreatedAtMs();
    }

    private static void cleanupDeliveryDedup(long now) {
        if (DELIVERY_DEDUP.size() < DELIVERY_DEDUP_CLEANUP_THRESHOLD) {
            return;
        }

        DELIVERY_DEDUP.entrySet().removeIf(entry -> (now - entry.getValue()) > DELIVERY_DEDUP_TTL_MS);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (this.message.length() > 0) {
            List<RoomUnit> sourceUsers = resolveUsers(ctx);
            List<Habbo> recipients = resolveRecipients(ctx, sourceUsers);
            Habbo sharedSourceHabbo = (this.visibilitySelection == VISIBILITY_ALL_ROOM_USERS)
                    ? resolveMessageSourceHabbo(ctx, sourceUsers)
                    : null;

            for (Habbo habbo : recipients) {
                if (!shouldDeliverToRecipient(ctx, habbo)) {
                    continue;
                }

                String msg = buildMessage(ctx, (sharedSourceHabbo != null) ? sharedSourceHabbo : habbo);
                habbo.getClient().sendResponse(new RoomUserWhisperComposer(
                        new RoomChatMessage(msg, habbo.getRoomUnit(), RoomChatMessageBubbles.getBubble(this.bubbleStyle))
                ));

                if (habbo.getRoomUnit().isIdle()) {
                    habbo.getRoomUnit().getRoom().unIdle(habbo);
                }
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.message, this.getDelay(), this.userSource, this.visibilitySelection, this.bubbleStyle));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);
        if(jsonData != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(jsonData.delay));
            this.message = WiredUtilityPayloadGuard.text(jsonData.message);
            this.userSource = (jsonData.userSource != null) ? WiredUtilityPayloadGuard.userSource(jsonData.userSource) : WiredSourceUtil.SOURCE_TRIGGER;
            this.visibilitySelection = (jsonData.visibilitySelection != null && jsonData.visibilitySelection == VISIBILITY_ALL_ROOM_USERS)
                    ? VISIBILITY_ALL_ROOM_USERS
                    : VISIBILITY_SOURCE_USERS;
            this.bubbleStyle = (jsonData.bubbleStyle != null) ? jsonData.bubbleStyle : RoomChatMessageBubbles.WIRED.getType();
        }
        else {
            this.message = "";

            String[] wiredDataSplit = wiredData != null ? wiredData.split("\t") : new String[0];
            if (wiredDataSplit.length >= 2) {
                super.setDelay(WiredUtilityPayloadGuard.parseDelay(wiredDataSplit[0]));
                this.message = wiredDataSplit[1];
            }

            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.visibilitySelection = VISIBILITY_SOURCE_USERS;
            this.bubbleStyle = RoomChatMessageBubbles.WIRED.getType();
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.message = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.visibilitySelection = VISIBILITY_SOURCE_USERS;
        this.bubbleStyle = RoomChatMessageBubbles.WIRED.getType();
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return (this.userSource == WiredSourceUtil.SOURCE_TRIGGER) || WiredTextPlaceholderUtil.requiresActor(this.getRoom(), this);
    }

    static class JsonData {
        String message;
        int delay;
        Integer userSource;
        Integer visibilitySelection;
        Integer bubbleStyle;

        public JsonData(String message, int delay, int userSource, int visibilitySelection, int bubbleStyle) {
            this.message = message;
            this.delay = delay;
            this.userSource = userSource;
            this.visibilitySelection = visibilitySelection;
            this.bubbleStyle = bubbleStyle;
        }
    }
}
