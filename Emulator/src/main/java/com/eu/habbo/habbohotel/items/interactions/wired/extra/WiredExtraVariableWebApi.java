package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.threading.ThreadPooling;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * The Variables Web API add-on. It holds a read key and a write key for the room it stands in and
 * the bulk-delete permission; the HTTP API itself lives in {@code networking.gameserver.wired}.
 *
 * <p>Keys are minted here, one at a time, when the owner asks for one (packet 2819). A save can only
 * keep a key or clear it. The stored keys are bound to this item id and to the owner who made
 * them, so a copied row or a box that changed owner by any route has no keys.
 */
public class WiredExtraVariableWebApi extends InteractionWiredExtra {
    public static final int CODE = 128;
    public static final int KEY_BYTES = 32;
    public static final int KEY_LENGTH = 43;
    public static final long GENERATE_COOLDOWN_MILLIS = 2000L;
    public static final String INTERACTION_TYPE = "wf_xtra_var_web_api";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{" + KEY_LENGTH + "}");
    private static final char FIELD_SEPARATOR = '\t';
    private static final KeyState EMPTY = new KeyState("", "", null, null, false);

    private volatile Held held = new Held(EMPTY, 0);
    private long lastGenerateMillis = Long.MIN_VALUE;

    public WiredExtraVariableWebApi(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableWebApi(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static String mintKey() {
        byte[] material = new byte[KEY_BYTES];
        RANDOM.nextBytes(material);
        return KEY_ENCODER.encodeToString(material);
    }

    /** SHA-256 of the key, or null for anything that cannot be a key. */
    public static byte[] hashKey(String key) {
        if (!isWellFormedKey(key)) {
            return null;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean isWellFormedKey(String key) {
        return key != null
                && key.length() == KEY_LENGTH
                && KEY_PATTERN.matcher(key).matches();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return true;
    }

    /**
     * Only the owner changes anything: a key survives when sent back unchanged, is cleared when sent
     * empty, and anything else is ignored. Saves by other users leave the box as it is, because they
     * are shown empty keys and would otherwise wipe them.
     */
    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Habbo habbo = gameClient == null ? null : gameClient.getHabbo();
        if (habbo == null
                || habbo.getHabboInfo() == null
                || habbo.getHabboInfo().getId() != this.getUserId()) {
            return true;
        }

        String stringParam = settings.getStringParam() == null ? "" : settings.getStringParam();
        int separator = stringParam.indexOf(FIELD_SEPARATOR);
        String sentRead = separator < 0 ? stringParam : stringParam.substring(0, separator);
        String sentWrite = separator < 0 ? "" : stringParam.substring(separator + 1);
        int[] intParams = settings.getIntParams();
        boolean bulk = intParams != null && intParams.length > 0 && intParams[0] == 1;

        synchronized (this) {
            KeyState current = this.keys();
            String read = keepOrClear(current.readKey(), sentRead);
            String write = keepOrClear(current.writeKey(), sentWrite);
            this.hold(KeyState.of(read, write, bulk));
        }

        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    private static String keepOrClear(String held, String sent) {
        return sent.isEmpty() ? "" : held;
    }

    /**
     * Mints a new read or write key for the owner and stores it at once. Null when the requester is
     * not the owner, the box does not stand in a room the owner owns, or the last key was minted less
     * than {@link #GENERATE_COOLDOWN_MILLIS} ago.
     */
    public String generateKey(Habbo requester, Room room, boolean readKey, long nowMillis) {
        if (requester == null
                || requester.getHabboInfo() == null
                || requester.getHabboInfo().getId() != this.getUserId()
                || !this.isUsableIn(room)) {
            return null;
        }

        String key;
        synchronized (this) {
            if (this.lastGenerateMillis != Long.MIN_VALUE
                    && nowMillis - this.lastGenerateMillis < GENERATE_COOLDOWN_MILLIS) {
                return null;
            }
            this.lastGenerateMillis = nowMillis;
            key = mintKey();
            KeyState current = this.keys();
            this.hold(
                    readKey
                            ? KeyState.of(key, current.writeKey(), current.bulkDelete())
                            : KeyState.of(current.readKey(), key, current.bulkDelete()));
        }

        this.needsUpdate(true);
        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) {
            threading.run(this);
        }
        return key;
    }

    /** True while the box stands in {@code room} and that room belongs to the box owner. */
    public boolean isUsableIn(Room room) {
        return room != null
                && this.getRoomId() > 0
                && room.getId() == this.getRoomId()
                && room.getOwnerId() == this.getUserId();
    }

    /** What a presented key hash opens on this box, or null. Both hashes are always compared. */
    public Access authenticate(byte[] presentedHash) {
        return this.keys().authenticate(presentedHash);
    }

    public boolean isBulkDeleteAllowed() {
        return this.keys().bulkDelete();
    }

    /** The keys, empty once the box belongs to someone other than who made them. */
    private KeyState keys() {
        Held current = this.held;
        return current.ownerId() > 0 && current.ownerId() == this.getUserId() ? current.keys() : EMPTY;
    }

    private void hold(KeyState state) {
        this.held = new Held(state, this.getUserId());
    }

    @Override
    public String getWiredData() {
        KeyState state = this.keys();
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getId(), this.getUserId(), state.readKey(), state.writeKey(), state.bulkDelete()));
    }

    /** Settings as anyone but the owner sees them: no keys. */
    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serialize(message, "", "", this.keys().bulkDelete());
    }

    @Override
    public void serializeWiredDataFor(ServerMessage message, Room room, Habbo viewer) {
        KeyState state = this.keys();
        if (viewer != null
                && viewer.getHabboInfo() != null
                && viewer.getHabboInfo().getId() == this.getUserId()) {
            this.serialize(message, state.readKey(), state.writeKey(), state.bulkDelete());
        } else {
            this.serialize(message, "", "", state.bulkDelete());
        }
    }

    private void serialize(ServerMessage message, String read, String write, boolean bulk) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(read + FIELD_SEPARATOR + write);
        message.appendInt(1);
        message.appendInt(bulk ? 1 : 0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    /**
     * Rows without the item and owner binding (the first design, or rows saved before the owner was
     * recorded) load without keys and the owner generates new ones.
     */
    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setExtradata("");
        this.hold(parseStored(set.getString("wired_data"), this.getId(), this.getUserId()));
    }

    /** The keys a stored row holds for the given item id, ignoring who owns it. */
    public static KeyState parseStored(String wiredData, int itemId) {
        JsonData data = parseRow(wiredData, itemId);
        return data == null ? EMPTY : keysOf(data);
    }

    /** The keys a stored row holds for this item and owner, empty when either does not match. */
    public static KeyState parseStored(String wiredData, int itemId, int ownerId) {
        JsonData data = parseRow(wiredData, itemId);
        return data == null || ownerId <= 0 || data.ownerId != ownerId ? EMPTY : keysOf(data);
    }

    private static JsonData parseRow(String wiredData, int itemId) {
        if (wiredData == null || !wiredData.startsWith("{")) {
            return null;
        }
        JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
        return data == null || data.itemId <= 0 || data.itemId != itemId ? null : data;
    }

    private static KeyState keysOf(JsonData data) {
        String read = isWellFormedKey(data.readKey) ? data.readKey : "";
        String write = isWellFormedKey(data.writeKey) ? data.writeKey : "";
        return KeyState.of(read, write, data.bulkDelete);
    }

    @Override
    public void onPickUp() {
        this.held = new Held(EMPTY, 0);
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    /** Only the owner may see this; it is here for the settings packet and persistence. */
    public String getReadKey() {
        return this.keys().readKey();
    }

    /** Only the owner may see this; it is here for the settings packet and persistence. */
    public String getWriteKey() {
        return this.keys().writeKey();
    }

    /** @deprecated the box no longer binds a variable. */
    @Deprecated
    public String getVariableToken() {
        return "";
    }

    /** @deprecated the box no longer binds a variable. */
    @Deprecated
    public int getVariableItemId() {
        return 0;
    }

    /** @deprecated whether a write key exists. */
    @Deprecated
    public boolean isWriteEnabled() {
        return !this.keys().writeKey().isEmpty();
    }

    /** @deprecated keys are only checked against the box of the requested room; this finds nothing. */
    @Deprecated
    public static Lookup resolve(String key) {
        return null;
    }

    public enum Access {
        READ,
        WRITE
    }

    public record Lookup(WiredExtraVariableWebApi addon, Access access) {}

    /** The keys of one box with their hashes. The plain keys are only for the owner and storage. */
    public record KeyState(String readKey, String writeKey, byte[] readHash, byte[] writeHash, boolean bulkDelete) {
        static KeyState of(String readKey, String writeKey, boolean bulkDelete) {
            String read = readKey == null ? "" : readKey;
            String write = writeKey == null ? "" : writeKey;
            return new KeyState(read, write, hashKey(read), hashKey(write), bulkDelete && !write.isEmpty());
        }

        public boolean hasKeys() {
            return this.readHash != null || this.writeHash != null;
        }

        public Access authenticate(byte[] presentedHash) {
            if (presentedHash == null) {
                return null;
            }
            boolean write = this.writeHash != null && MessageDigest.isEqual(this.writeHash, presentedHash);
            boolean read = this.readHash != null && MessageDigest.isEqual(this.readHash, presentedHash);
            if (write) {
                return Access.WRITE;
            }
            return read ? Access.READ : null;
        }

        @Override
        public String toString() {
            return "KeyState[bulkDelete=" + this.bulkDelete + "]";
        }
    }

    private record Held(KeyState keys, int ownerId) {}

    static class JsonData {
        int itemId;
        int ownerId;
        String readKey;
        String writeKey;
        boolean bulkDelete;

        JsonData(int itemId, int ownerId, String readKey, String writeKey, boolean bulkDelete) {
            this.itemId = itemId;
            this.ownerId = ownerId;
            this.readKey = readKey;
            this.writeKey = writeKey;
            this.bulkDelete = bulkDelete;
        }
    }
}
