package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBotSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WiredEffectBotTeleport extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_TELEPORT;

    private THashSet<HabboItem> items;
    private String botName = "";
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;

    public WiredEffectBotTeleport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredEffectBotTeleport(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        if (roomUnit == null || tile == null || roomUnit.isWiredTeleporting)
            return;

        Room room = roomUnit.getRoom();

        if (room == null) {
            return;
        }

        // makes a temporary effect

        roomUnit.getRoom().unIdle(roomUnit.getRoom().getHabbo(roomUnit));
        room.sendComposer(new RoomUserEffectComposer(roomUnit, 4).compose());
        Emulator.getThreading().run(new SendRoomUnitEffectComposer(room, roomUnit), WiredManager.TELEPORT_DELAY + 1000);

        if (tile == roomUnit.getCurrentLocation()) {
            return;
        }

        if (tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
            RoomTile alternativeTile = null;
            List<RoomTile> optionalTiles = room.getLayout().getTilesAround(tile);

            Collections.reverse(optionalTiles);
            for (RoomTile optionalTile : optionalTiles) {
                if (optionalTile.state != RoomTileState.INVALID && optionalTile.state != RoomTileState.BLOCKED) {
                    alternativeTile = optionalTile;
                    break;
                }
            }

            if (alternativeTile != null) {
                tile = alternativeTile;
            }
        }

        Emulator.getThreading().run(() -> { roomUnit.isWiredTeleporting = true; }, Math.max(0, WiredManager.TELEPORT_DELAY - 500));
        Emulator.getThreading().run(new RoomUnitTeleport(roomUnit, room, tile.x, tile.y, tile.getStackHeight() + (tile.state == RoomTileState.SIT ? -0.5 : 0), roomUnit.getEffectId()), WiredManager.TELEPORT_DELAY);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        itemsSnapshot = new ArrayList<>(this.items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot)
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

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        List<HabboItem> validItems = new ArrayList<>();
        for (HabboItem item : effectiveItems) {
            if (item != null && item.getRoomId() != 0) validItems.add(item);
        }

        if (validItems.isEmpty()) return;
        if (room.getLayout() == null) return;

        List<Bot> bots = WiredBotSourceUtil.resolveBots(ctx, room, this.botSource, this.botName);

        if (bots.isEmpty()) {
            return;
        }

        for (Bot bot : bots) {
            HabboItem targetItem = validItems.get(Emulator.getRandom().nextInt(validItems.size()));

            if (targetItem.getRoomId() == bot.getRoom().getId()) {
                RoomTile tile = room.getLayout().getTile(targetItem.getX(), targetItem.getY());
                if (tile != null) {
                    teleportUnitToTile(bot.getRoomUnit(), tile);
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
        ArrayList<Integer> itemIds = new ArrayList<>();

        if (this.items != null) {
            List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
            for (HabboItem item : itemsSnapshot) {
                if (item.getRoomId() != 0) {
                    itemIds.add(item.getId());
                }
            }
        }

        return WiredManager.getGson().toJson(new JsonData(this.botName, itemIds, this.getDelay(), this.furniSource, this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new THashSet<>();

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
        this.botName = "";
        this.items.clear();
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
