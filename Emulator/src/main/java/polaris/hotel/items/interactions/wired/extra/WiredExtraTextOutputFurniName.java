package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

public class WiredExtraTextOutputFurniName extends InteractionWiredExtra {
    public static final int CODE = 68;
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_MULTIPLE = 2;
    public static final String DEFAULT_PLACEHOLDER_NAME = "";
    /** AIR: the delimiter starts empty and takes at most 5 characters. */
    public static final String DEFAULT_DELIMITER = "";

    public static final int MAX_PLACEHOLDER_NAME_LENGTH = 32;
    public static final int MAX_DELIMITER_LENGTH = 5;
    /** Boxes saved before AIR's limit keep their longer delimiter until they are saved again. */
    private static final int MAX_STORED_DELIMITER_LENGTH = 16;

    private static final Pattern WRAPPED_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\((.*)\\)$");

    private final Set<UserItem> items;
    private String placeholderName = DEFAULT_PLACEHOLDER_NAME;
    private int placeholderType = TYPE_SINGLE;
    private String delimiter = DEFAULT_DELIMITER;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredExtraTextOutputFurniName(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredExtraTextOutputFurniName(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        String[] stringData = splitStringData(settings.getStringParam());
        boolean multiple = intParams.length > 0 && intParams[0] != 0;
        // AIR refuses an empty name too, also on the box's first frame.
        String refusal = WiredPlaceholderNameRules.refusal(stringData[0], multiple, stringData[1]);
        if (refusal != null) {
            throw new WiredSaveException(refusal);
        }

        this.placeholderType = multiple ? TYPE_MULTIPLE : TYPE_SINGLE;
        this.furniSource = normalizeFurniSource(
                settings.getFurniSourceTypes().length > 0
                        ? settings.getFurniSourceTypes()[0]
                        : WiredSourceUtil.SOURCE_TRIGGER);
        this.placeholderName = WiredPlaceholderNameRules.storedName(stringData[0]);
        this.delimiter = normalizeDelimiter(stringData[1], MAX_DELIMITER_LENGTH);
        this.items.clear();

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        if (settings.getFurniIds().length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        for (int itemId : settings.getFurniIds()) {
            UserItem item = room.getUserItem(itemId);

            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.placeholderName,
                        this.placeholderType,
                        this.delimiter,
                        this.furniSource,
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList())));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (UserItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        // AIR: the name alone, or name and delimiter when every name is written.
        message.appendString(
                this.placeholderType == TYPE_MULTIPLE
                        ? this.placeholderName + "\t" + this.delimiter
                        : this.placeholderName);
        // Official layout: [multiple (0 one, 1 all)], the furni in the official source slot.
        message.appendInt(1);
        message.appendInt(this.placeholderType == TYPE_MULTIPLE ? 1 : 0);
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
                this.delimiter = normalizeDelimiter(data.delimiter, MAX_STORED_DELIMITER_LENGTH);
                this.furniSource = normalizeFurniSource(data.furniSource);

                if (data.itemIds != null) {
                    for (Integer itemId : data.itemIds) {
                        UserItem item = room.getUserItem(itemId);

                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }
            }

            return;
        }

        String[] legacyData = splitStringData(wiredData);
        this.placeholderName = normalizePlaceholderName(legacyData[0]);
        this.delimiter = normalizeDelimiter(legacyData[1], MAX_STORED_DELIMITER_LENGTH);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.placeholderName = DEFAULT_PLACEHOLDER_NAME;
        this.placeholderType = TYPE_SINGLE;
        this.delimiter = DEFAULT_DELIMITER;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

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

    public int getFurniSource() {
        return this.furniSource;
    }

    public Set<UserItem> getItems() {
        return this.items;
    }

    private void refresh(Room room) {
        Set<UserItem> remove = new HashSet<>();

        for (UserItem item : this.items) {
            if (room.getUserItem(item.getId()) == null) {
                remove.add(item);
            }
        }

        for (UserItem item : remove) {
            this.items.remove(item);
        }
    }

    private static String[] splitStringData(String value) {
        if (value == null) {
            return new String[] {DEFAULT_PLACEHOLDER_NAME, DEFAULT_DELIMITER};
        }

        String[] parts = value.split("\t", -1);

        if (parts.length <= 1) {
            return new String[] {value, DEFAULT_DELIMITER};
        }

        return new String[] {parts[0], parts[1]};
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

    private static String normalizeDelimiter(String value, int maxLength) {
        if (value == null) {
            return DEFAULT_DELIMITER;
        }

        String normalized = value.replace("\t", "").replace("\r", "").replace("\n", "");

        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }

        return normalized;
    }

    private static int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
            case WiredSourceUtil.SOURCE_TRIGGER:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    static class JsonData {
        String placeholderName;
        int placeholderType;
        String delimiter;
        int furniSource;
        List<Integer> itemIds;

        JsonData(
                String placeholderName, int placeholderType, String delimiter, int furniSource, List<Integer> itemIds) {
            this.placeholderName = placeholderName;
            this.placeholderType = placeholderType;
            this.delimiter = delimiter;
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }
}
