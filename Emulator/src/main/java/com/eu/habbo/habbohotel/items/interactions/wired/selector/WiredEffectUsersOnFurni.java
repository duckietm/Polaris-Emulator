package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectUsersOnFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.USERS_ON_FURNI_SELECTOR;

    private final Set<HabboItem> items = new LinkedHashSet<>();
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersOnFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) {
            ctx.targets().setUsers(Collections.emptySet());
            return;
        }

        this.refresh(room);

        List<HabboItem> sourceItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (sourceItems.isEmpty()) {
            ctx.targets().setUsers(Collections.emptySet());
            return;
        }

        Set<RoomUnit> result = new LinkedHashSet<>();

        for (HabboItem sourceItem : sourceItems) {
            result.addAll(this.resolveUnitsOnItem(room, sourceItem));
        }

        result = this.applySelectorModifiers(result, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.furniSource = (params.length > 0) ? this.normalizeFurniSource(params[0]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.filterExisting = params.length > 1 && params[1] == 1;
        this.invert = params.length > 2 && params[2] == 1;

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
        message.appendInt(3);
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

    private Set<RoomUnit> resolveUnitsOnItem(Room room, HabboItem sourceItem) {
        Set<RoomUnit> result = new LinkedHashSet<>();

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

        for (RoomTile tile : occupiedTiles) {
            if (tile == null) {
                continue;
            }

            result.addAll(room.getUnitManager().getRoomUnitsAt(tile));
        }

        return result;
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

    static class JsonData {
        int furniSource;
        boolean filterExisting;
        boolean invert;
        List<Integer> itemIds;
        int delay;

        JsonData(int furniSource, boolean filterExisting, boolean invert, List<Integer> itemIds, int delay) {
            this.furniSource = furniSource;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.itemIds = itemIds;
            this.delay = delay;
        }
    }
}
