package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

/**
 * Global placeholder ({@code wf_xtra_text_output_global}, code 2000): a {@code $(name)} placeholder
 * with a fixed text that every wired text in the room is run through, not only its own stack. The
 * text is typed in the box, or taken from a typed global placeholder in another room of the same
 * owner.
 *
 * <p>Int params {@code [mode, 0, sourceRoomId]}; string param {@code name<TAB>text} for a typed value
 * and {@code name<TAB>sourcePlaceholderName} for another room's. The editor also receives a third
 * tab-separated field: the owner's shared placeholders as JSON.</p>
 */
public class WiredExtraTextOutputGlobal extends InteractionWiredExtra {
    public static final int CODE = 2000;
    public static final int MODE_FROM_VALUE = 0;
    public static final int MODE_FROM_ANOTHER_ROOM = 1;
    public static final int MAX_PLACEHOLDER_NAME_LENGTH = 32;
    public static final int MAX_VALUE_LENGTH = 100;

    private static final String MISSING_SOURCE_MESSAGE = "Pick a placeholder that another of your rooms shares.";
    private static final Pattern WRAPPED_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\((.*)\\)$");

    private String placeholderName = "";
    private int mode = MODE_FROM_VALUE;
    private String value = "";
    private int sourceRoomId = 0;
    private String sourcePlaceholderName = "";
    private String sourceValue = "";

    public WiredExtraTextOutputGlobal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTextOutputGlobal(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        String[] parts = splitStringData(settings.getStringParam());
        int nextMode = normalizeMode((intParams.length > 0) ? intParams[0] : MODE_FROM_VALUE);
        String nextName = normalizePlaceholderName(parts[0]);

        if (nextMode == MODE_FROM_VALUE) {
            this.placeholderName = nextName;
            this.mode = MODE_FROM_VALUE;
            this.value = normalizeValue(parts[1]);
            this.clearSource();
            return true;
        }

        int nextSourceRoomId = (intParams.length > 2) ? intParams[2] : 0;
        String nextSourceName = normalizePlaceholderName(parts[1]);
        Room room = settings.getRoom();

        if (nextSourceRoomId <= 0 || nextSourceName.isEmpty() || room == null || nextSourceRoomId == room.getId()) {
            throw new WiredSaveException(MISSING_SOURCE_MESSAGE);
        }

        WiredGlobalPlaceholderSupport.SharedPlaceholder shared =
                WiredGlobalPlaceholderSupport.findShared(room, nextSourceRoomId, nextSourceName);
        if (shared == null) {
            throw new WiredSaveException(MISSING_SOURCE_MESSAGE);
        }

        String live = WiredGlobalPlaceholderSupport.liveValue(room, shared.roomId(), shared.name());
        this.placeholderName = nextName.isEmpty() ? shared.name() : nextName;
        this.mode = MODE_FROM_ANOTHER_ROOM;
        this.value = "";
        this.sourceRoomId = shared.roomId();
        this.sourcePlaceholderName = shared.name();
        this.sourceValue = normalizeValue((live != null) ? live : shared.value());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.placeholderName,
                        this.mode,
                        this.value,
                        this.sourceRoomId,
                        this.sourcePlaceholderName,
                        this.sourceValue));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        String second = (this.mode == MODE_FROM_VALUE) ? this.value : this.sourcePlaceholderName;

        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.placeholderName + "\t" + second + "\t" + buildSharedListJson(room));
        message.appendInt(3);
        message.appendInt(this.mode);
        message.appendInt(0);
        message.appendInt(this.sourceRoomId);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        JsonData data = parseJsonData(set.getString("wired_data"));
        if (data == null) {
            return;
        }

        this.placeholderName = normalizePlaceholderName(data.placeholderName);
        this.mode = normalizeMode(data.mode);
        this.value = normalizeValue(data.value);
        this.sourceRoomId = Math.max(0, data.sourceRoomId);
        this.sourcePlaceholderName = normalizePlaceholderName(data.sourcePlaceholderName);
        this.sourceValue = normalizeValue(data.sourceValue);

        if (this.mode == MODE_FROM_ANOTHER_ROOM && (this.sourceRoomId <= 0 || this.sourcePlaceholderName.isEmpty())) {
            this.mode = MODE_FROM_VALUE;
            this.clearSource();
        }
    }

    @Override
    public void onPickUp() {
        this.placeholderName = "";
        this.mode = MODE_FROM_VALUE;
        this.value = "";
        this.clearSource();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

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

    public int getMode() {
        return this.mode;
    }

    public String getValue() {
        return this.value;
    }

    public int getSourceRoomId() {
        return this.sourceRoomId;
    }

    public String getSourcePlaceholderName() {
        return this.sourcePlaceholderName;
    }

    /** The text the placeholder stands for: the typed value, or the source's current text (last saved when its room is not loaded). */
    public String resolveText(Room room) {
        if (this.mode == MODE_FROM_VALUE) {
            return this.value;
        }

        String live = WiredGlobalPlaceholderSupport.liveValue(room, this.sourceRoomId, this.sourcePlaceholderName);
        return (live != null) ? normalizeValue(live) : this.sourceValue;
    }

    private void clearSource() {
        this.sourceRoomId = 0;
        this.sourcePlaceholderName = "";
        this.sourceValue = "";
    }

    private static String buildSharedListJson(Room room) {
        Map<Integer, SharedRoomData> rooms = new LinkedHashMap<>();

        for (WiredGlobalPlaceholderSupport.SharedPlaceholder shared : WiredGlobalPlaceholderSupport.loadShared(room)) {
            rooms.computeIfAbsent(shared.roomId(), key -> new SharedRoomData(shared.roomId(), shared.roomName()))
                    .placeholders
                    .add(shared.name());
        }

        return WiredManager.getGson().toJson(new SharedListData(new ArrayList<>(rooms.values())));
    }

    static JsonData parseJsonData(String wiredData) {
        return WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
    }

    private static String[] splitStringData(String value) {
        if (value == null) {
            return new String[] {"", ""};
        }

        String[] parts = value.split("\t", -1);
        return new String[] {parts[0], (parts.length > 1) ? parts[1] : ""};
    }

    static int normalizeMode(int value) {
        return (value == MODE_FROM_ANOTHER_ROOM) ? MODE_FROM_ANOTHER_ROOM : MODE_FROM_VALUE;
    }

    static String normalizePlaceholderName(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().replace("\t", "").replace("\r", "").replace("\n", "");
        if (WRAPPED_PLACEHOLDER_PATTERN.matcher(normalized).matches()) {
            normalized = normalized.substring(2, normalized.length() - 1).trim();
        }

        return (normalized.length() > MAX_PLACEHOLDER_NAME_LENGTH)
                ? normalized.substring(0, MAX_PLACEHOLDER_NAME_LENGTH)
                : normalized;
    }

    static String normalizeValue(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace("\t", "").replace("\r", "").replace("\n", "");
        return (normalized.length() > MAX_VALUE_LENGTH) ? normalized.substring(0, MAX_VALUE_LENGTH) : normalized;
    }

    static class JsonData {
        String placeholderName;
        int mode;
        String value;
        int sourceRoomId;
        String sourcePlaceholderName;
        String sourceValue;

        JsonData(
                String placeholderName,
                int mode,
                String value,
                int sourceRoomId,
                String sourcePlaceholderName,
                String sourceValue) {
            this.placeholderName = placeholderName;
            this.mode = mode;
            this.value = value;
            this.sourceRoomId = sourceRoomId;
            this.sourcePlaceholderName = sourcePlaceholderName;
            this.sourceValue = sourceValue;
        }
    }

    static class SharedListData {
        List<SharedRoomData> rooms;

        SharedListData(List<SharedRoomData> rooms) {
            this.rooms = rooms;
        }
    }

    static class SharedRoomData {
        int roomId;
        String roomName;
        List<String> placeholders = new ArrayList<>();

        SharedRoomData(int roomId, String roomName) {
            this.roomId = roomId;
            this.roomName = roomName;
        }
    }
}
