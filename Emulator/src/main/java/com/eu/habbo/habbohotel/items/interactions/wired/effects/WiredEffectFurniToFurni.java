package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectFurniToFurni extends InteractionWiredEffect {
    private static final int SOURCE_SECONDARY_SELECTED = 101;
    private static final String FURNI_SPLIT_REGEX = "[;,\\t]";
    private static final String FURNI_DELIMITER = ";";

    public static final WiredEffectType type = WiredEffectType.FURNI_TO_FURNI;

    private final List<HabboItem> moveItems = new ArrayList<>();
    private final List<HabboItem> targetItems = new ArrayList<>();
    private int moveSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int targetSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectFurniToFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        List<HabboItem> moveItems = this.resolveMoveItems(ctx);
        List<HabboItem> targetItems = this.resolveTargetItems(ctx);

        if (moveItems.isEmpty() || targetItems.isEmpty()) {
            return;
        }

        int targetIndex = 0;
        for (HabboItem moveItem : moveItems) {
            if (moveItem == null) {
                continue;
            }

            HabboItem targetItem = targetItems.get(targetIndex % targetItems.size());
            targetIndex++;

            if (targetItem == null || moveItem.getId() == targetItem.getId()) {
                continue;
            }

            RoomTile targetTile = room.getLayout().getTile(targetItem.getX(), targetItem.getY());
            if (targetTile == null) {
                continue;
            }

            FurnitureMovementError error = WiredMoveCarryHelper.moveFurni(room, this, moveItem, targetTile, moveItem.getRotation(), null, false, ctx);
            if (error == FurnitureMovementError.NONE) {
                continue;
            }

            WiredMoveCarryHelper.moveFurni(room, this, moveItem, targetTile, moveItem.getRotation(), targetItem.getZ(), null, false, ctx);
        }
    }

    @Deprecated
    @Override
    public boolean execute(com.eu.habbo.habbohotel.rooms.RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.moveItems.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.targetItems.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.moveSource,
                this.targetSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.moveItems.clear();
        this.targetItems.clear();

        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            this.setDelay(data.delay);
            this.moveSource = data.moveSource;
            this.targetSource = this.normalizeTargetSource(data.targetSource);

            this.loadItems(room, data.itemIds, this.moveItems);
            this.loadItems(room, data.targetItemIds, this.targetItems);

            if (this.moveSource == WiredSourceUtil.SOURCE_TRIGGER && !this.moveItems.isEmpty()) {
                this.moveSource = WiredSourceUtil.SOURCE_SELECTED;
            }

            if (this.targetSource == WiredSourceUtil.SOURCE_TRIGGER && !this.targetItems.isEmpty()) {
                this.targetSource = SOURCE_SECONDARY_SELECTED;
            }

            return;
        }

        if (wiredData != null && !wiredData.isEmpty()) {
            String[] wiredDataOld = wiredData.split("\t");

            if (wiredDataOld.length >= 1) {
                this.setDelay(Integer.parseInt(wiredDataOld[0]));
            }

            if (wiredDataOld.length >= 2 && !wiredDataOld[1].trim().isEmpty()) {
                this.loadItems(room, this.parseIds(wiredDataOld[1], room), this.moveItems);
            }
        }

        this.moveSource = this.moveItems.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        this.targetSource = this.targetItems.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : SOURCE_SECONDARY_SELECTED;
    }

    @Override
    public void onPickUp() {
        this.moveItems.clear();
        this.targetItems.clear();
        this.moveSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.targetSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateItems(this.moveItems);
        this.validateItems(this.targetItems);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.moveItems.size());

        for (HabboItem item : this.moveItems) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.serializeIds(this.targetItems));
        message.appendInt(2);
        message.appendInt(this.moveSource);
        message.appendInt(this.targetSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        this.moveSource = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : WiredSourceUtil.SOURCE_TRIGGER;
        this.targetSource = this.normalizeTargetSource((settings.getIntParams().length > 1) ? settings.getIntParams()[1] : WiredSourceUtil.SOURCE_TRIGGER);

        Room room = this.getRoom();
        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        List<HabboItem> newMoveItems = new ArrayList<>();
        if (this.moveSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int itemId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(itemId);

                if (item == null) {
                    throw new WiredSaveException(String.format("Item %s not found", itemId));
                }

                newMoveItems.add(item);
            }
        }

        if (newMoveItems.size() > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newTargetItems = new ArrayList<>();
        if (this.targetSource == SOURCE_SECONDARY_SELECTED) {
            newTargetItems.addAll(this.parseItems(settings.getStringParam(), room));
        }

        if (newTargetItems.size() > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.moveItems.clear();
        this.moveItems.addAll(newMoveItems);

        this.targetItems.clear();
        this.targetItems.addAll(newTargetItems);

        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_MOVEMENT;
    }

    private List<HabboItem> resolveMoveItems(WiredContext ctx) {
        return this.resolveItems(ctx, this.moveSource, this.moveItems);
    }

    private List<HabboItem> resolveTargetItems(WiredContext ctx) {
        int source = (this.targetSource == SOURCE_SECONDARY_SELECTED) ? WiredSourceUtil.SOURCE_SELECTED : this.targetSource;
        return this.resolveItems(ctx, source, this.targetItems);
    }

    private List<HabboItem> resolveItems(WiredContext ctx, int source, List<HabboItem> items) {
        if (source == WiredSourceUtil.SOURCE_SELECTED) {
            this.validateItems(items);
        }

        return WiredSourceUtil.resolveItems(ctx, source, items).stream()
                .filter(item -> item != null && ctx.room().getHabboItem(item.getId()) != null)
                .collect(Collectors.toList());
    }

    private List<HabboItem> parseItems(String data, Room room) throws WiredSaveException {
        List<HabboItem> items = new ArrayList<>();
        if (data == null || data.trim().isEmpty() || room == null) {
            return items;
        }

        Set<Integer> seen = new HashSet<>();

        for (String part : data.split(FURNI_SPLIT_REGEX)) {
            if (part == null) {
                continue;
            }

            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int itemId;
            try {
                itemId = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                continue;
            }

            if (itemId <= 0 || !seen.add(itemId)) {
                continue;
            }

            HabboItem item = room.getHabboItem(itemId);
            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            items.add(item);
        }

        return items;
    }

    private List<Integer> parseIds(String data, Room room) {
        try {
            return this.parseItems(data, room).stream().map(HabboItem::getId).collect(Collectors.toList());
        } catch (WiredSaveException e) {
            return new ArrayList<>();
        }
    }

    private void loadItems(Room room, List<Integer> itemIds, List<HabboItem> target) {
        if (room == null || itemIds == null || itemIds.isEmpty()) {
            return;
        }

        for (Integer itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);

            if (item != null) {
                target.add(item);
            }
        }
    }

    private int normalizeTargetSource(int source) {
        return (source == WiredSourceUtil.SOURCE_SELECTED) ? SOURCE_SECONDARY_SELECTED : source;
    }

    private String serializeIds(List<HabboItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        return items.stream()
                .map(HabboItem::getId)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(FURNI_DELIMITER));
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        List<Integer> targetItemIds;
        int moveSource;
        int targetSource;

        public JsonData(int delay, List<Integer> itemIds, List<Integer> targetItemIds, int moveSource, int targetSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.targetItemIds = targetItemIds;
            this.moveSource = moveSource;
            this.targetSource = targetSource;
        }
    }
}
