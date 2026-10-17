package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import java.util.Set;

/**
 * A Give Variable or Change Variable Value that wrote a variable also flashes that variable's definition box and the
 * variable add-ons on its tile (Time Utilities, Level-up and the like), as in Habbo. Behaviour observed in
 * plus-emulator/PlusEMU 259b9fa9, reimplemented.
 */
public final class WiredVariableDefinitionFlash {
    private WiredVariableDefinitionFlash() {}

    /** Flashes the definition box {@code definitionItemId} and the add-on boxes on its tile. */
    public static void flash(Room room, int definitionItemId) {
        if (room == null || definitionItemId <= 0 || room.getRoomSpecialTypes() == null) return;

        InteractionWiredExtra definition = room.getRoomSpecialTypes().getExtra(definitionItemId);
        if (definition == null) return;

        definition.flashBox(room);
        Set<InteractionWiredExtra> onTile = room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (onTile == null) return;
        for (InteractionWiredExtra extra : onTile) {
            if (extra != null && extra != definition) extra.flashBox(room);
        }
    }
}
