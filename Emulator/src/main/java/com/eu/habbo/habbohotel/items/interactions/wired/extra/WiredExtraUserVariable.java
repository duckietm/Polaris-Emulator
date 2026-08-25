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

public class WiredExtraUserVariable extends InteractionWiredExtra {
    public static final int CODE = 70;
    public static final int AVAILABILITY_ROOM = 0;
    public static final int AVAILABILITY_PERMANENT = 10;
    public static final int AVAILABILITY_SHARED = 11;

    private String variableName = "";
    private boolean hasValue = false;
    private int availability = AVAILABILITY_ROOM;

    public WiredExtraUserVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraUserVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        String normalizedName = WiredVariableNameValidator.normalizeForSave(settings.getStringParam());

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            throw new WiredSaveException("Room not found");
        }

        WiredVariableNameValidator.validateDefinitionName(room, this.getId(), normalizedName);

        this.variableName = normalizedName;
        this.hasValue = (intParams.length > 0) && (intParams[0] == 1);
        this.availability = normalizeAvailability((intParams.length > 1) ? intParams[1] : AVAILABILITY_ROOM);

        room.getUserVariableManager().handleDefinitionUpdated(this);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableName, this.hasValue, this.availability));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableName);
        message.appendInt(2);
        message.appendInt(this.hasValue ? 1 : 0);
        message.appendInt(this.availability);
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
                this.availability = normalizeAvailability(data.availability);
            }

            return;
        }

        this.variableName = WiredVariableNameValidator.normalizeLegacy(wiredData);
    }

    @Override
    public void onPickUp() {
        this.variableName = "";
        this.hasValue = false;
        this.availability = AVAILABILITY_ROOM;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getVariableName() {
        return variableName;
    }

    public boolean hasValue() {
        return hasValue;
    }

    public int getAvailability() {
        return availability;
    }

    public boolean isPermanentAvailability() {
        return this.availability == AVAILABILITY_PERMANENT || this.availability == AVAILABILITY_SHARED;
    }

    public boolean isSharedAvailability() {
        return this.availability == AVAILABILITY_SHARED;
    }

    private static int normalizeAvailability(int value) {
        if (value == AVAILABILITY_PERMANENT || value == AVAILABILITY_SHARED) {
            return value;
        }

        return AVAILABILITY_ROOM;
    }

    static class JsonData {
        String variableName;
        boolean hasValue;
        int availability;

        JsonData(String variableName, boolean hasValue, int availability) {
            this.variableName = variableName;
            this.hasValue = hasValue;
            this.availability = availability;
        }
    }
}
