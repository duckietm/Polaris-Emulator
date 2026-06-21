package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBotSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectBotFollowHabbo extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_FOLLOW_AVATAR;

    private String botName = "";
    private int mode = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;

    public WiredEffectBotFollowHabbo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotFollowHabbo(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(3);
        message.appendInt(this.mode);
        message.appendInt(this.userSource);
        message.appendInt(this.botSource);
        message.appendInt(1);
        message.appendInt(this.getType().code);
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
        if(settings.getIntParams().length < 2) throw new WiredSaveException("Mode is invalid");

        int mode = settings.getIntParams()[0];
        this.userSource = settings.getIntParams()[1];
        this.botSource = (settings.getIntParams().length > 2) ? WiredBotSourceUtil.normalizeBotSource(settings.getIntParams()[2]) : WiredBotSourceUtil.SOURCE_BOT_NAME;

        if(mode != 0 && mode != 1)
            throw new WiredSaveException("Mode is invalid");

        String botName = settings.getStringParam().replace("\t", "");
        botName = botName.substring(0, Math.min(botName.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.botName = botName;
        this.mode = mode;
        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return;
        RoomUnit roomUnit = targets.get(0);

        Habbo habbo = room.getHabbo(roomUnit);
        List<Bot> bots = WiredBotSourceUtil.resolveBots(ctx, room, this.botSource, this.botName);

        if (habbo != null && !bots.isEmpty()) {
            for (Bot bot : bots) {
                if (this.mode == 1) {
                    bot.startFollowingHabbo(habbo);
                } else {
                    bot.stopFollowingHabbo();
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
        return WiredManager.getGson().toJson(new JsonData(this.botName, this.mode, this.getDelay(), this.userSource, this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if(jsonData != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(jsonData.delay));
            this.mode = WiredEffectPayloadGuard.mode(jsonData.mode);
            this.botName = WiredEffectPayloadGuard.text(jsonData.bot_name);
            this.userSource = WiredSourceUtil.isDefaultUserSource(jsonData.userSource) ? jsonData.userSource : WiredSourceUtil.SOURCE_TRIGGER;
            this.botSource = (jsonData.botSource != null)
                    ? WiredBotSourceUtil.normalizeBotSource(jsonData.botSource)
                    : WiredBotSourceUtil.SOURCE_BOT_NAME;
        }
        else {
            String[] data = wiredData != null ? wiredData.split(((char) 9) + "") : new String[0];

            if (data.length == 3) {
                this.setDelay(WiredEffectPayloadGuard.parseDelay(data[0]));
                this.mode = WiredEffectPayloadGuard.mode(WiredEffectPayloadGuard.parseInt(data[1], 0));
                this.botName = WiredEffectPayloadGuard.text(data[2]);
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.mode = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER || WiredBotSourceUtil.requiresTriggeringUser(this.botSource);
    }

    static class JsonData {
        String bot_name;
        int mode;
        int delay;
        int userSource;
        Integer botSource;

        public JsonData(String bot_name, int mode, int delay, int userSource, int botSource) {
            this.bot_name = bot_name;
            this.mode = mode;
            this.delay = delay;
            this.userSource = userSource;
            this.botSource = botSource;
        }
    }
}
