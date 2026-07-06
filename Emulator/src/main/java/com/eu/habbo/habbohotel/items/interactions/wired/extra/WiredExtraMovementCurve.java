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
 * Movement-curve add-on (furni classname {@code wf_xtra_mov_curve}). A configuration-holder
 * add-on in the same family as {@link WiredExtraAnimationTime} / {@link WiredExtraMovePhysics}:
 * it stores the easing curve applied to wired furni/user movement and exposes it via
 * {@link #getCurveType()} for the movement subsystem to read. Without a class it loaded as
 * InteractionDefault (inert); this makes the furni open + persist its dialog.
 */
public class WiredExtraMovementCurve extends InteractionWiredExtra {
    public static final int CODE = 97;

    public static final int CURVE_LINEAR = 0;
    public static final int CURVE_EASE_IN = 1;
    public static final int CURVE_EASE_OUT = 2;
    public static final int CURVE_EASE_IN_OUT = 3;
    private static final int CURVE_MIN = CURVE_LINEAR;
    private static final int CURVE_MAX = CURVE_EASE_IN_OUT;

    private int curveType = CURVE_LINEAR;

    public WiredExtraMovementCurve(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMovementCurve(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int value = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : this.curveType;

        if (value == this.curveType && settings.getStringParam() != null && !settings.getStringParam().isEmpty()) {
            try {
                value = Integer.parseInt(settings.getStringParam());
            } catch (NumberFormatException ignored) {
                value = this.curveType;
            }
        }

        this.curveType = normalizeCurve(value);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.curveType));
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
        message.appendInt(this.curveType);
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
            this.curveType = normalizeCurve((data != null) ? data.curveType : CURVE_LINEAR);
            return;
        }

        try {
            this.curveType = normalizeCurve(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.curveType = CURVE_LINEAR;
        }
    }

    @Override
    public void onPickUp() {
        this.curveType = CURVE_LINEAR;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getCurveType() {
        return this.curveType;
    }

    private static int normalizeCurve(int value) {
        return Math.max(CURVE_MIN, Math.min(CURVE_MAX, value));
    }

    static class JsonData {
        int curveType;

        JsonData(int curveType) {
            this.curveType = curveType;
        }
    }
}
