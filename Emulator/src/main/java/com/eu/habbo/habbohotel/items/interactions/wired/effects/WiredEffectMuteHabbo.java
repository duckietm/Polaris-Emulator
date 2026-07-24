package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectMuteHabbo extends InteractionWiredEffect {
    private static final WiredEffectType type = WiredEffectType.MUTE_TRIGGER;

    private int length = 5;
    private String message = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMuteHabbo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMuteHabbo(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.message);
        message.appendInt(2);
        message.appendInt(this.length);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.getIntParams().length < 2) throw new WiredSaveException("invalid data");

        this.length = settings.getIntParams()[0];
        this.userSource = settings.getIntParams()[1];
        this.message = settings.getStringParam();

        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo == null) continue;

            if (room.hasRights(habbo)) continue;

            room.muteHabbo(habbo, Math.max(1, this.length));

            String message = this.message
                    .replace("%user%", habbo.getHabboInfo().getUsername())
                    .replace(
                            "%online_count%",
                            Emulator.getGameEnvironment().getHabboManager().getOnlineCount() + "")
                    .replace(
                            "%room_count%",
                            Emulator.getGameEnvironment()
                                            .getRoomManager()
                                            .getActiveRooms()
                                            .size() + "");
            message = WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, message);
            habbo.getClient()
                    .sendResponse(new RoomUserWhisperComposer(
                            new RoomChatMessage(message, habbo, habbo, RoomChatMessageBubbles.WIRED)));
        }
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.length, this.message, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.length = data.length;
            this.message = data.message;
            this.userSource = data.userSource;
        } else {
            String[] data = wiredData.split("\t");

            if (data.length >= 3) {
                try {
                    this.setDelay(Integer.parseInt(data[0]));
                    this.length = Integer.parseInt(data[1]);
                    this.message = data[2];
                } catch (Exception e) {
                    WiredCompatibilityDiagnostics.record(
                            WiredCompatibilityDiagnostics.FailurePoint.EFFECT_MUTE_HABBO_LEGACY,
                            this.getRoomId(),
                            this.getId(),
                            e);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.message = "";
        this.length = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER
                || WiredTextPlaceholderUtil.requiresActor(this.getRoom(), this);
    }

    static class JsonData {
        int delay;
        int length;
        String message;
        int userSource;

        public JsonData(int delay, int length, String message, int userSource) {
            this.delay = delay;
            this.length = length;
            this.message = message;
            this.userSource = userSource;
        }
    }
}
