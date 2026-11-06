package polaris.messages.outgoing.modtool;

import java.util.ArrayList;
import polaris.hotel.modtool.ModToolChatLog;
import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolRoomChatlogComposer extends MessageComposer {
    private final Room room;
    private final ArrayList<ModToolChatLog> chatlog;

    public ModToolRoomChatlogComposer(Room room, ArrayList<ModToolChatLog> chatlog) {
        this.room = room;
        this.chatlog = chatlog;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolRoomChatlogComposer);
        this.response.appendByte(1);
        this.response.appendShort(2);
        this.response.appendString("roomName");
        this.response.appendByte(2);
        this.response.appendString(this.room.getName());
        this.response.appendString("roomId");
        this.response.appendByte(1);
        this.response.appendInt(this.room.getId());

        this.response.appendShort(this.chatlog.size());
        for (ModToolChatLog line : this.chatlog) {
            this.response.appendString(ModToolChatlogTimestamp.format(line.timestamp));
            this.response.appendInt(line.userId);
            this.response.appendString(line.username);
            this.response.appendString(line.message);
            this.response.appendBoolean(false);
        }
        return this.response;
    }

    public Room getRoom() {
        return room;
    }

    public ArrayList<ModToolChatLog> getChatlog() {
        return chatlog;
    }
}
