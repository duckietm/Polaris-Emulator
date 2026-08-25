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
    public static final int TARGET_ROOM = 3;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";

    private String variableToken = "";
    private int variableItemId = 0;
    private int targetType = TARGET_USER;
    private boolean createdEnabled = true;
    private boolean valueChangedEnabled = true;
    private boolean increasedEnabled = true;
    private boolean decreasedEnabled = true;
    private boolean unchangedEnabled = true;
    private boolean deletedEnabled = true;

    public WiredTriggerVariableChanged(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerVariableChanged(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event == null || event.getType() != WiredEvent.Type.VARIABLE_CHANGED) {
            return false;
        }

        if (event.getVariableTargetType() != this.targetType || event.getVariableDefinitionItemId() != this.variableItemId) {
            return false;
        }

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
        message.appendString(this.variableToken == null ? "" : this.variableToken);
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
        this.setVariableToken(normalizeVariableToken(settings.getStringParam()));
        this.normalizeOptions();

        if (this.variableItemId <= 0) {
            throw new WiredTriggerSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        if (!this.hasAnyEnabledOption()) {
            return false;
        }

        if (room == null || !this.isValidDefinition(room)) {
            throw new WiredTriggerSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.variableToken,
                this.variableItemId,
                this.targetType,
                this.createdEnabled,
                this.valueChangedEnabled,
                this.increasedEnabled,
                this.decreasedEnabled,
                this.unchangedEnabled,
                this.deletedEnabled
        ));
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
        this.setVariableToken(normalizeVariableToken((data.variableToken != null) ? data.variableToken : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
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
                || (this.valueChangedEnabled && (this.increasedEnabled || this.decreasedEnabled || this.unchangedEnabled));
    }

    private boolean isValidDefinition(Room room) {
        WiredVariableDefinitionInfo definitionInfo = switch (this.targetType) {
            case TARGET_FURNI -> room.getFurniVariableManager().getDefinitionInfo(this.variableItemId);
            case TARGET_ROOM -> room.getRoomVariableManager().getDefinitionInfo(this.variableItemId);
            default -> room.getUserVariableManager().getDefinitionInfo(this.variableItemId);
        };

        return definitionInfo != null;
    }

    private static int normalizeTargetType(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_ROOM -> value;
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

        JsonData(String variableToken, int variableItemId, int targetType, boolean createdEnabled, boolean valueChangedEnabled, boolean increasedEnabled, boolean decreasedEnabled, boolean unchangedEnabled, boolean deletedEnabled) {
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.targetType = targetType;
            this.createdEnabled = createdEnabled;
            this.valueChangedEnabled = valueChangedEnabled;
            this.increasedEnabled = increasedEnabled;
            this.decreasedEnabled = decreasedEnabled;
            this.unchangedEnabled = unchangedEnabled;
            this.deletedEnabled = deletedEnabled;
        }
    }
}
