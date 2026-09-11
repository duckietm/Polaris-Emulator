package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLargePayload;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionState;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredVariableDefinitionData;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiredExtraContextVariable extends InteractionWiredExtra
        implements WiredArrayVariableDefinition, WiredLargePayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredExtraContextVariable.class);
    public static final int CODE = 84;

    private String variableName = "";
    private boolean hasValue = false;
    private final WiredArrayDefinitionState array = new WiredArrayDefinitionState();

    public WiredExtraContextVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraContextVariable(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

        int[] intParams = settings.getIntParams();
        WiredVariableDefinitionData definitionData;
        try {
            definitionData = WiredArrayDefinitionSupport.readEditorData(settings.getStringParam());
        } catch (IllegalArgumentException exception) {
            throw new WiredSaveException(exception.getMessage());
        }
        String normalizedName = WiredVariableNameValidator.normalizeForSave(definitionData.name);

        WiredVariableNameValidator.validateDefinitionName(room, this.getId(), normalizedName);

        WiredArrayDefinition nextArrayDefinition;
        try {
            nextArrayDefinition =
                    WiredArrayDefinitionSupport.parseArrayDefinition(definitionData, this.array.definition());
            room.getArrayVariableManager().validateDefinitionChange(this, nextArrayDefinition, false);
        } catch (IllegalArgumentException exception) {
            throw new WiredSaveException(exception.getMessage());
        }

        this.variableName = normalizedName;
        this.array.assign(nextArrayDefinition);
        this.hasValue = this.array.isArray() || ((intParams.length > 0) && (intParams[0] == 1));

        WiredContextVariableSupport.broadcastDefinitions(room);
        room.getArrayVariableManager().handleDefinitionUpdated(this);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.variableName, this.hasValue, this.array.persisted(this.variableName)));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.array.editorString(this.variableName));
        message.appendInt(1);
        message.appendInt(this.hasValue ? 1 : 0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
                this.hasValue = data.hasValue;
                if (data.definition != null) {
                    this.array.restore(
                            data.definition,
                            LOGGER,
                            "context",
                            this.getId(),
                            room == null ? this.getRoomId() : room.getId());
                    if (this.array.isArray()) this.hasValue = true;
                    if (this.array.isUnavailable()) this.hasValue = false;
                }
            }

            return;
        }

        this.variableName = WiredVariableNameValidator.normalizeLegacy(wiredData);
    }

    @Override
    public void onPickUp() {
        this.variableName = "";
        this.hasValue = false;
        this.array.clear();
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

    public boolean hasValue() {
        return this.hasValue;
    }

    @Override
    public WiredArrayVariableType getArrayVariableType() {
        return WiredArrayVariableType.CONTEXT;
    }

    @Override
    public WiredArrayDefinition getArrayDefinition() {
        return this.array.definition();
    }

    @Override
    public boolean isArrayPermanent() {
        return false;
    }

    @Override
    public boolean isArrayUnavailable() {
        return this.array.isUnavailable();
    }

    static class JsonData {
        String variableName;
        boolean hasValue;
        WiredVariableDefinitionData definition;

        JsonData(String variableName, boolean hasValue, WiredVariableDefinitionData definition) {
            this.variableName = variableName;
            this.hasValue = hasValue;
            this.definition = definition;
        }
    }
}
