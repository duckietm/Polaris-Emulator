package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectFurniOnFurni extends InteractionWiredEffect {
    private static final double EPSILON = 0.0001D;

    private static final int SELECT_FURNI_ABOVE = 0;
    private static final int SELECT_FURNI_BELOW = 1;
    private static final int SELECT_FURNI_SAME_HEIGHT = 2;
    private static final int SELECT_ALL_FURNI_ON_TILE = 3;

    public static final WiredEffectType type = WiredEffectType.FURNI_ON_FURNI_SELECTOR;

    private final Set<HabboItem> items = new LinkedHashSet<>();
    private int selectionType = SELECT_FURNI_ABOVE;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectFurniOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniOnFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) {
            ctx.targets().setItems(Collections.emptySet());
            return;
        }

        this.refresh(room);

        List<HabboItem> sourceItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (sourceItems.isEmpty()) {
            ctx.targets().setItems(Collections.emptySet());
            return;
        }

        Set<HabboItem> result = new LinkedHashSet<>();
        boolean includeWiredItems = this.includeWiredTargets(ctx);

        for (HabboItem sourceItem : sourceItems) {
            result.addAll(this.resolveRelatedItems(room, sourceItem, includeWiredItems));
        }

        result = this.applySelectorModifiers(result, this.getSelectableFloorItems(room, ctx), ctx.targets().items(), this.filterExisting, this.invert);

        ctx.targets().setItems(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.selectionType = (params.length > 0) ? this.normalizeSelectionType(params[0]) : SELECT_FURNI_ABOVE;
        this.furniSource = (params.length > 1) ? this.normalizeFurniSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.filterExisting = params.length > 2 && params[2] == 1;
        this.invert = params.length > 3 && params[3] == 1;

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room == null) {
                return false;
            }

            for (int itemId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(itemId);

                if (item != null) {
                    this.items.add(item);
                }
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
    public boolean isSelector() {
        return true;
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        this.refresh(Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));

        return WiredManager.getGson().toJson(new JsonData(
                this.selectionType,
                this.furniSource,
                this.filterExisting,
                this.invert,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.getDelay()
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.selectionType = this.normalizeSelectionType(data.selectionType);
        this.furniSource = this.normalizeFurniSource(data.furniSource);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);

        if (room == null || data.itemIds == null) {
            return;
        }

        for (Integer id : data.itemIds) {
            HabboItem item = room.getHabboItem(id);

            if (item != null) {
                this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.selectionType = SELECT_FURNI_ABOVE;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.filterExisting = false;
        this.invert = false;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.selectionType);
        message.appendInt(this.furniSource);
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private Set<HabboItem> resolveRelatedItems(Room room, HabboItem sourceItem, boolean includeWiredItems) {
        Set<HabboItem> result = new LinkedHashSet<>();

        if (sourceItem == null) {
            return result;
        }

        RoomTile baseTile = room.getLayout().getTile(sourceItem.getX(), sourceItem.getY());
        if (baseTile == null) {
            return result;
        }

        Set<RoomTile> occupiedTiles = room.getLayout().getTilesAt(baseTile, sourceItem.getBaseItem().getWidth(), sourceItem.getBaseItem().getLength(), sourceItem.getRotation());
        if (occupiedTiles == null) {
            return result;
        }

        double sourceBase = this.normalizeAltitude(sourceItem.getZ());
        double sourceTop = this.normalizeAltitude(sourceItem.getZ() + Item.getCurrentHeight(sourceItem));

        for (RoomTile tile : occupiedTiles) {
            if (tile == null) {
                continue;
            }

            for (HabboItem matchedItem : room.getItemsAt(tile)) {
                if (matchedItem == null || (!includeWiredItems && matchedItem instanceof InteractionWired)) {
                    continue;
                }

                if (matchedItem == sourceItem) {
                    if (this.selectionType == SELECT_FURNI_SAME_HEIGHT || this.selectionType == SELECT_ALL_FURNI_ON_TILE) {
                        result.add(matchedItem);
                    }
                    continue;
                }

                if (this.matchesSelectionType(sourceBase, sourceTop, matchedItem)) {
                    result.add(matchedItem);
                }
            }
        }

        return result;
    }

    private boolean matchesSelectionType(double sourceBase, double sourceTop, HabboItem matchedItem) {
        double matchedBase = this.normalizeAltitude(matchedItem.getZ());
        double matchedTop = this.normalizeAltitude(matchedItem.getZ() + Item.getCurrentHeight(matchedItem));

        switch (this.selectionType) {
            case SELECT_FURNI_BELOW:
                return matchedTop <= (sourceBase + EPSILON);
            case SELECT_FURNI_SAME_HEIGHT:
                return BigDecimal.valueOf(matchedBase).compareTo(BigDecimal.valueOf(sourceBase)) == 0;
            case SELECT_ALL_FURNI_ON_TILE:
                return true;
            case SELECT_FURNI_ABOVE:
            default:
                return matchedBase >= (sourceTop - EPSILON);
        }
    }

    private void refresh(Room room) {
        Set<HabboItem> invalidItems = new LinkedHashSet<>();

        if (room == null) {
            invalidItems.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (room.getHabboItem(item.getId()) == null) {
                    invalidItems.add(item);
                }
            }
        }

        this.items.removeAll(invalidItems);
    }

    private int normalizeSelectionType(int value) {
        if (value < SELECT_FURNI_ABOVE || value > SELECT_ALL_FURNI_ON_TILE) {
            return SELECT_FURNI_ABOVE;
        }

        return value;
    }

    private int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
            case WiredSourceUtil.SOURCE_TRIGGER:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    private double normalizeAltitude(double value) {
        double clampedValue = Math.max(0.0D, Math.min(Room.MAXIMUM_FURNI_HEIGHT, value));
        return BigDecimal.valueOf(clampedValue).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static class JsonData {
        int selectionType;
        int furniSource;
        boolean filterExisting;
        boolean invert;
        List<Integer> itemIds;
        int delay;

        JsonData(int selectionType, int furniSource, boolean filterExisting, boolean invert, List<Integer> itemIds, int delay) {
            this.selectionType = selectionType;
            this.furniSource = furniSource;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.itemIds = itemIds;
            this.delay = delay;
        }
    }
}
