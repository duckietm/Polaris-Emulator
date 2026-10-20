package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredProjectileFlights;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChangeType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredTriggerVariableChanged extends InteractionWiredTrigger implements WiredOfficialVariableIds {
    public static final WiredTriggerType type = WiredTriggerType.VARIABLE_CHANGED;

    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_CONTEXT = 2;
    public static final int TARGET_ROOM = 3;

    /** Official trigger modes ({@code variables.trigger_mode}): the picked variable, or the picked variable boxes. */
    public static final int MODE_SELECTED_VARIABLE = 0;

    public static final int MODE_PICKED_BOXES = 1;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";
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
    private int originMask = WiredVariableChangeOrigin.MASK_ALL;
    private boolean createdEnabled = true;
    private boolean valueChangedEnabled = true;
    private boolean increasedEnabled = true;
    private boolean decreasedEnabled = true;
    private boolean unchangedEnabled = true;
    private boolean deletedEnabled = true;
    private int arrayOptions = ARRAY_CREATED | ARRAY_CHANGED;
    private int arrayFieldId;
    private boolean arrayDataConfigured;
    private int mode = MODE_SELECTED_VARIABLE;
    /** The variable boxes picked for mode 1, by item id; any variable they define is watched. */
    private final Set<Integer> pickedBoxIds = new LinkedHashSet<>();

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

        if (this.mode == MODE_PICKED_BOXES) {
            // The picked boxes name their variables themselves, whatever the holder type.
            if (!event.getInternalVariableKey().isEmpty()
                    || !this.pickedBoxIds.contains(event.getVariableDefinitionItemId())) {
                return false;
            }
        } else if (event.getVariableTargetType() != this.targetType) {
            return false;
        } else if (this.variableToken.startsWith(INTERNAL_TOKEN_PREFIX)) {
            if (!this.variableToken.substring(INTERNAL_TOKEN_PREFIX.length()).equals(event.getInternalVariableKey())) {
                return false;
            }
        } else if (this.variableItemId <= 0
                || !event.getInternalVariableKey().isEmpty()
                || event.getVariableDefinitionItemId() != this.variableItemId) {
            return false;
        }

        if (!WiredVariableChangeOrigin.accepts(this.originMask, event.getVariableChangeOrigin())) {
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
        if (room != null) {
            this.pickedBoxIds.removeIf(id -> room.getHabboItem(id) == null);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.pickedBoxIds.size());
        for (Integer id : this.pickedBoxIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        // AIR sends no string; the variable goes out as the definition's variable id. Ours: an array variable's
        // options as JSON.
        message.appendString(
                this.resolveArrayDefinition(room) != null
                        ? WiredManager.getGson().toJson(new ArrayData(this.arrayOptions, this.arrayFieldId))
                        : "");
        // Official layout: [created, value changed, deleted, (bit 0 increased, 1 decreased, 2
        // unchanged), origins (official bits, -1 every one), mode (0 the picked variable, 1 the
        // picked variable boxes)]; ours follows: [variable target (official ids)].
        message.appendInt(7);
        message.appendInt(this.createdEnabled ? 1 : 0);
        message.appendInt(this.valueChangedEnabled ? 1 : 0);
        message.appendInt(this.deletedEnabled ? 1 : 0);
        message.appendInt(
                (this.increasedEnabled ? 1 : 0) | (this.decreasedEnabled ? 2 : 0) | (this.unchangedEnabled ? 4 : 0));
        message.appendInt(toOfficialOrigins(this.originMask));
        message.appendInt(this.mode);
        message.appendInt(WiredVariableTargets.toOfficial(this.targetType));
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    // Official origin bits: 0 this room, 1 another room, 2 inspection, 3 external. Ours: wired (this
    // room), other room (a shared variable changed elsewhere), creator tools (inspection), web API
    // (external).
    private static final int OFFICIAL_ORIGINS_ALL = -1;
    private static final int OFFICIAL_ORIGIN_THIS_ROOM = 0;
    private static final int OFFICIAL_ORIGIN_OTHER_ROOM = 1;
    private static final int OFFICIAL_ORIGIN_INSPECTION = 2;
    private static final int OFFICIAL_ORIGIN_EXTERNAL = 3;

    static int toOfficialOrigins(int ourMask) {
        if (ourMask == WiredVariableChangeOrigin.MASK_ALL || ourMask == 0) return OFFICIAL_ORIGINS_ALL;
        int official = 0;
        if ((ourMask & (1 << WiredVariableChangeOrigin.WIRED)) != 0) official |= 1 << OFFICIAL_ORIGIN_THIS_ROOM;
        if ((ourMask & (1 << WiredVariableChangeOrigin.OTHER_ROOM)) != 0) {
            official |= 1 << OFFICIAL_ORIGIN_OTHER_ROOM;
        }
        if ((ourMask & (1 << WiredVariableChangeOrigin.CREATOR_TOOLS)) != 0) {
            official |= 1 << OFFICIAL_ORIGIN_INSPECTION;
        }
        if ((ourMask & (1 << WiredVariableChangeOrigin.WEB_API)) != 0) official |= 1 << OFFICIAL_ORIGIN_EXTERNAL;
        return official;
    }

    static int fromOfficialOrigins(int officialMask) {
        if (officialMask < 0) return WiredVariableChangeOrigin.MASK_ALL;
        int ours = 0;
        if ((officialMask & (1 << OFFICIAL_ORIGIN_THIS_ROOM)) != 0) ours |= 1 << WiredVariableChangeOrigin.WIRED;
        if ((officialMask & (1 << OFFICIAL_ORIGIN_OTHER_ROOM)) != 0) {
            ours |= 1 << WiredVariableChangeOrigin.OTHER_ROOM;
        }
        if ((officialMask & (1 << OFFICIAL_ORIGIN_INSPECTION)) != 0) {
            ours |= 1 << WiredVariableChangeOrigin.CREATOR_TOOLS;
        }
        if ((officialMask & (1 << OFFICIAL_ORIGIN_EXTERNAL)) != 0) ours |= 1 << WiredVariableChangeOrigin.WEB_API;
        return ours;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        int[] params = settings.getIntParams();

        // Official layout (see serializeWiredData); our variable target follows the official ints.
        this.createdEnabled = (params.length <= 0) || (params[0] == 1);
        this.valueChangedEnabled = (params.length <= 1) || (params[1] == 1);
        this.deletedEnabled = (params.length <= 2) || (params[2] == 1);
        int changes = (params.length > 3) ? params[3] : 7;
        this.increasedEnabled = (changes & 1) != 0;
        this.decreasedEnabled = (changes & 2) != 0;
        this.unchangedEnabled = (changes & 4) != 0;
        this.originMask = WiredVariableChangeOrigin.normalizeMask(
                fromOfficialOrigins((params.length > 4) ? params[4] : OFFICIAL_ORIGINS_ALL));
        this.mode = (params.length > 5 && params[5] == MODE_PICKED_BOXES) ? MODE_PICKED_BOXES : MODE_SELECTED_VARIABLE;
        this.targetType = normalizeTargetType(WiredVariableTargets.fromOfficial(
                (params.length > 6) ? params[6] : WiredVariableTargets.OFFICIAL_USER));
        this.pickedBoxIds.clear();
        int[] furniIds = settings.getFurniIds() != null ? settings.getFurniIds() : new int[0];
        for (int i = 0; i < furniIds.length && i < WiredManager.MAXIMUM_FURNI_SELECTION; i++) {
            if (furniIds[i] > 0 && (room == null || room.getHabboItem(furniIds[i]) != null)) {
                this.pickedBoxIds.add(furniIds[i]);
            }
        }
        String text = settings.getStringParam() == null ? "" : settings.getStringParam();
        String arrayText;
        if (WiredVariableIds.present(settings.getVariableIds())) {
            // AIR: the variable is the first variable id; the string carries only our array options.
            this.setVariableToken(WiredVariableIds.tokenAt(settings.getVariableIds(), 0));
            int tab = text.indexOf('\t');
            arrayText = tab >= 0 ? text.substring(tab + 1) : text;
            if (params.length <= 6) {
                this.targetType = targetOfId(settings.getVariableIds()[0], this.targetType);
            }
        } else {
            // Older clients and share codes: the token, then the array options after a tab.
            String[] stringParts = text.split("\\t", 2);
            this.setVariableToken(normalizeVariableToken(stringParts.length > 0 ? stringParts[0] : ""));
            arrayText = stringParts.length > 1 ? stringParts[1] : null;
        }
        ArrayData arrayData = parseArrayData(arrayText);
        this.arrayOptions = arrayData.options;
        this.arrayFieldId = Math.max(0, arrayData.fieldId);
        this.normalizeOptions();

        if (this.mode == MODE_PICKED_BOXES) {
            // The picked boxes stand for the variables; the variable picker is off in this mode.
            this.arrayDataConfigured = false;
            return this.hasAnyEnabledOption();
        }

        if (this.variableToken.isEmpty()) {
            throw new WiredTriggerSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        // A pointer only exists while a stack runs, so nothing can watch it change (AIR VariableUpdate filter).
        if (room != null && WiredPointerVariableSupport.isPointer(room, this.variableItemId)) {
            throw new WiredTriggerSaveException(WiredPointerVariableSupport.REQUIRES_EXECUTION_CONTEXT_ERROR);
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
                        this.originMask,
                        this.arrayDataConfigured ? this.arrayOptions : null,
                        this.arrayDataConfigured ? this.arrayFieldId : null,
                        this.mode == MODE_PICKED_BOXES ? this.mode : null,
                        this.pickedBoxIds.isEmpty() ? null : new ArrayList<>(this.pickedBoxIds)));
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
        this.originMask = (data.originMask == null)
                ? WiredVariableChangeOrigin.MASK_ALL
                : WiredVariableChangeOrigin.normalizeMask(data.originMask);
        this.arrayDataConfigured = data.arrayOptions != null || data.arrayFieldId != null;
        int loadedArrayOptions = data.arrayOptions == null ? 0 : data.arrayOptions;
        this.arrayOptions = loadedArrayOptions == 0 ? ARRAY_CREATED | ARRAY_CHANGED : loadedArrayOptions;
        this.arrayFieldId = Math.max(0, data.arrayFieldId == null ? 0 : data.arrayFieldId);
        this.setVariableToken(normalizeVariableToken(
                (data.variableToken != null)
                        ? data.variableToken
                        : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
        // Boxes saved before the trigger modes watch their one variable (mode 0) and pick nothing.
        this.mode = (data.mode != null && data.mode == MODE_PICKED_BOXES) ? MODE_PICKED_BOXES : MODE_SELECTED_VARIABLE;
        if (data.pickedBoxIds != null) {
            for (Integer id : data.pickedBoxIds) {
                if (id != null && id > 0 && this.pickedBoxIds.size() < WiredManager.MAXIMUM_FURNI_SELECTION) {
                    this.pickedBoxIds.add(id);
                }
            }
        }
        this.normalizeOptions();
    }

    @Override
    public void onPickUp() {
        this.variableToken = "";
        this.variableItemId = 0;
        this.targetType = TARGET_USER;
        this.originMask = WiredVariableChangeOrigin.MASK_ALL;
        this.createdEnabled = true;
        this.valueChangedEnabled = true;
        this.increasedEnabled = true;
        this.decreasedEnabled = true;
        this.unchangedEnabled = true;
        this.deletedEnabled = true;
        this.arrayOptions = ARRAY_CREATED | ARRAY_CHANGED;
        this.arrayFieldId = 0;
        this.arrayDataConfigured = false;
        this.mode = MODE_SELECTED_VARIABLE;
        this.pickedBoxIds.clear();
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

        // Room variables and built-ins are never created or deleted, except a projectile's counts: its
        // traveling flag goes when it lands, which Habbo reports as a deletion.
        if (this.mode != MODE_PICKED_BOXES
                && (this.targetType == TARGET_ROOM
                        || (this.variableToken.startsWith(INTERNAL_TOKEN_PREFIX) && !this.watchesProjectile()))) {
            this.createdEnabled = false;
            this.deletedEnabled = false;
        }
    }

    /** Whether the box watches one of the projectile counts Habbo lets Variable Changed watch. */
    private boolean watchesProjectile() {
        return this.targetType == TARGET_FURNI
                && this.variableToken.startsWith(INTERNAL_TOKEN_PREFIX)
                && WiredProjectileFlights.WATCHED.contains(WiredInternalVariableSupport.normalizeKey(
                        this.variableToken.substring(INTERNAL_TOKEN_PREFIX.length())));
    }

    private boolean hasAnyEnabledOption() {
        return this.createdEnabled
                || this.deletedEnabled
                || (this.valueChangedEnabled
                        && (this.increasedEnabled || this.decreasedEnabled || this.unchangedEnabled));
    }

    private boolean isValidDefinition(Room room) {
        if (this.variableToken.startsWith(INTERNAL_TOKEN_PREFIX)) {
            String key = this.variableToken.substring(INTERNAL_TOKEN_PREFIX.length());
            return switch (this.targetType) {
                case TARGET_FURNI ->
                    WiredInternalVariableSupport.canUseFurniDestination(key) || this.watchesProjectile();
                case TARGET_ROOM -> WiredInternalVariableSupport.canUseRoomDestination(key);
                case TARGET_USER ->
                    WiredInternalVariableSupport.canUseUserDestination(key) || "@has_rights".equals(key);
                default -> false;
            };
        }

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

    @Override
    public String[] officialVariableIds() {
        if (this.mode == MODE_PICKED_BOXES || this.variableToken.isEmpty()) {
            return new String[0];
        }
        return new String[] {WiredVariableIds.toWireForHolder(this.targetType, this.variableToken)};
    }

    /** Our holder kind of the target a variable id names, else {@code fallback}. */
    private static int targetOfId(String id, int fallback) {
        int official = WiredVariableIds.officialTargetOf(id);
        return official == Integer.MIN_VALUE
                ? fallback
                : normalizeTargetType(WiredVariableTargets.fromOfficial(official));
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

        if (normalized.startsWith(INTERNAL_TOKEN_PREFIX)) {
            String key =
                    WiredInternalVariableSupport.normalizeKey(normalized.substring(INTERNAL_TOKEN_PREFIX.length()));
            return key.isEmpty() ? "" : INTERNAL_TOKEN_PREFIX + key;
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
        Integer originMask;
        Integer arrayOptions;
        Integer arrayFieldId;
        Integer mode;
        List<Integer> pickedBoxIds;

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
                Integer originMask,
                Integer arrayOptions,
                Integer arrayFieldId,
                Integer mode,
                List<Integer> pickedBoxIds) {
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.targetType = targetType;
            this.createdEnabled = createdEnabled;
            this.valueChangedEnabled = valueChangedEnabled;
            this.increasedEnabled = increasedEnabled;
            this.decreasedEnabled = decreasedEnabled;
            this.unchangedEnabled = unchangedEnabled;
            this.deletedEnabled = deletedEnabled;
            this.originMask = originMask;
            this.arrayOptions = arrayOptions;
            this.arrayFieldId = arrayFieldId;
            this.mode = mode;
            this.pickedBoxIds = pickedBoxIds;
        }
    }

    public int getMode() {
        return this.mode;
    }

    public Set<Integer> getPickedBoxIds() {
        return Set.copyOf(this.pickedBoxIds);
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
