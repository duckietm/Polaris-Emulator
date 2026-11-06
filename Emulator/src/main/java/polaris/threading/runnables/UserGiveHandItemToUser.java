package polaris.threading.runnables;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomHanditemBlockSupport;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;
import polaris.messages.outgoing.rooms.users.RoomUserReceivedHandItemComposer;

public class UserGiveHandItemToUser implements Runnable {
    private final User target;
    private final User from;

    public UserGiveHandItemToUser(User from, User target) {
        this.target = target;
        this.from = from;
    }

    @Override
    public void run() {
        if (this.from.getUserInfo().getCurrentRoom() == null
                || this.target.getUserInfo().getCurrentRoom() == null) return;

        if (this.from.getUserInfo().getCurrentRoom()
                != this.target.getUserInfo().getCurrentRoom()) return;

        Room room = this.from.getUserInfo().getCurrentRoom();

        if (RoomHanditemBlockSupport.isHanditemBlocked(room)) {
            return;
        }

        int itemId = this.from.getRoomUnit().getHandItem();

        if (itemId > 0) {
            this.from.getRoomUnit().setHandItem(0);
            this.from
                    .getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserHandItemComposer(this.from.getRoomUnit()).compose());
            this.target.getRoomUnit().lookAtPoint(this.from.getRoomUnit().getCurrentLocation());
            this.target.getRoomUnit().statusUpdate(true);
            this.target.getClient().sendResponse(new RoomUserReceivedHandItemComposer(this.from.getRoomUnit(), itemId));
            this.target.getRoomUnit().setHandItem(itemId);
            this.target
                    .getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserHandItemComposer(this.target.getRoomUnit()).compose());
        }
    }
}
