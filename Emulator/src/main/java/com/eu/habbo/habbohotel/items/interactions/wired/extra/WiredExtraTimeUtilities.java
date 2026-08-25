package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Time-utilities add-on (furni classname {@code wf_xtra_var_time_util}). A configuration-holder
 * add-on in the {@link WiredExtraAnimationTime} family: it stores the time unit used when a wired
 * stack reads/writes time-based values and exposes it via {@link #getTimeUnit()}. Previously this
 * furni had {@code interaction_type='default'} and loaded as InteractionDefault (inert); this makes
 * it open + persist its dialog.
 */
public class WiredExtraTimeUtilities extends InteractionWiredExtra {
    public static final int CODE = 98;

    public static final int UNIT_MILLISECONDS = 0;
    public static final int UNIT_SECONDS = 1;
    public static final int UNIT_MINUTES = 2;
    public static final int UNIT_HOURS = 3;
    private static final int UNIT_MIN = UNIT_MILLISECONDS;
    private static final int UNIT_MAX = UNIT_HOURS;

    private int timeUnit = UNIT_SECONDS;

    public WiredExtraTimeUtilities(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTimeUtilities(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int value = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : this.timeUnit;

        if (value == this.timeUnit && settings.getStringParam() != null && !settings.getStringParam().isEmpty()) {
            try {
                value = Integer.parseInt(settings.getStringParam());
            } catch (NumberFormatException ignored) {
                value = this.timeUnit;
            }
        }

        this.timeUnit = normalizeUnit(value);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.timeUnit));
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
        message.appendInt(this.timeUnit);
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
            this.timeUnit = normalizeUnit((data != null) ? data.timeUnit : UNIT_SECONDS);
            return;
        }

        try {
            this.timeUnit = normalizeUnit(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.timeUnit = UNIT_SECONDS;
        }
    }

    @Override
    public void onPickUp() {
        this.timeUnit = UNIT_SECONDS;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getTimeUnit() {
        return this.timeUnit;
    }

    private static int normalizeUnit(int value) {
        return Math.max(UNIT_MIN, Math.min(UNIT_MAX, value));
    }

    static class JsonData {
        int timeUnit;

        JsonData(int timeUnit) {
            this.timeUnit = timeUnit;
        }
    }
}
