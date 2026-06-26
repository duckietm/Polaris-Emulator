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
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectMakeUserSay extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;
    protected static final int VISIBILITY_SOURCE_USERS = 0;
    protected static final int VISIBILITY_ALL_ROOM_USERS = 1;
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LENGTH = 200;
    private static final int DEFAULT_SHOW_MESSAGE_MAX_LINES = 8;

    protected String message = "";
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int visibilitySelection = VISIBILITY_SOURCE_USERS;
    protected int bubbleStyle = RoomChatMessageBubbles.WIRED.getType();

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

    @Override
    public void execute(WiredContext ctx) {
        if (this.message.length() > 0) {
            Room room = ctx.room();
            if (room == null) {
                return;
            }

            List<RoomUnit> sourceUsers = resolveUsers(ctx);

            for (RoomUnit unit : sourceUsers) {
                Habbo h = room.getHabbo(unit);
                if (h == null) {
                    continue;
                }

                String msg = buildMessage(ctx, h);
                room.talk(
                        h,
                        new RoomChatMessage(msg, unit, RoomChatMessageBubbles.getBubble(this.bubbleStyle)),
                        RoomChatType.TALK,
                        true
                );

                if (h.getRoomUnit().isIdle()) {
                    h.getRoomUnit().getRoom().unIdle(h);
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
