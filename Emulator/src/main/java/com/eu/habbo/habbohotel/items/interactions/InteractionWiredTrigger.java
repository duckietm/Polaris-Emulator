package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.outgoing.wired.WiredTriggerDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Base class for all wired triggers in the game.
 * <p>
 * Triggers are the entry points for wired execution. They now implement
 * {@link IWiredTrigger} for the new context-driven architecture.
 * </p>
 */
public abstract class InteractionWiredTrigger extends InteractionWired implements IWiredTrigger {
    private int delay;

    protected InteractionWiredTrigger(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredTrigger(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
                client.sendResponse(new WiredTriggerDataComposer(this, room));
                this.activateBox(room);
            }
        }
    }

    public abstract WiredTriggerType getType();

    public abstract boolean saveData(WiredSettings settings);

    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        return this.saveData(settings);
    }

    protected int getDelay() {
        return this.delay;
    }

    protected void setDelay(int value) {
        this.delay = value;
    }

    public boolean isTriggeredByRoomUnit() {
        return false;
    }
    
    // ========== IWiredTrigger Implementation ==========
    
    /**
     * Returns the event type this trigger responds to.
     * Maps the WiredTriggerType to the new WiredEvent.Type.
     * 
     * @return the event type this trigger responds to
     */
    @Override
    public WiredEvent.Type listensTo() {
        return WiredEvent.Type.fromLegacyType(this.getType());
    }
    
    /**
     * Checks if this trigger matches the given event.
     * Subclasses must implement this to define their matching logic.
     * 
     * @param triggerItem the wired trigger furniture item
     * @param event the event that occurred
     * @return true if this trigger should activate
     */
    @Override
    public abstract boolean matches(HabboItem triggerItem, WiredEvent event);
    
    /**
     * Returns whether this trigger requires an actor (user) to activate.
     */
    @Override
    public boolean requiresActor() {
        return isTriggeredByRoomUnit();
    }

}
