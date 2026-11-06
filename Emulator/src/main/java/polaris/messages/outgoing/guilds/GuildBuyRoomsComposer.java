package polaris.messages.outgoing.guilds;

import java.util.Collection;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildBuyRoomsComposer extends MessageComposer {
    private final Collection<Room> rooms;

    public GuildBuyRoomsComposer(Collection<Room> rooms) {
        this.rooms = rooms;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildBuyRoomsComposer);
        this.response.appendInt(Emulator.getConfig().getInt("catalog.guild.price"));
        this.response.appendInt(this.rooms.size());

        for (Room room : this.rooms) {
            this.response.appendInt(room.getId());
            this.response.appendString(room.getName());
            this.response.appendBoolean(false);
        }

        this.response.appendInt(5);

        this.response.appendInt(10);
        this.response.appendInt(3);
        this.response.appendInt(4);

        this.response.appendInt(25);
        this.response.appendInt(17);
        this.response.appendInt(5);

        this.response.appendInt(25);
        this.response.appendInt(17);
        this.response.appendInt(3);

        this.response.appendInt(29);
        this.response.appendInt(11);
        this.response.appendInt(4);

        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        return this.response;
    }

    public Collection<Room> getRooms() {
        return rooms;
    }
}
