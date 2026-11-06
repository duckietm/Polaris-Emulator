package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

public class StalkUser extends RCONMessage<StalkUser.StalkUserJSON> {
    public StalkUser() {
        super(StalkUserJSON.class);
    }

    @Override
    public void handle(Gson gson, StalkUserJSON json) {
        User user = Emulator.getGameEnvironment().getUserManager().getUser(json.user_id);

        if (user != null) {
            User target = Emulator.getGameEnvironment().getUserManager().getUser(json.follow_id);

            if (target == null) {
                this.message = Emulator.getTexts()
                        .getValue("commands.error.cmd_stalk.not_found")
                        .replace("%user%", json.user_id + "");
                this.status = STATUS_ERROR;
                return;
            }

            if (target.getUserInfo().getCurrentRoom() == null) {
                this.message = Emulator.getTexts()
                        .getValue("commands.error.cmd_stalk.not_room")
                        .replace("%user%", json.user_id + "");
                this.status = STATUS_ERROR;
                return;
            }

            if (target.getUserInfo().getUsername().equals(user.getUserInfo().getUsername())) {
                this.message = Emulator.getTexts()
                        .getValue("commands.generic.cmd_stalk.self")
                        .replace("%user%", json.user_id + "");
                this.status = STATUS_ERROR;
                return;
            }

            if (target.getUserInfo().getCurrentRoom() == user.getUserInfo().getCurrentRoom()) {
                this.message = Emulator.getTexts()
                        .getValue("commands.generic.cmd_stalk.same_room")
                        .replace("%user%", json.user_id + "");
                this.status = STATUS_ERROR;
                return;
            }

            if (this.status == 0) {
                user.getClient()
                        .sendResponse(new ForwardToRoomComposer(
                                target.getUserInfo().getCurrentRoom().getId()));
            }
        } else {
            this.status = USER_NOT_FOUND;
            this.message = "offline";
        }
    }

    static class StalkUserJSON {

        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid target")
        public int follow_id;
    }
}
