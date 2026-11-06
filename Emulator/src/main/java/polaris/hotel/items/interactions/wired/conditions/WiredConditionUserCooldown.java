package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;

/**
 * "Trigger cooldown" ({@code wf_cnd_user_cooldown}): the triggering user passes, and then not again
 * until the configured seconds have gone by. One int: the seconds. The clock lives in memory, so a
 * restart forgives everyone; that is the kinder failure for a cooldown.
 */
public class WiredConditionUserCooldown extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.USER_COOLDOWN;
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 7 * 24 * 3600;
    private static final int MAX_TRACKED_USERS = 10_000;

    private int seconds = MIN_SECONDS;
    private final Map<Integer, Long> lastPassMillis = new LinkedHashMap<>();
    private LongSupplier clock = System::currentTimeMillis;

    public WiredConditionUserCooldown(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserCooldown(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    void clock(LongSupplier clock) {
        this.clock = clock;
    }

    public int getSeconds() {
        return this.seconds;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Integer userId = triggeringUserId(ctx);
        if (userId == null) return false;

        long now = this.clock.getAsLong();
        synchronized (this.lastPassMillis) {
            Long last = this.lastPassMillis.get(userId);
            return last == null || now - last >= this.seconds * 1000L;
        }
    }

    /** The cooldown starts only when the stack runs: a stack stopped by another condition does not start it. */
    @Override
    public void commit(WiredContext ctx) {
        Integer userId = triggeringUserId(ctx);
        if (userId == null) return;

        long now = this.clock.getAsLong();
        synchronized (this.lastPassMillis) {
            this.lastPassMillis.remove(userId);
            this.lastPassMillis.put(userId, now);
            trimOldest(this.lastPassMillis);
        }
    }

    static Integer triggeringUserId(WiredContext ctx) {
        Room room = ctx == null ? null : ctx.room();
        if (room == null) return null;
        List<RoomUnit> units = WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER);
        if (units.isEmpty() || units.get(0) == null) return null;
        User user = room.getUser(units.get(0));
        return (user == null || user.getUserInfo() == null)
                ? null
                : user.getUserInfo().getId();
    }

    static <V> void trimOldest(Map<Integer, V> map) {
        Iterator<Integer> oldest = map.keySet().iterator();
        while (map.size() > MAX_TRACKED_USERS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.seconds = normalizeSeconds((params != null && params.length > 0) ? params[0] : MIN_SECONDS);
        synchronized (this.lastPassMillis) {
            this.lastPassMillis.clear();
        }
        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.seconds));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.seconds);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data != null) this.seconds = normalizeSeconds(data.seconds);
    }

    @Override
    public void onPickUp() {
        this.seconds = MIN_SECONDS;
        synchronized (this.lastPassMillis) {
            this.lastPassMillis.clear();
        }
        this.setExtradata("");
    }

    static int normalizeSeconds(int value) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, value));
    }

    static class JsonData {
        int seconds;

        JsonData(int seconds) {
            this.seconds = seconds;
        }
    }
}
