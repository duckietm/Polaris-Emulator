package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLargePayload;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayCaptureSnapshot;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayCriterion;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEditorSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayView;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures one array entry into an execution-scoped {@code @array.<alias>} namespace. */
public final class WiredExtraArrayCaptureVariable extends InteractionWiredExtra implements WiredLargePayload {
    public static final int CODE = 116;
    public static final int MODE_INDEX = 0;
    public static final int MODE_FIND = 1;
    public static final int DIRECTION_FIRST = 0;
    public static final int DIRECTION_LAST = 1;
    public static final int DIRECTION_RANDOM = 2;
    public static final int CRITERIA_ALL = 0;
    public static final int CRITERIA_ANY = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredExtraArrayCaptureVariable.class);
    private static final int MAX_PAYLOAD_LENGTH = 32_768;

    private int variableType = WiredArrayVariableType.ROOM.code();
    private int variableItemId;
    private int contextVariableItemId;
    private int ownerSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int captureMode = MODE_INDEX;
    private int findDirection = DIRECTION_FIRST;
    private int criteriaMode = CRITERIA_ALL;
    private WiredArrayAddress index = new WiredArrayAddress();
    private List<WiredArrayCriterion> criteria = new ArrayList<>();
    private final List<HabboItem> selectedItems = new ArrayList<>();

    public WiredExtraArrayCaptureVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraArrayCaptureVariable(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = settings.getRoom();
        int[] params = settings.getIntParams();
        String raw = settings.getStringParam();
        if (room == null || room.getId() != this.getRoomId()) throw new WiredSaveException("Room not found");
        if (params == null || params.length < 5 || raw == null || raw.length() > MAX_PAYLOAD_LENGTH) {
            throw new WiredSaveException("Invalid Array Capturer data");
        }

        JsonData data = readData(raw);
        int nextType = WiredArrayVariableType.fromCode(params[0]).code();
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, nextType, data.variableItemId);
        if (definition == null || !definition.isArray()) {
            throw new WiredSaveException("Choose an array variable");
        }

        WiredExtraContextVariable contextDefinition =
                WiredContextVariableSupport.getDefinition(room, data.contextVariableItemId);
        if (contextDefinition == null || contextDefinition.isArray() || !contextDefinition.hasValue()) {
            throw new WiredSaveException("Choose a scalar Context variable with a value");
        }
        if (hasDuplicateAlias(room, contextDefinition.getVariableName())) {
            throw new WiredSaveException("Context capture variable is already used in this Wired stack");
        }

        int nextMode = params[2] == MODE_FIND ? MODE_FIND : MODE_INDEX;
        boolean rawIndexValid = WiredArrayEditorSupport.validRawAddress(data.index);
        WiredArrayAddress nextIndex = WiredArrayEditorSupport.normalizeAddress(data.index);
        if (nextMode == MODE_FIND && !WiredArrayEditorSupport.validRawCriteria(data.criteria)) {
            throw new WiredSaveException("Add between 1 and " + WiredArrayDefinition.MAX_FIELDS + " valid criteria");
        }
        List<WiredArrayCriterion> nextCriteria = WiredArrayEditorSupport.normalizeCriteria(data.criteria);
        if (nextMode == MODE_INDEX) {
            if (!rawIndexValid) throw new WiredSaveException("Invalid array index");
            if (!WiredArrayEditorSupport.isValidAddress(nextIndex, definition, room)) {
                throw new WiredSaveException("Invalid array index");
            }
        } else {
            if (nextCriteria.isEmpty() || nextCriteria.size() > WiredArrayDefinition.MAX_FIELDS) {
                throw new WiredSaveException("Add between 1 and " + WiredArrayDefinition.MAX_FIELDS + " criteria");
            }
            for (WiredArrayCriterion criterion : nextCriteria) {
                if (definition.getArrayDefinition().getField(criterion.fieldId) == null
                        || !WiredArrayEditorSupport.validComparison(criterion.comparison)) {
                    throw new WiredSaveException("Invalid array criterion");
                }
                if (!WiredArrayEditorSupport.isValidReference(criterion.reference, room)) {
                    throw new WiredSaveException("Invalid array criterion reference");
                }
            }
        }

        this.variableType = nextType;
        this.variableItemId = data.variableItemId;
        this.contextVariableItemId = data.contextVariableItemId;
        this.ownerSource = WiredArrayRuntimeSupport.normalizeSource(definition.getArrayVariableType(), params[1]);
        this.captureMode = nextMode;
        this.findDirection = normalizeDirection(params[3]);
        this.criteriaMode = params[4] == CRITERIA_ANY ? CRITERIA_ANY : CRITERIA_ALL;
        this.index = nextIndex;
        this.criteria = nextCriteria;
        this.selectedItems.clear();
        this.selectedItems.addAll(WiredArrayEditorSupport.parseItems(settings.getFurniIds(), room));
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
            this.contextVariableItemId = Math.max(0, data.contextVariableItemId);
            this.ownerSource = WiredArrayRuntimeSupport.normalizeSource(
                    WiredArrayVariableType.fromCode(this.variableType), data.ownerSource);
            this.captureMode = data.captureMode == MODE_FIND ? MODE_FIND : MODE_INDEX;
            this.findDirection = normalizeDirection(data.findDirection);
            this.criteriaMode = data.criteriaMode == CRITERIA_ANY ? CRITERIA_ANY : CRITERIA_ALL;
            this.index = WiredArrayEditorSupport.normalizeAddress(data.index);
            this.criteria = WiredArrayEditorSupport.normalizeCriteria(data.criteria);
            WiredArrayEditorSupport.loadItems(this.selectedItems, data.itemIds, room);
        } catch (Exception exception) {
            LOGGER.warn("Rejected invalid Array Capturer wired_data for item {}", this.getId());
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
        message.appendInt(5);
        message.appendInt(this.variableType);
        message.appendInt(this.ownerSource);
        message.appendInt(this.captureMode);
        message.appendInt(this.findDirection);
        message.appendInt(this.criteriaMode);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onPickUp() {
        this.variableType = WiredArrayVariableType.ROOM.code();
        this.variableItemId = 0;
        this.contextVariableItemId = 0;
        this.ownerSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.captureMode = MODE_INDEX;
        this.findDirection = DIRECTION_FIRST;
        this.criteriaMode = CRITERIA_ALL;
        this.index = new WiredArrayAddress();
        this.criteria = new ArrayList<>();
        this.selectedItems.clear();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getCaptureAlias(Room room) {
        WiredExtraContextVariable definition =
                WiredContextVariableSupport.getDefinition(room, this.contextVariableItemId);
        return definition == null ? "" : definition.getVariableName();
    }

    /** Read-only context projections exposed to ordinary variable pickers. */
    public List<String> getCaptureProjectionNames(Room room) {
        String alias = this.getCaptureAlias(room);
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, this.variableType, this.variableItemId);
        if (alias.isBlank() || definition == null || !definition.isArray()) return List.of();

        List<String> names = new ArrayList<>();
        names.add("@array." + alias + ".found");
        names.add("@array." + alias + ".index");
        for (var field : definition.getArrayDefinition().getFields()) {
            names.add(alias + "." + field.getName());
        }
        return List.copyOf(names);
    }

    public void publishMissing(WiredContext ctx) {
        if (ctx == null) return;
        String alias = this.getCaptureAlias(ctx.room());
        if (!alias.isBlank()) {
            ctx.contextVariables().publishArrayCapture(alias, WiredArrayCaptureSnapshot.missing(0));
            WiredContextVariableSupport.assignVariable(ctx, ctx.room(), this.contextVariableItemId, -1, true);
        }
    }

    /** Capture failures remain inspectable through {@code found=0}; they do not abort the stack. */
    public boolean capture(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) return false;
        WiredExtraContextVariable contextDefinition =
                WiredContextVariableSupport.getDefinition(ctx.room(), this.contextVariableItemId);
        String alias = contextDefinition == null ? "" : contextDefinition.getVariableName();
        if (alias.isBlank() || contextDefinition.isArray() || !contextDefinition.hasValue()) return false;

        ctx.contextVariables().publishArrayCapture(alias, WiredArrayCaptureSnapshot.missing(0));
        WiredContextVariableSupport.assignVariable(ctx, ctx.room(), this.contextVariableItemId, -1, true);

        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(ctx.room(), this.variableType, this.variableItemId);
        if (definition == null || !definition.isArray()) return this.captureFailed(ctx, "UNKNOWN_ARRAY");

        List<WiredArrayRuntimeSupport.Owner> owners =
                WiredArrayRuntimeSupport.resolveOwners(ctx, this.selectedItems, definition, this.ownerSource);
        if (owners.isEmpty()) return this.captureFailed(ctx, "MISSING_OWNER");

        int inspectedLength = 0;
        for (WiredArrayRuntimeSupport.Owner owner : owners) {
            WiredArrayView value = WiredArrayRuntimeSupport.getValue(ctx, definition, owner);
            if (value == null) continue;
            inspectedLength = Math.max(inspectedLength, value.getLengthForCondition());
            Integer matchedIndex = this.captureMode == MODE_FIND
                    ? this.findMatchingIndex(ctx, owner, value)
                    : WiredArrayRuntimeSupport.resolveIndex(ctx, this.selectedItems, this.index, definition, owner);
            if (matchedIndex == null) continue;
            WiredArrayEntry entry = value.getEntry(matchedIndex);
            if (entry == null) continue;

            ctx.contextVariables()
                    .publishArrayCapture(
                            alias,
                            WiredArrayCaptureSnapshot.found(
                                    definition.getArrayDefinition(),
                                    matchedIndex,
                                    value.getLengthForCondition(),
                                    entry));
            WiredContextVariableSupport.assignVariable(ctx, ctx.room(), this.contextVariableItemId, matchedIndex, true);
            return true;
        }

        ctx.contextVariables().publishArrayCapture(alias, WiredArrayCaptureSnapshot.missing(inspectedLength));
        return this.captureFailed(ctx, "ENTRY_NOT_FOUND");
    }

    private Integer findMatchingIndex(WiredContext ctx, WiredArrayRuntimeSupport.Owner owner, WiredArrayView value) {
        List<WiredArrayEditorSupport.ResolvedCriterion> resolved =
                WiredArrayEditorSupport.resolveCriteria(ctx, this.selectedItems, this.criteria, owner);
        if (resolved == null || resolved.isEmpty()) return null;

        List<Integer> matches = new ArrayList<>();
        for (Map.Entry<Integer, WiredArrayEntry> candidate : value.entriesView().entrySet()) {
            if (WiredArrayEditorSupport.matchesEntry(
                    candidate.getValue(), resolved, this.criteriaMode == CRITERIA_ANY)) {
                if (this.findDirection == DIRECTION_FIRST) return candidate.getKey();
                matches.add(candidate.getKey());
            }
        }
        if (matches.isEmpty()) return null;
        if (this.findDirection == DIRECTION_LAST) return matches.get(matches.size() - 1);
        return matches.get(ThreadLocalRandom.current().nextInt(matches.size()));
    }

    private boolean captureFailed(WiredContext ctx, String reason) {
        ctx.debug("Array Capturer %s failed: %s", this.getId(), reason);
        return false;
    }

    private boolean hasDuplicateAlias(Room room, String alias) {
        if (room == null || room.getRoomSpecialTypes() == null || alias == null || alias.isBlank()) return false;
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(this.getX(), this.getY())) {
            if (extra instanceof WiredExtraArrayCaptureVariable capture
                    && capture.getId() != this.getId()
                    && alias.equalsIgnoreCase(capture.getCaptureAlias(room))) return true;
        }
        return false;
    }

    private JsonData data(boolean editor, List<WiredArrayDefinitionSupport.EditorDefinition> definitions) {
        JsonData data = new JsonData();
        data.variableType = this.variableType;
        data.variableItemId = this.variableItemId;
        data.contextVariableItemId = this.contextVariableItemId;
        data.ownerSource = this.ownerSource;
        data.captureMode = this.captureMode;
        data.findDirection = this.findDirection;
        data.criteriaMode = this.criteriaMode;
        data.index = this.index;
        data.criteria = this.criteria;
        data.itemIds = this.selectedItems.stream().map(HabboItem::getId).toList();
        if (editor && definitions != null) data.variableDefinitions = definitions;
        data.maxOwnersPerExecution = WiredArraySettings.maxOwnersPerExecution();
        return data;
    }

    private static JsonData readData(String raw) throws WiredSaveException {
        if (raw == null || !raw.startsWith("{")) throw new WiredSaveException("Invalid Array Capturer data");
        try {
            JsonData data = WiredManager.getGson().fromJson(raw, JsonData.class);
            if (data == null) throw new IllegalArgumentException();
            return data;
        } catch (RuntimeException exception) {
            throw new WiredSaveException("Invalid Array Capturer data");
        }
    }

    private static int normalizeDirection(int value) {
        return value == DIRECTION_LAST || value == DIRECTION_RANDOM ? value : DIRECTION_FIRST;
    }

    static final class JsonData {
        int variableType = WiredArrayVariableType.ROOM.code();
        int variableItemId;
        int contextVariableItemId;
        int ownerSource;
        int captureMode = MODE_INDEX;
        int findDirection = DIRECTION_FIRST;
        int criteriaMode = CRITERIA_ALL;
        WiredArrayAddress index = new WiredArrayAddress();
        List<WiredArrayCriterion> criteria = new ArrayList<>();
        List<Integer> itemIds = new ArrayList<>();
        List<WiredArrayDefinitionSupport.EditorDefinition> variableDefinitions = new ArrayList<>();
        int maxOwnersPerExecution;
        int metadataVersion = 1;
    }
}
