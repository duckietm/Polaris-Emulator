package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.threading.runnables.RoomUnitGiveHanditem;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectBotGiveHandItem extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.BOT_GIVE_HANDITEM;
    private static final int BOT_SOURCE_NAME = 100;

    private String botName = "";
    private int itemId;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = BOT_SOURCE_NAME;

    public WiredEffectBotGiveHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotGiveHandItem(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        message.appendInt(1);
        message.appendInt(this.itemId);
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
    public int[] officialFurniSources() {
        return new int[] {};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.botSource, this.userSource};
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // Official layout: [hand item]; the bots are user slot 0, ours the receivers in user slot 1.
        if (settings.getIntParams().length < 1) throw new WiredSaveException("Missing item id");

        int itemId = this.normalizeHandItem(settings.getIntParams()[0]);
        this.botSource = this.normalizeBotSource(settings.userSource(0, BOT_SOURCE_NAME));
        this.userSource = this.normalizeUserSource(settings.userSource(1, WiredSourceUtil.SOURCE_TRIGGER));

        String botName = settings.getStringParam();

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.itemId = itemId;
        this.botName = botName.substring(
                0, Math.min(botName.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
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

        List<Bot> bots = this.resolveBots(ctx, room);
        if (bots.isEmpty()) return;

        // Every resolved user is served, by every bot the source names; the box used to serve the
        // first user only and gave up entirely when two bots shared the name.
        for (RoomUnit roomUnit : targets) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo == null) continue;

            for (Bot bot : bots) {
                this.serve(room, bot, roomUnit);
            }
        }
    }

    private void serve(Room room, Bot bot, RoomUnit roomUnit) {
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(new RoomUnitGiveHanditem(roomUnit, room, this.itemId));
        tasks.add(new RoomUnitGiveHanditem(bot.getRoomUnit(), room, 0));
        tasks.add(() -> {
            if (roomUnit.getRoom() != null
                    && roomUnit.getRoom().getId() == room.getId()
                    && roomUnit.getCurrentLocation().distance(bot.getRoomUnit().getCurrentLocation()) < 2) {
                WiredManager.triggerBotReachedHabbo(room, bot.getRoomUnit(), roomUnit);
            }
        });

        RoomTile tile = bot.getRoomUnit().getClosestAdjacentTile(roomUnit.getX(), roomUnit.getY(), true);

        if (tile != null) {
            bot.getRoomUnit().setGoalLocation(tile);
        }

        Emulator.getThreading().run(new RoomUnitGiveHanditem(bot.getRoomUnit(), room, this.itemId));
        Emulator.getThreading().run(new RoomUnitWalkToLocation(bot.getRoomUnit(), tile, room, tasks, tasks));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.botName, this.itemId, this.getDelay(), this.userSource, this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(jsonData.delay));
            this.itemId = this.normalizeHandItem(jsonData.item_id);
            this.botName = WiredEffectPayloadGuard.text(jsonData.bot_name);
            this.userSource = this.normalizeUserSource(jsonData.userSource);
            this.botSource = ((jsonData.botSource == WiredSourceUtil.SOURCE_TRIGGER)
                            && this.botName != null
                            && !this.botName.isEmpty())
                    ? BOT_SOURCE_NAME
                    : this.normalizeBotSource(jsonData.botSource);
        } else {
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
        return (this.userSource == WiredSourceUtil.SOURCE_TRIGGER)
                || (this.botSource == WiredSourceUtil.SOURCE_TRIGGER);
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

    private List<Bot> resolveBots(WiredContext ctx, Room room) {
        if (this.botSource == BOT_SOURCE_NAME) {
            if (this.botName == null || this.botName.isEmpty()) return new ArrayList<>();
            List<Bot> bots = room.getBots(this.botName);
            return (bots != null) ? new ArrayList<>(bots) : new ArrayList<>();
        }

        List<Bot> resolved = new ArrayList<>();
        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.botSource)) {
            Bot bot = room.getBot(roomUnit);

            if (bot != null && !resolved.contains(bot)) {
                resolved.add(bot);
            }
        }

        return resolved;
    }
}
