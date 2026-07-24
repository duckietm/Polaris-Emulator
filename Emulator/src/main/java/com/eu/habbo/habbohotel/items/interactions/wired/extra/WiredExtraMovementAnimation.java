package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraMovementAnimation extends InteractionWiredExtra {
    public static final int CODE = 115;

    public static final int EFFECT_DEFAULT = 0;
    public static final int EFFECT_EASE_IN = 1;
    public static final int EFFECT_EASE_OUT = 2;
    public static final int EFFECT_EASE_IN_OUT = 3;
    public static final int EFFECT_BOUNCE = 4;
    public static final int EFFECT_ELASTIC = 5;
    public static final int EFFECT_DROP = 6;

    private static final int EFFECT_MIN = EFFECT_DEFAULT;
    private static final int EFFECT_MAX = EFFECT_DROP;
    private static final int GRAVITY_MIN = 0;
    private static final int GRAVITY_MAX = 100;

    private int animationEffect = EFFECT_DEFAULT;
    private int gravityIntensity = GRAVITY_MIN;

    public WiredExtraMovementAnimation(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMovementAnimation(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.animationEffect = normalizeEffect(readInt(params, 0, this.animationEffect));
        this.gravityIntensity = normalizeGravity(readInt(params, 1, this.gravityIntensity));

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.animationEffect, this.gravityIntensity));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.animationEffect);
        message.appendInt(this.gravityIntensity);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.animationEffect = normalizeEffect(data.animationEffect);
                this.gravityIntensity = normalizeGravity(data.gravityIntensity);
            }

            return;
        }

        String[] legacyData = wiredData.split("\t");
        this.animationEffect = normalizeEffect(readLegacyInt(legacyData, 0, EFFECT_DEFAULT));
        this.gravityIntensity = normalizeGravity(readLegacyInt(legacyData, 1, GRAVITY_MIN));
    }

    @Override
    public void onPickUp() {
        this.animationEffect = EFFECT_DEFAULT;
        this.gravityIntensity = GRAVITY_MIN;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getAnimationEffect() {
        return this.animationEffect;
    }

    public int getGravityIntensity() {
        return this.gravityIntensity;
    }

    private static int readInt(int[] params, int index, int fallback) {
        return (params.length > index) ? params[index] : fallback;
    }

    private static int readLegacyInt(String[] data, int index, int fallback) {
        if (data.length <= index) {
            return fallback;
        }

        try {
            return Integer.parseInt(data[index]);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int normalizeEffect(int value) {
        return Math.max(EFFECT_MIN, Math.min(EFFECT_MAX, value));
    }

    private static int normalizeGravity(int value) {
        return Math.max(GRAVITY_MIN, Math.min(GRAVITY_MAX, value));
    }

    static class JsonData {
        int animationEffect;
        int gravityIntensity;

        JsonData(int animationEffect, int gravityIntensity) {
            this.animationEffect = animationEffect;
            this.gravityIntensity = gravityIntensity;
        }
    }
}
