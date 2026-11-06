package polaris.hotel.items.interactions.wired.conditions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialQuantifier;
import polaris.messages.ServerMessage;

public class WiredConditionFurniNotInRange extends InteractionWiredCondition implements WiredOfficialQuantifier {
    private static final int COMPARISON_LESS = 0;
    private static final int COMPARISON_EQUAL = 1;
    private static final int COMPARISON_GREATER = 2;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.FURNI_RANGE;

    private final HashSet<UserItem> items;
    private int comparison = COMPARISON_EQUAL;
    private double radius = 0.0D;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionFurniNotInRange(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new HashSet<>();
    }

    public WiredConditionFurniNotInRange(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new HashSet<>();
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        this.refresh(room);

        RoomLayout layout = room.getLayout();
        if (layout == null) {
            return false;
        }

        UserItem triggerItem = ctx.triggerItem();
        if (triggerItem == null) {
            return true;
        }

        RoomTile origin = layout.getTile(triggerItem.getX(), triggerItem.getY());
        if (origin == null) {
            return true;
        }

        List<UserItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        // Its own reading ("all are outside" / "any is outside"), so all holds over none.
        if (targets.isEmpty()) {
            return WiredQuantifier.overEmpty(this.quantifier != QUANTIFIER_ANY);
        }

        if (this.quantifier == QUANTIFIER_ANY) {
            return targets.stream().anyMatch(item -> this.isOutsideRange(origin, layout, item));
        }

        return targets.stream().allMatch(item -> this.isOutsideRange(origin, layout, item));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.comparison,
                        this.formatRadius(this.radius),
                        this.furniSource,
                        this.quantifier,
                        this.items.stream().map(UserItem::getId).toList()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.comparison = COMPARISON_EQUAL;
        this.radius = 0.0D;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

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

        this.comparison = this.normalizeComparison(data.comparison);
        this.radius = this.parseRadiusOrDefault(data.radius);
        this.furniSource = this.normalizeFurniSource(data.furniSource);
        this.quantifier = this.normalizeQuantifier(data.quantifier);

        if (data.itemIds == null) {
            return;
        }

        for (Integer id : data.itemIds) {
            if (id == null) {
                continue;
            }

            UserItem item = room.getUserItem(id);
            if (item != null) {
                this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.comparison = COMPARISON_EQUAL;
        this.radius = 0.0D;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (UserItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.formatRadius(this.radius));
        message.appendInt(2);
        message.appendInt(this.comparison);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.comparison = (params.length > 0) ? this.normalizeComparison(params[0]) : COMPARISON_EQUAL;
        this.furniSource = (params.length > 1) ? this.normalizeFurniSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = this.normalizeQuantifier(settings.getQuantifierCode());
        this.radius = this.parseRadiusOrDefault(settings.getStringParam());

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
                UserItem item = room.getUserItem(itemId);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        return true;
    }

    /**
     * The dialog offers three operators against the radius, and until now every one of them behaved
     * as "within". They now mean what they say, with {@code equals} keeping the historical inclusive
     * reading so a box saved before this change evaluates exactly as it did.
     */
    private boolean matchesRadius(double distance) {
        return switch (this.comparison) {
            case COMPARISON_LESS -> distance < this.radius;
            case COMPARISON_GREATER -> distance > this.radius;
            default -> distance <= this.radius;
        };
    }

    private boolean isOutsideRange(RoomTile origin, RoomLayout layout, UserItem item) {
        if (item == null) {
            return true;
        }

        RoomTile tile = layout.getTile(item.getX(), item.getY());
        if (tile == null) {
            return true;
        }

        return !this.matchesRadius(origin.distance(tile));
    }

    private void refresh(Room room) {
        var remove = new HashSet<UserItem>();

        for (UserItem item : this.items) {
            if (room.getUserItem(item.getId()) == null) {
                remove.add(item);
            }
        }

        for (UserItem item : remove) {
            this.items.remove(item);
        }
    }

    int normalizeComparison(int value) {
        if (value < COMPARISON_LESS || value > COMPARISON_GREATER) {
            return COMPARISON_EQUAL;
        }

        return value;
    }

    int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    int normalizeFurniSource(int value) {
        return switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL,
                    WiredSourceUtil.SOURCE_TRIGGER -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    double normalizeRadius(double value) {
        double clampedValue = Math.max(0.0D, value);
        return BigDecimal.valueOf(clampedValue)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    double parseRadiusOrDefault(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0D;
        }

        try {
            return this.normalizeRadius(new BigDecimal(value.trim()).doubleValue());
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    String formatRadius(double value) {
        BigDecimal decimal = BigDecimal.valueOf(this.normalizeRadius(value)).stripTrailingZeros();
        return (decimal.scale() < 0 ? decimal.setScale(0, RoundingMode.DOWN) : decimal).toPlainString();
    }

    static class JsonData {
        int comparison;
        String radius;
        int furniSource;
        int quantifier;
        List<Integer> itemIds;

        public JsonData(int comparison, String radius, int furniSource, int quantifier, List<Integer> itemIds) {
            this.comparison = comparison;
            this.radius = radius;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
            this.itemIds = itemIds;
        }
    }

    @Override
    public int officialQuantifierCode() {
        return this.quantifier;
    }
}
