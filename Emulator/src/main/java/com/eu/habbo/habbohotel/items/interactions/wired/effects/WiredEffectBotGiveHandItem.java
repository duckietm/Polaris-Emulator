package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.threading.runnables.RoomUnitGiveHanditem;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectBotGiveHandItem extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_GIVE_HANDITEM;
    private static final int BOT_SOURCE_NAME = 100;

    private String botName = "";
    private int itemId;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = BOT_SOURCE_NAME;

    public WiredEffectBotGiveHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotGiveHandItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        message.appendInt(this.itemId);
        message.appendInt(this.userSource);
        message.appendInt(this.botSource);
        message.appendInt(0);
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
        if(settings.getIntParams().length < 2) throw new WiredSaveException("Missing item id");

        int itemId = this.normalizeHandItem(settings.getIntParams()[0]);
        this.userSource = this.normalizeUserSource(settings.getIntParams()[1]);
        this.botSource = (settings.getIntParams().length > 2) ? this.normalizeBotSource(settings.getIntParams()[2]) : BOT_SOURCE_NAME;

        String botName = settings.getStringParam();

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.itemId = itemId;
        this.botName = botName.substring(0, Math.min(botName.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
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
        Bot bot = this.resolveBot(ctx, room);

        if (habbo != null && bot != null) {

            List<Runnable> tasks = new ArrayList<>();
            tasks.add(new RoomUnitGiveHanditem(roomUnit, room, this.itemId));
            tasks.add(new RoomUnitGiveHanditem(bot.getRoomUnit(), room, 0));
            tasks.add(() -> {
                if(roomUnit.getRoom() != null && roomUnit.getRoom().getId() == room.getId() && roomUnit.getCurrentLocation().distance(bot.getRoomUnit().getCurrentLocation()) < 2) {
                    WiredManager.triggerBotReachedHabbo(room, bot.getRoomUnit(), roomUnit);
                }
            });

            RoomTile tile = bot.getRoomUnit().getClosestAdjacentTile(roomUnit.getX(), roomUnit.getY(), true);

            if(tile != null) {
                bot.getRoomUnit().setGoalLocation(tile);
            }

            Emulator.getThreading().run(new RoomUnitGiveHanditem(bot.getRoomUnit(), room, this.itemId));
            Emulator.getThreading().run(new RoomUnitWalkToLocation(bot.getRoomUnit(), tile, room, tasks, tasks));
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.botName, this.itemId, this.getDelay(), this.userSource, this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if(jsonData != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(jsonData.delay));
            this.itemId = this.normalizeHandItem(jsonData.item_id);
            this.botName = WiredEffectPayloadGuard.text(jsonData.bot_name);
            this.userSource = this.normalizeUserSource(jsonData.userSource);
            this.botSource = ((jsonData.botSource == WiredSourceUtil.SOURCE_TRIGGER) && this.botName != null && !this.botName.isEmpty())
                    ? BOT_SOURCE_NAME
                    : this.normalizeBotSource(jsonData.botSource);
        }
        else {
            String[] data = wiredData != null ? wiredData.split(((char) 9) + "") : new String[0];

            if (data.length == 3) {
                this.setDelay(WiredEffectPayloadGuard.parseDelay(data[0]));
                this.itemId = this.normalizeHandItem(WiredEffectPayloadGuard.parseInt(data[1], 0));
                this.botName = WiredEffectPayloadGuard.text(data[2]);
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.botSource = BOT_SOURCE_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.itemId = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = BOT_SOURCE_NAME;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return (this.userSource == WiredSourceUtil.SOURCE_TRIGGER) || (this.botSource == WiredSourceUtil.SOURCE_TRIGGER);
    }

    static class JsonData {
        String bot_name;
        int item_id;
        int delay;
        int userSource;
        int botSource;

        public JsonData(String bot_name, int item_id, int delay, int userSource, int botSource) {
            this.bot_name = bot_name;
            this.item_id = item_id;
            this.delay = delay;
            this.userSource = userSource;
            this.botSource = botSource;
        }
    }

    private int normalizeHandItem(int value) {
        return Math.max(0, value);
    }

    private int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeBotSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_TRIGGER:
            case BOT_SOURCE_NAME:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
                return value;
            default:
                return BOT_SOURCE_NAME;
        }
    }

    private Bot resolveBot(WiredContext ctx, Room room) {
        if (this.botSource == BOT_SOURCE_NAME) {
            List<Bot> bots = room.getBots(this.botName);
            return (bots.size() == 1) ? bots.get(0) : null;
        }

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.botSource)) {
            Bot bot = room.getBot(roomUnit);

            if (bot != null) {
                return bot;
            }
        }

        return null;
    }
}
