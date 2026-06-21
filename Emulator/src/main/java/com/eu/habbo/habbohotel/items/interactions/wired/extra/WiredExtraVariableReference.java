package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredExtraVariableReference extends InteractionWiredExtra {
    public static final int CODE = 81;

    private String variableName = "";
    private int sourceRoomId = 0;
    private String sourceRoomName = "";
    private int sourceVariableItemId = 0;
    private String sourceVariableName = "";
    private int sourceTargetType = WiredVariableReferenceSupport.TARGET_USER;
    private boolean hasValue = false;
    private boolean readOnly = true;

    public WiredExtraVariableReference(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableReference(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        ConfigData config = parseConfigData(settings.getStringParam());
        String normalizedName = WiredVariableNameValidator.normalizeForSave(config.variableName);

        WiredVariableNameValidator.validateDefinitionName(room, this.getId(), normalizedName);

        if (config.sourceRoomId <= 0 || config.sourceVariableItemId <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        WiredVariableReferenceSupport.SharedDefinitionOption definition = WiredVariableReferenceSupport.findSharedDefinition(
            room,
            config.sourceRoomId,
            config.sourceVariableItemId,
            config.sourceTargetType
        );

        if (definition == null) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        this.variableName = normalizedName;
        this.sourceRoomId = definition.getRoomId();
        this.sourceRoomName = sanitizeLabel(definition.getRoomName());
        this.sourceVariableItemId = definition.getItemId();
        this.sourceVariableName = definition.getName();
        this.sourceTargetType = definition.getTargetType();
        this.hasValue = definition.hasValue();
        this.readOnly = config.readOnly;

        room.getUserVariableManager().broadcastSnapshot();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.variableName,
            this.sourceRoomId,
            this.sourceRoomName,
            this.sourceVariableItemId,
            this.sourceVariableName,
            this.sourceTargetType,
            this.hasValue,
            this.readOnly
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(buildEditorPayload(room));
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
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
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

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

    private String buildEditorPayload(Room room) {
        List<RoomEditorData> roomOptions = new ArrayList<>();

        for (WiredVariableReferenceSupport.RoomOption option : WiredVariableReferenceSupport.loadRoomOptions(room)) {
            List<VariableEditorData> variables = new ArrayList<>();

            for (WiredVariableReferenceSupport.SharedDefinitionOption definition : option.getVariables()) {
                variables.add(new VariableEditorData(definition.getItemId(), definition.getName(), definition.getTargetType(), definition.hasValue()));
            }

            roomOptions.add(new RoomEditorData(option.getRoomId(), option.getRoomName(), variables));
        }

        return WiredManager.getGson().toJson(new EditorPayload(
            this.variableName,
            this.sourceRoomId,
            this.sourceRoomName,
            this.sourceVariableItemId,
            this.sourceVariableName,
            this.sourceTargetType,
            this.readOnly,
            roomOptions
        ));
    }

    private static ConfigData parseConfigData(String value) {
        if (value == null || value.isEmpty() || !value.startsWith("{")) {
            return new ConfigData();
        }

        ConfigData config = WiredExtraPayloadGuard.fromJson(value, ConfigData.class);
        return (config != null) ? config : new ConfigData();
    }

    private static int normalizeTargetType(int value) {
        return (value == WiredVariableReferenceSupport.TARGET_ROOM) ? WiredVariableReferenceSupport.TARGET_ROOM : WiredVariableReferenceSupport.TARGET_USER;
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

        JsonData(String variableName, int sourceRoomId, String sourceRoomName, int sourceVariableItemId, String sourceVariableName, int sourceTargetType, boolean hasValue, boolean readOnly) {
            this.variableName = variableName;
            this.sourceRoomId = sourceRoomId;
            this.sourceRoomName = sourceRoomName;
            this.sourceVariableItemId = sourceVariableItemId;
            this.sourceVariableName = sourceVariableName;
            this.sourceTargetType = sourceTargetType;
            this.hasValue = hasValue;
            this.readOnly = readOnly;
        }
    }

    static class ConfigData {
        String variableName = "";
        int sourceRoomId = 0;
        int sourceVariableItemId = 0;
        int sourceTargetType = WiredVariableReferenceSupport.TARGET_USER;
        boolean readOnly = true;
    }

    static class EditorPayload extends ConfigData {
        String sourceRoomName;
        String sourceVariableName;
        List<RoomEditorData> rooms;

        EditorPayload(String variableName, int sourceRoomId, String sourceRoomName, int sourceVariableItemId, String sourceVariableName, int sourceTargetType, boolean readOnly, List<RoomEditorData> rooms) {
            this.variableName = variableName;
            this.sourceRoomId = sourceRoomId;
            this.sourceRoomName = sourceRoomName;
            this.sourceVariableItemId = sourceVariableItemId;
            this.sourceVariableName = sourceVariableName;
            this.sourceTargetType = sourceTargetType;
            this.readOnly = readOnly;
            this.rooms = rooms;
        }
    }

    static class RoomEditorData {
        int roomId;
        String roomName;
        List<VariableEditorData> variables;

        RoomEditorData(int roomId, String roomName, List<VariableEditorData> variables) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.variables = variables;
        }
    }

    static class VariableEditorData {
        int itemId;
        String name;
        int targetType;
        boolean hasValue;

        VariableEditorData(int itemId, String name, int targetType, boolean hasValue) {
            this.itemId = itemId;
            this.name = name;
            this.targetType = targetType;
            this.hasValue = hasValue;
        }
    }
}
