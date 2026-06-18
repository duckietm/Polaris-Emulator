package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionNotFurniHaveFurni extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.NOT_FURNI_HAVE_FURNI;

    private boolean all;
    private THashSet<HabboItem> items;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredConditionNotFurniHaveFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredConditionNotFurniHaveFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();

        this.refresh();

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.isEmpty())
            return true;

        if (room.getLayout() == null)
            return true;

        if(this.all) {
            return targets.stream().allMatch(item -> {
                if (item == null) return true;
                RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
                if (baseTile == null) return true;
                double minZ = item.getZ() + Item.getCurrentHeight(item);
                THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(baseTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
                if (occupiedTiles == null) return true;
                return occupiedTiles.stream().noneMatch(tile -> tile != null && room.getItemsAt(tile).stream().anyMatch(matchedItem -> matchedItem != item && matchedItem.getZ() >= minZ));
            });
        }
        else {
            return targets.stream().anyMatch(item -> {
                if (item == null) return true;
                RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
                if (baseTile == null) return true;
                double minZ = item.getZ() + Item.getCurrentHeight(item);
                THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(baseTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
                if (occupiedTiles == null) return true;
                return occupiedTiles.stream().noneMatch(tile -> tile != null && room.getItemsAt(tile).stream().anyMatch(matchedItem -> matchedItem != item && matchedItem.getZ() >= minZ));
            });
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(
                this.all,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.all = data.all;
            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);

            for (int id : WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                HabboItem item = room.getHabboItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length >= 1) {
                this.all = (data[0].equals("1"));

                if (data.length == 2) {
                    for (int id : WiredFurniConditionInputGuard.parseLegacyItemIds(data[1], WiredManager.MAXIMUM_FURNI_SELECTION)) {
                        HabboItem item = room.getHabboItem(id);

                        if (item != null)
                            this.items.add(item);
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, !this.items.isEmpty());
    }

    @Override
    public void onPickUp() {
        this.all = false;
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.all ? 1 : 0);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 1) return false;
        int[] params = settings.getIntParams();
        this.all = params[0] == 1;
        this.furniSource = (params.length > 1) ? WiredFurniConditionInputGuard.normalizeFurniSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, count > 0);

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room == null) return false;

            for (int i = 0; i < count; i++) {
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

                if (item != null)
                    this.items.add(item);
            }
        }
        return true;
    }

    private void refresh() {
        THashSet<HabboItem> items = new THashSet<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (room.getHabboItem(item.getId()) == null)
                    items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
    }

    @Override
    public WiredConditionOperator operator() {
        // NICE TRY BUT THAT'S NOT HOW IT WORKS. NOTHING IN HABBO IS AN "OR" CONDITION - EVERY CONDITION MUST BE SUCCESS FOR THE STACK TO EXECUTE, BUT LET'S LEAVE IT IMPLEMENTED FOR PLUGINS TO USE.
        //return this.all ? WiredConditionOperator.AND : WiredConditionOperator.OR;
        return WiredConditionOperator.AND;
    }

    static class JsonData {
        boolean all;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(boolean all, List<Integer> itemIds, int furniSource) {
            this.all = all;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
