package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wired condition that validates all movement effects can complete successfully.
 * <p>
 * When this condition is present in a wired stack, all movement effects are first
 * simulated to verify they can complete. If ANY movement would fail (e.g., destination
 * has a user, bot, furniture, or is a hole), this condition returns FALSE and the 
 * stack does not execute.
 * </p>
 * <p>
 * <b>Key Feature:</b> The simulation tracks cumulative position changes. If Effect 1
 * moves an item from tile 0 to tile 1, and Effect 2 moves it forward again, the
 * simulation correctly validates the move from tile 1 to tile 2 (not from tile 0).
 * </p>
 * <p>
 * Use cases:
 * <ul>
 *   <li>Moving furniture in formation - if one piece can't move, none should</li>
 *   <li>Puzzles where partial movement would break the puzzle state</li>
 *   <li>Chain movements where items depend on each other's positions</li>
 * </ul>
 * </p>
 * 
 * @see WiredSimulation
 * @see IWiredEffect#simulate
 */
public class WiredConditionMovementValidation extends InteractionWiredCondition {
    
    public static final WiredConditionType type = WiredConditionType.MOVEMENT_VALIDATION;

    public WiredConditionMovementValidation(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionMovementValidation(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        WiredStack stack = ctx.stack();
        if (stack == null) {
            return true;
        }
        
        WiredSimulation simulation = new WiredSimulation(ctx.room());
        
        for (IWiredEffect effect : stack.effects()) {
            if (effect.requiresActor() && !ctx.hasActor()) {
                continue;
            }
            
            try {
                boolean success = effect.simulate(ctx, simulation);
                if (!success || simulation.hasFailed()) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public String getWiredData() {
        return "";
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
    }

    @Override
    public void onPickUp() {
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
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
    public boolean saveData(WiredSettings settings) {
        return true;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }
}
