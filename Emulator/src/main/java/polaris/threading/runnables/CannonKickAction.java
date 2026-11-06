package polaris.threading.runnables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.InteractionCannon;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;

public class CannonKickAction implements Runnable {
    private final InteractionCannon cannon;
    private final Room room;
    private final GameClient client;

    public CannonKickAction(InteractionCannon cannon, Room room, GameClient client) {
        this.cannon = cannon;
        this.room = room;
        this.client = client;
    }

    @Override
    public void run() {
        if (this.client != null) {
            this.client.getUser().getRoomUnit().setCanWalk(true);
        }
        Map<String, String> dater = new HashMap<>();
        dater.put("title", "${notification.room.kick.cannonball.title}");
        dater.put("message", "${notification.room.kick.cannonball.message}");

        int rotation = this.cannon.getRotation();
        List<RoomTile> tiles = this.room
                .getLayout()
                .getTilesInFront(
                        this.room.getLayout().getTile(this.cannon.getX(), this.cannon.getY()), rotation + 6, 3);

        ServerMessage message = new BubbleAlertComposer("cannon.png", dater).compose();

        for (RoomTile t : tiles) {
            for (User user : this.room.getUsersAt(t.x, t.y)) {
                if (!user.hasPermission(Permission.ACC_UNKICKABLE) && !this.room.isOwner(user)) {
                    Emulator.getGameEnvironment().getRoomManager().leaveRoom(user, this.room);
                    user.getClient().sendResponse(message); // kicked composer
                }
            }
        }
    }
}
