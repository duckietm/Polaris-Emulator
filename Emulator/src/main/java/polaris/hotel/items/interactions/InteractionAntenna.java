package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;

/**
 * A wired antenna. Someone with rights who uses it sends its signal: the Receive Signal stacks on this antenna run
 * with that user as the triggering user. The signal carries no furni and no other users. The antenna still changes
 * its state as any switchable furni does.
 */
public class InteractionAntenna extends InteractionDefault {
    public InteractionAntenna(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionAntenna(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (sendsSignal(client, room, objects)) {
            WiredEvent event = this.signal(room, client.getUser().getRoomUnit());
            if (event != null) WiredManager.handleEvent(event);
        }
    }

    /** Only a use by a user with rights: a wired Toggle State or a click without rights sends nothing. */
    static boolean sendsSignal(GameClient client, Room room, Object[] objects) {
        if (client == null || room == null || client.getUser() == null) return false;
        if (objects != null && objects.length >= 2 && objects[1] instanceof WiredEffectType) return false;
        return client.getUser().getRoomUnit() != null && room.hasRights(client.getUser());
    }

    WiredEvent signal(Room room, RoomUnit actor) {
        if (room.getLayout() == null) return null;
        RoomTile tile = room.getLayout().getTile(this.getX(), this.getY());
        if (tile == null) return null;

        return WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .tile(tile)
                .signalChannel(this.getId())
                .signalUserCount(0)
                .signalFurniCount(0)
                .sourceItem(this)
                .signalPath(Set.of(this.getId()))
                .actor(actor)
                .originActor(actor)
                .build();
    }
}
