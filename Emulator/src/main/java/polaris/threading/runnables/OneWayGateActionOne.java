package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class OneWayGateActionOne implements Runnable {
    private UserItem oneWayGate;
    private Room room;
    private GameClient client;

    public OneWayGateActionOne(GameClient client, Room room, UserItem item) {
        this.oneWayGate = item;
        this.room = room;
        this.client = client;
    }

    @Override
    public void run() {
        this.room.sendComposer(new RoomUserStatusComposer(this.client.getUser().getRoomUnit()).compose());

        RoomTile t = this.room
                .getLayout()
                .getTileInFront(
                        this.room.getLayout().getTile(this.oneWayGate.getX(), this.oneWayGate.getY()),
                        (this.oneWayGate.getRotation() + 4) % 8);

        if (this.client.getUser().getRoomUnit().animateWalk) {
            this.client.getUser().getRoomUnit().animateWalk = false;
        }

        if (t.isWalkable()) {
            if (this.room.tileWalkable(t)
                    && this.client.getUser().getRoomUnit().getX() == this.oneWayGate.getX()
                    && this.client.getUser().getRoomUnit().getY() == this.oneWayGate.getY()) {
                this.client.getUser().getRoomUnit().setGoalLocation(t);

                if (!this.oneWayGate.getExtradata().equals("0")) {
                    Emulator.getThreading().run(new UserItemNewState(this.oneWayGate, this.room, "0"), 1000);
                }
            }
            // else if (this.client.getUser().getRoomUnit().getX() == this.oneWayGate.getX() &&
            // this.client.getUser().getRoomUnit().getY() == this.oneWayGate.getY())
            // {

            // }
            else {
                if (!this.oneWayGate.getExtradata().equals("0")) {
                    Emulator.getThreading().run(new UserItemNewState(this.oneWayGate, this.room, "0"), 1000);
                }
            }
        }
    }
}
