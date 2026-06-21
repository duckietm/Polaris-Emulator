package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class WiredExtraTextInputVariable extends InteractionWiredExtra {
    public static final int CODE = 85;
    public static final int DISPLAY_NUMERIC = 1;
    public static final int DISPLAY_TEXTUAL = 2;
    public static final String DEFAULT_CAPTURER_NAME = "";
    public static final int MAX_CAPTURER_NAME_LENGTH = 32;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final Pattern WRAPPED_PLACEHOLDER_PATTERN = Pattern.compile("^#(.*)#$");

    private int variableItemId = 0;
    private String variableToken = "";
    private String capturerName = DEFAULT_CAPTURER_NAME;
    private int displayType = DISPLAY_NUMERIC;

    public WiredExtraTextInputVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTextInputVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        String[] stringData = splitStringData(settings.getStringParam());
        String nextVariableToken = normalizeVariableToken(stringData[0]);
        int nextVariableItemId = getCustomItemId(nextVariableToken);

        if (nextVariableItemId <= 0) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        WiredVariableDefinitionInfo definitionInfo = WiredContextVariableSupport.getDefinitionInfo(room, nextVariableItemId);
        if (definitionInfo == null || !definitionInfo.hasValue()) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        this.variableItemId = nextVariableItemId;
        this.variableToken = nextVariableToken;
        this.capturerName = normalizeCapturerName(stringData[1]);
        this.displayType = normalizeDisplayType((intParams.length > 0) ? intParams[0] : DISPLAY_NUMERIC);

        if (!canUseTextualDisplay(room, this.variableItemId)) {
            this.displayType = DISPLAY_NUMERIC;
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableToken, this.variableItemId, this.capturerName, this.displayType));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken + "\t" + this.capturerName);
        message.appendInt(1);
        message.appendInt(this.getDisplayType(room));
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
                this.variableToken = normalizeVariableToken((data.variableToken != null)
                        ? data.variableToken
                        : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : ""));
                this.variableItemId = getCustomItemId(this.variableToken);
                this.capturerName = normalizeCapturerName(data.capturerName);
                this.displayType = normalizeDisplayType(data.displayType);
            }

            return;
        }

        String[] legacyData = splitStringData(wiredData);
        this.variableToken = normalizeVariableToken(legacyData[0]);
        this.variableItemId = getCustomItemId(this.variableToken);
        this.capturerName = normalizeCapturerName(legacyData[1]);
    }

    @Override
    public void onPickUp() {
        this.variableItemId = 0;
        this.variableToken = "";
        this.capturerName = DEFAULT_CAPTURER_NAME;
        this.displayType = DISPLAY_NUMERIC;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getVariableItemId() {
        return this.variableItemId;
    }

    public String getVariableToken() {
        return this.variableToken;
    }

    public String getCapturerName() {
        return this.capturerName;
    }

    public String getPlaceholderToken() {
        return this.capturerName.isEmpty() ? "" : "#" + this.capturerName + "#";
    }

    public int getDisplayType(Room room) {
        return (this.displayType == DISPLAY_TEXTUAL && canUseTextualDisplay(room, this.variableItemId))
                ? DISPLAY_TEXTUAL
                : DISPLAY_NUMERIC;
    }

    public Integer resolveCapturedValue(Room room, String rawValue) {
        String capturedValue = rawValue != null ? rawValue : "";
        String normalizedValue = capturedValue.trim();

        if (this.getDisplayType(room) == DISPLAY_TEXTUAL) {
            return WiredVariableTextConnectorSupport.toValue(room, this.variableItemId, capturedValue);
        }

        if (normalizedValue.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(normalizedValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean canUseTextualDisplay(Room room, int definitionItemId) {
        WiredVariableDefinitionInfo definitionInfo = WiredContextVariableSupport.getDefinitionInfo(room, definitionItemId);
        return definitionInfo != null && definitionInfo.hasValue() && definitionInfo.isTextConnected();
    }

    private static String[] splitStringData(String value) {
        if (value == null) {
            return new String[]{ "", DEFAULT_CAPTURER_NAME };
        }

        String[] parts = value.split("\t", -1);
        if (parts.length == 1) {
            return new String[]{ parts[0], DEFAULT_CAPTURER_NAME };
        }

        return new String[]{ parts[0], parts[1] };
    }

    private static int normalizeDisplayType(int value) {
        return (value == DISPLAY_TEXTUAL) ? DISPLAY_TEXTUAL : DISPLAY_NUMERIC;
    }

    private static String normalizeCapturerName(String value) {
        if (value == null) {
            return DEFAULT_CAPTURER_NAME;
        }

        String normalized = value.trim().replace("\t", "").replace("\r", "").replace("\n", "");
        if (WRAPPED_PLACEHOLDER_PATTERN.matcher(normalized).matches()) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        if (normalized.length() > MAX_CAPTURER_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_CAPTURER_NAME_LENGTH);
        }

        return normalized;
    }

    private static String normalizeVariableToken(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        if (normalized.startsWith(CUSTOM_TOKEN_PREFIX)) {
            return normalized;
        }

        try {
            int parsedValue = Integer.parseInt(normalized);
            return parsedValue > 0 ? (CUSTOM_TOKEN_PREFIX + parsedValue) : "";
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
        String capturerName;
        int displayType;

        JsonData(String variableToken, int variableItemId, String capturerName, int displayType) {
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.capturerName = capturerName;
            this.displayType = displayType;
        }
    }
}
