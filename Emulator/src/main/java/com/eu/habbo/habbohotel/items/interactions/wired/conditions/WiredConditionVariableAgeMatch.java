package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
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
    /** The duration read from a variable (official, WIN63-202610121624), on the box's own selection. */
    protected boolean fromVariable = false;

    protected int durationVariableId = 0;
    protected int durationTarget = WiredValueVariable.TARGET_GLOBAL;

    public WiredConditionVariableAgeMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableAgeMatch(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        // Official layout: [target, comparison (0 less, 2 more), useUpdate, durationHigh, durationLow, unit,
        // fromVariable, durationTarget], the variable then a tab and the duration variable in the string,
        // the furni and users in the official source slots; our quantifier follows.
        message.appendString((this.variableToken == null ? "" : this.variableToken) + "\t" + this.durationVariableId);
        message.appendInt(9);
        message.appendInt(WiredVariableTargets.toOfficial(this.targetType));
        message.appendInt(this.comparison);
        message.appendInt(this.compareValue);
        message.appendInt(0);
        message.appendInt(this.durationAmount);
        message.appendInt(this.durationUnit);
        message.appendInt(this.fromVariable ? 1 : 0);
        message.appendInt(this.durationTarget);
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

        this.targetType = (params.length > 0)
                ? normalizeTargetTypeExtended(WiredVariableTargets.fromOfficial(params[0]))
                : TARGET_USER;
        this.comparison = (params.length > 1) ? normalizeComparison(params[1]) : COMPARISON_LOWER_THAN;
        this.compareValue = (params.length > 2) ? normalizeCompareValue(params[2]) : COMPARE_VALUE_CREATED;
        long duration = (params.length > 4) ? (((long) params[3]) << 32) | (params[4] & 0xFFFFFFFFL) : 0L;
        this.durationAmount = normalizeDurationAmount((int) Math.clamp(duration, 0, MAX_DURATION_AMOUNT));
        this.durationUnit = (params.length > 5) ? normalizeDurationUnit(params[5]) : DURATION_UNIT_SECONDS;
        this.durationTarget =
                WiredValueVariable.normalizeTarget((params.length > 7) ? params[7] : WiredValueVariable.TARGET_GLOBAL);
        this.quantifier = (params.length > 8) ? normalizeQuantifier(params[8]) : QUANTIFIER_ALL;
        this.userSource = settings.getUserSourceTypes().length > 0
                ? normalizeUserSource(settings.getUserSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = settings.getFurniSourceTypes().length > 0
                ? normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        String text = settings.getStringParam() == null ? "" : settings.getStringParam();
        int tab = text.indexOf('\t');
        this.setVariableToken(normalizeVariableToken(tab < 0 ? text : text.substring(0, tab)));
        this.durationVariableId = tab < 0 ? 0 : WiredValueVariable.parseId(text.substring(tab + 1));
        this.fromVariable = (params.length > 6) && params[6] == 1 && this.durationVariableId > 0;
        if (!this.fromVariable) this.durationVariableId = 0;

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

        if (room == null
                || this.variableToken == null
                || this.variableToken.isEmpty()
                || !isCustomVariableToken(this.variableToken)) {
            return false;
        }

        int amount = this.durationAmount;
        if (this.fromVariable) {
            Integer read = WiredValueVariable.read(
                    ctx,
                    room,
                    this.durationTarget,
                    this.durationVariableId,
                    this.furniSource,
                    this.selectedItems,
                    this.userSource);
            if (read == null) return false;
            amount = normalizeDurationAmount(read);
        }
        long thresholdMs = durationToMillis(amount, this.durationUnit);
        // A pointer is checked as the variable it stands for in this stack.
        int variableId = WiredPointerVariableSupport.resolve(ctx, room, this.variableItemId);
        if (variableId <= 0) return false;

        return switch (this.targetType) {
            case TARGET_FURNI -> this.evaluateFurniTargets(ctx, room, thresholdMs, variableId);
            case TARGET_ROOM -> this.evaluateRoomTarget(room, thresholdMs, variableId);
            case TARGET_CONTEXT -> this.evaluateContextTarget(ctx, room, thresholdMs, variableId);
            default -> this.evaluateUserTargets(ctx, room, thresholdMs, variableId);
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

        JsonData data = new JsonData(
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
                this.quantifier);
        data.fromVariable = this.fromVariable;
        data.durationVariableId = this.durationVariableId;
        data.durationTarget = this.durationTarget;
        return WiredManager.getGson().toJson(data);
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
                this.durationVariableId = Math.max(0, data.durationVariableId);
                this.fromVariable = data.fromVariable && this.durationVariableId > 0;
                this.durationTarget = data.durationTarget == null
                        ? WiredValueVariable.TARGET_GLOBAL
                        : WiredValueVariable.normalizeTarget(data.durationTarget);
                this.setVariableToken(normalizeVariableToken(
                        (data.variableToken != null)
                                ? data.variableToken
                                : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));

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
        this.fromVariable = false;
        this.durationVariableId = 0;
        this.durationTarget = WiredValueVariable.TARGET_GLOBAL;
        this.compareValue = COMPARE_VALUE_CREATED;
        this.comparison = COMPARISON_LOWER_THAN;
        this.durationAmount = 0;
        this.durationUnit = DURATION_UNIT_SECONDS;
    }

    private boolean evaluateUserTargets(WiredContext ctx, Room room, long thresholdMs, int variableId) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            for (RoomUnit roomUnit : targets) {
                if (this.matchesAge(this.readUserAgeMs(room, roomUnit, variableId), thresholdMs)) return true;
            }

            return false;
        }

        for (RoomUnit roomUnit : targets) {
            if (!this.matchesAge(this.readUserAgeMs(room, roomUnit, variableId), thresholdMs)) return false;
        }

        return true;
    }

    private boolean evaluateFurniTargets(WiredContext ctx, Room room, long thresholdMs, int variableId) {
        this.refresh();

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedItems);
        if (targets.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ANY) {
            for (HabboItem item : targets) {
                if (this.matchesAge(this.readFurniAgeMs(room, item, variableId), thresholdMs)) return true;
            }

            return false;
        }

        for (HabboItem item : targets) {
            if (!this.matchesAge(this.readFurniAgeMs(room, item, variableId), thresholdMs)) return false;
        }

        return true;
    }

    private boolean evaluateRoomTarget(Room room, long thresholdMs, int variableId) {
        return this.matchesAge(this.readRoomAgeMs(room, variableId), thresholdMs);
    }

    private boolean evaluateContextTarget(WiredContext ctx, Room room, long thresholdMs, int variableId) {
        return this.matchesAge(this.readContextAgeMs(ctx, room, variableId), thresholdMs);
    }

    private Long readUserAgeMs(Room room, RoomUnit roomUnit, int variableId) {
        if (room == null || roomUnit == null) return null;

        int holder = UserVariableHolders.of(room, roomUnit);
        if (holder == 0 || !room.getUserVariableManager().hasVariable(holder, variableId)) return null;

        int timestamp = (this.compareValue == COMPARE_VALUE_UPDATED)
                ? room.getUserVariableManager().getUpdatedAt(holder, variableId)
                : room.getUserVariableManager().getCreatedAt(holder, variableId);

        return timestampToAgeMs(timestamp);
    }

    private Long readFurniAgeMs(Room room, HabboItem item, int variableId) {
        if (room == null || item == null || !room.getFurniVariableManager().hasVariable(item.getId(), variableId))
            return null;

        int timestamp = (this.compareValue == COMPARE_VALUE_UPDATED)
                ? room.getFurniVariableManager().getUpdatedAt(item.getId(), variableId)
                : room.getFurniVariableManager().getCreatedAt(item.getId(), variableId);

        return timestampToAgeMs(timestamp);
    }

    private Long readRoomAgeMs(Room room, int variableId) {
        if (room == null) return null;
        if (this.compareValue == COMPARE_VALUE_CREATED) return null;

        int timestamp = room.getRoomVariableManager().getUpdatedAt(variableId);
        return timestampToAgeMs(timestamp);
    }

    private Long readContextAgeMs(WiredContext ctx, Room room, int variableId) {
        if (ctx == null || room == null || !WiredContextVariableSupport.hasVariable(ctx, variableId)) return null;

        int timestamp = (this.compareValue == COMPARE_VALUE_UPDATED)
                ? WiredContextVariableSupport.getUpdatedAt(ctx, variableId)
                : WiredContextVariableSupport.getCreatedAt(ctx, variableId);

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
                WiredVariableDefinitionInfo definition =
                        room.getRoomVariableManager().getDefinitionInfo(this.variableItemId);
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
            case DURATION_UNIT_MILLISECONDS,
                    DURATION_UNIT_SECONDS,
                    DURATION_UNIT_MINUTES,
                    DURATION_UNIT_HOURS,
                    DURATION_UNIT_DAYS,
                    DURATION_UNIT_WEEKS,
                    DURATION_UNIT_MONTHS,
                    DURATION_UNIT_YEARS -> value;
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
        boolean fromVariable;
        int durationVariableId;
        Integer durationTarget;

        JsonData(
                List<Integer> itemIds,
                int targetType,
                String variableToken,
                int variableItemId,
                int compareValue,
                int comparison,
                int durationAmount,
                int durationUnit,
                int userSource,
                int furniSource,
                int quantifier) {
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
