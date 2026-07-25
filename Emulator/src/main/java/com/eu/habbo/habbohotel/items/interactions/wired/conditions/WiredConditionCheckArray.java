package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLargePayload;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayCriterion;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayReference;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks entry matches, result counts, or bounded array state. */
public final class WiredConditionCheckArray extends InteractionWiredCondition implements WiredLargePayload {
    public static final WiredConditionType type = WiredConditionType.CHECK_ARRAY;
    public static final int MODE_MATCH = 0;
    public static final int MODE_STATE = 1;
    public static final int SCOPE_ANY_INDEX = 0;
    public static final int SCOPE_SPECIFIC_INDEX = 1;
    public static final int CRITERIA_ALL = 0;
    public static final int CRITERIA_ANY = 1;
    public static final int QUANTIFIER_ALL = 0;
    public static final int QUANTIFIER_ANY = 1;
    public static final int RESULT_ALL = 0;
    public static final int RESULT_AT_LEAST_ONE = 1;
    public static final int RESULT_NOT_ALL = 2;
    public static final int RESULT_NONE = 3;
    public static final int RESULT_LESS_THAN = 4;
    public static final int RESULT_EXACTLY = 5;
    public static final int RESULT_MORE_THAN = 6;
    public static final int STATE_EMPTY = 0;
    public static final int STATE_FULL = 2;
    public static final int STATE_LENGTH = 3;
    public static final int STATE_AVAILABLE_INDEXES = 4;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredConditionCheckArray.class);
    private static final int MAX_PAYLOAD_LENGTH = 32_768;

    private int variableType = WiredArrayVariableType.ROOM.code();
    private int variableItemId;
    private int ownerSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int conditionMode = MODE_MATCH;
    private int searchScope = SCOPE_ANY_INDEX;
    private int criteriaMode = CRITERIA_ALL;
    private int resultMode = RESULT_AT_LEAST_ONE;
    private int resultComparison = 2;
    private int stateCheck = STATE_EMPTY;
    private int stateComparison = 2;
    private int quantifier = QUANTIFIER_ALL;
    private WiredArrayAddress index = new WiredArrayAddress();
    private List<WiredArrayCriterion> criteria = new ArrayList<>();
    private WiredArrayReference resultReference = new WiredArrayReference();
    private WiredArrayReference stateReference = new WiredArrayReference();
    private final List<HabboItem> selectedItems = new ArrayList<>();

    public WiredConditionCheckArray(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionCheckArray(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) return false;
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(ctx.room(), this.variableType, this.variableItemId);
        if (definition == null || !definition.isArray()) return false;
        List<WiredArrayRuntimeSupport.Owner> owners =
                WiredArrayRuntimeSupport.resolveOwners(ctx, this.selectedItems, definition, this.ownerSource);
        if (owners.isEmpty()) return false;

        boolean any = this.quantifier == QUANTIFIER_ANY;
        for (WiredArrayRuntimeSupport.Owner owner : owners) {
            WiredArrayValue value = WiredArrayRuntimeSupport.getValue(ctx, definition, owner);
            boolean matches = value != null
                    && (this.conditionMode == MODE_STATE
                            ? this.evaluateState(ctx, owner, value)
                            : this.evaluateMatch(ctx, definition, owner, value));
            if (any && matches) return true;
            if (!any && !matches) return false;
        }
        return !any;
    }

    private boolean evaluateMatch(
            WiredContext ctx,
            WiredArrayVariableDefinition definition,
            WiredArrayRuntimeSupport.Owner owner,
            WiredArrayValue value) {
        List<ResolvedCriterion> resolved = this.resolveCriteria(ctx, owner);
        if (resolved == null || resolved.isEmpty()) return false;
        if (this.searchScope == SCOPE_SPECIFIC_INDEX) {
            Integer resolvedIndex =
                    WiredArrayRuntimeSupport.resolveIndex(ctx, this.selectedItems, this.index, definition, owner);
            if (resolvedIndex == null) return false;
            WiredArrayEntry entry = value.getEntry(resolvedIndex);
            return entry != null && matchesEntry(entry, resolved, this.criteriaMode == CRITERIA_ANY);
        }

        long reference = 0L;
        if (resultNeedsReference(this.resultMode)) {
            Long resolvedReference =
                    WiredArrayRuntimeSupport.resolveReference(ctx, this.selectedItems, this.resultReference, owner);
            if (resolvedReference == null || resolvedReference < 0) return false;
            reference = resolvedReference;
        }
        int matches = 0;
        int occupied = value.getOccupiedCount();
        for (WiredArrayEntry entry : value.entriesSnapshot().values()) {
            if (matchesEntry(entry, resolved, this.criteriaMode == CRITERIA_ANY)) matches++;
        }
        return switch (this.resultMode) {
            case RESULT_ALL -> matches == occupied;
            case RESULT_AT_LEAST_ONE -> matches > 0;
            case RESULT_NOT_ALL -> matches < occupied;
            case RESULT_NONE -> matches == 0;
            case RESULT_LESS_THAN -> matches < reference;
            case RESULT_EXACTLY -> matches == reference;
            case RESULT_MORE_THAN -> matches > reference;
            default -> false;
        };
    }

    private boolean evaluateState(WiredContext ctx, WiredArrayRuntimeSupport.Owner owner, WiredArrayValue value) {
        if (this.stateCheck == STATE_EMPTY) return value.isEmpty();
        if (this.stateCheck == STATE_FULL) return value.isFull();
        if (this.stateCheck != STATE_LENGTH && this.stateCheck != STATE_AVAILABLE_INDEXES) return false;
        Long reference = WiredArrayRuntimeSupport.resolveReference(ctx, this.selectedItems, this.stateReference, owner);
        if (reference == null) return false;
        long current = this.stateCheck == STATE_LENGTH ? value.getLengthForCondition() : value.getAvailableIndexes();
        return WiredArrayRuntimeSupport.compare(current, reference, this.stateComparison);
    }

    private List<ResolvedCriterion> resolveCriteria(WiredContext ctx, WiredArrayRuntimeSupport.Owner owner) {
        List<ResolvedCriterion> result = new ArrayList<>();
        for (WiredArrayCriterion criterion : this.criteria) {
            if (criterion == null || criterion.reference == null || !validComparison(criterion.comparison)) return null;
            Long reference =
                    WiredArrayRuntimeSupport.resolveReference(ctx, this.selectedItems, criterion.reference, owner);
            if (reference == null) return null;
            result.add(new ResolvedCriterion(criterion.fieldId, criterion.comparison, reference));
        }
        return result;
    }

    private static boolean matchesEntry(WiredArrayEntry entry, List<ResolvedCriterion> criteria, boolean any) {
        for (ResolvedCriterion criterion : criteria) {
            boolean matches;
            try {
                matches = WiredArrayRuntimeSupport.compare(
                        entry.getValue(criterion.fieldId()), criterion.reference(), criterion.comparison());
            } catch (IllegalArgumentException exception) {
                return false;
            }
            if (any && matches) return true;
            if (!any && !matches) return false;
        }
        return !any;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = settings.getRoom();
        int[] params = settings.getIntParams();
        String raw = settings.getStringParam();
        if (room == null
                || room.getId() != this.getRoomId()
                || params == null
                || params.length < 10
                || raw == null
                || raw.length() > MAX_PAYLOAD_LENGTH) return false;
        try {
            JsonData data = readData(raw);
            int nextType = WiredArrayVariableType.fromCode(params[0]).code();
            WiredArrayVariableDefinition definition =
                    WiredArrayDefinitionSupport.resolve(room, nextType, data.variableItemId);
            if (definition == null || !definition.isArray()) return false;

            int nextMode = params[2] == MODE_STATE ? MODE_STATE : MODE_MATCH;
            int nextScope = params[3] == SCOPE_SPECIFIC_INDEX ? SCOPE_SPECIFIC_INDEX : SCOPE_ANY_INDEX;
            int nextResultMode = normalizeResultMode(params[5]);
            int nextStateCheck = normalizeStateCheck(params[7]);
            if (nextMode == MODE_MATCH && !validRawCriteria(data.criteria)) return false;
            boolean rawIndexValid = validRawAddress(data.index);
            boolean rawResultReferenceValid = validRawReference(data.resultReference);
            boolean rawStateReferenceValid = validRawReference(data.stateReference);
            List<WiredArrayCriterion> nextCriteria = normalizeCriteria(data.criteria);
            WiredArrayAddress nextIndex = normalizeAddress(data.index);
            WiredArrayReference nextResultReference = normalizeReference(data.resultReference);
            WiredArrayReference nextStateReference = normalizeReference(data.stateReference);

            if (nextMode == MODE_MATCH) {
                if (nextCriteria.isEmpty() || nextCriteria.size() > WiredArrayDefinition.MAX_FIELDS) return false;
                for (WiredArrayCriterion criterion : nextCriteria) {
                    if (definition.getArrayDefinition().getField(criterion.fieldId) == null
                            || !validComparison(criterion.comparison)
                            || !validReference(criterion.reference, room)) return false;
                }
                if (nextScope == SCOPE_SPECIFIC_INDEX) {
                    if (!rawIndexValid || !validAddress(nextIndex, definition, room)) return false;
                } else if (resultNeedsReference(nextResultMode)
                        && (!rawResultReferenceValid || !validReference(nextResultReference, room))) {
                    return false;
                }
            } else if ((nextStateCheck == STATE_LENGTH || nextStateCheck == STATE_AVAILABLE_INDEXES)
                    && (!rawStateReferenceValid || !validReference(nextStateReference, room))) {
                return false;
            }

            List<HabboItem> nextItems = parseItems(settings.getFurniIds(), room);
            this.variableType = nextType;
            this.variableItemId = data.variableItemId;
            this.ownerSource = WiredArrayRuntimeSupport.normalizeSource(definition.getArrayVariableType(), params[1]);
            this.conditionMode = nextMode;
            this.searchScope = nextScope;
            this.criteriaMode = params[4] == CRITERIA_ANY ? CRITERIA_ANY : CRITERIA_ALL;
            this.resultMode = nextResultMode;
            this.resultComparison = normalizeComparison(params[6]);
            this.stateCheck = nextStateCheck;
            this.stateComparison = normalizeComparison(params[8]);
            this.quantifier = params[9] == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            this.index = nextIndex;
            this.criteria = nextCriteria;
            this.resultReference = nextResultReference;
            this.stateReference = nextStateReference;
            this.selectedItems.clear();
            this.selectedItems.addAll(nextItems);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejected invalid Check Array settings for item {}", this.getId());
            return false;
        }
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(this.data(false, null));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String raw = set.getString("wired_data");
        if (raw == null || !raw.startsWith("{") || raw.length() > MAX_PAYLOAD_LENGTH) return;
        try {
            JsonData data = readData(raw);
            this.variableType =
                    WiredArrayVariableType.fromCode(data.variableType).code();
            this.variableItemId = Math.max(0, data.variableItemId);
            this.ownerSource = WiredArrayRuntimeSupport.normalizeSource(
                    WiredArrayVariableType.fromCode(this.variableType), data.ownerSource);
            this.conditionMode = data.conditionMode == MODE_STATE ? MODE_STATE : MODE_MATCH;
            this.searchScope = data.searchScope == SCOPE_SPECIFIC_INDEX ? SCOPE_SPECIFIC_INDEX : SCOPE_ANY_INDEX;
            this.criteriaMode = data.criteriaMode == CRITERIA_ANY ? CRITERIA_ANY : CRITERIA_ALL;
            this.resultMode = normalizeResultMode(data.resultMode);
            this.resultComparison = normalizeComparison(data.resultComparison);
            this.stateCheck = normalizeStateCheck(data.stateCheck);
            this.stateComparison = normalizeComparison(data.stateComparison);
            this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            this.index = normalizeAddress(data.index);
            this.criteria = normalizeCriteria(data.criteria);
            this.resultReference = normalizeReference(data.resultReference);
            this.stateReference = normalizeReference(data.stateReference);
            this.loadItems(data.itemIds, room);
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejected invalid Check Array wired_data for item {}", this.getId());
            this.onPickUp();
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.selectedItems.size());
        for (HabboItem item : this.selectedItems) message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(this.data(true, WiredArrayDefinitionSupport.collect(room))));
        message.appendInt(10);
        message.appendInt(this.variableType);
        message.appendInt(this.ownerSource);
        message.appendInt(this.conditionMode);
        message.appendInt(this.searchScope);
        message.appendInt(this.criteriaMode);
        message.appendInt(this.resultMode);
        message.appendInt(this.resultComparison);
        message.appendInt(this.stateCheck);
        message.appendInt(this.stateComparison);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.variableType = WiredArrayVariableType.ROOM.code();
        this.variableItemId = 0;
        this.ownerSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.conditionMode = MODE_MATCH;
        this.searchScope = SCOPE_ANY_INDEX;
        this.criteriaMode = CRITERIA_ALL;
        this.resultMode = RESULT_AT_LEAST_ONE;
        this.resultComparison = 2;
        this.stateCheck = STATE_EMPTY;
        this.stateComparison = 2;
        this.quantifier = QUANTIFIER_ALL;
        this.index = new WiredArrayAddress();
        this.criteria = new ArrayList<>();
        this.resultReference = new WiredArrayReference();
        this.stateReference = new WiredArrayReference();
        this.selectedItems.clear();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public WiredConditionType getType() {
        return type;
    }

    public boolean requiresTriggeringUser() {
        if (this.variableType == WiredArrayVariableType.USER.code()
                && this.ownerSource == WiredSourceUtil.SOURCE_TRIGGER) return true;
        return addressRequiresTrigger(this.index)
                || this.criteria.stream().anyMatch(value -> referenceRequiresTrigger(value.reference))
                || referenceRequiresTrigger(this.resultReference)
                || referenceRequiresTrigger(this.stateReference);
    }

    private JsonData data(boolean editor, List<WiredArrayDefinitionSupport.EditorDefinition> definitions) {
        JsonData data = new JsonData();
        data.variableType = this.variableType;
        data.variableItemId = this.variableItemId;
        data.ownerSource = this.ownerSource;
        data.conditionMode = this.conditionMode;
        data.searchScope = this.searchScope;
        data.criteriaMode = this.criteriaMode;
        data.resultMode = this.resultMode;
        data.resultComparison = this.resultComparison;
        data.stateCheck = this.stateCheck;
        data.stateComparison = this.stateComparison;
        data.quantifier = this.quantifier;
        data.index = this.index;
        data.criteria = this.criteria;
        data.resultReference = this.resultReference;
        data.stateReference = this.stateReference;
        data.itemIds = this.selectedItems.stream().map(HabboItem::getId).toList();
        if (editor && definitions != null) data.variableDefinitions = definitions;
        data.maxOwnersPerExecution = WiredArraySettings.maxOwnersPerExecution();
        return data;
    }

    private static JsonData readData(String raw) {
        if (raw == null || !raw.startsWith("{")) throw new IllegalArgumentException();
        JsonData data = WiredManager.getGson().fromJson(raw, JsonData.class);
        if (data == null) throw new IllegalArgumentException();
        return data;
    }

    private static List<WiredArrayCriterion> normalizeCriteria(List<WiredArrayCriterion> values) {
        List<WiredArrayCriterion> result = new ArrayList<>();
        if (values == null) return result;
        for (WiredArrayCriterion criterion : values) {
            if (result.size() >= WiredArrayDefinition.MAX_FIELDS) break;
            if (criterion == null) continue;
            criterion.comparison = normalizeComparison(criterion.comparison);
            criterion.reference = normalizeReference(criterion.reference);
            result.add(criterion);
        }
        return result;
    }

    private static boolean validRawCriteria(List<WiredArrayCriterion> values) {
        if (values == null || values.isEmpty() || values.size() > WiredArrayDefinition.MAX_FIELDS) return false;
        for (WiredArrayCriterion criterion : values) {
            if (criterion == null || !validComparison(criterion.comparison) || !validRawReference(criterion.reference))
                return false;
        }
        return true;
    }

    private static boolean validRawReference(WiredArrayReference value) {
        return value != null
                && (value.mode == WiredArrayReference.CONSTANT || value.mode == WiredArrayReference.VARIABLE)
                && (value.mode != WiredArrayReference.CONSTANT || value.value != null);
    }

    private static boolean validRawAddress(WiredArrayAddress value) {
        return value != null && (value.mode == WiredArrayAddress.CONSTANT || value.mode == WiredArrayAddress.VARIABLE);
    }

    private static WiredArrayReference normalizeReference(WiredArrayReference value) {
        WiredArrayReference result = value == null ? new WiredArrayReference() : value;
        result.mode = result.mode == WiredArrayReference.VARIABLE
                ? WiredArrayReference.VARIABLE
                : WiredArrayReference.CONSTANT;
        result.value = result.value == null ? "0" : result.value.trim();
        result.variableType =
                WiredArrayVariableType.fromCode(result.variableType).code();
        result.variableSource = WiredArrayRuntimeSupport.normalizeSource(
                WiredArrayVariableType.fromCode(result.variableType), result.variableSource);
        result.capturePath = result.capturePath == null ? "" : result.capturePath.trim();
        return result;
    }

    private static WiredArrayAddress normalizeAddress(WiredArrayAddress value) {
        WiredArrayAddress result = value == null ? new WiredArrayAddress() : value;
        result.mode =
                result.mode == WiredArrayAddress.VARIABLE ? WiredArrayAddress.VARIABLE : WiredArrayAddress.CONSTANT;
        result.variableType =
                WiredArrayVariableType.fromCode(result.variableType).code();
        result.variableSource = WiredArrayRuntimeSupport.normalizeSource(
                WiredArrayVariableType.fromCode(result.variableType), result.variableSource);
        result.capturePath = result.capturePath == null ? "" : result.capturePath.trim();
        return result;
    }

    private static boolean validAddress(WiredArrayAddress address, WiredArrayVariableDefinition definition, Room room) {
        if (address.mode == WiredArrayAddress.CONSTANT) {
            return address.value >= 0
                    && address.value < definition.getArrayDefinition().getMaxEntries();
        }
        return validScalar(address.variableType, address.variableItemId, address.capturePath, room);
    }

    private static boolean validReference(WiredArrayReference reference, Room room) {
        if (reference == null) return false;
        if (reference.mode == WiredArrayReference.CONSTANT) {
            try {
                Long.parseLong(reference.value);
                return true;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return reference.mode == WiredArrayReference.VARIABLE
                && validScalar(reference.variableType, reference.variableItemId, reference.capturePath, room);
    }

    private static boolean validScalar(int type, int itemId, String capturePath, Room room) {
        if (capturePath != null && !capturePath.isBlank()) {
            return WiredArrayRuntimeSupport.isValidCapturePath(capturePath);
        }
        WiredArrayVariableDefinition definition = WiredArrayDefinitionSupport.resolve(room, type, itemId);
        return definition != null && !definition.isArray() && definition.hasValue();
    }

    private static int normalizeResultMode(int value) {
        return value >= RESULT_ALL && value <= RESULT_MORE_THAN ? value : RESULT_AT_LEAST_ONE;
    }

    private static int normalizeStateCheck(int value) {
        return value == STATE_EMPTY || value == STATE_FULL || value == STATE_LENGTH || value == STATE_AVAILABLE_INDEXES
                ? value
                : STATE_EMPTY;
    }

    private static int normalizeComparison(int value) {
        return validComparison(value) ? value : 2;
    }

    private static boolean validComparison(int value) {
        return value >= 0 && value <= 5;
    }

    private static boolean resultNeedsReference(int value) {
        return value == RESULT_LESS_THAN || value == RESULT_EXACTLY || value == RESULT_MORE_THAN;
    }

    private static List<HabboItem> parseItems(int[] ids, Room room) {
        List<HabboItem> result = new ArrayList<>();
        if (ids == null) return result;
        int limit = Math.max(0, Math.min(WiredInputGuard.MAX_ABSOLUTE_FURNI_IDS, WiredManager.MAXIMUM_FURNI_SELECTION));
        if (ids.length > limit) throw new IllegalArgumentException("Too many selected furni");
        for (int id : ids) {
            HabboItem item = room.getHabboItem(id);
            if (item == null) throw new IllegalArgumentException("Selected furni is missing");
            if (!result.contains(item)) result.add(item);
        }
        return result;
    }

    private void loadItems(List<Integer> ids, Room room) {
        if (ids == null || room == null) return;
        for (int index = 0; index < Math.min(ids.size(), WiredManager.MAXIMUM_FURNI_SELECTION); index++) {
            Integer id = ids.get(index);
            HabboItem item = id == null ? null : room.getHabboItem(id);
            if (item != null && !this.selectedItems.contains(item)) this.selectedItems.add(item);
        }
    }

    private static boolean addressRequiresTrigger(WiredArrayAddress address) {
        return address != null
                && address.mode == WiredArrayAddress.VARIABLE
                && address.variableType == WiredArrayVariableType.USER.code()
                && address.variableSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    private static boolean referenceRequiresTrigger(WiredArrayReference reference) {
        return reference != null
                && reference.mode == WiredArrayReference.VARIABLE
                && reference.variableType == WiredArrayVariableType.USER.code()
                && reference.variableSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    private record ResolvedCriterion(int fieldId, int comparison, long reference) {}

    static final class JsonData {
        int variableType = WiredArrayVariableType.ROOM.code();
        int variableItemId;
        int ownerSource;
        int conditionMode = MODE_MATCH;
        int searchScope = SCOPE_ANY_INDEX;
        int criteriaMode = CRITERIA_ALL;
        int resultMode = RESULT_AT_LEAST_ONE;
        int resultComparison = 2;
        int stateCheck = STATE_EMPTY;
        int stateComparison = 2;
        int quantifier = QUANTIFIER_ALL;
        WiredArrayAddress index = new WiredArrayAddress();
        List<WiredArrayCriterion> criteria = new ArrayList<>();
        WiredArrayReference resultReference = new WiredArrayReference();
        WiredArrayReference stateReference = new WiredArrayReference();
        List<Integer> itemIds = new ArrayList<>();
        List<WiredArrayDefinitionSupport.EditorDefinition> variableDefinitions = new ArrayList<>();
        int maxOwnersPerExecution;
        int metadataVersion = 1;
    }
}
