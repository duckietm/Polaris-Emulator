package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WiredExtraTextOutputVariable extends InteractionWiredExtra {
    public static final int CODE = 80;
    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_CONTEXT = 2;
    public static final int TARGET_ROOM = 3;
    public static final int DISPLAY_NUMERIC = 1;
    public static final int DISPLAY_TEXTUAL = 2;
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_MULTIPLE = 2;
    public static final String DEFAULT_VARIABLE_TOKEN = "";
    public static final String DEFAULT_PLACEHOLDER_NAME = "";
    public static final String DEFAULT_DELIMITER = ", ";
    public static final int MAX_PLACEHOLDER_NAME_LENGTH = 32;
    public static final int MAX_DELIMITER_LENGTH = 16;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";
    private static final Pattern WRAPPED_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\((.*)\\)$");

    private final THashSet<HabboItem> items;
    private int targetType = TARGET_USER;
    private int displayType = DISPLAY_NUMERIC;
    private int placeholderType = TYPE_SINGLE;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int variableItemId = 0;
    private String variableToken = DEFAULT_VARIABLE_TOKEN;
    private String placeholderName = DEFAULT_PLACEHOLDER_NAME;
    private String delimiter = DEFAULT_DELIMITER;

    public WiredExtraTextOutputVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredExtraTextOutputVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
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
        int nextTargetType = normalizeTargetType((intParams.length > 0) ? intParams[0] : TARGET_USER);
        String nextVariableToken = normalizeVariableToken(stringData[0]);

        if (nextVariableToken.isEmpty()) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.missing_variable");
        }

        if (!isValidVariable(room, nextTargetType, nextVariableToken)) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }

        int nextFurniSource = normalizeFurniSource((intParams.length > 4) ? intParams[4] : WiredSourceUtil.SOURCE_TRIGGER);
        this.items.clear();

        if (nextTargetType == TARGET_FURNI && nextFurniSource == WiredSourceUtil.SOURCE_SELECTED) {
            if (settings.getFurniIds().length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
                throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
            }

            for (int itemId : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(itemId);

                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        this.targetType = nextTargetType;
        this.setVariableToken(nextVariableToken);
        this.displayType = normalizeDisplayType((intParams.length > 1) ? intParams[1] : DISPLAY_NUMERIC);
        this.placeholderType = normalizePlaceholderType((intParams.length > 2) ? intParams[2] : TYPE_SINGLE);
        this.userSource = normalizeUserSource((intParams.length > 3) ? intParams[3] : WiredSourceUtil.SOURCE_TRIGGER);
        this.furniSource = nextFurniSource;
        this.placeholderName = normalizePlaceholderName(stringData[1]);
        this.delimiter = normalizeDelimiter(stringData[2]);

        if (!canUseTextualDisplay(room, this.targetType, this.variableToken)) {
            this.displayType = DISPLAY_NUMERIC;
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.targetType,
                this.variableToken,
                this.variableItemId,
                this.displayType,
                this.placeholderType,
                this.userSource,
                this.furniSource,
                this.placeholderName,
                this.delimiter,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        List<HabboItem> selectedItems = new ArrayList<>();
        if (this.targetType == TARGET_FURNI && this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            selectedItems.addAll(this.items);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(selectedItems.size());

        for (HabboItem item : selectedItems) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.variableToken + "\t" + this.placeholderName + "\t" + this.delimiter);
        message.appendInt(5);
        message.appendInt(this.targetType);
        message.appendInt(this.displayType);
        message.appendInt(this.placeholderType);
        message.appendInt(this.userSource);
        message.appendInt(this.furniSource);
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
                this.targetType = normalizeTargetType(data.targetType);
                this.setVariableToken(normalizeVariableToken((data.variableToken != null) ? data.variableToken : ((data.variableItemId > 0) ? String.valueOf(data.variableItemId) : "")));
                this.displayType = normalizeDisplayType(data.displayType);
                this.placeholderType = normalizePlaceholderType(data.placeholderType);
                this.userSource = normalizeUserSource(data.userSource);
                this.furniSource = normalizeFurniSource(data.furniSource);
                this.placeholderName = normalizePlaceholderName(data.placeholderName);
                this.delimiter = normalizeDelimiter(data.delimiter);

                if (room != null && data.itemIds != null) {
                    for (Integer itemId : data.itemIds) {
                        if (itemId == null || itemId <= 0) {
                            continue;
                        }

                        HabboItem item = room.getHabboItem(itemId);
                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }

                if (room == null || !canUseTextualDisplay(room, this.targetType, this.variableToken)) {
                    this.displayType = DISPLAY_NUMERIC;
                }
            }

            return;
        }

        String[] legacyData = splitStringData(wiredData);
        this.setVariableToken(normalizeVariableToken(legacyData[0]));
        this.placeholderName = normalizePlaceholderName(legacyData[1]);
        this.delimiter = normalizeDelimiter(legacyData[2]);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.targetType = TARGET_USER;
        this.setVariableToken(DEFAULT_VARIABLE_TOKEN);
        this.displayType = DISPLAY_NUMERIC;
        this.placeholderType = TYPE_SINGLE;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.placeholderName = DEFAULT_PLACEHOLDER_NAME;
        this.delimiter = DEFAULT_DELIMITER;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getTargetType() {
        return this.targetType;
    }

    public String getVariableToken() {
        return this.variableToken;
    }

    public int getVariableItemId() {
        return this.variableItemId;
    }

    public int getDisplayType(Room room) {
        return (this.displayType == DISPLAY_TEXTUAL && canUseTextualDisplay(room, this.targetType, this.variableToken))
                ? DISPLAY_TEXTUAL
                : DISPLAY_NUMERIC;
    }

    public int getPlaceholderType() {
        return this.placeholderType;
    }

    public String getPlaceholderName() {
        return this.placeholderName;
    }

    public String getPlaceholderToken() {
        return this.placeholderName.isEmpty() ? "" : "$(" + this.placeholderName + ")";
    }

    public String getDelimiter() {
        return this.delimiter;
    }

    public int getUserSource() {
        return this.userSource;
    }

    public int getFurniSource() {
        return this.furniSource;
    }

    public THashSet<HabboItem> getItems() {
        return this.items;
    }

    public boolean requiresActor() {
        return this.targetType == TARGET_USER
                && (this.userSource == WiredSourceUtil.SOURCE_TRIGGER || this.userSource == WiredSourceUtil.SOURCE_CLICKED_USER);
    }

    public void refresh(Room room) {
        THashSet<HabboItem> remove = new THashSet<>();

        for (HabboItem item : this.items) {
            if (room == null || room.getHabboItem(item.getId()) == null) {
                remove.add(item);
            }
        }

        for (HabboItem item : remove) {
            this.items.remove(item);
        }
    }

    public static boolean isCustomVariableToken(String token) {
        return token != null && token.startsWith(CUSTOM_TOKEN_PREFIX);
    }

    public static boolean isInternalVariableToken(String token) {
        return token != null && token.startsWith(INTERNAL_TOKEN_PREFIX);
    }

    public static String getInternalVariableKey(String token) {
        return isInternalVariableToken(token) ? WiredInternalVariableSupport.normalizeKey(token.substring(INTERNAL_TOKEN_PREFIX.length())) : "";
    }

    public static int getCustomItemId(String token) {
        if (!isCustomVariableToken(token)) {
            return 0;
        }

        try {
            return Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String[] splitStringData(String value) {
        if (value == null) {
            return new String[]{ DEFAULT_VARIABLE_TOKEN, DEFAULT_PLACEHOLDER_NAME, DEFAULT_DELIMITER };
        }

        String[] parts = value.split("\t", -1);
        if (parts.length == 1) {
            return new String[]{ value, DEFAULT_PLACEHOLDER_NAME, DEFAULT_DELIMITER };
        }

        if (parts.length == 2) {
            return new String[]{ parts[0], parts[1], DEFAULT_DELIMITER };
        }

        return new String[]{ parts[0], parts[1], parts[2] };
    }

    private static int normalizeTargetType(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    private static int normalizeDisplayType(int value) {
        return (value == DISPLAY_TEXTUAL) ? DISPLAY_TEXTUAL : DISPLAY_NUMERIC;
    }

    private static int normalizePlaceholderType(int value) {
        return (value == TYPE_MULTIPLE) ? TYPE_MULTIPLE : TYPE_SINGLE;
    }

    private static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    private static int normalizeFurniSource(int value) {
        return switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
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

    private static String normalizeVariableToken(String value) {
        String normalized = (value == null) ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        if (isCustomVariableToken(normalized)) {
            return normalized;
        }

        if (isInternalVariableToken(normalized)) {
            return INTERNAL_TOKEN_PREFIX + WiredInternalVariableSupport.normalizeKey(normalized.substring(INTERNAL_TOKEN_PREFIX.length()));
        }

        try {
            int parsedValue = Integer.parseInt(normalized);
            return parsedValue > 0 ? (CUSTOM_TOKEN_PREFIX + parsedValue) : "";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private void setVariableToken(String token) {
        this.variableToken = normalizeVariableToken(token);
        this.variableItemId = getCustomItemId(this.variableToken);
    }

    private static boolean canUseTextualDisplay(Room room, int targetType, String variableToken) {
        if (room == null || !isCustomVariableToken(variableToken)) {
            return false;
        }

        int itemId = getCustomItemId(variableToken);
        if (itemId <= 0) {
            return false;
        }

        return switch (targetType) {
            case TARGET_USER -> {
                WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(itemId);
                yield definition != null && definition.hasValue() && definition.isTextConnected();
            }
            case TARGET_FURNI -> {
                WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(itemId);
                yield definition != null && definition.hasValue() && definition.isTextConnected();
            }
            case TARGET_CONTEXT -> {
                WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, itemId);
                yield definition != null && definition.hasValue() && definition.isTextConnected();
            }
            case TARGET_ROOM -> {
                WiredVariableDefinitionInfo definition = room.getRoomVariableManager().getDefinitionInfo(itemId);
                yield definition != null && definition.hasValue() && definition.isTextConnected();
            }
            default -> false;
        };
    }

    private static boolean isValidVariable(Room room, int targetType, String variableToken) {
        if (room == null) {
            return false;
        }

        return switch (targetType) {
            case TARGET_USER -> isInternalVariableToken(variableToken)
                    ? canUseUserInternalReference(getInternalVariableKey(variableToken))
                    : isUserCustomValue(room, getCustomItemId(variableToken));
            case TARGET_FURNI -> isInternalVariableToken(variableToken)
                    ? canUseFurniInternalReference(getInternalVariableKey(variableToken))
                    : isFurniCustomValue(room, getCustomItemId(variableToken));
            case TARGET_CONTEXT -> isInternalVariableToken(variableToken)
                    ? WiredInternalVariableSupport.canUseContextReference(getInternalVariableKey(variableToken))
                    : isContextCustomValue(room, getCustomItemId(variableToken));
            case TARGET_ROOM -> isInternalVariableToken(variableToken)
                    ? canUseRoomInternalReference(getInternalVariableKey(variableToken))
                    : isRoomCustomValue(room, getCustomItemId(variableToken));
            default -> false;
        };
    }

    private static boolean isUserCustomValue(Room room, int itemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getUserVariableManager().getDefinitionInfo(itemId) : null;
        return definition != null && definition.hasValue();
    }

    private static boolean isFurniCustomValue(Room room, int itemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getFurniVariableManager().getDefinitionInfo(itemId) : null;
        return definition != null && definition.hasValue();
    }

    private static boolean isRoomCustomValue(Room room, int itemId) {
        WiredVariableDefinitionInfo definition = (room != null) ? room.getRoomVariableManager().getDefinitionInfo(itemId) : null;
        return definition != null && definition.hasValue();
    }

    private static boolean isContextCustomValue(Room room, int itemId) {
        WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, itemId);
        return definition != null && definition.hasValue();
    }

    private static boolean canUseUserInternalReference(String key) {
        return WiredInternalVariableSupport.canUseUserReference(key);
    }

    private static boolean canUseFurniInternalReference(String key) {
        return WiredInternalVariableSupport.canUseFurniReference(key);
    }

    private static boolean canUseRoomInternalReference(String key) {
        return WiredInternalVariableSupport.canUseRoomReference(key);
    }

    static class JsonData {
        int targetType;
        String variableToken;
        int variableItemId;
        int displayType;
        int placeholderType;
        int userSource;
        int furniSource;
        String placeholderName;
        String delimiter;
        List<Integer> itemIds;

        JsonData(int targetType, String variableToken, int variableItemId, int displayType, int placeholderType, int userSource, int furniSource, String placeholderName, String delimiter, List<Integer> itemIds) {
            this.targetType = targetType;
            this.variableToken = variableToken;
            this.variableItemId = variableItemId;
            this.displayType = displayType;
            this.placeholderType = placeholderType;
            this.userSource = userSource;
            this.furniSource = furniSource;
            this.placeholderName = placeholderName;
            this.delimiter = delimiter;
            this.itemIds = itemIds;
        }
    }
}
