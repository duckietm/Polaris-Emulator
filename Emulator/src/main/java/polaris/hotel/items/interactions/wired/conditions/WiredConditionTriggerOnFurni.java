package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
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
import polaris.hotel.wired.WiredConditionOperator;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

public class WiredConditionTriggerOnFurni extends InteractionWiredCondition implements WiredOfficialQuantifier {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.TRIGGER_ON_FURNI;

    protected Set<UserItem> items = new LinkedHashSet<>();
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int quantifier = QUANTIFIER_ALL;

    public WiredConditionTriggerOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTriggerOnFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        this.refresh();

        List<RoomUnit> userTargets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (userTargets.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier != QUANTIFIER_ANY);

        List<UserItem> itemTargets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (itemTargets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            return this.isAnyUserOnFurni(userTargets, itemTargets, ctx.room());
        }

        return this.areAllUsersOnFurni(userTargets, itemTargets, ctx.room());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    protected boolean isAnyUserOnFurni(Collection<RoomUnit> users, Collection<UserItem> items, Room room) {
        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) continue;
            Set<UserItem> itemsAtUser = room.getItemsAt(wiredLocationOf(roomUnit));
            if (items.stream().anyMatch(itemsAtUser::contains)) {
                return true;
            }
        }
        return false;
    }

    protected boolean areAllUsersOnFurni(Collection<RoomUnit> users, Collection<UserItem> items, Room room) {
        for (RoomUnit roomUnit : users) {
            if (roomUnit == null) {
                return false;
            }

            Set<UserItem> itemsAtUser = room.getItemsAt(wiredLocationOf(roomUnit));
            if (itemsAtUser == null || items.stream().noneMatch(itemsAtUser::contains)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.furniSource,
                        this.userSource,
                        this.quantifier));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = WiredFurniConditionInputGuard.normalizeFurniSource(data.furniSource);
            this.userSource = WiredFurniConditionInputGuard.normalizeUserSource(data.userSource);
            this.quantifier = this.normalizeQuantifier(data.quantifier);

            for (int id :
                    WiredFurniConditionInputGuard.sanitizeItemIds(data.itemIds, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                UserItem item = room.getUserItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            for (int id :
                    WiredFurniConditionInputGuard.parseLegacyItemIds(wiredData, WiredManager.MAXIMUM_FURNI_SELECTION)) {
                UserItem item = room.getUserItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.quantifier = QUANTIFIER_ALL;
        }
        this.furniSource =
                WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, !this.items.isEmpty());
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
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
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        int[] params = settings.getIntParams();
        // Official layout: no int params of its own, the furni and users in the official source slots;
        // the quantifier (0 all, 1 any) in its slot.
        this.furniSource = settings.getFurniSourceTypes().length > 0
                ? WiredFurniConditionInputGuard.normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = settings.getUserSourceTypes().length > 0
                ? WiredFurniConditionInputGuard.normalizeUserSource(settings.getUserSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());

        this.furniSource = WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(this.furniSource, count > 0);

        this.items.clear();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

            if (room != null) {
                for (int i = 0; i < count; i++) {
                    UserItem item = room.getUserItem(settings.getFurniIds()[i]);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        }

        return true;
    }

    protected void refresh() {
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
                if (item.getRoomId() != room.getId()) items.add(item);
            }
        }

        this.items.removeAll(items);
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    protected int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
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

    int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    static class JsonData {
        List<Integer> itemIds;
        int furniSource;
        int userSource;
        int quantifier;

        public JsonData(List<Integer> itemIds, int furniSource, int userSource, int quantifier) {
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.getQuantifier();
    }

    /** Where the condition counts a unit: the tile of the step being announced, or its current tile. */
    private static RoomTile wiredLocationOf(RoomUnit unit) {
        RoomTile stepping = unit.getWiredLocation();
        return stepping != null ? stepping : unit.getCurrentLocation();
    }
}
