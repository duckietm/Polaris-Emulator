package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.users.DanceType;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredUserActionType;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserIdleEvent;

public class RoomUserDanceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        int danceId = this.packet.readInt();
        if (RoomUserInputGuard.isClubOnlyDance(danceId)
                && !this.client.getUser().getUserStats().hasActiveClub()) return;
        if (danceId >= 0 && danceId <= 4) {
            if (this.client.getUser().getRoomUnit().isInRoom()) {

                User user = this.client.getUser();

                if (this.client.getUser().getRoomUnit().getCacheable().get("control") != null) {
                    user = (User)
                            this.client.getUser().getRoomUnit().getCacheable().get("control");

                    if (user.getUserInfo().getCurrentRoom()
                            != this.client.getUser().getUserInfo().getCurrentRoom()) {
                        user.getRoomUnit().getCacheable().remove("controller");
                        this.client.getUser().getRoomUnit().getCacheable().remove("control");
                        user = this.client.getUser();
                    }
                }

                UserIdleEvent event = new UserIdleEvent(this.client.getUser(), UserIdleEvent.IdleReason.DANCE, false);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (!event.idle) {
                        this.client.getUser().getUserInfo().getCurrentRoom().unIdle(user);
                    }
                }

                boolean startsDancing = danceId > 0
                        && user == this.client.getUser()
                        && user.getRoomUnit().getDanceType() != DanceType.values()[danceId];
                this.client.getUser().getUserInfo().getCurrentRoom().dance(user, DanceType.values()[danceId]);
                if (startsDancing) {
                    polaris.hotel.quests.QuestProgressEvents.progress(
                            user, polaris.hotel.quests.QuestGoalType.DANCE, 1);
                }

                if (danceId > 0) {
                    WiredManager.triggerUserPerformsAction(
                            this.client.getUser().getUserInfo().getCurrentRoom(),
                            user.getRoomUnit(),
                            WiredUserActionType.DANCE,
                            danceId);
                }
            }
        }
    }
}
