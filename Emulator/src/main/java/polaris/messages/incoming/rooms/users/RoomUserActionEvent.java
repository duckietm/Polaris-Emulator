package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUserAction;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredUserActionType;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserActionComposer;
import polaris.plugin.events.users.UserIdleEvent;

public class RoomUserActionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room != null) {
            User user = this.client.getUser();

            if (this.client.getUser().getRoomUnit().getCacheable().get("control") != null) {
                user = (User) this.client.getUser().getRoomUnit().getCacheable().get("control");

                if (user.getUserInfo().getCurrentRoom() != room) {
                    user.getRoomUnit().getCacheable().remove("controller");
                    this.client.getUser().getRoomUnit().getCacheable().remove("control");
                    user = this.client.getUser();
                }
            }

            int action = this.packet.readInt();
            if (!RoomUserInputGuard.isValidAction(action)) return;
            if (RoomUserInputGuard.isClubOnlyAction(action)
                    && !this.client.getUser().getUserStats().hasActiveClub()) return;

            int wiredAction = WiredUserActionType.NONE;

            if (action == 5) {
                UserIdleEvent event = new UserIdleEvent(this.client.getUser(), UserIdleEvent.IdleReason.ACTION, true);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (event.idle) {
                        room.idle(user);
                        wiredAction = WiredUserActionType.RELAX;
                    } else {
                        room.unIdle(user);
                        wiredAction = WiredUserActionType.AWAKE;
                    }
                }
            } else {
                UserIdleEvent event = new UserIdleEvent(this.client.getUser(), UserIdleEvent.IdleReason.ACTION, false);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (!event.idle) {
                        room.unIdle(user);
                    }
                }
            }

            room.sendComposer(
                    new RoomUserActionComposer(user.getRoomUnit(), RoomUserAction.fromValue(action)).compose());
            if (action == 1 && user == this.client.getUser()) {
                polaris.hotel.quests.QuestProgressEvents.progress(user, polaris.hotel.quests.QuestGoalType.WAVE, 1);
            }

            if (wiredAction == WiredUserActionType.NONE) {
                switch (action) {
                    case 1:
                        wiredAction = WiredUserActionType.WAVE;
                        break;
                    case 2:
                        wiredAction = WiredUserActionType.BLOW_KISS;
                        break;
                    case 3:
                        wiredAction = WiredUserActionType.LAUGH;
                        break;
                    case 7:
                        wiredAction = WiredUserActionType.THUMB_UP;
                        break;
                    default:
                        break;
                }
            }

            if (wiredAction != WiredUserActionType.NONE) {
                WiredManager.triggerUserPerformsAction(room, user.getRoomUnit(), wiredAction, -1);
            }
        }
    }
}
