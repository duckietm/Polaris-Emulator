package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserInfo;

public class ChangeRoomOwner extends RCONMessage<ChangeRoomOwner.JSON> {
    public ChangeRoomOwner() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        if (json.room_id <= 0 || json.user_id <= 0) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "invalid room or user";
            return;
        }

        UserInfo owner = Emulator.getGameEnvironment().getUserManager().getUserInfo(json.user_id);
        if (owner == null) {
            this.status = RCONMessage.USER_NOT_FOUND;
            this.message = "user not found";
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(json.room_id);

        if (room == null) {
            this.status = RCONMessage.ROOM_NOT_FOUND;
            this.message = "room not found";
            return;
        }

        room.setOwnerId(owner.getId());
        room.setOwnerName(owner.getUsername());
        room.setNeedsUpdate(true);
        room.save();
        Emulator.getGameEnvironment().getRoomManager().unloadRoom(room);
        this.message = "updated room owner";
    }

    static class JSON {

        @Positive(message = "invalid room")
        public int room_id;

        @Positive(message = "invalid user")
        public int user_id;

        public String username;
    }
}
