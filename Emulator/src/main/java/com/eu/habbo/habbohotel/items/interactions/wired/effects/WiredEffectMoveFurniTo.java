package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectMoveFurniTo extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_TO;
    private final List<HabboItem> items = new ArrayList<>();
    private int direction;
    private int spacing = 1;
    private Map<Integer, Integer> indexOffset = new LinkedHashMap<>();
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMoveFurniTo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniTo(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            return false;

        this.items.clear();
        this.indexOffset.clear();

        if(settings.getIntParams().length < 3) throw new WiredSaveException("invalid data");
        this.direction = settings.getIntParams()[0];
        this.spacing = settings.getIntParams()[1];
        this.furniSource = settings.getIntParams()[2];

        int count = settings.getFurniIds().length;

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < count; i++) {
                this.items.add(room.getHabboItem(settings.getFurniIds()[i]));
            }
        }

        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            List<HabboItem> toRemove = new ArrayList<>();
            for (HabboItem item : effectiveItems) {
                if (item == null || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                    toRemove.add(item);
            }
            for (HabboItem item : toRemove) {
                this.items.remove(item);
            }
        }

        if (effectiveItems.isEmpty())
            return;

        Object[] stuff = ctx.legacySettings();
        if (stuff != null && stuff.length > 0) {
            for (Object object : stuff) {
                if (object instanceof HabboItem) {
                    HabboItem targetItem = effectiveItems.get(Emulator.getRandom().nextInt(effectiveItems.size()));

                    if (targetItem != null) {
                        int indexOffset = 0;
                        if (!this.indexOffset.containsKey(targetItem.getId())) {
                            this.indexOffset.put(targetItem.getId(), indexOffset);
                        } else {
                            indexOffset = this.indexOffset.get(targetItem.getId()) + this.spacing;
                        }

                        RoomTile objectTile = room.getLayout().getTile(targetItem.getX(), targetItem.getY());

                        if (objectTile != null) {
                            RoomTile tile = room.getLayout().getTileInFront(objectTile, this.direction, indexOffset);
                            if (tile == null || !tile.getAllowStack()) {
                                indexOffset = 0;
                                tile = room.getLayout().getTileInFront(objectTile, this.direction, indexOffset);
                            }

                            if(tile == null) {
                                continue;
                            }

                            HabboItem movingItem = (HabboItem) object;
                            RoomTile oldLocation = room.getLayout().getTile(movingItem.getX(), movingItem.getY());
                            if (oldLocation == null) {
                                continue;
                            }

                            FurnitureMovementError movementError = WiredMoveCarryHelper.moveFurni(room, this, movingItem, tile, movingItem.getRotation(), null, false, ctx);

                            if (movementError != FurnitureMovementError.NONE) {
                                continue;
                            }

                            this.indexOffset.put(targetItem.getId(), indexOffset);
                        }
                    }
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
    public boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return true;
        
        Object[] stuff = ctx.legacySettings();
        if (stuff == null || stuff.length == 0) return true;
        
        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        for (Object object : stuff) {
            if (object instanceof HabboItem) {
                HabboItem item = (HabboItem) object;
                
                if (effectiveItems.isEmpty()) continue;
                HabboItem targetItem = effectiveItems.get(0);
                if (targetItem == null) continue;
                
                WiredSimulation.SimulatedPosition targetPos = simulation.getItemPosition(targetItem);
                RoomTile objectTile = room.getLayout().getTile(targetPos.x, targetPos.y);
                if (objectTile == null) continue;
                
                RoomTile tile = room.getLayout().getTileInFront(objectTile, this.direction, 0);
                if (tile == null) continue;
                
                WiredSimulation.SimulatedPosition currentPos = simulation.getItemPosition(item);
                if (!simulation.isTileValidForItem(tile.x, tile.y, item)) {
                    return false;
                }
                if (!simulation.moveItem(item, tile.x, tile.y, tile.getStackHeight(), currentPos.rotation)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    @Override
    public String getWiredData() {
        Set<HabboItem> itemsToRemove = new HashSet<>();
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);

        for (HabboItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                itemsToRemove.add(item);
        }

        for (HabboItem item : itemsToRemove) {
            this.items.remove(item);
        }

        itemsSnapshot = new ArrayList<>(this.items);
        return WiredManager.getGson().toJson(new JsonData(
                this.direction,
                this.spacing,
                this.getDelay(),
                itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        Set<HabboItem> items = new HashSet<>();

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
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.direction);
        message.appendInt(this.spacing);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.direction = data.direction;
            this.spacing = data.spacing;
            this.setDelay(data.delay);
            this.furniSource = data.furniSource;

            for (Integer id: data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] data = wiredData.split("\t");

            if (data.length == 4) {
                try {
                    this.direction = Integer.parseInt(data[0]);
                    this.spacing = Integer.parseInt(data[1]);
                    this.setDelay(Integer.parseInt(data[2]));
                } catch (Exception e) {
                }

                for (String s : data[3].split("\r")) {
                    HabboItem item = room.getHabboItem(Integer.parseInt(s));

                    if (item != null)
                        this.items.add(item);
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.items.clear();
        this.direction = 0;
        this.spacing = 0;
        this.indexOffset.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return 495;
    }

    static class JsonData {
        int direction;
        int spacing;
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int direction, int spacing, int delay, List<Integer> itemIds, int furniSource) {
            this.direction = direction;
            this.spacing = spacing;
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
