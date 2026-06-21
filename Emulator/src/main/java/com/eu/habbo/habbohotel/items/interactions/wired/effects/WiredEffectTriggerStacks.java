package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectTriggerStacks extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CALL_STACKS;

    protected THashSet<HabboItem> items;
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectTriggerStacks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredEffectTriggerStacks(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
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
        for (HabboItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.furniSource);
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
        int[] params = settings.getIntParams();
        this.furniSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;

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
        this.setDelay(delay);

        return true;
    }

    /**
     * Maximum recursion depth to prevent infinite loops when trigger stacks call each other.
     */
    protected static final int MAX_STACK_DEPTH = 10;
    
    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        RoomUnit roomUnit = ctx.actor().orElse(null);
        
        // Get the current call stack depth from the event
        int currentDepth = ctx.event().getCallStackDepth();
        
        // Prevent excessive recursion depth
        if (currentDepth >= MAX_STACK_DEPTH) {
            return;
        }

        THashSet<RoomTile> usedTiles = collectTargetTiles(room, ctx);

        WiredManager.executeEffectsAtTiles(usedTiles, roomUnit, room, currentDepth + 1);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }


    @Override
    public String getWiredData() {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new THashSet<>();
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredMovementPayloadGuard.delay(jsonData.delay));
            this.furniSource = WiredMovementPayloadGuard.furniSource(jsonData.furniSource);
            if (jsonData.itemIds != null) {
                for (Integer id: jsonData.itemIds) {
                    if (id == null) continue;
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] wiredDataOld = wiredData != null ? wiredData.split("\t") : new String[0];

            if (wiredDataOld.length >= 1) {
                this.setDelay(WiredMovementPayloadGuard.parseDelay(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    for (String s : wiredDataOld[1].split(";")) {
                        int itemId = WiredMovementPayloadGuard.parseInt(s, 0);
                        HabboItem item = itemId > 0 ? room.getHabboItem(itemId) : null;

                        if (item != null)
                            this.items.add(item);
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_TRIGGER_STACKS;
    }

    protected List<HabboItem> resolveEffectiveItems(WiredContext ctx) {
        return WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
    }

    protected THashSet<RoomTile> collectTargetTiles(Room room, WiredContext ctx) {
        THashSet<RoomTile> usedTiles = new THashSet<>();

        if (room == null || room.getLayout() == null) {
            return usedTiles;
        }

        for (HabboItem item : resolveEffectiveItems(ctx)) {
            if (item == null) {
                continue;
            }

            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile != null) {
                usedTiles.add(tile);
            }
        }

        return usedTiles;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int delay, List<Integer> itemIds, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
