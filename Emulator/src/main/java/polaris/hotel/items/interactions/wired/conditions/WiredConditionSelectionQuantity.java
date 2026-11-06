package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;

public class WiredConditionSelectionQuantity extends InteractionWiredCondition
        implements polaris.hotel.wired.protocol.WiredOfficialSources {
    private static final int COMPARISON_LESS_THAN = 0;
    private static final int COMPARISON_EQUAL = 1;
    private static final int COMPARISON_GREATER_THAN = 2;

    private static final int SOURCE_GROUP_USERS = 0;
    private static final int SOURCE_GROUP_FURNI = 1;
    private static final int SOURCE_USER_TRIGGER = 0;
    private static final int SOURCE_USER_SIGNAL = 1;
    private static final int SOURCE_USER_CLICKED = 2;
    private static final int SOURCE_FURNI_TRIGGER = 3;
    private static final int SOURCE_FURNI_PICKED = 4;
    private static final int SOURCE_FURNI_SIGNAL = 5;

    public static final WiredConditionType type = WiredConditionType.SLC_QUANTITY;

    private final Set<UserItem> items;
    private int comparison = COMPARISON_EQUAL;
    private int quantity = 0;
    private int sourceGroup = SOURCE_GROUP_USERS;
    private int sourceType = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredConditionSelectionQuantity(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredConditionSelectionQuantity(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        boolean pickMode = this.sourceGroup == SOURCE_GROUP_FURNI && this.sourceType == WiredSourceUtil.SOURCE_SELECTED;

        message.appendBoolean(pickMode);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(pickMode ? this.items.size() : 0);
        if (pickMode) {
            for (UserItem item : this.items) {
                message.appendInt(item.getId());
            }
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        // Official layout: [merged, amount (0-100), comparison (0 less, 1 equal, 2 more)], what is counted in
        // the official furni or user source slot.
        message.appendInt(3);
        message.appendInt(0);
        message.appendInt(this.quantity);
        message.appendInt(this.comparison);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();

        this.quantity = (params.length > 1) ? this.normalizeQuantity(params[1]) : 0;
        this.comparison = (params.length > 2) ? this.normalizeComparison(params[2]) : COMPARISON_EQUAL;
        this.items.clear();

        // What is counted: the furni slot when one is sent, otherwise the user slot.
        if (settings.getFurniSourceTypes().length > 0) {
            this.sourceGroup = SOURCE_GROUP_FURNI;
            this.sourceType = this.normalizeSourceType(SOURCE_GROUP_FURNI, settings.getFurniSourceTypes()[0]);
        } else {
            this.sourceGroup = SOURCE_GROUP_USERS;
            this.sourceType = this.normalizeSourceType(
                    SOURCE_GROUP_USERS,
                    settings.getUserSourceTypes().length > 0
                            ? settings.getUserSourceTypes()[0]
                            : WiredSourceUtil.SOURCE_TRIGGER);
        }

        if (this.sourceGroup != SOURCE_GROUP_FURNI || this.sourceType != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        for (int itemId : settings.getFurniIds()) {
            UserItem item = room.getUserItem(itemId);

            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        int count = this.resolveCount(ctx);

        switch (this.comparison) {
            case COMPARISON_LESS_THAN:
                return count < this.quantity;
            case COMPARISON_GREATER_THAN:
                return count > this.quantity;
            default:
                return count == this.quantity;
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh(Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));

        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.comparison,
                        this.quantity,
                        this.sourceGroup,
                        this.sourceType,
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList())));
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

            if (data == null) {
                return;
            }

            this.comparison = this.normalizeComparison(data.comparison);
            this.quantity = this.normalizeQuantity(data.quantity);
            this.sourceGroup = this.normalizeSourceGroup(data.sourceGroup);
            this.sourceType = this.normalizeSourceType(this.sourceGroup, data.sourceType);
            this.loadSelectedItems(data.itemIds, room);
            return;
        }

        String[] parts = wiredData.split("\t");

        try {
            if (parts.length > 0) {
                this.comparison = this.normalizeComparison(Integer.parseInt(parts[0]));
            }
            if (parts.length > 1) {
                this.quantity = this.normalizeQuantity(Integer.parseInt(parts[1]));
            }
            if (parts.length > 2) {
                this.sourceGroup = this.normalizeSourceGroup(Integer.parseInt(parts[2]));
            }
            if (parts.length > 3) {
                this.sourceType = this.normalizeSourceType(this.sourceGroup, Integer.parseInt(parts[3]));
            }
        } catch (NumberFormatException ignored) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.comparison = COMPARISON_EQUAL;
        this.quantity = 0;
        this.sourceGroup = SOURCE_GROUP_USERS;
        this.sourceType = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public int[] officialFurniSources() {
        return this.sourceGroup == SOURCE_GROUP_FURNI ? new int[] {this.sourceType} : new int[0];
    }

    @Override
    public int[] officialUserSources() {
        return this.sourceGroup == SOURCE_GROUP_USERS ? new int[] {this.sourceType} : new int[0];
    }

    private int resolveCount(WiredContext ctx) {
        if (this.sourceGroup == SOURCE_GROUP_FURNI) {
            List<UserItem> items = WiredSourceUtil.resolveItems(ctx, this.sourceType, this.items);

            return items.size();
        }

        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, this.sourceType);

        return users.size();
    }

    private int normalizeComparison(int value) {
        switch (value) {
            case COMPARISON_LESS_THAN:
            case COMPARISON_GREATER_THAN:
                return value;
            default:
                return COMPARISON_EQUAL;
        }
    }

    private int normalizeQuantity(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private int normalizeSourceGroup(int value) {
        return (value == SOURCE_GROUP_FURNI) ? SOURCE_GROUP_FURNI : SOURCE_GROUP_USERS;
    }

    private int normalizeSourceType(int group, int value) {
        if (group == SOURCE_GROUP_USERS) {
            switch (value) {
                case WiredSourceUtil.SOURCE_CLICKED_USER:
                case WiredSourceUtil.SOURCE_SIGNAL:
                case WiredSourceUtil.SOURCE_SELECTOR:
                    return value;
                default:
                    return WiredSourceUtil.SOURCE_TRIGGER;
            }
        }

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

    private int getSourceSelection() {
        if (this.sourceGroup == SOURCE_GROUP_FURNI) {
            switch (this.sourceType) {
                case WiredSourceUtil.SOURCE_SELECTED:
                    return SOURCE_FURNI_PICKED;
                case WiredSourceUtil.SOURCE_SIGNAL:
                    return SOURCE_FURNI_SIGNAL;
                default:
                    return SOURCE_FURNI_TRIGGER;
            }
        }

        switch (this.sourceType) {
            case WiredSourceUtil.SOURCE_CLICKED_USER:
                return SOURCE_USER_CLICKED;
            case WiredSourceUtil.SOURCE_SIGNAL:
                return SOURCE_USER_SIGNAL;
            default:
                return SOURCE_USER_TRIGGER;
        }
    }

    private void setSourceSelection(int value) {
        switch (value) {
            case SOURCE_USER_SIGNAL:
                this.sourceGroup = SOURCE_GROUP_USERS;
                this.sourceType = WiredSourceUtil.SOURCE_SIGNAL;
                break;
            case SOURCE_USER_CLICKED:
                this.sourceGroup = SOURCE_GROUP_USERS;
                this.sourceType = WiredSourceUtil.SOURCE_CLICKED_USER;
                break;
            case SOURCE_FURNI_TRIGGER:
                this.sourceGroup = SOURCE_GROUP_FURNI;
                this.sourceType = WiredSourceUtil.SOURCE_TRIGGER;
                break;
            case SOURCE_FURNI_PICKED:
                this.sourceGroup = SOURCE_GROUP_FURNI;
                this.sourceType = WiredSourceUtil.SOURCE_SELECTED;
                break;
            case SOURCE_FURNI_SIGNAL:
                this.sourceGroup = SOURCE_GROUP_FURNI;
                this.sourceType = WiredSourceUtil.SOURCE_SIGNAL;
                break;
            default:
                this.sourceGroup = SOURCE_GROUP_USERS;
                this.sourceType = WiredSourceUtil.SOURCE_TRIGGER;
                break;
        }
    }

    private void loadSelectedItems(List<Integer> itemIds, Room room) {
        this.items.clear();

        if (itemIds == null || room == null) {
            return;
        }

        for (Integer itemId : itemIds) {
            UserItem item = room.getUserItem(itemId);

            if (item != null) {
                this.items.add(item);
            }
        }
    }

    private void refresh(Room room) {
        if (room == null || this.items.isEmpty()) {
            return;
        }

        Set<UserItem> itemsToRemove = new HashSet<>();

        for (UserItem item : this.items) {
            if (item == null || room.getUserItem(item.getId()) == null) {
                itemsToRemove.add(item);
            }
        }

        for (UserItem item : itemsToRemove) {
            this.items.remove(item);
        }
    }

    static class JsonData {
        int comparison;
        int quantity;
        int sourceGroup;
        int sourceType;
        List<Integer> itemIds;

        public JsonData(int comparison, int quantity, int sourceGroup, int sourceType, List<Integer> itemIds) {
            this.comparison = comparison;
            this.quantity = quantity;
            this.sourceGroup = sourceGroup;
            this.sourceType = sourceType;
            this.itemIds = itemIds;
        }
    }
}
