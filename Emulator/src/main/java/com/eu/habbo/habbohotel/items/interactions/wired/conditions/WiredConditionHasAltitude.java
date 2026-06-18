package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionHasAltitude extends InteractionWiredCondition {
    private static final int COMPARISON_LESS = 0;
    private static final int COMPARISON_EQUAL = 1;
    private static final int COMPARISON_GREATER = 2;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.HAS_ALTITUDE;

    private final THashSet<HabboItem> items;
    private int comparison = COMPARISON_EQUAL;
    private double altitude = 0.0D;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionHasAltitude(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredConditionHasAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        this.refresh(room);

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (targets.isEmpty()) {
            return false;
        }

        if (this.quantifier == QUANTIFIER_ANY) {
            return targets.stream().anyMatch(this::matchesAltitude);
        }

        return targets.stream().allMatch(this::matchesAltitude);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.comparison,
                this.formatAltitude(this.altitude),
                this.furniSource,
                this.quantifier,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.comparison = COMPARISON_EQUAL;
        this.altitude = 0.0D;
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
        this.altitude = this.parseAltitudeOrDefault(data.altitude);
        this.furniSource = this.normalizeFurniSource(data.furniSource);
        this.quantifier = this.normalizeQuantifier(data.quantifier);

        if (data.itemIds == null) {
            return;
        }

        for (Integer id : data.itemIds) {
            if (id == null) {
                continue;
            }

            HabboItem item = room.getHabboItem(id);
            if (item != null) {
                this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.comparison = COMPARISON_EQUAL;
        this.altitude = 0.0D;
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

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.formatAltitude(this.altitude));
        message.appendInt(3);
        message.appendInt(this.comparison);
        message.appendInt(this.furniSource);
        message.appendInt(this.quantifier);
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
        this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2]) : QUANTIFIER_ALL;
        this.altitude = this.parseAltitudeOrDefault(settings.getStringParam());

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

        return true;
    }

    private boolean matchesAltitude(HabboItem item) {
        if (item == null) {
            return false;
        }

        double normalizedAltitude = this.normalizeAltitude(item.getZ());

        switch (this.comparison) {
            case COMPARISON_LESS:
                return normalizedAltitude < this.altitude;
            case COMPARISON_GREATER:
                return normalizedAltitude > this.altitude;
            default:
                return BigDecimal.valueOf(normalizedAltitude).compareTo(BigDecimal.valueOf(this.altitude)) == 0;
        }
    }

    private void refresh(Room room) {
        THashSet<HabboItem> remove = new THashSet<>();

        for (HabboItem item : this.items) {
            if (room.getHabboItem(item.getId()) == null) {
                remove.add(item);
            }
        }

        for (HabboItem item : remove) {
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

    double normalizeAltitude(double value) {
        double clampedValue = Math.max(0.0D, Math.min(Room.MAXIMUM_FURNI_HEIGHT, value));
        return BigDecimal.valueOf(clampedValue).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    double parseAltitudeOrDefault(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0D;
        }

        try {
            return this.normalizeAltitude(new BigDecimal(value.trim()).doubleValue());
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    String formatAltitude(double value) {
        BigDecimal decimal = BigDecimal.valueOf(this.normalizeAltitude(value)).stripTrailingZeros();
        return (decimal.scale() < 0 ? decimal.setScale(0, RoundingMode.DOWN) : decimal).toPlainString();
    }

    static class JsonData {
        int comparison;
        String altitude;
        int furniSource;
        int quantifier;
        List<Integer> itemIds;

        public JsonData(int comparison, String altitude, int furniSource, int quantifier, List<Integer> itemIds) {
            this.comparison = comparison;
            this.altitude = altitude;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
            this.itemIds = itemIds;
        }
    }
}
