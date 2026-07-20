package com.eu.habbo.stress;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

final class StressWiredEffect extends InteractionWiredEffect {
    private final AtomicLong executions;

    StressWiredEffect(int id, int userId, Item item, AtomicLong executions) {
        super(id, userId, item, "0", 0, 0);
        this.executions = executions;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.SHOW_MESSAGE;
    }

    @Override
    public void execute(WiredContext context) {
        this.executions.incrementAndGet();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return "";
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
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        // Synthetic wired effects have no persisted settings.
    }

    @Override
    public void onPickUp() {
        // No retained runtime state.
    }

    @Override
    public void run() {
        // Stress entities are deliberately never persisted.
    }
}
