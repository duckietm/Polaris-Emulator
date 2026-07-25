package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.outgoing.wired.WiredConditionDataComposer;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Base class for all wired conditions in the game.
 * <p>
 * Conditions are evaluated before effects execute to determine if they should run.
 * They now implement {@link IWiredCondition} for the new context-driven architecture.
 * </p>
 */
public abstract class InteractionWiredCondition extends InteractionWired implements IWiredCondition {
    public InteractionWiredCondition(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredCondition(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (room.canInspectWired(client.getHabbo())) {
                client.sendResponse(new WiredConditionDataComposer(this, room));
                this.activateBox(room);
            }
        }
    }

    public abstract WiredConditionType getType();

    public abstract boolean saveData(WiredSettings settings);

    /**
     * Historical condition-save hook retained for plugin binaries compiled against the
     * packet-based WIRED API. First-party conditions use {@link #saveData(WiredSettings)}; the
     * incoming save adapter calls this descriptor only for a class that has no concrete typed
     * implementation.
     *
     * @param packet packet positioned immediately after the condition item ID
     * @return whether the legacy condition accepted its settings
     */
    public boolean saveData(ClientMessage packet) {
        return false;
    }

    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    // ========== IWiredCondition Implementation ==========

    /**
     * Evaluates whether this condition passes.
     * Subclasses must implement this to define their evaluation logic.
     *
     * @param ctx the wired context containing event data
     * @return true if the condition passes
     */
    @Override
    public abstract boolean evaluate(WiredContext ctx);
}
