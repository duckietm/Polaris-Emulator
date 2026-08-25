package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredConditionVariableAgeMatch extends WiredConditionHasVariable {
    public static final WiredConditionType type = WiredConditionType.VAR_AGE_MATCH;

    private static final int TARGET_CONTEXT = 2;
    private static final int COMPARE_VALUE_CREATED = 0;
    private static final int COMPARE_VALUE_UPDATED = 1;
    private static final int COMPARISON_LOWER_THAN = 0;
    private static final int COMPARISON_HIGHER_THAN = 2;
    private static final int DURATION_UNIT_MILLISECONDS = 0;
    private static final int DURATION_UNIT_SECONDS = 1;
    private static final int DURATION_UNIT_MINUTES = 2;
    private static final int DURATION_UNIT_HOURS = 3;
    private static final int DURATION_UNIT_DAYS = 4;
    private static final int DURATION_UNIT_WEEKS = 5;
    private static final int DURATION_UNIT_MONTHS = 6;
    private static final int DURATION_UNIT_YEARS = 7;
    static final int MAX_DURATION_AMOUNT = 1_000_000;

    protected int compareValue = COMPARE_VALUE_CREATED;
    protected int comparison = COMPARISON_LOWER_THAN;
    protected int durationAmount = 0;
    protected int durationUnit = DURATION_UNIT_SECONDS;

    public WiredConditionVariableAgeMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableAgeMatch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        List<HabboItem> serializedItems = new ArrayList<>();
        if (this.targetType == TARGET_FURNI && this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            serializedItems.addAll(this.selectedItems);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(serializedItems.size());

        for (HabboItem item : serializedItems) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken == null ? "" : this.variableToken);
        message.appendInt(8);
        message.appendInt(this.targetType);
        message.appendInt(this.compareValue);
        message.appendInt(this.comparison);
        message.appendInt(this.durationAmount);
        message.appendInt(this.durationUnit);
        message.appendInt(this.userSource);
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
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        this.targetType = (params.length > 0) ? normalizeTargetTypeExtended(params[0]) : TARGET_USER;
        this.compareValue = (params.length > 1) ? normalizeCompareValue(params[1]) : COMPARE_VALUE_CREATED;
        this.comparison = (params.length > 2) ? normalizeComparison(params[2]) : COMPARISON_LOWER_THAN;
        this.durationAmount = normalizeDurationAmount((params.length > 3) ? params[3] : 0);
        this.durationUnit = (params.length > 4) ? normalizeDurationUnit(params[4]) : DURATION_UNIT_SECONDS;
        this.userSource = (params.length > 5) ? normalizeUserSource(params[5]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = (params.length > 6) ? normalizeFurniSource(params[6]) : WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = (params.length > 7) ? normalizeQuantifier(params[7]) : QUANTIFIER_ALL;
        this.setVariableToken(normalizeVariableToken(settings.getStringParam()));

        if (!this.isValidSource(room)) {
            return false;
        }

        this.selectedItems.clear();

        if (this.targetType == TARGET_FURNI && this.furniSource == WiredSourceUtil.SOURCE_SELECTED && room != null) {
            int[] furniIds = settings.getFurniIds();
            if (furniIds.length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
                return false;
            }

            for (int furniId : furniIds) {
                HabboItem item = room.getHabboItem(furniId);

                if (item != null) {
                    this.selectedItems.add(item);
                }
            }
        }

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) {
            return false;
        }

        Room room = ctx.room();

        if (room == null || this.variableToken == null || this.variableToken.isEmpty() || !isCustomVariableToken(this.variableToken)) {
            return false;
        }

        long thresholdMs = durationToMillis(this.durationAmount, this.durationUnit);

        return switch (this.targetType) {
            case TARGET_FURNI -> this.evaluateFurniTargets(ctx, room, thresholdMs);
            case TARGET_ROOM -> this.evaluateRoomTarget(room, thresholdMs);
            case TARGET_CONTEXT -> this.evaluateContextTarget(ctx, room, thresholdMs);
            default -> this.evaluateUserTargets(ctx, room, thresholdMs);
        };
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();

        List<Integer> itemIds = new ArrayList<>();
        for (HabboItem item : this.selectedItems) {
            if (item != null) itemIds.add(item.getId());
        }

        return WiredManager.getGson().toJson(new JsonData(
            itemIds,
            this.targetType,
            this.variableToken,
            this.variableItemId,
            this.compareValue,
            this.comparison,
            this.durationAmount,
            this.durationUnit,
            this.userSource,
            this.furniSource,
            this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) return;

        try {
            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

                if (data == null) return;

                this.targetType = normalizeTargetTypeExtended(data.targetType);
                this.compareValue = normalizeCompareValue(data.compareValue);
                this.comparison = normalizeComparison(data.comparison);
                this.durationAmount = normalizeDurationAmount(data.durationAmount);
                this.durationUnit = normalizeDurationUnit(data.durationUnit);
                this.userSource = normalizeUserSource(data.userSource);
                this.furniSource = normalizeFurniSource(data.furniSource);
                this.quantifier = normalizeQuantifier(data.quantifier);
                this.setVariableToken(normalizeVariableToken((data.variableToken != null) ? data.variableToken : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));

                if (room != null && data.itemIds != null) {
                    for (Integer itemId : data.itemIds) {
                        if (itemId == null || itemId <= 0) continue;

                        HabboItem item = room.getHabboItem(itemId);
                        if (item != null) this.selectedItems.add(item);
                    }
                }

                return;
            }

            this.setVariableToken(normalizeVariableToken(wiredData));
        } catch (Exception e) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.compareValue = COMPARE_VALUE_CREATED;
        this.comparison = COMPARISON_LOWER_THAN;
        this.durationAmount = 0;
        this.durationUnit = DURATION_UNIT_SECONDS;
    }

    private boolean evaluateUserTargets(WiredContext ctx, Room room, long thresholdMs) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            for (RoomUnit roomUnit : targets) {
                if (this.matchesAge(this.readUserAgeMs(room, roomUnit), thresholdMs)) return true;
            }

            return false;
        }

        for (RoomUnit roomUnit : targets) {
            if (!this.matchesAge(this.readUserAgeMs(room, roomUnit), thresholdMs)) return false;
        }

        return true;
    }

    private boolean evaluateFurniTargets(WiredContext ctx, Room room, long thresholdMs) {
        this.refresh();

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedItems);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            for (HabboItem item : targets) {
                if (this.matchesAge(this.readFurniAgeMs(room, item), thresholdMs)) return true;
            }

            return false;
        }

        for (HabboItem item : targets) {
            if (!this.matchesAge(this.readFurniAgeMs(room, item), thresholdMs)) return false;
        }

        return true;
    }

    private boolean evaluateRoomTarget(Room room, long thresholdMs) {
        return this.matchesAge(this.readRoomAgeMs(room), thresholdMs);
    }

    private boolean evaluateContextTarget(WiredContext ctx, Room room, long thresholdMs) {
        return this.matchesAge(this.readContextAgeMs(ctx, room), thresholdMs);
    }

    private Long readUserAgeMs(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) return null;

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || !room.getUserVariableManager().hasVariable(habbo.getHabboInfo().getId(), this.variableItemId)) return null;

        int timestamp = (this.compareValue == COMPARE_VALUE_UPDATED)
            ? room.getUserVariableManager().getUpdatedAt(habbo.getHabboInfo().getId(), this.variableItemId)
            : room.getUserVariableManager().getCreatedAt(habbo.getHabboInfo().getId(), this.variableItemId);

        return timestampToAgeMs(timestamp);
    }

    private Long readFurniAgeMs(Room room, HabboItem item) {
        if (room == null || item == null || !room.getFurniVariableManager().hasVariable(item.getId(), this.variableItemId)) return null;

        int timestamp = (this.compareValue == COMPARE_VALUE_UPDATED)
            ? room.getFurniVariableManager().getUpdatedAt(item.getId(), this.variableItemId)
            : room.getFurniVariableManager().getCreatedAt(item.getId(), this.variableItemId);

        return timestampToAgeMs(timestamp);
    }

    private Long readRoomAgeMs(Room room) {
        if (room == null) return null;
        if (this.compareValue == COMPARE_VALUE_CREATED) return null;

        int timestamp = room.getRoomVariableManager().getUpdatedAt(this.variableItemId);
        return timestampToAgeMs(timestamp);
    }

    private Long readContextAgeMs(WiredContext ctx, Room room) {
        if (ctx == null || room == null || !WiredContextVariableSupport.hasVariable(ctx, this.variableItemId)) return null;

        int timestamp = (this.compareValue == COMPARE_VALUE_UPDATED)
            ? WiredContextVariableSupport.getUpdatedAt(ctx, this.variableItemId)
            : WiredContextVariableSupport.getCreatedAt(ctx, this.variableItemId);

        return timestampToAgeMs(timestamp);
    }

    private boolean matchesAge(Long ageMs, long thresholdMs) {
        if (ageMs == null) return false;

        return switch (this.comparison) {
            case COMPARISON_HIGHER_THAN -> ageMs > thresholdMs;
            default -> ageMs < thresholdMs;
        };
    }

    private boolean isValidSource(Room room) {
        if (room == null || !isCustomVariableToken(this.variableToken)) return false;

        return switch (this.targetType) {
            case TARGET_FURNI -> room.getFurniVariableManager().getDefinitionInfo(this.variableItemId) != null;
            case TARGET_CONTEXT -> WiredContextVariableSupport.getDefinitionInfo(room, this.variableItemId) != null;
            case TARGET_ROOM -> {
                WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(this.variableItemId);
                yield this.compareValue == COMPARE_VALUE_UPDATED && definition != null;
            }
            default -> room.getUserVariableManager().getDefinitionInfo(this.variableItemId) != null;
        };
    }

    private static Long timestampToAgeMs(int timestampSeconds) {
        if (timestampSeconds <= 0) return null;

        long timestampMs = (timestampSeconds * 1000L);
        return Math.max(0L, System.currentTimeMillis() - timestampMs);
    }

    static long durationToMillis(int amount, int unit) {
        long normalizedAmount = normalizeDurationAmount(amount);

        return switch (unit) {
            case DURATION_UNIT_MILLISECONDS -> normalizedAmount;
            case DURATION_UNIT_MINUTES -> safeMultiply(normalizedAmount, 60_000L);
            case DURATION_UNIT_HOURS -> safeMultiply(normalizedAmount, 3_600_000L);
            case DURATION_UNIT_DAYS -> safeMultiply(normalizedAmount, 86_400_000L);
            case DURATION_UNIT_WEEKS -> safeMultiply(normalizedAmount, 604_800_000L);
            case DURATION_UNIT_MONTHS -> safeMultiply(normalizedAmount, 2_592_000_000L);
            case DURATION_UNIT_YEARS -> safeMultiply(normalizedAmount, 31_536_000_000L);
            default -> safeMultiply(normalizedAmount, 1_000L);
        };
    }

    private static long safeMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0L;
        if (left > (Long.MAX_VALUE / right)) return Long.MAX_VALUE;

        return left * right;
    }

    static int normalizeDurationAmount(int value) {
        return Math.max(0, Math.min(MAX_DURATION_AMOUNT, value));
    }

    static int normalizeTargetTypeExtended(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    static int normalizeCompareValue(int value) {
        return (value == COMPARE_VALUE_UPDATED) ? COMPARE_VALUE_UPDATED : COMPARE_VALUE_CREATED;
    }

    static int normalizeComparison(int value) {
        return (value == COMPARISON_HIGHER_THAN) ? COMPARISON_HIGHER_THAN : COMPARISON_LOWER_THAN;
    }

    static int normalizeDurationUnit(int value) {
        return switch (value) {
            case DURATION_UNIT_MILLISECONDS, DURATION_UNIT_SECONDS, DURATION_UNIT_MINUTES, DURATION_UNIT_HOURS,
                DURATION_UNIT_DAYS, DURATION_UNIT_WEEKS, DURATION_UNIT_MONTHS, DURATION_UNIT_YEARS -> value;
            default -> DURATION_UNIT_SECONDS;
        };
    }

    protected static class JsonData {
        List<Integer> itemIds;
        int targetType;
        String variableToken;
        int variableItemId;
        int compareValue;
        int comparison;
        int durationAmount;
        int durationUnit;
        int userSource;
        int furniSource;
        int quantifier;

        JsonData(List<Integer> itemIds, int targetType, String variableToken, int variableItemId, int compareValue, int comparison, int durationAmount, int durationUnit, int userSource, int furniSource, int quantifier) {
            this.itemIds = itemIds;
            this.targetType = targetType;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.compareValue = compareValue;
            this.comparison = comparison;
            this.durationAmount = durationAmount;
            this.durationUnit = durationUnit;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
        }
    }
}
