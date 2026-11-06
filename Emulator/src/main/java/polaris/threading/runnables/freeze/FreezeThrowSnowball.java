package polaris.threading.runnables.freeze;

import polaris.Emulator;
import polaris.hotel.games.freeze.FreezeGamePlayer;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeTile;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.threading.runnables.UserItemNewState;

public class FreezeThrowSnowball implements Runnable {
    public final User user;
    public final InteractionFreezeTile targetTile;
    public final Room room;
    public final int radius;

    public FreezeThrowSnowball(User user, UserItem targetTile, Room room) {
        this.user = user;
        this.targetTile = (InteractionFreezeTile) targetTile;
        this.room = room;
        this.radius = ((FreezeGamePlayer) user.getUserInfo().getGamePlayer()).getExplosionBoost();
    }

    @Override
    public void run() {
        ((FreezeGamePlayer) this.user.getUserInfo().getGamePlayer()).takeSnowball();
        this.targetTile.setExtradata((this.radius + 1) * 1000 + "");
        this.room.updateItem(this.targetTile);
        Emulator.getThreading().run(new FreezeHandleSnowballExplosion(this), 2000);
        Emulator.getThreading().run(new UserItemNewState(this.targetTile, this.room, "11000"), 2000);
        Emulator.getThreading().run(new UserItemNewState(this.targetTile, this.room, "0"), 3000);
    }
}
