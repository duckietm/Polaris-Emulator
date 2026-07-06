package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredDerivedVariableBox;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for the gen-3 Wired 2.0 QUEST boxes ({@code wf_var_quest} / {@code wf_var_quest_chain}). A quest
 * box is a variable-definition add-on (config-holder, like the level-up box): placed on the SAME stack as
 * a base counter variable, it exposes DERIVED read-only sub-variables (progress / is_complete / …)
 * computed from that counter against a configured {@code targetValue}. Completion is detected for free via
 * the existing {@code VARIABLE_CHANGED} event on the backing counter.
 *
 * <p>Subclasses define the dialog {@link #code()} and the derived sub-variable set + formulas. All
 * sub-variables are always exposed (no selection UI in v1). Config = a single {@code targetValue} int.</p>
 */
public abstract class WiredExtraQuestBase extends InteractionWiredExtra implements WiredDerivedVariableBox {
    protected int targetValue = 0;

    protected WiredExtraQuestBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredExtraQuestBase(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** Client {@code WiredActionLayoutCode} for this box's dialog (108 quest / 109 quest-chain). */
    protected abstract int code();

    /** Number of derived sub-variables this box exposes. */
    public abstract int subvariableCount();

    public int getTargetValue() {
        return this.targetValue;
    }

    @Override
    public List<Integer> getSelectedSubvariables() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < this.subvariableCount(); i++) result.add(i);
        return result;
    }

    @Override
    public boolean hasSubvariable(int subType) {
        return subType >= 0 && subType < this.subvariableCount();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.targetValue = (params.length > 0) ? Math.max(0, params[0]) : 0;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.targetValue));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data != null) this.targetValue = Math.max(0, data.targetValue);
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
        message.appendInt(this.targetValue);
        message.appendInt(0);
        message.appendInt(this.code());
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.targetValue = 0;
    }

    static class JsonData {
        int targetValue;

        JsonData() {
        }

        JsonData(int targetValue) {
            this.targetValue = targetValue;
        }
    }
}
