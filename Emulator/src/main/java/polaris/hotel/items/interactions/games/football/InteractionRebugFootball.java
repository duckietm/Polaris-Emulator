package polaris.hotel.items.interactions.games.football;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.messages.outgoing.rooms.items.ItemStateComposer;
import polaris.threading.runnables.RebugKickBallAction;
import polaris.util.pathfinding.Direction8;

public class InteractionRebugFootball extends InteractionDefault {

    private RebugKickBallAction currentThread;
    private Direction8 lastDribbleDirection;

    public InteractionRebugFootball(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionRebugFootball(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
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
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Direction8 userDir = Direction8.getDirection(roomUnit.getBodyRotation().getValue());
        this.lastDribbleDirection = userDir;

        RoomTile goal = roomUnit.getGoal();
        if (goal != null && goal.x == this.getX() && goal.y == this.getY()) {
            this.kick(room, roomUnit, 55);
        } else {
            this.kick(room, roomUnit, 0);
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        if (objects != null
                && objects.length >= 2
                && objects[1] instanceof RoomTile
                && objects[0] instanceof RoomTile) {
            RoomTile fromTile = (RoomTile) objects[0];
            RoomTile nextTile = (RoomTile) objects[1];

            int dx = nextTile.x - fromTile.x;
            int dy = nextTile.y - fromTile.y;
            Direction8 walkDir = Direction8.fromDelta(dx, dy);

            if (this.lastDribbleDirection != null && walkDir.getRot() == this.lastDribbleDirection.getRot()) {
                this.kick(room, roomUnit, 55);
                return;
            }
        }

        if (this.currentThread != null) {
            this.currentThread.dead = true;
            this.currentThread = null;
        }
        this.setExtradata("0");
        room.sendComposer(new ItemStateComposer(this).compose());
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client == null) return;
        RoomUnit unit = client.getUser().getRoomUnit();
        if (RoomLayout.tilesAdjecent(unit.getCurrentLocation(), room.getLayout().getTile(this.getX(), this.getY()))) {
            this.kick(room, unit, 55);
        }
    }

    private void kick(Room room, RoomUnit kicker, int momentum) {
        boolean wasMoving = this.currentThread != null && !this.currentThread.dead && !this.currentThread.isDribble();

        if (this.currentThread != null) {
            this.currentThread.dead = true;
        }

        Direction8 direction = Direction8.getDirection(kicker.getBodyRotation().getValue());
        boolean zigzag = wasMoving && momentum > 0;

        this.currentThread = new RebugKickBallAction(this, room, direction, momentum, zigzag);
        Emulator.getThreading().run(this.currentThread, 50);
    }
}
