package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Sets the room's roller speed (the number of cycles between roller movements; -1 disables rollers,
 * 0 is fastest, higher is slower — see {@code RoomCycleManager}). Carries a single signed int, which
 * needs a dedicated dialog, so it uses the NEW client code {@link WiredEffectType#SET_ROLLER_SPEED}
 * (88) with a matching Nitro {@code WiredActionLayoutCode.SET_ROLLER_SPEED} component.
 */
public class WiredEffectSetRollerSpeed extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SET_ROLLER_SPEED;
    private static final int MIN_SPEED = -1;
    private static final int MAX_SPEED = 10;
    private static final int DEFAULT_SPEED = 2;

    private int speed = DEFAULT_SPEED;

    public WiredEffectSetRollerSpeed(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSetRollerSpeed(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room != null) {
            room.setRollerSpeed(this.speed);
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
        message.appendInt(1);
        message.appendInt(this.speed);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.speed = (params.length > 0) ? clampSpeed(params[0]) : DEFAULT_SPEED;

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.setDelay(delay);
        return true;
    }

    private static int clampSpeed(int value) {
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, value));
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.speed));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = WiredUtilityPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
            this.speed = clampSpeed(data.speed);
        } else {
            this.setDelay(0);
            this.speed = DEFAULT_SPEED;
        }
    }

    @Override
    public void onPickUp() {
        this.speed = DEFAULT_SPEED;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int delay;
        int speed;

        public JsonData(int delay, int speed) {
            this.delay = delay;
            this.speed = speed;
        }
    }
}
