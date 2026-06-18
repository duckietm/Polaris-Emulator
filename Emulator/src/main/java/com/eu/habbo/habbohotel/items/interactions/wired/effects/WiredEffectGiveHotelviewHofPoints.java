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
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectGiveHotelviewHofPoints extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;

    private int amount = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGiveHotelviewHofPoints(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveHotelviewHofPoints(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.amount + "");
        message.appendInt(1);
        message.appendInt(this.userSource);
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
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int nextAmount = WiredNumericInputGuard.parsePositiveAmount(settings.getStringParam(), WiredNumericInputGuard.maxRewardAmount());
        if (nextAmount <= 0) {
            return false;
        }
        this.amount = nextAmount;

        int[] params = settings.getIntParams();
        this.userSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;

        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        if (this.amount <= 0) return;

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = ctx.room().getHabbo(unit);
            if (habbo == null) continue;

            habbo.getHabboStats().hofPoints += this.amount;
            Emulator.getThreading().run(habbo.getHabboStats());
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.amount, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.amount = data.amount;
            this.setDelay(data.delay);
            this.userSource = data.userSource;
        }
        else {
            this.amount = 0;

            if (wiredData.split("\t").length >= 2) {
                super.setDelay(Integer.parseInt(wiredData.split("\t")[0]));

                try {
                    this.amount = Integer.parseInt(this.getWiredData().split("\t")[1]);
                } catch (Exception e) {
                }
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.amount = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        int amount;
        int delay;
        int userSource;

        public JsonData(int amount, int delay, int userSource) {
            this.amount = amount;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
