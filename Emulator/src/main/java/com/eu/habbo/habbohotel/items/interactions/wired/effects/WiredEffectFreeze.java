package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WiredEffectFreeze extends InteractionWiredEffect {
    private static final Set<Integer> ALLOWED_EFFECT_IDS = Set.of(218, 12, 11, 53, 163);
    public static final WiredEffectType type = WiredEffectType.FREEZE;

    private int effectId = 218;
    private boolean cancelOnTeleport = false;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectFreeze(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFreeze(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            if (room.getHabbo(roomUnit) == null) {
                continue;
            }

            WiredFreezeUtil.freeze(room, roomUnit, this.effectId, this.cancelOnTeleport);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.effectId, this.cancelOnTeleport, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.effectId = ALLOWED_EFFECT_IDS.contains(data.effectId) ? data.effectId : 218;
            this.cancelOnTeleport = data.cancelOnTeleport;
            this.setDelay(data.delay);
            this.userSource = data.userSource;
        } else {
            this.effectId = 218;
            this.cancelOnTeleport = false;
            this.setDelay(0);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.effectId = 218;
        this.cancelOnTeleport = false;
        this.setDelay(0);
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.effectId);
        message.appendInt(this.cancelOnTeleport ? 1 : 0);
        message.appendInt(this.userSource);
        message.appendInt(0);
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
        if (settings.getIntParams().length < 3) {
            throw new WiredSaveException("Invalid data");
        }

        int nextEffectId = settings.getIntParams()[0];
        if (!ALLOWED_EFFECT_IDS.contains(nextEffectId)) {
            throw new WiredSaveException("Invalid freeze effect");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.effectId = nextEffectId;
        this.cancelOnTeleport = settings.getIntParams()[1] == 1;
        this.userSource = settings.getIntParams()[2];
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int effectId;
        boolean cancelOnTeleport;
        int delay;
        int userSource;

        public JsonData(int effectId, boolean cancelOnTeleport, int delay, int userSource) {
            this.effectId = effectId;
            this.cancelOnTeleport = cancelOnTeleport;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
