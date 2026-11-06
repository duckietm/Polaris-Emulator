package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

public class ForwardUser extends RCONMessage<ForwardUser.ForwardUserJSON> {

    public ForwardUser() {
        super(ForwardUserJSON.class);
    }

    @Override
    public void handle(Gson gson, ForwardUserJSON object) {
        User user = Emulator.getGameEnvironment().getUserManager().getUser(object.user_id);

        if (user != null) {
            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(object.room_id);

            if (room != null) {
                if (user.getUserInfo().getCurrentRoom() != null) {
                    Emulator.getGameEnvironment()
                            .getRoomManager()
                            .leaveRoom(user, user.getUserInfo().getCurrentRoom());
                }

                user.getClient().sendResponse(new ForwardToRoomComposer(object.room_id));
                Emulator.getGameEnvironment().getRoomManager().enterRoom(user, object.room_id, "", true);
                return;
            } else {
                this.status = RCONMessage.ROOM_NOT_FOUND;
                return;
            }
        }

        this.status = RCONMessage.USER_NOT_FOUND;
    }

    static class ForwardUserJSON {

        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid room")
        public int room_id;
    }
}
