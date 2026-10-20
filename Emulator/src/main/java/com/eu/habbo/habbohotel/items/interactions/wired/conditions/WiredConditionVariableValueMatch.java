package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEditorSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayReference;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayView;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredQuantifier;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSecondaryPicks;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The official "variable value matches" (AIR code 36). Int params {@code [target, comparison, operandIsVariable,
 * operandHigh, operandLow, operandTarget, sameHolder]}; variable ids {@code [variable, reference or none]}; sources slot
 * 0 the compared holders and slot 1 the reference holders (AIR's merged selections), the reference picks as the second
 * picks; the quantifier in its slot. The string carries only our array addresses (JSON). Boxes and older clients that
 * sent the reference sources as ints 7 and 8 and the variables, reference picks and array data as the string are
 * still read.
 */
public class WiredConditionVariableValueMatch extends WiredConditionHasVariable
        implements WiredOfficialSources, WiredOfficialSecondaryPicks {
    public static final WiredConditionType type = WiredConditionType.VAR_VAL_MATCH;

    private static final int TARGET_CONTEXT = 2;
    private static final int SOURCE_SECONDARY_SELECTED = 101;
    private static final int REFERENCE_CONSTANT = 0;
    private static final int REFERENCE_VARIABLE = 1;
    private static final int COMPARISON_GREATER_THAN = 0;
    private static final int COMPARISON_GREATER_THAN_OR_EQUAL = 1;
    private static final int COMPARISON_EQUAL = 2;
    private static final int COMPARISON_LESS_THAN_OR_EQUAL = 3;
    private static final int COMPARISON_LESS_THAN = 4;
    private static final int COMPARISON_NOT_EQUAL = 5;
    static final int MAX_ABS_REFERENCE_CONSTANT = 1_000_000_000;

    protected int comparison = COMPARISON_EQUAL;
    protected int referenceMode = REFERENCE_CONSTANT;
    protected int referenceConstantValue = 0;
    protected int referenceTargetType = TARGET_USER;
    protected int referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected int referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
    protected String referenceVariableToken = "";
    protected int referenceVariableItemId = 0;
    protected final Set<HabboItem> referenceSelectedItems = new LinkedHashSet<>();
    private WiredArrayAddress arrayAddress = new WiredArrayAddress();
    private WiredArrayAddress referenceArrayAddress = new WiredArrayAddress();
    private String arrayReferenceConstant = "0";
    /** Each target is compared with the reference variable it holds itself (official, WIN63-202610121624). */
    protected boolean sameHolder = false;

    public WiredConditionVariableValueMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableValueMatch(
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
        this.refreshReferenceItems();

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
        message.appendString(this.serializeStringData(room));
        // Official layout: [target, comparison, operandIsVariable, operandHigh, operandLow, operandTarget,
        // sameHolder], the variables as variable ids, the compared and reference holders in source slots 0 and
        // 1, the quantifier in its slot.
        message.appendInt(7);
        message.appendInt(WiredVariableTargets.toOfficial(this.targetType));
        message.appendInt(WiredVariableTargets.comparisonToOfficial(this.comparison));
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceConstantValue < 0 ? -1 : 0);
        message.appendInt(this.referenceConstantValue);
        message.appendInt(WiredVariableTargets.toOfficial(this.referenceTargetType));
        message.appendInt(this.sameHolder ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        int[] params = settings.getIntParams();
        String[] stringParts = this.parseStringData(settings.getStringParam());
        int nextTargetType = normalizeTargetTypeExtended(
                WiredVariableTargets.fromOfficial(param(params, 0, WiredVariableTargets.OFFICIAL_USER)));
        int nextComparison = normalizeComparison(WiredVariableTargets.comparisonFromOfficial(param(params, 1, 1)));
        int nextReferenceMode = normalizeReferenceMode(param(params, 2, REFERENCE_CONSTANT));
        long operand = (((long) param(params, 3, 0)) << 32) | (param(params, 4, 0) & 0xFFFFFFFFL);
        int nextReferenceConstantValue = normalizeReferenceConstantValue(
                (int) Math.clamp(operand, -MAX_ABS_REFERENCE_CONSTANT, MAX_ABS_REFERENCE_CONSTANT));
        int nextReferenceTargetType = normalizeTargetTypeExtended(
                WiredVariableTargets.fromOfficial(param(params, 5, WiredVariableTargets.OFFICIAL_USER)));
        boolean nextSameHolder = nextReferenceMode == REFERENCE_VARIABLE && param(params, 6, 0) == 1;
        int nextUserSource = settings.getUserSourceTypes().length > 0
                ? normalizeUserSource(settings.getUserSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        int nextFurniSource = settings.getFurniSourceTypes().length > 0
                ? normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : WiredSourceUtil.SOURCE_TRIGGER;
        // AIR: the reference holders in source slot 1; our earlier editors sent them as ints 7 and 8.
        int nextReferenceUserSource = normalizeUserSource(
                params.length > 7
                        ? params[7]
                        : settings.getUserSourceTypes().length > 1
                                ? settings.getUserSourceTypes()[1]
                                : WiredSourceUtil.SOURCE_TRIGGER);
        int nextReferenceFurniSource = normalizeReferenceFurniSource(
                params.length > 8
                        ? params[8]
                        : settings.getFurniSourceTypes().length > 1
                                ? settings.getFurniSourceTypes()[1]
                                : WiredSourceUtil.SOURCE_TRIGGER);
        int nextQuantifier = normalizeQuantifier(settings.getQuantifierCode());
        boolean fromVariableIds = WiredVariableIds.present(settings.getVariableIds());
        String nextVariableToken;
        String nextReferenceVariableToken;
        ArrayData nextArrayData;
        if (fromVariableIds) {
            // AIR: [variable, reference]; the string carries only our array addresses.
            nextVariableToken = WiredVariableIds.tokenAt(settings.getVariableIds(), 0);
            nextReferenceVariableToken = WiredVariableIds.tokenAt(settings.getVariableIds(), 1);
            nextArrayData = parseArrayData(settings.getStringParam());
        } else {
            nextVariableToken =
                    normalizeVariableToken((stringParts.length > 0) ? stringParts[0] : settings.getStringParam());
            nextReferenceVariableToken = normalizeVariableToken((stringParts.length > 1) ? stringParts[1] : "");
            nextArrayData = parseArrayData(stringParts.length > 3 ? stringParts[3] : null);
        }

        if (!this.isValidSource(room, nextTargetType, nextVariableToken)) return false;
        if (nextReferenceMode == REFERENCE_VARIABLE
                && !this.isValidReference(room, nextReferenceTargetType, nextReferenceVariableToken)) return false;
        WiredArrayVariableDefinition targetArray =
                this.resolveArrayDefinition(room, nextTargetType, getCustomItemId(nextVariableToken));
        if (targetArray != null && !WiredArrayEditorSupport.isAddressableCell(nextArrayData.address, targetArray))
            return false;
        WiredArrayVariableDefinition referenceArray =
                this.resolveArrayDefinition(room, nextReferenceTargetType, getCustomItemId(nextReferenceVariableToken));
        if (nextReferenceMode == REFERENCE_VARIABLE
                && referenceArray != null
                && !WiredArrayEditorSupport.isAddressableCell(nextArrayData.referenceAddress, referenceArray))
            return false;
        if (targetArray != null && nextReferenceMode == REFERENCE_CONSTANT) {
            try {
                Long.parseLong(nextArrayData.referenceConstant);
            } catch (NumberFormatException exception) {
                return false;
            }
        }

        int selectionLimit = Emulator.getConfig().getInt("hotel.wired.furni.selection.count");
        List<HabboItem> nextSelectedItems =
                (nextTargetType == TARGET_FURNI && nextFurniSource == WiredSourceUtil.SOURCE_SELECTED)
                        ? this.parseItems(settings.getFurniIds(), room)
                        : new ArrayList<>();
        List<HabboItem> nextReferenceItems = (nextReferenceMode == REFERENCE_VARIABLE
                        && nextReferenceTargetType == TARGET_FURNI
                        && nextReferenceFurniSource == SOURCE_SECONDARY_SELECTED)
                ? (fromVariableIds
                        ? this.parseItems(settings.getFurniIds2(), room)
                        : this.parseItems((stringParts.length > 2) ? stringParts[2] : "", room))
                : new ArrayList<>();

        if (nextSelectedItems.size() > selectionLimit || nextReferenceItems.size() > selectionLimit) return false;

        this.selectedItems.clear();
        this.selectedItems.addAll(nextSelectedItems);
        this.referenceSelectedItems.clear();
        this.referenceSelectedItems.addAll(nextReferenceItems);
        this.targetType = nextTargetType;
        this.comparison = nextComparison;
        this.referenceMode = nextReferenceMode;
        this.referenceConstantValue = nextReferenceConstantValue;
        this.referenceTargetType = nextReferenceTargetType;
        this.userSource = nextUserSource;
        this.furniSource = nextFurniSource;
        this.referenceUserSource = nextReferenceUserSource;
        this.referenceFurniSource = nextReferenceFurniSource;
        this.quantifier = nextQuantifier;
        this.sameHolder = nextSameHolder;
        this.setVariableToken(nextVariableToken);
        this.setReferenceVariableToken(nextReferenceVariableToken);
        this.arrayAddress = nextArrayData.address;
        this.referenceArrayAddress = nextArrayData.referenceAddress;
        this.arrayReferenceConstant = nextArrayData.referenceConstant;

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) {
            return false;
        }

        Room room = ctx.room();

        if (room == null || this.variableToken == null || this.variableToken.isEmpty()) {
            return false;
        }

        // A pointer is compared as the variable it stands for in this stack.
        int variableId = WiredPointerVariableSupport.resolve(ctx, room, this.variableItemId);
        int referenceId = WiredPointerVariableSupport.resolve(ctx, room, this.referenceVariableItemId);
        if (variableId <= 0 && !isInternalVariableToken(this.variableToken)) return false;

        WiredArrayVariableDefinition arrayDefinition = this.resolveArrayDefinition(room, this.targetType, variableId);
        if (arrayDefinition != null) return this.evaluateArray(ctx, arrayDefinition, variableId, referenceId);

        return switch (this.targetType) {
            case TARGET_FURNI -> this.evaluateFurniTargets(ctx, room, variableId, referenceId);
            case TARGET_ROOM -> this.evaluateRoomTarget(ctx, room, variableId, referenceId);
            case TARGET_CONTEXT -> this.evaluateContextTarget(ctx, room, variableId, referenceId);
            default -> this.evaluateUserTargets(ctx, room, variableId, referenceId);
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
        this.refreshReferenceItems();

        JsonData data = new JsonData(
                this.targetType,
                this.variableToken,
                this.variableItemId,
                this.comparison,
                this.referenceMode,
                this.referenceConstantValue,
                this.referenceTargetType,
                this.referenceVariableToken,
                this.referenceVariableItemId,
                this.userSource,
                this.furniSource,
                this.referenceUserSource,
                this.referenceFurniSource,
                this.quantifier,
                this.toIds(this.selectedItems),
                this.toIds(this.referenceSelectedItems));
        data.arrayData = new ArrayData(this.arrayAddress, this.referenceArrayAddress, this.arrayReferenceConstant);
        data.sameHolder = this.sameHolder;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) return;

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException exception) {
            this.onPickUp();
            return;
        }

        if (data == null) return;

        this.targetType = normalizeTargetTypeExtended(data.targetType);
        this.setVariableToken(normalizeVariableToken(
                (data.variableToken != null)
                        ? data.variableToken
                        : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
        this.comparison = normalizeComparison(data.comparison);
        this.referenceMode = normalizeReferenceMode(data.referenceMode);
        this.referenceConstantValue = normalizeReferenceConstantValue(data.referenceConstantValue);
        this.referenceTargetType = normalizeTargetTypeExtended(data.referenceTargetType);
        this.setReferenceVariableToken(normalizeVariableToken(
                (data.referenceVariableToken != null)
                        ? data.referenceVariableToken
                        : ((data.referenceVariableItemId > 0) ? String.valueOf(data.referenceVariableItemId) : "")));
        this.userSource = normalizeUserSource(data.userSource);
        this.furniSource = normalizeFurniSource(data.furniSource);
        this.referenceUserSource = normalizeUserSource(data.referenceUserSource);
        this.referenceFurniSource = normalizeReferenceFurniSource(data.referenceFurniSource);
        this.quantifier = normalizeQuantifier(data.quantifier);
        this.sameHolder = this.referenceMode == REFERENCE_VARIABLE && data.sameHolder;
        ArrayData arrayData = normalizeArrayData(data.arrayData);
        this.arrayAddress = arrayData.address;
        this.referenceArrayAddress = arrayData.referenceAddress;
        this.arrayReferenceConstant = arrayData.referenceConstant;

        if (room == null) return;

        this.selectedItems.addAll(this.parseItems(data.selectedItemIds, room));
        this.referenceSelectedItems.addAll(this.parseItems(data.referenceSelectedItemIds, room));
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.comparison = COMPARISON_EQUAL;
        this.referenceMode = REFERENCE_CONSTANT;
        this.referenceConstantValue = 0;
        this.referenceTargetType = TARGET_USER;
        this.referenceUserSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceFurniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.referenceSelectedItems.clear();
        this.arrayAddress = new WiredArrayAddress();
        this.referenceArrayAddress = new WiredArrayAddress();
        this.arrayReferenceConstant = "0";
        this.sameHolder = false;
        this.setReferenceVariableToken("");
    }

    public boolean requiresTriggeringUser() {
        return (this.targetType == TARGET_USER && this.userSource == WiredSourceUtil.SOURCE_TRIGGER)
                || (this.referenceMode == REFERENCE_VARIABLE
                        && this.referenceTargetType == TARGET_USER
                        && this.referenceUserSource == WiredSourceUtil.SOURCE_TRIGGER);
    }

    private boolean evaluateUserTargets(WiredContext ctx, Room room, int variableId, int referenceId) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier != QUANTIFIER_ANY);

        ReferenceSnapshot references = this.resolveReferences(ctx, room, variableId, referenceId);

        if (this.quantifier == QUANTIFIER_ANY) {
            int index = 0;
            for (RoomUnit roomUnit : targets) {
                Integer currentValue = this.readUserValue(room, roomUnit, variableId, referenceId);
                Long referenceValue =
                        this.referenceFor(references, roomUnit != null ? roomUnit.getId() : 0, TARGET_USER, index++);

                if (this.matchesComparison(currentValue, referenceValue)) return true;
            }

            return false;
        }

        int index = 0;
        for (RoomUnit roomUnit : targets) {
            Integer currentValue = this.readUserValue(room, roomUnit, variableId, referenceId);
            Long referenceValue =
                    this.referenceFor(references, roomUnit != null ? roomUnit.getId() : 0, TARGET_USER, index++);

            if (!this.matchesComparison(currentValue, referenceValue)) return false;
        }

        return true;
    }

    private boolean evaluateFurniTargets(WiredContext ctx, Room room, int variableId, int referenceId) {
        this.refresh();

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedItems);
        if (targets.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier != QUANTIFIER_ANY);

        ReferenceSnapshot references = this.resolveReferences(ctx, room, variableId, referenceId);

        if (this.quantifier == QUANTIFIER_ANY) {
            int index = 0;
            for (HabboItem item : targets) {
                Integer currentValue = this.readFurniValue(room, item, variableId, referenceId);
                Long referenceValue =
                        this.referenceFor(references, item != null ? item.getId() : 0, TARGET_FURNI, index++);

                if (this.matchesComparison(currentValue, referenceValue)) return true;
            }

            return false;
        }

        int index = 0;
        for (HabboItem item : targets) {
            Integer currentValue = this.readFurniValue(room, item, variableId, referenceId);
            Long referenceValue = this.referenceFor(references, item != null ? item.getId() : 0, TARGET_FURNI, index++);

            if (!this.matchesComparison(currentValue, referenceValue)) return false;
        }

        return true;
    }

    private boolean evaluateRoomTarget(WiredContext ctx, Room room, int variableId, int referenceId) {
        Integer currentValue = this.readRoomValue(room, variableId, referenceId);
        Long referenceValue = this.referenceFor(
                this.resolveReferences(ctx, room, variableId, referenceId), room.getId(), TARGET_ROOM, 0);

        return this.matchesComparison(currentValue, referenceValue);
    }

    private boolean evaluateContextTarget(WiredContext ctx, Room room, int variableId, int referenceId) {
        Long currentValue = this.readContextTargetValue(ctx, room, variableId, referenceId);
        Long referenceValue = this.referenceFor(
                this.resolveReferences(ctx, room, variableId, referenceId), variableId, TARGET_CONTEXT, 0);

        return this.matchesComparison(currentValue, referenceValue);
    }

    private boolean evaluateArray(
            WiredContext ctx, WiredArrayVariableDefinition definition, int variableId, int referenceId) {
        int source =
                definition.getArrayVariableType() == WiredArrayVariableType.FURNI ? this.furniSource : this.userSource;
        List<WiredArrayRuntimeSupport.Owner> owners =
                WiredArrayRuntimeSupport.resolveOwners(ctx, this.selectedItems, definition, source);
        if (owners.isEmpty()) return WiredQuantifier.overEmpty(this.quantifier != QUANTIFIER_ANY);
        ReferenceSnapshot scalarReferences = this.referenceMode == REFERENCE_VARIABLE
                        && this.resolveArrayDefinition(ctx.room(), this.referenceTargetType, referenceId) == null
                ? this.resolveReferences(ctx, ctx.room(), variableId, referenceId)
                : null;
        boolean any = this.quantifier == QUANTIFIER_ANY;

        int destinationIndex = 0;
        for (WiredArrayRuntimeSupport.Owner owner : owners) {
            Integer index = WiredArrayRuntimeSupport.resolveIndex(
                    ctx, this.selectedItems, this.arrayAddress, definition, owner);
            WiredArrayView value = WiredArrayRuntimeSupport.getValue(ctx, definition, owner);
            Long current = index == null || value == null ? null : value.readField(index, this.arrayAddress.fieldId);
            Long reference = this.resolveArrayComparisonReference(
                    ctx, owner, scalarReferences, destinationIndex++, variableId, referenceId);
            boolean match = current != null
                    && reference != null
                    && WiredArrayRuntimeSupport.compare(current, reference, this.comparison);
            if (any && match) return true;
            if (!any && !match) return false;
        }
        return !any;
    }

    private Long resolveArrayComparisonReference(
            WiredContext ctx,
            WiredArrayRuntimeSupport.Owner owner,
            ReferenceSnapshot scalarReferences,
            int destinationIndex,
            int variableId,
            int referenceId) {
        if (this.referenceMode == REFERENCE_CONSTANT) {
            try {
                return Long.parseLong(this.arrayReferenceConstant);
            } catch (NumberFormatException ignored) {
                return (long) this.referenceConstantValue;
            }
        }
        WiredArrayVariableDefinition referenceDefinition =
                this.resolveArrayDefinition(ctx.room(), this.referenceTargetType, referenceId);
        if (referenceDefinition == null) {
            Long value = this.referenceFor(
                    scalarReferences,
                    owner.unit() == null ? owner.id() : owner.unit().getId(),
                    targetCode(owner.type()),
                    destinationIndex);
            return value == null ? null : value.longValue();
        }
        WiredArrayReference reference = new WiredArrayReference();
        reference.mode = WiredArrayReference.VARIABLE;
        reference.variableType = referenceDefinition.getArrayVariableType().code();
        reference.variableItemId = referenceDefinition.getId();
        reference.variableSource = referenceDefinition.getArrayVariableType() == WiredArrayVariableType.FURNI
                ? this.referenceFurniSource
                : this.referenceUserSource;
        reference.address = this.referenceArrayAddress;
        return WiredArrayRuntimeSupport.resolveReference(ctx, this.referenceSelectedItems, reference, owner);
    }

    private WiredArrayVariableDefinition resolveArrayDefinition(Room room, int targetType, int itemId) {
        WiredArrayVariableType type =
                switch (targetType) {
                    case TARGET_FURNI -> WiredArrayVariableType.FURNI;
                    case TARGET_CONTEXT -> WiredArrayVariableType.CONTEXT;
                    case TARGET_ROOM -> WiredArrayVariableType.ROOM;
                    default -> WiredArrayVariableType.USER;
                };
        WiredArrayVariableDefinition definition = WiredArrayDefinitionSupport.resolve(room, type.code(), itemId);
        return definition != null && definition.isArray() ? definition : null;
    }

    private static int targetCode(WiredArrayVariableType type) {
        return switch (type) {
            case FURNI -> TARGET_FURNI;
            case CONTEXT -> TARGET_CONTEXT;
            case ROOM -> TARGET_ROOM;
            case USER -> TARGET_USER;
        };
    }

    private ReferenceSnapshot resolveReferences(WiredContext ctx, Room room, int variableId, int referenceId) {
        if (this.referenceMode != REFERENCE_VARIABLE) return null;
        WiredArrayVariableDefinition array = this.resolveArrayDefinition(room, this.referenceTargetType, referenceId);
        if (array != null) {
            ReferenceSnapshot snapshot = new ReferenceSnapshot(this.referenceTargetType);
            int source = array.getArrayVariableType() == WiredArrayVariableType.FURNI
                    ? this.referenceFurniSource
                    : this.referenceUserSource;
            if (source == SOURCE_SECONDARY_SELECTED) source = WiredSourceUtil.SOURCE_SELECTED;
            for (WiredArrayRuntimeSupport.Owner owner :
                    WiredArrayRuntimeSupport.resolveOwners(ctx, this.referenceSelectedItems, array, source)) {
                Integer index = WiredArrayRuntimeSupport.resolveIndex(
                        ctx, this.referenceSelectedItems, this.referenceArrayAddress, array, owner);
                WiredArrayView value = WiredArrayRuntimeSupport.getValue(ctx, array, owner);
                Long field = index == null || value == null
                        ? null
                        : value.readField(index, this.referenceArrayAddress.fieldId);
                if (field != null)
                    snapshot.add(
                            owner.unit() == null ? owner.id() : owner.unit().getId(), field);
            }
            return snapshot.isEmpty() ? null : snapshot;
        }

        return switch (this.referenceTargetType) {
            case TARGET_USER -> this.userReferences(ctx, room, variableId, referenceId);
            case TARGET_FURNI -> this.furniReferences(ctx, room, variableId, referenceId);
            case TARGET_CONTEXT -> this.contextReferences(ctx, room, variableId, referenceId);
            case TARGET_ROOM -> this.roomReferences(room, variableId, referenceId);
            default -> null;
        };
    }

    private ReferenceSnapshot userReferences(WiredContext ctx, Room room, int variableId, int referenceId) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_USER);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseUserInternalReference(key)) return null;

            for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.referenceUserSourceInUse())) {
                Integer value = this.readUserInternalValue(room, roomUnit, key);
                if (value != null && roomUnit != null) snapshot.add(roomUnit.getId(), value);
            }

            return snapshot.isEmpty() ? null : snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(referenceId);
        if (definition == null || !definition.hasValue()) return null;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.referenceUserSourceInUse())) {
            if (roomUnit == null) continue;

            int holder = UserVariableHolders.of(room, roomUnit);
            if (holder != 0)
                snapshot.add(roomUnit.getId(), room.getUserVariableManager().getCurrentValue(holder, referenceId));
        }

        return snapshot.isEmpty() ? null : snapshot;
    }

    private ReferenceSnapshot furniReferences(WiredContext ctx, Room room, int variableId, int referenceId) {
        int source = this.sameHolder
                ? this.furniSource
                : (this.referenceFurniSource == SOURCE_SECONDARY_SELECTED)
                        ? WiredSourceUtil.SOURCE_SELECTED
                        : this.referenceFurniSource;
        if (source == WiredSourceUtil.SOURCE_SELECTED) this.refreshReferenceItems();
        java.util.Collection<HabboItem> referenceItems =
                this.sameHolder ? this.selectedItems : this.referenceSelectedItems;

        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_FURNI);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseFurniInternalReference(key)) return null;

            for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, referenceItems)) {
                Integer value = this.readFurniInternalValue(room, item, key);
                if (value != null && item != null) snapshot.add(item.getId(), value);
            }

            return snapshot.isEmpty() ? null : snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(referenceId);
        if (definition == null || !definition.hasValue()) return null;

        for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, referenceItems)) {
            if (item != null)
                snapshot.add(item.getId(), room.getFurniVariableManager().getCurrentValue(item.getId(), referenceId));
        }

        return snapshot.isEmpty() ? null : snapshot;
    }

    private ReferenceSnapshot roomReferences(Room room, int variableId, int referenceId) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_ROOM);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseRoomInternalReference(key)) return null;

            Integer value = this.readRoomInternalValue(room, key);
            if (value == null) return null;

            snapshot.add(room.getId(), value);
            return snapshot;
        }

        WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(referenceId);
        if (definition == null || !definition.hasValue()) return null;

        snapshot.add(room.getId(), room.getRoomVariableManager().getCurrentValue(referenceId));
        return snapshot;
    }

    private ReferenceSnapshot contextReferences(WiredContext ctx, Room room, int variableId, int referenceId) {
        ReferenceSnapshot snapshot = new ReferenceSnapshot(TARGET_CONTEXT);

        if (isInternalVariableToken(this.referenceVariableToken)) {
            String key = getInternalVariableKey(this.referenceVariableToken);
            if (!canUseContextInternalReference(key)) return null;

            Long value = WiredInternalVariableSupport.readContextLongValue(ctx, key);
            if (value == null) return null;

            snapshot.add(referenceId > 0 ? referenceId : (room != null ? room.getId() : 0), value);
            return snapshot;
        }

        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, referenceId);
        if (definition == null || !definition.hasValue() || !WiredContextVariableSupport.hasVariable(ctx, referenceId))
            return null;

        Integer value = WiredContextVariableSupport.getCurrentValue(ctx, referenceId);
        if (value == null) return null;

        snapshot.add(referenceId, value);
        return snapshot;
    }

    private Integer readUserValue(Room room, RoomUnit roomUnit, int variableId, int referenceId) {
        if (room == null || roomUnit == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseUserInternalReference(key) ? this.readUserInternalValue(room, roomUnit, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(variableId);
        if (definition == null || !definition.hasValue()) return null;

        int holder = UserVariableHolders.of(room, roomUnit);
        return (holder != 0) ? room.getUserVariableManager().getCurrentValue(holder, variableId) : null;
    }

    private Integer readFurniValue(Room room, HabboItem item, int variableId, int referenceId) {
        if (room == null || item == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseFurniInternalReference(key) ? this.readFurniInternalValue(room, item, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(variableId);
        return (definition != null && definition.hasValue())
                ? room.getFurniVariableManager().getCurrentValue(item.getId(), variableId)
                : null;
    }

    private Integer readRoomValue(Room room, int variableId, int referenceId) {
        if (room == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseRoomInternalReference(key) ? this.readRoomInternalValue(room, key) : null;
        }

        WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(variableId);
        return (definition != null && definition.hasValue())
                ? room.getRoomVariableManager().getCurrentValue(variableId)
                : null;
    }

    private Long readContextTargetValue(WiredContext ctx, Room room, int variableId, int referenceId) {
        if (ctx == null || room == null) return null;

        if (isInternalVariableToken(this.variableToken)) {
            String key = getInternalVariableKey(this.variableToken);
            return canUseContextInternalReference(key)
                    ? WiredInternalVariableSupport.readContextLongValue(ctx, key)
                    : null;
        }

        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, variableId);
        if (definition == null || !definition.hasValue() || !WiredContextVariableSupport.hasVariable(ctx, variableId))
            return null;

        Integer value = WiredContextVariableSupport.getCurrentValue(ctx, variableId);
        return value == null ? null : value.longValue();
    }

    private Long referenceFor(
            ReferenceSnapshot snapshot, int destinationEntityId, int destinationTarget, int destinationIndex) {
        if (this.referenceMode != REFERENCE_VARIABLE) return (long) this.referenceConstantValue;
        if (snapshot == null || snapshot.isEmpty()) return null;
        if (snapshot.targetType == destinationTarget && snapshot.values.containsKey(destinationEntityId))
            return snapshot.values.get(destinationEntityId);
        // Same holder: a target without the reference variable has nothing to compare with.
        if (this.sameHolder && snapshot.targetType == destinationTarget) return null;
        if (destinationIndex >= 0 && destinationIndex < snapshot.values.size())
            return new ArrayList<>(snapshot.values.values()).get(destinationIndex);
        return new ArrayList<>(snapshot.values.values()).get(0);
    }

    /** The user source the reference is read on: the targets' own with "same holder". */
    private int referenceUserSourceInUse() {
        return this.sameHolder ? this.userSource : this.referenceUserSource;
    }

    private boolean matchesComparison(Number currentValue, Number referenceValue) {
        if (currentValue == null || referenceValue == null) return false;

        return WiredArrayRuntimeSupport.compare(currentValue.longValue(), referenceValue.longValue(), this.comparison);
    }

    private boolean isValidSource(Room room, int targetType, String variableToken) {
        if (variableToken == null || variableToken.isEmpty()) return false;

        return switch (targetType) {
            case TARGET_USER ->
                isInternalVariableToken(variableToken)
                        ? canUseUserInternalReference(getInternalVariableKey(variableToken))
                        : this.isValidUserCustomValue(room, getCustomItemId(variableToken));
            case TARGET_FURNI ->
                isInternalVariableToken(variableToken)
                        ? canUseFurniInternalReference(getInternalVariableKey(variableToken))
                        : this.isValidFurniCustomValue(room, getCustomItemId(variableToken));
            case TARGET_CONTEXT ->
                isInternalVariableToken(variableToken)
                        ? canUseContextInternalReference(getInternalVariableKey(variableToken))
                        : this.isValidContextCustomValue(room, getCustomItemId(variableToken));
            case TARGET_ROOM ->
                isInternalVariableToken(variableToken)
                        ? canUseRoomInternalReference(getInternalVariableKey(variableToken))
                        : this.isValidRoomCustomValue(room, getCustomItemId(variableToken));
            default -> false;
        };
    }

    private boolean isValidReference(Room room, int targetType, String variableToken) {
        return this.isValidSource(room, targetType, variableToken);
    }

    private boolean isValidUserCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition =
                (room != null) ? room.getUserVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && (definition.hasValue() || definition.isArray());
    }

    private boolean isValidFurniCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition =
                (room != null) ? room.getFurniVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && (definition.hasValue() || definition.isArray());
    }

    private boolean isValidRoomCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition =
                (room != null) ? room.getRoomVariableManager().getDefinitionInfo(variableItemId) : null;
        return definition != null && (definition.hasValue() || definition.isArray());
    }

    private boolean isValidContextCustomValue(Room room, int variableItemId) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, variableItemId);
        return definition != null && (definition.hasValue() || definition.isArray());
    }

    private Integer readUserInternalValue(Room room, RoomUnit roomUnit, String key) {
        return WiredInternalVariableSupport.readUserValue(room, roomUnit, key);
    }

    private Integer readFurniInternalValue(Room room, HabboItem item, String key) {
        return WiredInternalVariableSupport.readFurniValue(room, item, key);
    }

    private Integer readRoomInternalValue(Room room, String key) {
        return WiredInternalVariableSupport.readRoomValue(room, key);
    }

    private Integer getUserTeamScore(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo().getGamePlayer() == null) return null;

        Game game = this.resolveTeamGame(room, habbo);
        GamePlayer gamePlayer = habbo.getHabboInfo().getGamePlayer();

        if (game == null || gamePlayer.getTeamColor() == null) return gamePlayer.getScore();

        GameTeam team = game.getTeam(gamePlayer.getTeamColor());
        return (team != null) ? team.getTotalScore() : gamePlayer.getScore();
    }

    private Integer getTeamColorId(int effectId) {
        TeamEffectData data = this.getTeamEffectData(effectId);
        return data == null ? null : data.colorId;
    }

    private Integer getTeamTypeId(int effectId) {
        TeamEffectData data = this.getTeamEffectData(effectId);
        return data == null ? null : data.typeId;
    }

    private int getTeamMetric(Room room, GameTeamColors color, boolean score) {
        Game game = this.resolveTeamGame(room, null);
        if (game == null || color == null) return 0;

        GameTeam team = game.getTeam(color);
        if (team == null) return 0;

        return score ? team.getTotalScore() : team.getMembers().size();
    }

    private Game resolveTeamGame(Room room, Habbo habbo) {
        if (room == null) return null;

        if (habbo != null
                && habbo.getHabboInfo() != null
                && habbo.getHabboInfo().getCurrentGame() != null) {
            Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());
            if (game != null) return game;
        }

        Game wiredGame = room.getGame(WiredGame.class);
        if (wiredGame != null) return wiredGame;

        Game freezeGame = room.getGame(FreezeGame.class);
        if (freezeGame != null) return freezeGame;

        return room.getGame(BattleBanzaiGame.class);
    }

    private List<HabboItem> parseItems(int[] ids, Room room) {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || room == null) return items;

        for (int id : ids) {
            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }

        return items;
    }

    private List<HabboItem> parseItems(List<Integer> ids, Room room) {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || room == null) return items;

        for (Integer id : ids) {
            if (id == null || id <= 0) continue;

            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }

        return items;
    }

    private List<HabboItem> parseItems(String ids, Room room) {
        List<HabboItem> items = new ArrayList<>();
        if (ids == null || ids.trim().isEmpty() || room == null) return items;

        for (String part : ids.split("[;,\\t]")) {
            int id = parseInteger(part);
            if (id <= 0) continue;

            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }

        return items;
    }

    private void refreshReferenceItems() {
        Set<HabboItem> staleItems = new HashSet<>();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            staleItems.addAll(this.referenceSelectedItems);
        } else {
            for (HabboItem item : this.referenceSelectedItems) {
                if (item == null || item.getRoomId() != room.getId() || room.getHabboItem(item.getId()) == null) {
                    staleItems.add(item);
                }
            }
        }

        this.referenceSelectedItems.removeAll(staleItems);
    }

    /** AIR sends no string; ours carries the array addresses when the variable or the reference is an array. */
    private String serializeStringData(Room room) {
        boolean array = this.resolveArrayDefinition(room, this.targetType, this.variableItemId) != null
                || (this.referenceMode == REFERENCE_VARIABLE
                        && this.resolveArrayDefinition(room, this.referenceTargetType, this.referenceVariableItemId)
                                != null);
        if (!array) return "";
        return WiredManager.getGson()
                .toJson(new ArrayData(this.arrayAddress, this.referenceArrayAddress, this.arrayReferenceConstant));
    }

    @Override
    public String[] officialVariableIds() {
        return WiredVariableIds.list(
                WiredVariableIds.toWireForHolder(this.targetType, this.variableToken),
                this.referenceMode == REFERENCE_VARIABLE
                        ? WiredVariableIds.toWireForHolder(this.referenceTargetType, this.referenceVariableToken)
                        : "");
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource, this.referenceFurniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.userSource, this.referenceUserSource};
    }

    @Override
    public int[] officialFurniIds2() {
        if (this.referenceMode != REFERENCE_VARIABLE
                || this.referenceTargetType != TARGET_FURNI
                || this.referenceFurniSource != SOURCE_SECONDARY_SELECTED) {
            return new int[0];
        }
        return this.toIds(this.referenceSelectedItems).stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    private String[] parseStringData(String value) {
        return (value == null || value.isEmpty()) ? new String[0] : value.split("\\t", -1);
    }

    private static ArrayData parseArrayData(String json) {
        if (json == null || json.isBlank()) return new ArrayData();
        try {
            return normalizeArrayData(WiredManager.getGson().fromJson(json, ArrayData.class));
        } catch (RuntimeException ignored) {
            return new ArrayData();
        }
    }

    private static ArrayData normalizeArrayData(ArrayData data) {
        ArrayData normalized = data == null ? new ArrayData() : data;
        if (normalized.address == null) normalized.address = new WiredArrayAddress();
        if (normalized.referenceAddress == null) normalized.referenceAddress = new WiredArrayAddress();
        if (normalized.referenceConstant == null || normalized.referenceConstant.isBlank()) {
            normalized.referenceConstant = "0";
        } else {
            normalized.referenceConstant = normalized.referenceConstant.trim();
        }
        return normalized;
    }

    private List<Integer> toIds(Set<HabboItem> items) {
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : items) {
            if (item != null) ids.add(item.getId());
        }
        return ids;
    }

    private void setReferenceVariableToken(String token) {
        this.referenceVariableToken = normalizeVariableToken(token);
        this.referenceVariableItemId = getCustomItemId(this.referenceVariableToken);
    }

    private static boolean canUseUserInternalReference(String key) {
        return WiredInternalVariableSupport.canUseUserReference(key);
    }

    private static boolean canUseFurniInternalReference(String key) {
        return WiredInternalVariableSupport.canUseFurniReference(key);
    }

    private static boolean canUseRoomInternalReference(String key) {
        return WiredInternalVariableSupport.canUseRoomReference(key);
    }

    private static boolean canUseContextInternalReference(String key) {
        return WiredInternalVariableSupport.canUseContextReference(key);
    }

    private static int param(int[] params, int index, int fallback) {
        return (params.length > index) ? params[index] : fallback;
    }

    static int normalizeTargetTypeExtended(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    static int normalizeReferenceMode(int value) {
        return (value == REFERENCE_VARIABLE) ? REFERENCE_VARIABLE : REFERENCE_CONSTANT;
    }

    static int normalizeReferenceFurniSource(int value) {
        return switch (value) {
            case SOURCE_SECONDARY_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    static int normalizeComparison(int value) {
        return switch (value) {
            case COMPARISON_GREATER_THAN,
                    COMPARISON_GREATER_THAN_OR_EQUAL,
                    COMPARISON_LESS_THAN_OR_EQUAL,
                    COMPARISON_LESS_THAN,
                    COMPARISON_NOT_EQUAL -> value;
            default -> COMPARISON_EQUAL;
        };
    }

    static int normalizeReferenceConstantValue(int value) {
        return Math.max(-MAX_ABS_REFERENCE_CONSTANT, Math.min(MAX_ABS_REFERENCE_CONSTANT, value));
    }

    static int parseInteger(String value) {
        try {
            return (value == null || value.trim().isEmpty()) ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static class JsonData {
        int targetType,
                variableItemId,
                comparison,
                referenceMode,
                referenceConstantValue,
                referenceTargetType,
                referenceVariableItemId,
                userSource,
                furniSource,
                referenceUserSource,
                referenceFurniSource,
                quantifier;
        String variableToken, referenceVariableToken;
        List<Integer> selectedItemIds, referenceSelectedItemIds;
        boolean sameHolder;
        ArrayData arrayData;

        JsonData(
                int targetType,
                String variableToken,
                int variableItemId,
                int comparison,
                int referenceMode,
                int referenceConstantValue,
                int referenceTargetType,
                String referenceVariableToken,
                int referenceVariableItemId,
                int userSource,
                int furniSource,
                int referenceUserSource,
                int referenceFurniSource,
                int quantifier,
                List<Integer> selectedItemIds,
                List<Integer> referenceSelectedItemIds) {
            this.targetType = targetType;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.comparison = comparison;
            this.referenceMode = referenceMode;
            this.referenceConstantValue = referenceConstantValue;
            this.referenceTargetType = referenceTargetType;
            this.referenceVariableToken = referenceVariableToken;
            this.referenceVariableItemId = referenceVariableItemId;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.referenceUserSource = referenceUserSource;
            this.referenceFurniSource = referenceFurniSource;
            this.quantifier = quantifier;
            this.selectedItemIds = selectedItemIds;
            this.referenceSelectedItemIds = referenceSelectedItemIds;
        }
    }

    static class ArrayData {
        WiredArrayAddress address = new WiredArrayAddress();
        WiredArrayAddress referenceAddress = new WiredArrayAddress();
        String referenceConstant = "0";

        ArrayData() {}

        ArrayData(WiredArrayAddress address, WiredArrayAddress referenceAddress, String referenceConstant) {
            this.address = address == null ? new WiredArrayAddress() : address;
            this.referenceAddress = referenceAddress == null ? new WiredArrayAddress() : referenceAddress;
            this.referenceConstant = referenceConstant == null ? "0" : referenceConstant;
        }
    }

    private static class ReferenceSnapshot {
        final int targetType;
        final LinkedHashMap<Integer, Long> values = new LinkedHashMap<>();

        ReferenceSnapshot(int targetType) {
            this.targetType = targetType;
        }

        void add(int entityId, long value) {
            this.values.put(entityId, value);
        }

        boolean isEmpty() {
            return this.values.isEmpty();
        }
    }
}
