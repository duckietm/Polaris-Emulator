package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The room linker. It is bought in pairs like a teleporter and one is placed in each room; the
 * teleport-to-room wired effect leads users through a picked linker to the room its pair stands in,
 * where they arrive on the pair. Being a teleporter is what gives it the pair at purchase, the pair
 * lookup and the arrival tile; everything that makes a teleporter carry a user by itself is switched
 * off, so the linker does nothing when clicked and can simply be stood on.
 */
public class InteractionWiredRoomLinker extends InteractionTeleport {

    public InteractionWiredRoomLinker(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredRoomLinker(
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

    /** Only the wired effect moves users through a linker. */
    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {}

    @Override
    public boolean isUsable() {
        return false;
    }

    @Override
    public boolean invalidatesToRoomKick() {
        return false;
    }
}
