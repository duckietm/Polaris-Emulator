package polaris.messages.outgoing.rooms;

import java.util.HashSet;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomBan;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomBannedUsersComposer extends MessageComposer {
    private final Room room;

    public RoomBannedUsersComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        int timeStamp = Emulator.getIntUnixTimestamp();

        Set<RoomBan> roomBans = new HashSet<>();

        for (RoomBan ban : this.room.getBannedUsers().values()) {
            if (ban.endTimestamp > timeStamp) roomBans.add(ban);
        }

        if (roomBans.isEmpty()) return null;

        this.response.init(Outgoing.RoomBannedUsersComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendInt(roomBans.size());

        for (RoomBan ban : roomBans) {
            this.response.appendInt(ban.userId);
            this.response.appendString(ban.username);
        }

        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
