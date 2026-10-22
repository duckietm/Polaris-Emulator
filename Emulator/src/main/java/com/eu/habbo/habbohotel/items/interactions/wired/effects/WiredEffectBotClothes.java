package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBotSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

public class WiredEffectBotClothes extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.BOT_CLOTHES;

    private String botName = "";
    private String botLook = "";
    private int botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;

    public WiredEffectBotClothes(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotClothes(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName + ((char) 9) + "" + this.botLook);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public int[] officialFurniSources() {
        return new int[0];
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.botSource};
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String dataString = settings.getStringParam();
        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        String splitBy = "\t";
        if (!dataString.contains(splitBy)) throw new WiredSaveException("Malformed data string");

        String[] data = dataString.split(Pattern.quote(splitBy));

        if (data.length != 2) throw new WiredSaveException("Malformed data string. Invalid data length");

        // The look goes straight onto the bot and out to everyone in the room; a malformed one is
        // refused here rather than broadcast.
        if (!RoomItemInputGuard.isValidFigure(data[1])) throw new WiredSaveException("Invalid figure");

        // Official: "name\tfigure", no int params; the bots are user slot 0.
        this.botSource =
                WiredBotSourceUtil.normalizeBotSource(settings.userSource(0, WiredBotSourceUtil.SOURCE_BOT_NAME));
        this.botName = data[0].substring(
                0, Math.min(data[0].length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
        this.botLook = data[1];
        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        if (!RoomItemInputGuard.isValidFigure(this.botLook)) return;

        Room room = ctx.room();
        List<Bot> bots = WiredBotSourceUtil.resolveBots(ctx, room, this.botSource, this.botName);

        for (Bot bot : bots) {
            bot.setFigure(this.botLook);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.botName, this.botLook, this.getDelay(), this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(jsonData.delay));
            this.botName = WiredEffectPayloadGuard.text(jsonData.bot_name);
            this.botLook = RoomItemInputGuard.isValidFigure(jsonData.look) ? jsonData.look : "";
            this.botSource = (jsonData.botSource != null)
                    ? WiredBotSourceUtil.normalizeBotSource(jsonData.botSource)
                    : WiredBotSourceUtil.SOURCE_BOT_NAME;
        } else {
            String[] data = wiredData != null ? wiredData.split(((char) 9) + "") : new String[0];

            if (data.length >= 3) {
                this.setDelay(WiredEffectPayloadGuard.parseDelay(data[0]));
                this.botName = WiredEffectPayloadGuard.text(data[1]);
                this.botLook = RoomItemInputGuard.isValidFigure(data[2]) ? data[2] : "";
            }

            this.needsUpdate(true);
            this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.botLook = "";
        this.botName = "";
        this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return WiredBotSourceUtil.requiresTriggeringUser(this.botSource);
    }

    public String getBotName() {
        return this.botName;
    }

    public void setBotName(String botName) {
        this.botName = botName;
    }

    public String getBotLook() {
        return this.botLook;
    }

    public void setBotLook(String botLook) {
        this.botLook = botLook;
    }

    static class JsonData {
        String bot_name;
        String look;
        int delay;
        Integer botSource;

        public JsonData(String bot_name, String look, int delay, int botSource) {
            this.bot_name = bot_name;
            this.look = look;
            this.delay = delay;
            this.botSource = botSource;
        }
    }
}
