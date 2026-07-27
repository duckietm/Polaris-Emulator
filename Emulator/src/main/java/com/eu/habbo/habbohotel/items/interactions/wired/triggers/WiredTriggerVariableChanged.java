package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChangeType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerVariableChanged extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.VARIABLE_CHANGED;

    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_CONTEXT = 2;
    public static final int TARGET_ROOM = 3;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final int ARRAY_CREATED = 1;
    private static final int ARRAY_CHANGED = 1 << 1;
    private static final int ARRAY_APPENDED = 1 << 2;
    private static final int ARRAY_INSERTED = 1 << 3;
    private static final int ARRAY_REMOVED = 1 << 4;
    private static final int ARRAY_INDEX_CLEARED = 1 << 5;
    private static final int ARRAY_REPLACED = 1 << 6;
    private static final int ARRAY_MOVED = 1 << 7;
    private static final int ARRAY_SWAPPED = 1 << 8;
    private static final int ARRAY_FIELD_CHANGED = 1 << 9;
    private static final int ARRAY_LENGTH_CHANGED = 1 << 10;
    private static final int ARRAY_CLEARED = 1 << 11;
    private static final int ARRAY_SHUFFLED = 1 << 12;
    private static final int ARRAY_SPECIFIC = ARRAY_APPENDED
            | ARRAY_INSERTED
            | ARRAY_REMOVED
            | ARRAY_INDEX_CLEARED
            | ARRAY_REPLACED
            | ARRAY_MOVED
            | ARRAY_SWAPPED
            | ARRAY_FIELD_CHANGED
            | ARRAY_LENGTH_CHANGED
            | ARRAY_CLEARED
            | ARRAY_SHUFFLED;

    private String variableToken = "";
    private int variableItemId = 0;
    private int targetType = TARGET_USER;
    private boolean createdEnabled = true;
    private boolean valueChangedEnabled = true;
    private boolean increasedEnabled = true;
    private boolean decreasedEnabled = true;
    private boolean unchangedEnabled = true;
    private boolean deletedEnabled = true;
    private int arrayOptions = ARRAY_CREATED | ARRAY_CHANGED;
    private int arrayFieldId;
    private boolean arrayDataConfigured;

    public WiredTriggerVariableChanged(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerVariableChanged(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event == null || event.getType() != WiredEvent.Type.VARIABLE_CHANGED) {
            return false;
        }

        if (event.getVariableTargetType() != this.targetType
                || event.getVariableDefinitionItemId() != this.variableItemId) {
            return false;
        }

        if (event.getArrayChange() != null) return this.matchesArrayChange(event.getArrayChange());

        if (this.createdEnabled && event.isVariableCreated()) {
            return true;
        }

        if (this.deletedEnabled && event.isVariableDeleted()) {
            return true;
        }

        if (!this.valueChangedEnabled) {
            return false;
        }

        return switch (event.getVariableChangeKind()) {
            case INCREASED -> this.increasedEnabled;
            case DECREASED -> this.decreasedEnabled;
            case UNCHANGED -> this.unchangedEnabled;
            default -> false;
        };
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        String editorToken = this.variableToken == null ? "" : this.variableToken;
        if (this.resolveArrayDefinition(room) != null) {
            editorToken += "\t" + WiredManager.getGson().toJson(new ArrayData(this.arrayOptions, this.arrayFieldId));
        }
        message.appendString(editorToken);
        message.appendInt(7);
        message.appendInt(this.targetType);
        message.appendInt(this.createdEnabled ? 1 : 0);
        message.appendInt(this.valueChangedEnabled ? 1 : 0);
        message.appendInt(this.increasedEnabled ? 1 : 0);
        message.appendInt(this.decreasedEnabled ? 1 : 0);
        message.appendInt(this.unchangedEnabled ? 1 : 0);
        message.appendInt(this.deletedEnabled ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        int[] params = settings.getIntParams();

        this.targetType = normalizeTargetType((params.length > 0) ? params[0] : TARGET_USER);
        this.createdEnabled = (params.length <= 1) || (params[1] == 1);
        this.valueChangedEnabled = (params.length <= 2) || (params[2] == 1);
        this.increasedEnabled = (params.length <= 3) || (params[3] == 1);
        this.decreasedEnabled = (params.length <= 4) || (params[4] == 1);
        this.unchangedEnabled = (params.length <= 5) || (params[5] == 1);
        this.deletedEnabled = (params.length <= 6) || (params[6] == 1);
        String[] stringParts = settings.getStringParam() == null
                ? new String[0]
                : settings.getStringParam().split("\\t", 2);
        this.setVariableToken(normalizeVariableToken(stringParts.length > 0 ? stringParts[0] : ""));
        ArrayData arrayData = parseArrayData(stringParts.length > 1 ? stringParts[1] : null);
        this.arrayOptions = arrayData.options;
        this.arrayFieldId = Math.max(0, arrayData.fieldId);
        this.normalizeOptions();

        if (this.variableItemId <= 0) {
            throw new WiredTriggerSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        if (room == null || !this.isValidDefinition(room)) {
            throw new WiredTriggerSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        WiredArrayVariableDefinition arrayDefinition = this.resolveArrayDefinition(room);
        this.arrayDataConfigured = arrayDefinition != null;
        if (arrayDefinition != null) {
            if ((this.arrayOptions & (ARRAY_CREATED | ARRAY_CHANGED | ARRAY_SPECIFIC)) == 0) return false;
            if (this.arrayFieldId > 0 && arrayDefinition.getArrayDefinition().getField(this.arrayFieldId) == null)
                return false;
        } else if (!this.hasAnyEnabledOption()) {
            return false;
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.variableToken,
                        this.variableItemId,
                        this.targetType,
                        this.createdEnabled,
                        this.valueChangedEnabled,
                        this.increasedEnabled,
                        this.decreasedEnabled,
                        this.unchangedEnabled,
                        this.deletedEnabled,
                        this.arrayDataConfigured ? this.arrayOptions : null,
                        this.arrayDataConfigured ? this.arrayFieldId : null));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (!wiredData.startsWith("{")) {
            this.setVariableToken(normalizeVariableToken(wiredData));
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.targetType = normalizeTargetType(data.targetType);
        this.createdEnabled = data.createdEnabled;
        this.valueChangedEnabled = data.valueChangedEnabled;
        this.increasedEnabled = data.increasedEnabled;
        this.decreasedEnabled = data.decreasedEnabled;
        this.unchangedEnabled = data.unchangedEnabled;
        this.deletedEnabled = data.deletedEnabled;
        this.arrayDataConfigured = data.arrayOptions != null || data.arrayFieldId != null;
        int loadedArrayOptions = data.arrayOptions == null ? 0 : data.arrayOptions;
        this.arrayOptions = loadedArrayOptions == 0 ? ARRAY_CREATED | ARRAY_CHANGED : loadedArrayOptions;
        this.arrayFieldId = Math.max(0, data.arrayFieldId == null ? 0 : data.arrayFieldId);
        this.setVariableToken(normalizeVariableToken(
                (data.variableToken != null)
                        ? data.variableToken
                        : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
        this.normalizeOptions();
    }

    @Override
    public void onPickUp() {
        this.variableToken = "";
        this.variableItemId = 0;
        this.targetType = TARGET_USER;
        this.createdEnabled = true;
        this.valueChangedEnabled = true;
        this.increasedEnabled = true;
        this.decreasedEnabled = true;
        this.unchangedEnabled = true;
        this.deletedEnabled = true;
        this.arrayOptions = ARRAY_CREATED | ARRAY_CHANGED;
        this.arrayFieldId = 0;
        this.arrayDataConfigured = false;
    }

    private void setVariableToken(String token) {
        this.variableToken = normalizeVariableToken(token);
        this.variableItemId = getCustomItemId(this.variableToken);
    }

    private void normalizeOptions() {
        if (!this.valueChangedEnabled) {
            this.increasedEnabled = false;
            this.decreasedEnabled = false;
            this.unchangedEnabled = false;
        }

        if (this.targetType == TARGET_ROOM) {
            this.createdEnabled = false;
            this.deletedEnabled = false;
        }
    }

    private boolean hasAnyEnabledOption() {
        return this.createdEnabled
                || this.deletedEnabled
                || (this.valueChangedEnabled
                        && (this.increasedEnabled || this.decreasedEnabled || this.unchangedEnabled));
    }

    private boolean isValidDefinition(Room room) {
        WiredVariableDefinitionInfo definitionInfo =
                switch (this.targetType) {
                    case TARGET_FURNI -> room.getFurniVariableManager().getDefinitionInfo(this.variableItemId);
                    case TARGET_CONTEXT -> WiredContextVariableSupport.getDefinitionInfo(room, this.variableItemId);
                    case TARGET_ROOM -> room.getRoomVariableManager().getDefinitionInfo(this.variableItemId);
                    default -> room.getUserVariableManager().getDefinitionInfo(this.variableItemId);
                };

        return definitionInfo != null;
    }

    private WiredArrayVariableDefinition resolveArrayDefinition(Room room) {
        WiredArrayVariableType type =
                switch (this.targetType) {
                    case TARGET_FURNI -> WiredArrayVariableType.FURNI;
                    case TARGET_CONTEXT -> WiredArrayVariableType.CONTEXT;
                    case TARGET_ROOM -> WiredArrayVariableType.ROOM;
                    default -> WiredArrayVariableType.USER;
                };
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, type.code(), this.variableItemId);
        return definition != null && definition.isArray() ? definition : null;
    }

    private boolean matchesArrayChange(WiredArrayChange change) {
        if (change.changeType() == WiredArrayChangeType.ARRAY_CREATED) {
            return (this.arrayOptions & ARRAY_CREATED) != 0;
        }
        if ((this.arrayOptions & ARRAY_CHANGED) == 0) return false;
        int selectedSpecific = this.arrayOptions & ARRAY_SPECIFIC;
        if (selectedSpecific == 0) return true;
        if ((this.arrayOptions & ARRAY_LENGTH_CHANGED) != 0 && change.oldLength() != change.newLength()) return true;
        int required =
                switch (change.changeType()) {
                    case WiredArrayChangeType.ENTRY_APPENDED -> ARRAY_APPENDED;
                    case WiredArrayChangeType.ENTRY_INSERTED -> ARRAY_INSERTED;
                    case WiredArrayChangeType.ENTRY_REMOVED -> ARRAY_REMOVED;
                    case WiredArrayChangeType.INDEX_CLEARED -> ARRAY_INDEX_CLEARED;
                    case WiredArrayChangeType.ENTRY_REPLACED -> ARRAY_REPLACED;
                    case WiredArrayChangeType.ENTRY_MOVED -> ARRAY_MOVED;
                    case WiredArrayChangeType.ENTRIES_SWAPPED -> ARRAY_SWAPPED;
                    case WiredArrayChangeType.FIELD_VALUE_CHANGED -> ARRAY_FIELD_CHANGED;
                    case WiredArrayChangeType.ARRAY_CLEARED -> ARRAY_CLEARED;
                    case WiredArrayChangeType.ARRAY_SHUFFLED -> ARRAY_SHUFFLED;
                    default -> 0;
                };
        if (required == 0 || (this.arrayOptions & required) == 0) return false;
        return change.changeType() != WiredArrayChangeType.FIELD_VALUE_CHANGED
                || this.arrayFieldId == 0
                || this.arrayFieldId == change.fieldId();
    }

    private static ArrayData parseArrayData(String value) {
        if (value == null || value.isBlank()) return new ArrayData();
        try {
            ArrayData data = WiredManager.getGson().fromJson(value, ArrayData.class);
            return data == null ? new ArrayData() : data;
        } catch (RuntimeException ignored) {
            return new ArrayData();
        }
    }

    private static int normalizeTargetType(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    private static String normalizeVariableToken(String token) {
        if (token == null) {
            return "";
        }

        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        if (normalized.startsWith(CUSTOM_TOKEN_PREFIX)) {
            return normalized;
        }

        try {
            int itemId = Integer.parseInt(normalized);
            return (itemId > 0) ? (CUSTOM_TOKEN_PREFIX + itemId) : "";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static int getCustomItemId(String token) {
        if (token == null || !token.startsWith(CUSTOM_TOKEN_PREFIX)) {
            return 0;
        }

        try {
            return Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static class JsonData {
        String variableToken;
        int variableItemId;
        int targetType;
        boolean createdEnabled;
        boolean valueChangedEnabled;
        boolean increasedEnabled;
        boolean decreasedEnabled;
        boolean unchangedEnabled;
        boolean deletedEnabled;
        Integer arrayOptions;
        Integer arrayFieldId;

        JsonData(
                String variableToken,
                int variableItemId,
                int targetType,
                boolean createdEnabled,
                boolean valueChangedEnabled,
                boolean increasedEnabled,
                boolean decreasedEnabled,
                boolean unchangedEnabled,
                boolean deletedEnabled,
                Integer arrayOptions,
                Integer arrayFieldId) {
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.targetType = targetType;
            this.createdEnabled = createdEnabled;
            this.valueChangedEnabled = valueChangedEnabled;
            this.increasedEnabled = increasedEnabled;
            this.decreasedEnabled = decreasedEnabled;
            this.unchangedEnabled = unchangedEnabled;
            this.deletedEnabled = deletedEnabled;
            this.arrayOptions = arrayOptions;
            this.arrayFieldId = arrayFieldId;
        }
    }

    static class ArrayData {
        int options = ARRAY_CREATED | ARRAY_CHANGED;
        int fieldId;

        ArrayData() {}

        ArrayData(int options, int fieldId) {
            this.options = options;
            this.fieldId = fieldId;
        }
    }
}
