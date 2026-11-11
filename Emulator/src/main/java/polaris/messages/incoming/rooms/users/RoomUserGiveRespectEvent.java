package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.rooms.TalentShowRespect;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.CustomNotificationComposer;
import polaris.plugin.events.users.UserRespectedEvent;

public class RoomUserGiveRespectEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (userId == client.getUser().getUserInfo().getId()) {
            return;
        }

        if (this.client.getUser().getUserStats().respectPointsToGive > 0) {
            if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

            User target = this.client.getUser().getUserInfo().getCurrentRoom().getUser(userId);
            if (target == null) return;

            // A talent show room gives respect only to a user on the stage, before an audience.
            TalentShowRespect.Verdict verdict =
                    TalentShowRespect.check(this.client.getUser().getUserInfo().getCurrentRoom(), target);
            if (verdict != TalentShowRespect.Verdict.ALLOWED) {
                this.client.sendResponse(new CustomNotificationComposer(verdict.notification()));
                return;
            }

            if (Emulator.getPluginManager().isRegistered(UserRespectedEvent.class, false)) {
                if (Emulator.getPluginManager()
                        .fireEvent(new UserRespectedEvent(target, this.client.getUser()))
                        .isCancelled()) return;
            }

            this.client.getUser().respect(target);
        }
    }
}
