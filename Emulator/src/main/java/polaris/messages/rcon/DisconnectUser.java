package polaris.messages.rcon;

import com.google.gson.Gson;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.outgoing.handshake.DisconnectReasonComposer;

public class DisconnectUser extends RCONMessage<DisconnectUser.DisconnectUserJSON> {

    public DisconnectUser() {
        super(DisconnectUserJSON.class);
    }

    @Override
    public void handle(Gson gson, DisconnectUserJSON json) {
        User target;

        if (json.user_id > 0) {
            target = Emulator.getGameEnvironment().getUserManager().getUser(json.user_id);
        } else if (json.username != null && !json.username.isBlank()) {
            target = Emulator.getGameEnvironment().getUserManager().getUser(json.username);
        } else {
            this.status = RCONMessage.USER_NOT_FOUND;
            return;
        }

        if (target == null) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = Emulator.getTexts().getValue("commands.error.cmd_disconnect.user_offline");
            return;
        }

        Emulator.getGameServer()
                .getGameClientManager()
                .disconnectWithReason(target.getClient(), DisconnectReasonComposer.LOGOUT);
        this.message = Emulator.getTexts()
                .getValue("commands.succes.cmd_disconnect.disconnected")
                .replace("%user%", target.getUserInfo().getUsername());
    }

    static class DisconnectUserJSON {

        public int user_id = -1;

        public String username;
    }
}
