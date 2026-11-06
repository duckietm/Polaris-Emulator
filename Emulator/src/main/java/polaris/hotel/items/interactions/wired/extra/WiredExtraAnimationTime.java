package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.WiredMovementsComposer;

public class WiredExtraAnimationTime extends InteractionWiredExtra {
    public static final int CODE = 60;
    public static final int MIN_DURATION_MS = 50;
    public static final int MAX_DURATION_MS = 2000;

    // A fresh box starts at the official 50 ms.
    private int durationMs = MIN_DURATION_MS;

    public WiredExtraAnimationTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraAnimationTime(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int value = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : this.durationMs;

        // The string is the fallback for a dialog that only has a text field: it is read when no
        // int was sent at all, never over an int that merely equals the current value.
        if (settings.getIntParams().length == 0
                && settings.getStringParam() != null
                && !settings.getStringParam().isEmpty()) {
            try {
                value = Integer.parseInt(settings.getStringParam());
            } catch (NumberFormatException ignored) {
                value = this.durationMs;
            }
        }

        this.durationMs = normalizeDuration(value);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.durationMs));
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
        message.appendInt(this.durationMs);
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
            this.durationMs =
                    normalizeDuration((data != null) ? data.durationMs : WiredMovementsComposer.DEFAULT_DURATION);
            return;
        }

        try {
            this.durationMs = normalizeDuration(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.durationMs = WiredMovementsComposer.DEFAULT_DURATION;
        }
    }

    @Override
    public void onPickUp() {
        this.durationMs = MIN_DURATION_MS;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getDurationMs() {
        return this.durationMs;
    }

    private static int normalizeDuration(int value) {
        return Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, value));
    }

    static class JsonData {
        int durationMs;

        JsonData(int durationMs) {
            this.durationMs = durationMs;
        }
    }
}
