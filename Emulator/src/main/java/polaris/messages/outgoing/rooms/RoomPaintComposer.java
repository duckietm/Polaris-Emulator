package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomPaintComposer extends MessageComposer {
    private final String type;
    private final String value;

    public RoomPaintComposer(String type, String value) {
        this.type = type;
        this.value = value;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomPaintComposer);
        this.response.appendString(this.type);
        this.response.appendString(this.value);
        return this.response;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
