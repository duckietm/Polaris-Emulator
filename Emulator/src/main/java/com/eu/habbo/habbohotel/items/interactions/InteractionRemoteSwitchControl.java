package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Base of the room configuration switches ({@code conf_*}): off is state 0, on is state 1.
 *
 * <p>A click flips between the two states whatever {@code interaction_modes_count} says, so a
 * controller whose catalog row was imported with a modes count of 1 still turns on. The plain
 * {@link InteractionDefault} cycle would compute {@code (0 + 1) % 1 == 0} for such a row and the
 * switch could never be switched on.
 */
public class InteractionRemoteSwitchControl extends InteractionDefault {
    public static final String STATE_OFF = "0";
    public static final String STATE_ON = "1";

    public InteractionRemoteSwitchControl(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRemoteSwitchControl(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    /** Whether this switch is on (state 1). */
    public boolean isSwitchedOn() {
        return STATE_ON.equals(this.getExtradata());
    }

    /** The state a toggle moves this switch to. */
    public static String nextSwitchState(String extradata) {
        return STATE_ON.equals(extradata) ? STATE_OFF : STATE_ON;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        boolean stateClick = objects != null && objects.length > 0 && objects[0] instanceof Integer;

        if (room == null || !stateClick) {
            super.onClick(client, room, objects);
            return;
        }

        boolean wiredToggle = objects.length >= 2 && objects[1] instanceof WiredEffectType;

        if (client != null && !wiredToggle && !this.canToggle(client.getHabbo(), room)) {
            return;
        }

        this.setExtradata(nextSwitchState(this.getExtradata()));
        this.needsUpdate(true);
        room.updateItemState(this);

        if (client != null && !wiredToggle) {
            // The state is already set; this only fires the stack's "furni state changed" triggers.
            super.onClick(client, room, new Object[] {"TOGGLE_OVERRIDE"});
        }
    }
}
