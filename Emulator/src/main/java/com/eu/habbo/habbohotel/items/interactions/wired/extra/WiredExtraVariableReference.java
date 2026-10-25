package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredVariableDefinitionData;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.WiredAllVariablesDiffComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredExtraVariableReference extends InteractionWiredExtra
        implements WiredArrayVariableDefinition, WiredOfficialVariableIds {
    public static final int CODE = 81;
    /** AIR wired context SHARED_VARIABLE_LIST: the shared variables of the owner's other rooms. */
    private static final int CONTEXT_SHARED_VARIABLE_LIST = 4;

    private String variableName = "";
    private int sourceRoomId = 0;
    private String sourceRoomName = "";
    private int sourceVariableItemId = 0;
    private String sourceVariableName = "";
    private int sourceTargetType = WiredVariableReferenceSupport.TARGET_USER;
    private boolean hasValue = false;
    private boolean readOnly = true;
    private WiredArrayDefinition arrayDefinition;

    public WiredExtraVariableReference(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableReference(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = findLoadedRoom(this.getRoomId());

        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        ConfigData config = readEditor(room, settings);
        String normalizedName = WiredVariableNameValidator.normalizeForSave(config.variableName);

        WiredVariableNameValidator.validateDefinitionName(room, this.getId(), normalizedName);

        if (config.sourceRoomId <= 0 || config.sourceVariableItemId <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        WiredVariableReferenceSupport.SharedDefinitionOption definition =
                WiredVariableReferenceSupport.findSharedDefinition(
                        room, config.sourceRoomId, config.sourceVariableItemId, config.sourceTargetType);

        // The official refusal for a reference to a variable that is not (or no longer) shared (18f2688f).
        if (definition == null) {
            throw new WiredSaveException("wiredfurni.error.variables.reference.valid");
        }

        this.variableName = normalizedName;
        this.sourceRoomId = definition.getRoomId();
        this.sourceRoomName = sanitizeLabel(definition.getRoomName());
        this.sourceVariableItemId = definition.getItemId();
        this.sourceVariableName = definition.getName();
        this.sourceTargetType = definition.getTargetType();
        this.hasValue = definition.hasValue();
        // The shared variable lives in another room of the same owner. Writing through the reference
        // reaches that room, where the editor may hold no rights at all, so only the owner (or staff who
        // own any room) may save a writable reference; anyone else gets a read-only one.
        this.readOnly = config.readOnly || !mayWriteSource(room, gameClient);
        this.arrayDefinition = definition.getArrayDefinition();

        room.getUserVariableManager().broadcastSnapshot();
        return true;
    }

    static boolean mayWriteSource(Room room, GameClient gameClient) {
        Habbo editor = gameClient == null ? null : gameClient.getHabbo();

        return editor != null
                && (editor.getHabboInfo().getId() == room.getOwnerId()
                        || editor.hasPermission(Permission.ACC_ANYROOMOWNER));
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.variableName,
                        this.sourceRoomId,
                        this.sourceRoomName,
                        this.sourceVariableItemId,
                        this.sourceVariableName,
                        this.sourceTargetType,
                        this.hasValue,
                        this.readOnly,
                        this.arrayDefinition == null
                                ? null
                                : WiredVariableDefinitionData.array(this.variableName, this.arrayDefinition)));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        // AIR (ReferenceVariable): the name, [readOnly], the shared variable as the definition's variable id and
        // the owner's shared variables in the SHARED_VARIABLE_LIST context.
        message.appendString(this.variableName);
        message.appendInt(1);
        message.appendInt(this.readOnly ? 1 : 0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);

        List<WiredVariableReferenceSupport.SharedDefinitionOption> shared = new ArrayList<>();
        for (WiredVariableReferenceSupport.RoomOption option : WiredVariableReferenceSupport.loadRoomOptions(room)) {
            shared.addAll(option.getVariables());
        }
        boolean listsSource = false;
        for (WiredVariableReferenceSupport.SharedDefinitionOption definition : shared) {
            listsSource |= definition.getItemId() == this.sourceVariableItemId
                    && definition.getTargetType() == this.sourceTargetType;
        }

        message.appendInt(1);
        message.appendInt(CONTEXT_SHARED_VARIABLE_LIST);
        boolean keepsLostSource = !listsSource && this.sourceVariableItemId > 0;
        message.appendInt(shared.size() + (keepsLostSource ? 1 : 0));
        for (WiredVariableReferenceSupport.SharedDefinitionOption definition : shared) {
            appendSharedVariable(
                    message,
                    definition.getRoomId(),
                    definition.getRoomName(),
                    definition.getItemId(),
                    definition.getName(),
                    definition.getTargetType(),
                    definition.hasValue());
        }
        if (keepsLostSource) {
            // A source that is no longer shared still shows, so the editor opens on it.
            appendSharedVariable(
                    message,
                    this.sourceRoomId,
                    this.sourceRoomName,
                    this.sourceVariableItemId,
                    this.sourceVariableName,
                    this.sourceTargetType,
                    this.hasValue);
        }
    }

    @Override
    public String[] officialVariableIds() {
        return this.sourceVariableItemId > 0
                ? new String[] {sharedVariableId(this.sourceTargetType, this.sourceVariableItemId)}
                : new String[0];
    }

    /** The id of a shared variable as the catalog names it: {@code user:<id>} or {@code room:<id>}. */
    static String sharedVariableId(int targetType, int itemId) {
        return RoomWiredVariableCatalog.variableId(catalogTarget(targetType), itemId);
    }

    private static int catalogTarget(int targetType) {
        return (targetType == WiredVariableReferenceSupport.TARGET_ROOM)
                ? RoomWiredVariableCatalog.TARGET_ROOM
                : RoomWiredVariableCatalog.TARGET_USER;
    }

    private static void appendSharedVariable(
            ServerMessage message,
            int roomId,
            String roomName,
            int itemId,
            String name,
            int targetType,
            boolean hasValue) {
        message.appendInt(roomId);
        message.appendString(sanitizeLabel(roomName));
        WiredAllVariablesDiffComposer.appendVariable(
                message,
                new RoomWiredVariableCatalog.Variable(
                        sharedVariableId(targetType, itemId),
                        RoomWiredVariableCatalog.TYPE_CREATED,
                        name != null ? name : "",
                        WiredVariableReferenceSupport.SHARED_AVAILABILITY,
                        catalogTarget(targetType),
                        hasValue,
                        false,
                        false));
    }

    /**
     * What the editor saved: AIR's name, [readOnly] and shared variable id, or the JSON string of our earlier editor
     * (kept so older share codes and clients still save).
     */
    static ConfigData readEditor(Room room, WiredSettings settings) {
        String text = settings.getStringParam();
        if (text != null && text.trim().startsWith("{")) {
            return parseConfigData(text.trim());
        }

        ConfigData config = new ConfigData();
        config.variableName = (text != null) ? text : "";
        int[] ints = settings.getIntParams();
        config.readOnly = ints.length == 0 || ints[0] != 0;

        String[] variableIds = settings.getVariableIds();
        String id = (variableIds.length > 0 && variableIds[0] != null) ? variableIds[0].trim() : "";
        config.sourceVariableItemId = RoomWiredVariableCatalog.definitionIdOf(id);
        config.sourceTargetType = RoomWiredVariableCatalog.targetOf(id) == RoomWiredVariableCatalog.TARGET_ROOM
                ? WiredVariableReferenceSupport.TARGET_ROOM
                : WiredVariableReferenceSupport.TARGET_USER;

        for (WiredVariableReferenceSupport.RoomOption option : WiredVariableReferenceSupport.loadRoomOptions(room)) {
            for (WiredVariableReferenceSupport.SharedDefinitionOption definition : option.getVariables()) {
                if (definition.getItemId() == config.sourceVariableItemId
                        && definition.getTargetType() == config.sourceTargetType) {
                    config.sourceRoomId = definition.getRoomId();
                }
            }
        }
        return config;
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
        this.sourceRoomId = Math.max(0, data.sourceRoomId);
        this.sourceRoomName = sanitizeLabel(data.sourceRoomName);
        this.sourceVariableItemId = Math.max(0, data.sourceVariableItemId);
        this.sourceVariableName = WiredVariableNameValidator.normalizeLegacy(data.sourceVariableName);
        this.sourceTargetType = normalizeTargetType(data.sourceTargetType);
        this.hasValue = data.hasValue;
        this.readOnly = data.readOnly;
        if (data.definition != null) {
            try {
                this.arrayDefinition =
                        com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport.parseArrayDefinition(
                                data.definition);
                if (this.arrayDefinition != null) this.hasValue = true;
            } catch (IllegalArgumentException ignored) {
                this.arrayDefinition = null;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.variableName = "";
        this.sourceRoomId = 0;
        this.sourceRoomName = "";
        this.sourceVariableItemId = 0;
        this.sourceVariableName = "";
        this.sourceTargetType = WiredVariableReferenceSupport.TARGET_USER;
        this.hasValue = false;
        this.readOnly = true;
        this.arrayDefinition = null;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public int getSourceRoomId() {
        return this.sourceRoomId;
    }

    public String getSourceRoomName() {
        return this.sourceRoomName;
    }

    public int getSourceVariableItemId() {
        return this.sourceVariableItemId;
    }

    public String getSourceVariableName() {
        return this.sourceVariableName;
    }

    public int getSourceTargetType() {
        return this.sourceTargetType;
    }

    public boolean hasValue() {
        return this.hasValue;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public int getAvailability() {
        return WiredVariableReferenceSupport.SHARED_AVAILABILITY;
    }

    public boolean isUserReference() {
        return this.sourceTargetType == WiredVariableReferenceSupport.TARGET_USER;
    }

    public boolean isRoomReference() {
        return this.sourceTargetType == WiredVariableReferenceSupport.TARGET_ROOM;
    }

    @Override
    public WiredArrayVariableType getArrayVariableType() {
        return this.isRoomReference() ? WiredArrayVariableType.ROOM : WiredArrayVariableType.USER;
    }

    @Override
    public WiredArrayDefinition getArrayDefinition() {
        return this.arrayDefinition;
    }

    @Override
    public boolean isArrayPermanent() {
        return true;
    }

    @Override
    public int getArrayStorageRoomId(int currentRoomId) {
        return this.sourceRoomId;
    }

    @Override
    public int getArrayStorageDefinitionItemId() {
        return this.sourceVariableItemId;
    }

    @Override
    public boolean isArrayWritable() {
        return !this.readOnly;
    }

    @Override
    public boolean isArrayShared() {
        return true;
    }

    @Override
    public boolean isArraySourceValid() {
        return WiredVariableReferenceSupport.isSharedSourceStillAvailable(this);
    }

    Room loadedSourceRoom() {
        return findLoadedRoom(this.sourceRoomId);
    }

    private static Room findLoadedRoom(int roomId) {
        return Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
    }

    private static ConfigData parseConfigData(String value) {
        if (value == null || value.isEmpty() || !value.startsWith("{")) {
            return new ConfigData();
        }

        ConfigData config = WiredExtraPayloadGuard.fromJson(value, ConfigData.class);
        return (config != null) ? config : new ConfigData();
    }

    private static int normalizeTargetType(int value) {
        return (value == WiredVariableReferenceSupport.TARGET_ROOM)
                ? WiredVariableReferenceSupport.TARGET_ROOM
                : WiredVariableReferenceSupport.TARGET_USER;
    }

    private static String sanitizeLabel(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replace("\t", "").replace("\r", "").replace("\n", "");
    }

    static class JsonData {
        String variableName;
        int sourceRoomId;
        String sourceRoomName;
        int sourceVariableItemId;
        String sourceVariableName;
        int sourceTargetType;
        boolean hasValue;
        boolean readOnly;
        WiredVariableDefinitionData definition;

        JsonData(
                String variableName,
                int sourceRoomId,
                String sourceRoomName,
                int sourceVariableItemId,
                String sourceVariableName,
                int sourceTargetType,
                boolean hasValue,
                boolean readOnly,
                WiredVariableDefinitionData definition) {
            this.variableName = variableName;
            this.sourceRoomId = sourceRoomId;
            this.sourceRoomName = sourceRoomName;
            this.sourceVariableItemId = sourceVariableItemId;
            this.sourceVariableName = sourceVariableName;
            this.sourceTargetType = sourceTargetType;
            this.hasValue = hasValue;
            this.readOnly = readOnly;
            this.definition = definition;
        }
    }

    static class ConfigData {
        String variableName = "";
        int sourceRoomId = 0;
        int sourceVariableItemId = 0;
        int sourceTargetType = WiredVariableReferenceSupport.TARGET_USER;
        boolean readOnly = true;
    }
}
