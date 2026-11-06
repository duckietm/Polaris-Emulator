package polaris.threading.runnables.freeze;

import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserEffectComposer;

public class FreezeClearEffects implements Runnable {
    private final User user;

    public FreezeClearEffects(User user) {
        this.user = user;
    }

    @Override
    public void run() {
        this.user.getRoomUnit().setEffectId(0, 0);
        this.user.getRoomUnit().setCanWalk(true);
        if (this.user.getUserInfo().getCurrentRoom() != null) {
            this.user
                    .getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserEffectComposer(this.user.getRoomUnit()).compose());
        }
    }
}
