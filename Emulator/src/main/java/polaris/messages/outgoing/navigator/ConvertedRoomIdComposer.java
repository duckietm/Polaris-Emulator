package polaris.messages.outgoing.navigator;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ConvertedRoomIdComposer extends MessageComposer {
    private final String unknownString1;
    private final int unknownInt1;

    public ConvertedRoomIdComposer(String unknownString1, int unknownInt1) {
        this.unknownString1 = unknownString1;
        this.unknownInt1 = unknownInt1;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ConvertedRoomIdComposer);
        this.response.appendString(this.unknownString1);
        this.response.appendInt(this.unknownInt1);
        return this.response;
    }

    public String getUnknownString1() {
        return unknownString1;
    }

    public int getUnknownInt1() {
        return unknownInt1;
    }
}
