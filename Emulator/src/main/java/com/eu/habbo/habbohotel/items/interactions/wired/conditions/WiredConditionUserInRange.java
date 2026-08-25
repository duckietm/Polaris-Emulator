package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Passes when a resolved user (actor / clicked user / selector / signal) is within {@code radius}
 * tiles of the trigger furni. Structural mirror of {@link WiredConditionFurniInRange} but the targets
 * are USERS ({@link WiredSourceUtil#resolveUsers}) instead of furni, and each {@link RoomUnit}'s tile
 * distance to the trigger furni tile is tested. Reuses the HAS_ALTITUDE dialog (numeric field reused
 * as the radius + source + quantifier), so it needs no new client dialog. No furni are selected, so
 * the furni-picker portion of the dialog is left empty.
 */
public class WiredConditionUserInRange extends InteractionWiredCondition {
    private static final int COMPARISON_LESS = 0;
    private static final int COMPARISON_EQUAL = 1;
    private static final int COMPARISON_GREATER = 2;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.HAS_ALTITUDE;

    private int comparison = COMPARISON_EQUAL;
    private double radius = 0.0D;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionUserInRange(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserInRange(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        RoomLayout layout = room.getLayout();
        if (layout == null) {
            return false;
        }

        HabboItem triggerItem = ctx.triggerItem();
        if (triggerItem == null) {
            return false;
        }

        RoomTile origin = layout.getTile(triggerItem.getX(), triggerItem.getY());
        if (origin == null) {
            return false;
        }

        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) {
            return false;
        }

        if (this.quantifier == QUANTIFIER_ANY) {
            return targets.stream().anyMatch(unit -> this.isInsideRange(origin, unit));
        }

        return targets.stream().allMatch(unit -> this.isInsideRange(origin, unit));
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
                this.formatRadius(this.radius),
                this.userSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.comparison = COMPARISON_EQUAL;
        this.radius = 0.0D;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
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
        this.userSource = this.normalizeUserSource(data.userSource);
        this.quantifier = this.normalizeQuantifier(data.quantifier);
    }

    @Override
    public void onPickUp() {
        this.comparison = COMPARISON_EQUAL;
        this.radius = 0.0D;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(0);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.formatRadius(this.radius));
        message.appendInt(3);
        message.appendInt(this.comparison);
        message.appendInt(this.userSource);
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
        this.userSource = (params.length > 1) ? this.normalizeUserSource(params[1]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 2) ? this.normalizeQuantifier(params[2]) : QUANTIFIER_ALL;
        this.radius = this.parseRadiusOrDefault(settings.getStringParam());

        return true;
    }

    private boolean isInsideRange(RoomTile origin, RoomUnit unit) {
        if (unit == null) {
            return false;
        }

        RoomTile tile = unit.getCurrentLocation();
        if (tile == null) {
            return false;
        }

        return origin.distance(tile) <= this.radius;
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

    int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    double normalizeRadius(double value) {
        double clampedValue = Math.max(0.0D, value);
        return BigDecimal.valueOf(clampedValue).setScale(2, RoundingMode.HALF_UP).doubleValue();
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
        int userSource;
        int quantifier;

        public JsonData(int comparison, String radius, int userSource, int quantifier) {
            this.comparison = comparison;
            this.radius = radius;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
