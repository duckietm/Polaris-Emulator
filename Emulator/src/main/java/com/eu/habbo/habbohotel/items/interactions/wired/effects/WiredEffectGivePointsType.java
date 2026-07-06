package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredNumericInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives the resolved user(s) an amount of a SELECTABLE currency (points) type. Unlike
 * {@link WiredEffectGiveDiamonds} / {@link WiredEffectGiveDuckets} (which hard-code a single
 * currency and reuse the SHOW_MESSAGE dialog), this effect lets the builder pick the points type
 * in the dialog, so it carries three ints — {@code [pointsType, amount, userSource]} — and needs a
 * dedicated NEW client dialog {@link WiredEffectType#GIVE_POINTS_TYPE} (90) with a matching Nitro
 * {@code WiredActionLayoutCode.GIVE_POINTS_TYPE} component.
 *
 * <p>Currency is granted through {@link Habbo#givePoints(int, int)} — the same channel the
 * diamond/seasonal effects use. Amount is capped via {@link WiredNumericInputGuard} and the points
 * type is range-clamped so a malformed packet can never touch an unintended currency slot.</p>
 */
public class WiredEffectGivePointsType extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.GIVE_POINTS_TYPE;

    private static final int MIN_POINTS_TYPE = 0;
    private static final int MAX_POINTS_TYPE = 100;
    private static final int DEFAULT_POINTS_TYPE = 0;

    private int pointsType = DEFAULT_POINTS_TYPE;
    private int amount = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGivePointsType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGivePointsType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null) continue;

            habbo.givePoints(this.pointsType, this.amount);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.pointsType);
        message.appendInt(this.amount);
        message.appendInt(this.userSource);
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

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length < 3) {
            throw new WiredSaveException("Invalid data");
        }

        int nextPointsType = clampPointsType(params[0]);
        int nextAmount = clampAmount(params[1]);
        if (nextAmount <= 0) {
            throw new WiredSaveException("Amount is invalid");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.pointsType = nextPointsType;
        this.amount = nextAmount;
        this.userSource = params[2];
        this.setDelay(delay);

        return true;
    }

    private static int clampPointsType(int value) {
        return Math.max(MIN_POINTS_TYPE, Math.min(MAX_POINTS_TYPE, value));
    }

    private static int clampAmount(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, WiredNumericInputGuard.maxRewardAmount());
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.pointsType, this.amount, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.pointsType = clampPointsType(data.pointsType);
            this.amount = clampAmount(data.amount);
            this.setDelay(data.delay);
            this.userSource = data.userSource;
        } else {
            this.pointsType = DEFAULT_POINTS_TYPE;
            this.amount = 0;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.setDelay(0);
        }
    }

    @Override
    public void onPickUp() {
        this.pointsType = DEFAULT_POINTS_TYPE;
        this.amount = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int pointsType;
        int amount;
        int delay;
        int userSource;

        public JsonData(int pointsType, int amount, int delay, int userSource) {
            this.pointsType = pointsType;
            this.amount = amount;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
