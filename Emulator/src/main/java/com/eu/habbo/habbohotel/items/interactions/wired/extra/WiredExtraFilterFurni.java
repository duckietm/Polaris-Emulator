package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The official selector filter (AIR addons 10 / 11). Int params {@code [N, N from a variable, variable target]}, ours
 * follow: {@code [random pick]}; variable ids {@code [N variable]} and no string (older clients sent the N variable's
 * id as the string; still read).
 */
public class WiredExtraFilterFurni extends InteractionWiredExtra implements WiredOfficialVariableIds {
    public static final int CODE = 56;
    private static final int MAX_FILTER_AMOUNT = 10000;

    private int amount = 0;
    /** Official: N may come from a variable (the first variable id), read on the triggering holder. */
    private boolean amountFromVariable = false;

    private int amountTarget = WiredValueVariable.TARGET_USER;
    private int amountVariableId = 0;
    /** Ours: keep a random N rather than the first N (saved boxes keep the random pick). */
    private boolean randomPick = true;

    public WiredExtraFilterFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraFilterFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int value = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : 0;

        // The string is the fallback for a dialog that only has a text field: it is read when no
        // int was sent at all, never over an int that merely equals the current value.
        if (settings.getIntParams().length == 0
                && settings.getStringParam() != null
                && !settings.getStringParam().isEmpty()) {
            try {
                value = Integer.parseInt(settings.getStringParam());
            } catch (NumberFormatException ignored) {
                value = 0;
            }
        }

        this.amount = normalizeAmount(value);
        // Official layout: [N, N from a variable, variable target]; ours follow: [random pick].
        int[] params = settings.getIntParams();
        this.amountFromVariable = params.length > 1 && params[1] == 1;
        this.amountTarget =
                WiredValueVariable.normalizeTarget(params.length > 2 ? params[2] : WiredValueVariable.TARGET_USER);
        this.randomPick = params.length > 3 && params[3] == 1;
        int variableId = WiredVariableIds.present(settings.getVariableIds())
                ? WiredVariableIds.boxIdOf(settings.getVariableIds()[0])
                : WiredValueVariable.parseId(settings.getStringParam());
        this.amountVariableId = this.amountFromVariable && params.length > 0 ? variableId : 0;
        if (this.amountFromVariable && this.amountVariableId <= 0) {
            return false;
        }
        return true;
    }

    @Override
    public String[] officialVariableIds() {
        return WiredVariableIds.list(
                this.amountFromVariable ? WiredVariableIds.toWire(this.amountTarget, this.amountVariableId) : "");
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(this.amount);
        data.amountFromVariable = this.amountFromVariable;
        data.amountTarget = this.amountTarget;
        data.amountVariableId = this.amountVariableId;
        data.randomPick = this.randomPick;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.amount);
        message.appendInt(this.amountFromVariable ? 1 : 0);
        message.appendInt(this.amountTarget);
        message.appendInt(this.randomPick ? 1 : 0);
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
            this.amount = normalizeAmount((data != null) ? data.amount : 0);
            if (data != null) {
                this.amountVariableId = Math.max(0, data.amountVariableId);
                this.amountFromVariable = data.amountFromVariable && this.amountVariableId > 0;
                this.amountTarget = WiredValueVariable.normalizeTarget(
                        data.amountTarget == null ? WiredValueVariable.TARGET_USER : data.amountTarget);
                this.randomPick = data.randomPick == null || data.randomPick;
            }
            return;
        }

        try {
            this.amount = normalizeAmount(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.amount = 0;
        }
    }

    @Override
    public void onPickUp() {
        this.amount = 0;
        this.amountFromVariable = false;
        this.amountTarget = WiredValueVariable.TARGET_USER;
        this.amountVariableId = 0;
        this.randomPick = true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getAmount() {
        return this.amount;
    }

    /** N for this firing: the number, or the variable read on the triggering holder (0 when unset). */
    public int resolveAmount(WiredContext ctx) {
        if (!this.amountFromVariable) return this.amount;
        Integer value = WiredValueVariable.read(
                ctx,
                ctx == null ? null : ctx.room(),
                this.amountTarget,
                this.amountVariableId,
                WiredSourceUtil.SOURCE_TRIGGER,
                WiredSourceUtil.SOURCE_TRIGGER);
        return value == null ? 0 : normalizeAmount(value);
    }

    public boolean isRandomPick() {
        return this.randomPick;
    }

    private static int normalizeAmount(int value) {
        return Math.max(0, Math.min(MAX_FILTER_AMOUNT, value));
    }

    static class JsonData {
        int amount;
        boolean amountFromVariable;
        Integer amountTarget;
        int amountVariableId;
        Boolean randomPick;

        JsonData(int amount) {
            this.amount = amount;
        }
    }
}
