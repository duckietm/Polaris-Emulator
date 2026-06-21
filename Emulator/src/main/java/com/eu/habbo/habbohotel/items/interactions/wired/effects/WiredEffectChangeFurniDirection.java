package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.*;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WiredEffectChangeFurniDirection extends InteractionWiredEffect {
    public static final int ACTION_WAIT = 0;
    public static final int ACTION_TURN_RIGHT_45 = 1;
    public static final int ACTION_TURN_RIGHT_90 = 2;
    public static final int ACTION_TURN_LEFT_45 = 3;
    public static final int ACTION_TURN_LEFT_90 = 4;
    public static final int ACTION_TURN_BACK = 5;
    public static final int ACTION_TURN_RANDOM = 6;

    public static final WiredEffectType type = WiredEffectType.MOVE_DIRECTION;

    private final Map<HabboItem, WiredChangeDirectionSetting> items = new LinkedHashMap<>(0);
    private final ConcurrentHashMap<Integer, WiredChangeDirectionSetting> runtimeItems = new ConcurrentHashMap<>();
    private RoomUserRotation startRotation = RoomUserRotation.NORTH;
    private int blockedAction = 0;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean blockOnUserCollision = false;

    public WiredEffectChangeFurniDirection(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeFurniDirection(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        List<HabboItem> resolvedItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items.keySet());
        Map<HabboItem, WiredChangeDirectionSetting> effectiveItems;

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.runtimeItems.clear();
            Set<HabboItem> toRemove = new HashSet<>();
            for (HabboItem item : this.items.keySet()) {
                if (item == null || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                    toRemove.add(item);
            }
            for (HabboItem item : toRemove) {
                this.items.remove(item);
            }
            effectiveItems = this.items;
        } else {
            this.pruneRuntimeItems(room);
            effectiveItems = new HashMap<>();
            for (HabboItem item : resolvedItems) {
                if (item != null) {
                    WiredChangeDirectionSetting setting = this.runtimeItems.computeIfAbsent(
                            item.getId(),
                            key -> new WiredChangeDirectionSetting(item.getId(), item.getRotation(), this.startRotation));
                    if (setting == null) {
                        setting = new WiredChangeDirectionSetting(item.getId(), item.getRotation(), this.startRotation);
                    }
                    effectiveItems.put(item, setting);
                }
            }
        }

        if (effectiveItems.isEmpty()) return;

        for (Map.Entry<HabboItem, WiredChangeDirectionSetting> entry : effectiveItems.entrySet()) {
            HabboItem item = entry.getKey();
            if (item == null || entry.getValue() == null) continue;
            
            RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
            if (itemTile == null) continue;
            
            RoomTile targetTile = room.getLayout().getTileInFront(itemTile, entry.getValue().direction.getValue());

            int count = 1;
            while (this.shouldSearchNextDirection(room, item, targetTile, ctx) && count < 8) {
                entry.getValue().direction = this.nextRotation(entry.getValue().direction);

                RoomTile tile = room.getLayout().getTileInFront(itemTile, entry.getValue().direction.getValue());
                if (tile != null && tile.state != RoomTileState.INVALID) {
                    targetTile = tile;
                }

                count++;
            }
        }

        for (Map.Entry<HabboItem, WiredChangeDirectionSetting> entry : effectiveItems.entrySet()) {
            HabboItem item = entry.getKey();
            if (item == null || entry.getValue() == null) continue;
            
            int newDirection = entry.getValue().direction.getValue();

            RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
            if (itemTile == null) continue;
            
            RoomTile targetTile = room.getLayout().getTileInFront(itemTile, newDirection);

            if(item.getRotation() != entry.getValue().rotation) {
                if (targetTile == null || room.furnitureFitsAt(targetTile, item, entry.getValue().rotation, false) != FurnitureMovementError.NONE)
                    continue;

                WiredMoveCarryHelper.moveFurni(room, this, entry.getKey(), targetTile, entry.getValue().rotation, null, true, ctx);
            }

            if (targetTile == null) continue;

            FurnitureMovementError movementError = WiredMoveCarryHelper.getMovementError(room, this, entry.getKey(), targetTile, item.getRotation(), ctx);

            if (movementError == FurnitureMovementError.TILE_HAS_HABBOS
                    || movementError == FurnitureMovementError.TILE_HAS_BOTS
                    || movementError == FurnitureMovementError.TILE_HAS_PETS) {
                Emulator.getThreading().run(() -> {
                    for (RoomUnit roomUnit : room.getRoomUnits(targetTile)) {
                        WiredManager.triggerBotCollision(room, roomUnit);
                        break;
                    }
                });
            }

            if (targetTile.state != RoomTileState.INVALID && movementError == FurnitureMovementError.NONE) {
                RoomTile oldLocation = room.getLayout().getTile(entry.getKey().getX(), entry.getKey().getY());
                if (oldLocation != null && WiredMoveCarryHelper.moveFurni(room, this, entry.getKey(), targetTile, item.getRotation(), null, false, ctx) == FurnitureMovementError.NONE) {
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
        ArrayList<WiredChangeDirectionSetting> settings = new ArrayList<>(this.items.values());
        return WiredManager.getGson().toJson(new JsonData(this.startRotation, this.blockedAction, settings, this.getDelay(), this.furniSource, this.blockOnUserCollision));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {

        this.items.clear();
        this.runtimeItems.clear();

        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.startRotation = data.start_direction;
            this.blockedAction = data.blocked_action;
            this.furniSource = data.furniSource;
            this.blockOnUserCollision = data.blockOnUserCollision;

            for(WiredChangeDirectionSetting setting : data.items) {
                HabboItem item = room.getHabboItem(setting.item_id);

                if (item != null) {
                    this.items.put(item, setting);
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        }
        else {
            String[] data = wiredData.split("\t");

            if (data.length >= 4) {
                this.setDelay(Integer.parseInt(data[0]));
                this.startRotation = RoomUserRotation.fromValue(Integer.parseInt(data[1]));
                this.blockedAction = Integer.parseInt(data[2]);

                int itemCount = Integer.parseInt(data[3]);

                if (itemCount > 0) {
                    for (int i = 4; i < data.length; i++) {
                        String[] subData = data[i].split(":");

                        if (subData.length >= 2) {
                            HabboItem item = room.getHabboItem(Integer.parseInt(subData[0]));

                            if (item != null) {
                                int rotation = item.getRotation();

                                if (subData.length > 2) {
                                    rotation = Integer.parseInt(subData[2]);
                                }

                                this.items.put(item, new WiredChangeDirectionSetting(item.getId(), rotation, RoomUserRotation.fromValue(Integer.parseInt(subData[1]))));
                            }
                        }
                    }
                }
            }

            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.blockOnUserCollision = false;
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.items.clear();
        this.runtimeItems.clear();
        this.blockedAction = 0;
        this.startRotation = RoomUserRotation.NORTH;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.blockOnUserCollision = false;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (Map.Entry<HabboItem, WiredChangeDirectionSetting> item : this.items.entrySet()) {
            message.appendInt(item.getKey().getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.startRotation != null ? this.startRotation.getValue() : 0);
        message.appendInt(this.blockedAction);
        message.appendInt(this.furniSource);
        message.appendInt(this.blockOnUserCollision ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 3) throw new WiredSaveException("Invalid data");

        int startDirectionInt = settings.getIntParams()[0];

        if(startDirectionInt < 0 || startDirectionInt > 7) {
            throw new WiredSaveException("Start direction is invalid");
        }

        RoomUserRotation startDirection = RoomUserRotation.fromValue(startDirectionInt);

        int blockedActionInt = settings.getIntParams()[1];
        this.furniSource = settings.getIntParams()[2];
        this.blockOnUserCollision = settings.getIntParams().length > 3 && settings.getIntParams()[3] == 1;

        if(blockedActionInt < 0 || blockedActionInt > 6) {
            throw new WiredSaveException("Blocked action is invalid");
        }

        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        Map<HabboItem, WiredChangeDirectionSetting> newItems = new LinkedHashMap<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

            if(it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newItems.put(it, new WiredChangeDirectionSetting(it.getId(), it.getRotation(), startDirection));
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        this.runtimeItems.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.putAll(newItems);
        }
        this.startRotation = startDirection;
        this.blockedAction = blockedActionInt;
        this.setDelay(delay);

        return true;
    }

    private void pruneRuntimeItems(Room room) {
        if (room == null || this.runtimeItems.isEmpty()) {
            return;
        }

        this.runtimeItems.entrySet().removeIf(entry -> room.getHabboItem(entry.getKey()) == null);
    }

    private boolean shouldSearchNextDirection(Room room, HabboItem item, RoomTile targetTile, WiredContext ctx) {
        if (targetTile == null || targetTile.state == RoomTileState.INVALID) {
            return true;
        }

        if (room.furnitureFitsAt(targetTile, item, item.getRotation(), false) != FurnitureMovementError.NONE) {
            return true;
        }

        if (this.blockOnUserCollision) {
            return false;
        }

        FurnitureMovementError unitCollision = WiredMoveCarryHelper.getMovementError(room, this, item, targetTile, item.getRotation(), ctx);
        return unitCollision == FurnitureMovementError.TILE_HAS_HABBOS;
    }

    private RoomUserRotation nextRotation(RoomUserRotation currentRotation) {
        switch (this.blockedAction) {
            case ACTION_TURN_BACK:
                return RoomUserRotation.fromValue(currentRotation.getValue()).getOpposite();
            case ACTION_TURN_LEFT_45:
                return RoomUserRotation.counterClockwise(currentRotation);
            case ACTION_TURN_LEFT_90:
                return RoomUserRotation.counterClockwise(RoomUserRotation.counterClockwise(currentRotation));
            case ACTION_TURN_RIGHT_45:
                return RoomUserRotation.clockwise(currentRotation);
            case ACTION_TURN_RIGHT_90:
                return RoomUserRotation.clockwise(RoomUserRotation.clockwise(currentRotation));
            case ACTION_TURN_RANDOM:
                return RoomUserRotation.fromValue(Emulator.getRandom().nextInt(8));
            case ACTION_WAIT:
            default:
                return currentRotation;
        }
    }

    @Override
    protected long requiredCooldown() {
        return 495;
    }

    static class JsonData {
        RoomUserRotation start_direction;
        int blocked_action;
        List<WiredChangeDirectionSetting> items;
        int delay;
        int furniSource;
        boolean blockOnUserCollision;

        public JsonData(RoomUserRotation start_direction, int blocked_action, List<WiredChangeDirectionSetting> items, int delay, int furniSource, boolean blockOnUserCollision) {
            this.start_direction = start_direction;
            this.blocked_action = blocked_action;
            this.items = items;
            this.delay = delay;
            this.furniSource = furniSource;
            this.blockOnUserCollision = blockOnUserCollision;
        }
    }
}
