package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLargePayload;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomArrayVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEditorSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayReference;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayView;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded structural modifier for list and slot arrays. */
public final class WiredEffectModifyArray extends InteractionWiredEffect implements WiredLargePayload {
    public static final WiredEffectType type = WiredEffectType.MODIFY_ARRAY;
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectModifyArray.class);
    private static final int MAX_PAYLOAD_LENGTH = 32_768;

    private int variableType = WiredArrayVariableType.ROOM.code();
    private int variableItemId;
    private int operation = WiredArrayStructuralOperation.APPEND.code();
    private int ownerSource = WiredSourceUtil.SOURCE_TRIGGER;
    private WiredArrayAddress firstIndex = new WiredArrayAddress();
    private WiredArrayAddress secondIndex = new WiredArrayAddress();
    private Map<Integer, WiredArrayReference> fieldInputs = new LinkedHashMap<>();
    private final List<HabboItem> selectedItems = new ArrayList<>();

    public WiredEffectModifyArray(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectModifyArray(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null) return;
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(ctx.room(), this.variableType, this.variableItemId);
        WiredArrayStructuralOperation structuralOperation = WiredArrayStructuralOperation.fromCode(this.operation);
        if (definition == null
                || !definition.isArray()
                || structuralOperation == null
                || !structuralOperation.supports(definition.getArrayDefinition().getMode())) {
            this.logFailure(ctx, WiredArrayMutationResult.INVALID_OPERATION);
            return;
        }

        List<WiredArrayRuntimeSupport.Owner> owners =
                WiredArrayRuntimeSupport.resolveOwners(ctx, this.selectedItems, definition, this.ownerSource);
        if (owners.isEmpty()) {
            this.logFailure(ctx, WiredArrayMutationResult.INVALID_INDEX);
            return;
        }

        boolean changed = false;
        for (WiredArrayRuntimeSupport.Owner owner : owners) {
            WiredArrayView before = WiredArrayRuntimeSupport.getValue(ctx, definition, owner);
            Integer first = structuralOperation.requiresFirstIndex()
                    ? WiredArrayRuntimeSupport.resolveIndex(ctx, this.selectedItems, this.firstIndex, definition, owner)
                    : 0;
            Integer second = structuralOperation.requiresSecondIndex()
                    ? WiredArrayRuntimeSupport.resolveIndex(
                            ctx, this.selectedItems, this.secondIndex, definition, owner)
                    : 0;
            if (first == null || second == null) {
                this.logFailure(ctx, WiredArrayMutationResult.INVALID_INDEX);
                continue;
            }

            Map<Integer, Long> values =
                    structuralOperation.requiresEntryValues() ? this.resolveEntryValues(ctx, definition, owner) : null;
            if (structuralOperation.requiresEntryValues() && values == null) {
                this.logFailure(ctx, WiredArrayMutationResult.MISSING_FIELD);
                continue;
            }

            RoomArrayVariableManager.MutationOutcome outcome =
                    WiredArrayRuntimeSupport.mutate(ctx, definition, owner, structuralOperation, first, second, values);
            if (outcome.changed()) {
                changed = true;
                int oldLength = before == null ? 0 : before.getLengthForCondition();
                int newLength =
                        outcome.value() == null ? oldLength : outcome.value().getLengthForCondition();
                WiredArrayRuntimeSupport.dispatchChange(
                        ctx,
                        definition,
                        owner,
                        WiredArrayChange.structural(structuralOperation, first, second, oldLength, newLength));
            } else if (outcome.result() != WiredArrayMutationResult.NO_CHANGE) {
                this.logFailure(ctx, outcome.result());
            }
        }

        if (changed) {
            this.activateBox(ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = settings.getRoom();
        if (room == null || room.getId() != this.getRoomId()) throw new WiredSaveException("Room not found");
        int[] params = settings.getIntParams();
        if (params.length < 3) throw new WiredSaveException("Invalid Modify Array data");
        if (settings.getStringParam() != null && settings.getStringParam().length() > MAX_PAYLOAD_LENGTH) {
            throw new WiredSaveException("Modify Array data is too large");
        }

        JsonData data = readData(settings.getStringParam());
        int nextType = WiredArrayVariableType.fromCode(params[0]).code();
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, nextType, data.variableItemId);
        WiredArrayStructuralOperation nextOperation = WiredArrayStructuralOperation.fromCode(params[1]);
        if (definition == null || !definition.isArray()) throw new WiredSaveException("Choose an array variable");
        if (nextOperation == null
                || !nextOperation.supports(definition.getArrayDefinition().getMode())) {
            throw new WiredSaveException("Invalid operation for this array mode");
        }

        validateAddress(data.firstIndex, definition, nextOperation.requiresFirstIndex(), room);
        validateAddress(data.secondIndex, definition, nextOperation.requiresSecondIndex(), room);
        validateRawInputs(data.fieldInputs);
        Map<Integer, WiredArrayReference> nextInputs = WiredArrayEditorSupport.normalizeInputs(data.fieldInputs);
        if (nextOperation.requiresEntryValues()) validateInputs(nextInputs, definition, room);

        int maxDelay = WiredInputGuard.maxDelay();
        if (settings.getDelay() < 0 || settings.getDelay() > maxDelay) {
            throw new WiredSaveException("Delay too long");
        }
        List<HabboItem> nextSelected = WiredArrayEditorSupport.parseItems(settings.getFurniIds(), room);

        this.variableType = nextType;
        this.variableItemId = data.variableItemId;
        this.operation = nextOperation.code();
        this.ownerSource = WiredArrayRuntimeSupport.normalizeSource(definition.getArrayVariableType(), params[2]);
        this.firstIndex = WiredArrayEditorSupport.normalizeAddress(data.firstIndex);
        this.secondIndex = WiredArrayEditorSupport.normalizeAddress(data.secondIndex);
        this.fieldInputs = nextInputs;
        this.selectedItems.clear();
        this.selectedItems.addAll(nextSelected);
        this.setDelay(settings.getDelay());
        return true;
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
            WiredArrayStructuralOperation loaded = WiredArrayStructuralOperation.fromCode(data.operation);
            this.operation = loaded == null ? WiredArrayStructuralOperation.APPEND.code() : loaded.code();
            this.ownerSource = WiredArrayRuntimeSupport.normalizeSource(
                    WiredArrayVariableType.fromCode(this.variableType), data.ownerSource);
            this.firstIndex = WiredArrayEditorSupport.normalizeAddress(data.firstIndex);
            this.secondIndex = WiredArrayEditorSupport.normalizeAddress(data.secondIndex);
            this.fieldInputs = WiredArrayEditorSupport.normalizeInputs(data.fieldInputs);
            this.setDelay(Math.min(Math.max(0, data.delay), WiredInputGuard.maxDelay()));
            WiredArrayEditorSupport.loadItems(this.selectedItems, data.itemIds, room);
        } catch (Exception exception) {
            LOGGER.warn("Rejected invalid Modify Array wired_data for item {}", this.getId());
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
        message.appendInt(3);
        message.appendInt(this.variableType);
        message.appendInt(this.operation);
        message.appendInt(this.ownerSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.variableType = WiredArrayVariableType.ROOM.code();
        this.variableItemId = 0;
        this.operation = WiredArrayStructuralOperation.APPEND.code();
        this.ownerSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.firstIndex = new WiredArrayAddress();
        this.secondIndex = new WiredArrayAddress();
        this.fieldInputs = new LinkedHashMap<>();
        this.selectedItems.clear();
        this.setDelay(0);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        if (this.variableType == WiredArrayVariableType.USER.code()
                && this.ownerSource == WiredSourceUtil.SOURCE_TRIGGER) return true;
        return WiredArrayEditorSupport.requiresTrigger(this.firstIndex)
                || WiredArrayEditorSupport.requiresTrigger(this.secondIndex)
                || this.fieldInputs.values().stream().anyMatch(WiredArrayEditorSupport::requiresTrigger);
    }

    private Map<Integer, Long> resolveEntryValues(
            WiredContext ctx, WiredArrayVariableDefinition definition, WiredArrayRuntimeSupport.Owner owner) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (var field : definition.getArrayDefinition().getFields()) {
            WiredArrayReference reference = this.fieldInputs.get(field.getId());
            if (reference == null) {
                result.put(field.getId(), 0L);
                continue;
            }
            Long value = WiredArrayRuntimeSupport.resolveReference(ctx, this.selectedItems, reference, owner);
            if (value == null) return null;
            result.put(field.getId(), value);
        }
        return result;
    }

    private JsonData data(boolean editor, List<WiredArrayDefinitionSupport.EditorDefinition> definitions) {
        JsonData data = new JsonData();
        data.variableType = this.variableType;
        data.variableItemId = this.variableItemId;
        data.operation = this.operation;
        data.ownerSource = this.ownerSource;
        data.firstIndex = this.firstIndex;
        data.secondIndex = this.secondIndex;
        data.fieldInputs = this.fieldInputs;
        data.delay = this.getDelay();
        data.itemIds = this.selectedItems.stream().map(HabboItem::getId).toList();
        if (editor && definitions != null) data.variableDefinitions = definitions;
        data.maxOwnersPerExecution = WiredArraySettings.maxOwnersPerExecution();
        return data;
    }

    private static void validateAddress(
            WiredArrayAddress address, WiredArrayVariableDefinition definition, boolean required, Room room)
            throws WiredSaveException {
        if (!required) return;
        if (!WiredArrayEditorSupport.validRawAddress(address)) {
            throw new WiredSaveException("Invalid array index");
        }
        WiredArrayAddress normalized = WiredArrayEditorSupport.normalizeAddress(address);
        if (normalized.mode == WiredArrayAddress.CONSTANT) {
            if (normalized.value < 0
                    || normalized.value >= definition.getArrayDefinition().getMaxEntries()) {
                throw new WiredSaveException("Array index is outside the configured maximum");
            }
            return;
        }
        if (!WiredArrayEditorSupport.isValidScalarReference(
                normalized.variableType, normalized.variableItemId, normalized.capturePath, room)) {
            throw new WiredSaveException("Choose a scalar variable with a value");
        }
    }

    private static void validateInputs(
            Map<Integer, WiredArrayReference> inputs, WiredArrayVariableDefinition definition, Room room)
            throws WiredSaveException {
        if (inputs.size() > WiredArrayDefinition.MAX_FIELDS) {
            throw new WiredSaveException("Too many array field values");
        }
        for (Map.Entry<Integer, WiredArrayReference> entry : inputs.entrySet()) {
            if (definition.getArrayDefinition().getField(entry.getKey()) == null) {
                throw new WiredSaveException("Unknown array field");
            }
            WiredArrayReference reference = entry.getValue();
            if (!WiredArrayEditorSupport.isValidReference(reference, room)) {
                throw new WiredSaveException("Invalid array field input");
            }
        }
    }

    private static void validateRawInputs(Map<Integer, WiredArrayReference> inputs) throws WiredSaveException {
        if (inputs == null) return;
        if (inputs.size() > WiredArrayDefinition.MAX_FIELDS) {
            throw new WiredSaveException("Too many array field values");
        }
        for (Map.Entry<Integer, WiredArrayReference> entry : inputs.entrySet()) {
            if (entry.getKey() == null
                    || entry.getKey() <= 0
                    || !WiredArrayEditorSupport.validRawReference(entry.getValue())) {
                throw new WiredSaveException("Invalid array field input");
            }
        }
    }

    private static JsonData readData(String raw) throws WiredSaveException {
        if (raw == null || !raw.startsWith("{")) throw new WiredSaveException("Invalid Modify Array data");
        try {
            JsonData data = WiredManager.getGson().fromJson(raw, JsonData.class);
            if (data == null) throw new IllegalArgumentException();
            return data;
        } catch (RuntimeException exception) {
            throw new WiredSaveException("Invalid Modify Array data");
        }
    }

    private void logFailure(WiredContext ctx, WiredArrayMutationResult result) {
        ctx.debug("Modify Array %s failed: %s", this.getId(), result.name());
    }

    static final class JsonData {
        int variableType = WiredArrayVariableType.ROOM.code();
        int variableItemId;
        int operation = WiredArrayStructuralOperation.APPEND.code();
        int ownerSource;
        int delay;
        WiredArrayAddress firstIndex = new WiredArrayAddress();
        WiredArrayAddress secondIndex = new WiredArrayAddress();
        Map<Integer, WiredArrayReference> fieldInputs = new LinkedHashMap<>();
        List<Integer> itemIds = new ArrayList<>();
        List<WiredArrayDefinitionSupport.EditorDefinition> variableDefinitions = new ArrayList<>();
        int maxOwnersPerExecution;
        int metadataVersion = 1;
    }
}
