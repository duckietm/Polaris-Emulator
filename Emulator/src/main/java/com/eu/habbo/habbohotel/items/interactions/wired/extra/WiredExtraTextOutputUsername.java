package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class WiredExtraTextOutputUsername extends InteractionWiredExtra {
    public static final int CODE = 67;
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_MULTIPLE = 2;
    public static final String DEFAULT_PLACEHOLDER_NAME = "";
    public static final String DEFAULT_DELIMITER = ", ";
    public static final int MAX_PLACEHOLDER_NAME_LENGTH = 32;
    public static final int MAX_DELIMITER_LENGTH = 16;

    private static final Pattern WRAPPED_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\((.*)\\)$");

    private String placeholderName = DEFAULT_PLACEHOLDER_NAME;
    private int placeholderType = TYPE_SINGLE;
    private String delimiter = DEFAULT_DELIMITER;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredExtraTextOutputUsername(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTextOutputUsername(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] intParams = settings.getIntParams();
        String[] stringData = splitStringData(settings.getStringParam());

        this.placeholderType = normalizePlaceholderType((intParams.length > 0) ? intParams[0] : TYPE_SINGLE);
        this.userSource = normalizeUserSource((intParams.length > 1) ? intParams[1] : WiredSourceUtil.SOURCE_TRIGGER);
        this.placeholderName = normalizePlaceholderName(stringData[0]);
        this.delimiter = normalizeDelimiter(stringData[1]);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.placeholderName, this.placeholderType, this.delimiter, this.userSource));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.placeholderName + "\t" + this.delimiter);
        message.appendInt(2);
        message.appendInt(this.placeholderType);
        message.appendInt(this.userSource);
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
                this.placeholderName = normalizePlaceholderName(data.placeholderName);
                this.placeholderType = normalizePlaceholderType(data.placeholderType);
                this.delimiter = normalizeDelimiter(data.delimiter);
                this.userSource = normalizeUserSource(data.userSource);
            }

            return;
        }

        String[] legacyData = splitStringData(wiredData);
        this.placeholderName = normalizePlaceholderName(legacyData[0]);
        this.delimiter = normalizeDelimiter(legacyData[1]);
    }

    @Override
    public void onPickUp() {
        this.placeholderName = DEFAULT_PLACEHOLDER_NAME;
        this.placeholderType = TYPE_SINGLE;
        this.delimiter = DEFAULT_DELIMITER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public String getPlaceholderName() {
        return this.placeholderName;
    }

    public String getPlaceholderToken() {
        return this.placeholderName.isEmpty() ? "" : "$(" + this.placeholderName + ")";
    }

    public int getPlaceholderType() {
        return this.placeholderType;
    }

    public String getDelimiter() {
        return this.delimiter;
    }

    public int getUserSource() {
        return this.userSource;
    }

    private static String[] splitStringData(String value) {
        if (value == null) {
            return new String[] { DEFAULT_PLACEHOLDER_NAME, DEFAULT_DELIMITER };
        }

        String[] parts = value.split("\t", -1);

        if (parts.length <= 1) {
            return new String[] { value, DEFAULT_DELIMITER };
        }

        return new String[] { parts[0], parts[1] };
    }

    private static int normalizePlaceholderType(int value) {
        return (value == TYPE_MULTIPLE) ? TYPE_MULTIPLE : TYPE_SINGLE;
    }

    private static String normalizePlaceholderName(String value) {
        if (value == null) {
            return DEFAULT_PLACEHOLDER_NAME;
        }

        String normalized = value.trim().replace("\t", "").replace("\r", "").replace("\n", "");
        if (WRAPPED_PLACEHOLDER_PATTERN.matcher(normalized).matches()) {
            normalized = normalized.substring(2, normalized.length() - 1).trim();
        }

        if (normalized.length() > MAX_PLACEHOLDER_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_PLACEHOLDER_NAME_LENGTH);
        }

        return normalized;
    }

    private static String normalizeDelimiter(String value) {
        if (value == null) {
            return DEFAULT_DELIMITER;
        }

        String normalized = value.replace("\t", "").replace("\r", "").replace("\n", "");

        if (normalized.length() > MAX_DELIMITER_LENGTH) {
            normalized = normalized.substring(0, MAX_DELIMITER_LENGTH);
        }

        return normalized;
    }

    private static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        String placeholderName;
        int placeholderType;
        String delimiter;
        int userSource;

        JsonData(String placeholderName, int placeholderType, String delimiter, int userSource) {
            this.placeholderName = placeholderName;
            this.placeholderType = placeholderType;
            this.delimiter = delimiter;
            this.userSource = userSource;
        }
    }
}
