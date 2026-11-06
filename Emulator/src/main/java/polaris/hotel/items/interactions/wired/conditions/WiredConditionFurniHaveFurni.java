package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;

public class WiredConditionFurniHaveFurni extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.FURNI_HAS_FURNI;

    private boolean all;
    private Set<UserItem> items;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredConditionFurniHaveFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredConditionFurniHaveFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        Room room = ctx.room();

        this.refresh();

        List<UserItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        // Nothing to check has nothing on it, as in Habbo; the NOT box passes instead.
        if (targets.isEmpty()) return false;

        if (room.getLayout() == null) return false;

        if (this.all) {
            return targets.stream().allMatch(item -> {
                if (item == null) return false;
                RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
                if (baseTile == null) return false;
                double minZ = item.getZ() + Item.getCurrentHeight(item);
                Set<RoomTile> occupiedTiles = room.getLayout()
                        .getTilesAt(
                                baseTile,
                                item.getBaseItem().getWidth(),
                                item.getBaseItem().getLength(),
                                item.getRotation());
                if (occupiedTiles == null) return false;
                return occupiedTiles.stream()
                        .anyMatch(tile -> tile != null
                                && room.getItemsAt(tile).stream()
                                        .anyMatch(matchedItem -> matchedItem != item && matchedItem.getZ() >= minZ));
            });
        } else {
            return targets.stream().anyMatch(item -> {
                if (item == null) return false;
                RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
                if (baseTile == null) return false;
                double minZ = item.getZ() + Item.getCurrentHeight(item);
                Set<RoomTile> occupiedTiles = room.getLayout()
                        .getTilesAt(
                                baseTile,
                                item.getBaseItem().getWidth(),
                                item.getBaseItem().getLength(),
                                item.getRotation());
                if (occupiedTiles == null) return false;
                return occupiedTiles.stream()
                        .anyMatch(tile -> tile != null
                                && room.getItemsAt(tile).stream()
                                        .anyMatch(matchedItem -> matchedItem != item && matchedItem.getZ() >= minZ));
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
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.all,
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.furniSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data;
            try {
                data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            } catch (RuntimeException exception) {
                this.onPickUp();
                return;
            }

            if (data == null) {
                return;
            }

            this.all = data.all;
            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);

            for (int id :
                    WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                UserItem item = room.getUserItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length >= 1) {
                this.all = (data[0].equals("1"));

                if (data.length == 2) {
                    for (int id : WiredFurniConditionInputGuard.parseLegacyItemIds(
                            data[1], WiredManager.MAXIMUM_FURNI_SELECTION)) {
                        UserItem item = room.getUserItem(id);

                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
        this.furniSource =
                WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, !this.items.isEmpty());
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.all = false;
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

        for (UserItem item : this.items) message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.all ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 1) return false;

        int[] params = settings.getIntParams();
        this.all = params[0] == 1;
        // Official layout: param 0 "all"; the furni in the official source slot.
        this.furniSource = settings.getFurniSourceTypes().length > 0
                ? WiredFurniConditionInputGuard.normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, count > 0);

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room == null) return false;

            for (int i = 0; i < count; i++) {
                UserItem item = room.getUserItem(settings.getFurniIds()[i]);

                if (item != null) this.items.add(item);
            }
        }
        return true;
    }

    int normalizeFurniSource(int value) {
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

    private void refresh() {
        Set<UserItem> items = new HashSet<>();

        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return;
        }

        Room room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            items.addAll(this.items);
        } else {
            for (UserItem item : this.items) {
                if (room.getUserItem(item.getId()) == null) items.add(item);
            }
        }

        for (UserItem item : items) {
            this.items.remove(item);
        }
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
