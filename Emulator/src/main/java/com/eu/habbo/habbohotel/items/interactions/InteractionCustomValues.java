package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class InteractionCustomValues extends HabboItem {
    /** The furthest an area furni reaches from its own tile, on each side. */
    protected static final int MAX_AREA_TILES = 128;

    public final Map<String, String> values = new HashMap<>();

    /** The keys a save may set: the ones the furni starts with. */
    private final Set<String> knownKeys;

    public InteractionCustomValues(ResultSet set, Item baseItem, Map<String, String> defaultValues)
            throws SQLException {
        super(set, baseItem);

        this.values.putAll(defaultValues);
        this.knownKeys = Set.copyOf(defaultValues.keySet());

        for (String s : set.getString("extra_data").split(";")) {
            // Split on the FIRST '=' only: values such as room-ad image URLs
            // carry '=' in their query string, and splitting on every '='
            // used to drop them (data.length != 2), so the ad image vanished.
            int separator = s.indexOf('=');
            if (separator > 0) {
                this.values.put(s.substring(0, separator), decode(s.substring(separator + 1)));
            }
        }
    }

    public InteractionCustomValues(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells,
            Map<String, String> defaultValues) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.values.putAll(defaultValues);
        this.knownKeys = Set.copyOf(defaultValues.keySet());
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void run() {
        this.setExtradata(this.toExtraData());

        super.run();
    }

    public String toExtraData() {
        StringBuilder data = new StringBuilder();
        synchronized (this.values) {
            for (Map.Entry<String, String> set : this.values.entrySet()) {
                data.append(set.getKey())
                        .append("=")
                        .append(encode(set.getValue()))
                        .append(";");
            }
        }

        return data.toString();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(1 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(this.values.size());
        for (Map.Entry<String, String> set : this.values.entrySet()) {
            serverMessage.appendString(set.getKey());
            serverMessage.appendString(set.getValue());
        }

        super.serializeExtradata(serverMessage);
    }

    public void onCustomValuesSaved(Room room, GameClient client, Map<String, String> oldValues) {}

    /** Whether a save may set this key: only the furni's own keys are kept. */
    public boolean acceptsKey(String key) {
        return this.knownKeys.contains(key);
    }

    /** The value a save stores for one of the furni's keys, or null to keep the stored one. */
    public String sanitizeValue(String key, String value) {
        return value;
    }

    /** A whole number in [0, max] as text, or null when the value is not a number. */
    protected static String boundedInt(String value, int max) {
        try {
            return Integer.toString(Math.max(0, Math.min(Integer.parseInt(value.trim()), max)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A stored number, 0 when it is missing or not a number: a bad value must not break every check. */
    protected static int intValue(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(values.get(key).trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // A value may hold ';' (a list of builders, an address), which also separates the stored entries.
    static String encode(String value) {
        return value == null ? "" : value.replace("%", "%25").replace(";", "%3B");
    }

    static String decode(String value) {
        return value.replace("%3B", ";").replace("%25", "%");
    }
}
