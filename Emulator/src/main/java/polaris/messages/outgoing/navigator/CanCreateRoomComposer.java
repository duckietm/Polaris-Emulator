package polaris.messages.outgoing.navigator;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CanCreateRoomComposer extends MessageComposer {
    private final int count;
    private final int max;

    public CanCreateRoomComposer(int count, int max) {
        this.count = count;
        this.max = max;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CanCreateRoomComposer);

        this.response.appendInt(this.count >= this.max ? 1 : 0);
        this.response.appendInt(this.max);

        return this.response;
    }

    public int getCount() {
        return count;
    }

    public int getMax() {
        return max;
    }
}
