package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Cancel Transaction (furni classname {@code wf_act_cancel_transaction}). v1 (outcome-signal model):
 * raises a {@link WiredEvent.Type#TRANSACTION_FAIL} event in the room, firing every
 * {@code wf_trg_transaction_fail} trigger (the "failure/rollback" branch). Pair with
 * {@code wf_act_init_transaction} for the success branch.
 */
public class WiredEffectCancelTransaction extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CANCEL_TRANSACTION;

    public WiredEffectCancelTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectCancelTransaction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;

        RoomTile tile = room.getLayout().getTile(this.getX(), this.getY());
        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, room).sourceItem(this);
        if (tile != null) builder.tile(tile);
        ctx.actor().ifPresent(builder::actor);

        WiredManager.dispatchEffectTriggeredEvent(builder.build());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setDelay(0);
        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) this.setDelay(data.delay);
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
    }

    static class JsonData {
        int delay;

        public JsonData(int delay) {
            this.delay = delay;
        }
    }
}
