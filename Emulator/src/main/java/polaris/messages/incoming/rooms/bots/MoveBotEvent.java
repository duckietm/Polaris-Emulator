package polaris.messages.incoming.rooms.bots;

import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

/**
 * A rentable bot moved or turned like furni (the official client's infostand Move and Rotate, and shift-click): bot
 * id, tile x, tile y, direction (0 to 7). Allowed for whoever has rights in the room or owns the bot, and never in
 * play-test mode. The bot is set down on a walkable tile; one that roams walks on from there.
 */
public class MoveBotEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        Bot bot = room.getBot(Math.abs(this.packet.readInt()));
        int x = this.packet.readInt();
        int y = this.packet.readInt();
        int direction = this.packet.readInt();

        if (bot == null || bot.getRoomUnit() == null) return;
        if (RoomPlayTestSupport.isRestricted(this.client.getUser())) return;

        boolean owner = bot.getOwnerId() == this.client.getUser().getUserInfo().getId();
        if (!owner && !room.hasRights(this.client.getUser())) return;

        if (direction < 0 || direction > 7) return;

        RoomTile tile = room.getLayout().getTile((short) x, (short) y);
        if (tile == null || !tile.isWalkable()) return;

        RoomUnit unit = bot.getRoomUnit();
        unit.stopWalking();
        unit.setLocation(tile);
        unit.setPreviousLocation(tile);
        unit.setZ(tile.z);
        unit.setPreviousLocationZ(unit.getZ());
        unit.setRotation(RoomUserRotation.fromValue(direction));
        room.sendComposer(new RoomUserStatusComposer(unit).compose());
        bot.needsUpdate(true);
    }
}
