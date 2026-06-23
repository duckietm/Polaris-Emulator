package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBotSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDanceComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Makes a named bot start or stop dancing. Carries a bot {@code name} (stringParam) plus a single
 * {@code danceType} int (0=stop/NONE, 1=HAB_HOP, 2=POGO_MOGO, 3=DUCK_FUNK, 4=THE_ROLLIE), which
 * needs a dedicated dialog, so it uses the NEW client code {@link WiredEffectType#BOT_DANCE} (89)
 * with a matching Nitro {@code WiredActionLayoutCode.BOT_DANCE} component. Both
 * {@code wf_act_bot_start_dance} and {@code wf_act_bot_stop_dance} register to this class; the dance
 * int distinguishes them (a start-dance furni defaults to a dance, a stop-dance furni to 0).
 */
public class WiredEffectBotDance extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_DANCE;
    private static final int MIN_DANCE = 0;
    private static final int MAX_DANCE = 4;

    private String botName = "";
    private int danceType = 0;

    public WiredEffectBotDance(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotDance(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        List<Bot> bots = WiredBotSourceUtil.resolveBots(ctx, room, WiredBotSourceUtil.SOURCE_BOT_NAME, this.botName);
        if (bots.isEmpty()) {
            return;
        }

        DanceType dance = danceFromInt(this.danceType);

        for (Bot bot : bots) {
            if (bot.getRoomUnit() == null) {
                continue;
            }

            bot.getRoomUnit().setDanceType(dance);
            room.sendComposer(new RoomUserDanceComposer(bot.getRoomUnit()).compose());
            bot.needsUpdate(true);
            Emulator.getThreading().run(bot);
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
        message.appendString(this.botName);
        message.appendInt(1);
        message.appendInt(this.danceType);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.danceType = (params.length > 0) ? clampDance(params[0]) : 0;

        String name = settings.getStringParam();
        name = (name == null) ? "" : name.replace("\t", "");
        this.botName = name.substring(0, Math.min(name.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.setDelay(delay);
        return true;
    }

    private static int clampDance(int value) {
        return Math.max(MIN_DANCE, Math.min(MAX_DANCE, value));
    }

    private static DanceType danceFromInt(int value) {
        switch (clampDance(value)) {
            case 1:
                return DanceType.HAB_HOP;
            case 2:
                return DanceType.POGO_MOGO;
            case 3:
                return DanceType.DUCK_FUNK;
            case 4:
                return DanceType.THE_ROLLIE;
            default:
                return DanceType.NONE;
        }
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.botName, this.danceType));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = WiredEffectPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(data.delay));
            this.botName = WiredEffectPayloadGuard.text(data.bot_name);
            this.danceType = clampDance(data.dance_type);
        } else {
            this.setDelay(0);
            this.botName = "";
            this.danceType = 0;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.danceType = 0;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int delay;
        String bot_name;
        int dance_type;

        public JsonData(int delay, String bot_name, int dance_type) {
            this.delay = delay;
            this.bot_name = bot_name;
            this.dance_type = dance_type;
        }
    }
}
