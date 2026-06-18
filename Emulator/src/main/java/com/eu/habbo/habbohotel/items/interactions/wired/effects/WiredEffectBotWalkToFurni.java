package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBotSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectBotWalkToFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_MOVE;

    private List<HabboItem> items;
    private String botName = "";
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;

    public WiredEffectBotWalkToFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new ArrayList<>();
    }

    public WiredEffectBotWalkToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new ArrayList<>();
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.items) {
            if (item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(2);
        message.appendInt(this.furniSource);
        message.appendInt(this.botSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String botName = settings.getStringParam();
        int[] params = settings.getIntParams();
        this.furniSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = (params.length > 1) ? WiredBotSourceUtil.normalizeBotSource(params[1]) : WiredBotSourceUtil.SOURCE_BOT_NAME;
        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        List<HabboItem> newItems = new ArrayList<>();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < itemsCount; i++) {
                int itemId = settings.getFurniIds()[i];
                HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

                if(it == null)
                    throw new WiredSaveException(String.format("Item %s not found", itemId));

                newItems.add(it);
            }
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.addAll(newItems);
        }
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
        List<Bot> bots = WiredBotSourceUtil.resolveBots(ctx, room, this.botSource, this.botName);

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null);
        }

        if (effectiveItems.isEmpty() || bots.isEmpty()) {
            return;
        }

        for (Bot bot : bots) {
            List<HabboItem> possibleItems = effectiveItems.stream()
                    .filter(item -> !room.getBotsOnItem(item).contains(bot))
                    .collect(Collectors.toList());

            if (possibleItems.isEmpty()) {
                continue;
            }

            HabboItem item = possibleItems.get(Emulator.getRandom().nextInt(possibleItems.size()));

            if (item.getRoomId() != 0 && item.getRoomId() == bot.getRoom().getId() && room.getLayout() != null) {
                bot.getRoomUnit().setGoalLocation(room.getLayout().getTile(item.getX(), item.getY()));
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
        ArrayList<Integer> itemIds = new ArrayList<>();

        if (this.items != null) {
            for (HabboItem item : this.items) {
                if (item.getRoomId() != 0) {
                    itemIds.add(item.getId());
                }
            }
        }

        return WiredManager.getGson().toJson(new JsonData(this.botName, itemIds, this.getDelay(), this.furniSource, this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new ArrayList<>();

        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if(jsonData != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(jsonData.delay));
            this.botName = WiredEffectPayloadGuard.text(jsonData.bot_name);
            this.furniSource = WiredEffectPayloadGuard.furniSource(jsonData.furniSource);
            this.botSource = (jsonData.botSource != null)
                    ? WiredBotSourceUtil.normalizeBotSource(jsonData.botSource)
                    : WiredBotSourceUtil.SOURCE_BOT_NAME;

            if (jsonData.items != null) {
                for(int itemId : jsonData.items) {
                    HabboItem item = room.getHabboItem(itemId);

                    if (item != null)
                        this.items.add(item);
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        }
        else {
            String[] wiredDataSplit = wiredData != null ? wiredData.split("\t") : new String[0];

            if (wiredDataSplit.length >= 2) {
                this.setDelay(WiredEffectPayloadGuard.parseDelay(wiredDataSplit[0]));
                String[] data = wiredDataSplit[1].split(";");

                if (data.length > 1) {
                    this.botName = WiredEffectPayloadGuard.text(data[0]);

                    for (int i = 1; i < data.length; i++) {
                        int itemId = WiredEffectPayloadGuard.parseInt(data[i], 0);
                        HabboItem item = itemId > 0 ? room.getHabboItem(itemId) : null;

                        if (item != null)
                            this.items.add(item);
                    }
                }
            }

            this.needsUpdate(true);
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.botName = "";
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return WiredBotSourceUtil.requiresTriggeringUser(this.botSource);
    }

    static class JsonData {
        String bot_name;
        List<Integer> items;
        int delay;
        int furniSource;
        Integer botSource;

        public JsonData(String bot_name, List<Integer> items, int delay, int furniSource, int botSource) {
            this.bot_name = bot_name;
            this.items = items;
            this.delay = delay;
            this.furniSource = furniSource;
            this.botSource = botSource;
        }
    }
}
