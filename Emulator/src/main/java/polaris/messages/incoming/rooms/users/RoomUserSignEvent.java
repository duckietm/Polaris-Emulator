package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionVoteCounter;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredUserActionType;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSignEvent;

public class RoomUserSignEvent extends MessageHandler {
    private static final int MIN_SIGN_ID = 0;
    private static final int MAX_SIGN_ID = 17;

    static boolean isValidSignId(int signId) {
        return signId >= MIN_SIGN_ID && signId <= MAX_SIGN_ID;
    }

    @Override
    public void handle() throws Exception {
        int signId = this.packet.readInt();

        if (!isValidSignId(signId)) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserSignEvent event = new UserSignEvent(this.client.getUser(), signId);
        if (!Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            this.client.getUser().getRoomUnit().setStatus(RoomUnitStatus.SIGN, event.sign + "");
            this.client.getUser().getUserInfo().getCurrentRoom().unIdle(this.client.getUser());
            WiredManager.triggerUserPerformsAction(
                    room, this.client.getUser().getRoomUnit(), WiredUserActionType.SIGN, event.sign);

            int userId = this.client.getUser().getUserInfo().getId();
            for (UserItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionVoteCounter.class)) {
                if (item instanceof InteractionVoteCounter) {
                    ((InteractionVoteCounter) item).vote(room, userId, signId);
                }
            }
        }
    }
}
