package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FloodCounterComposer extends MessageComposer {
    private final int time;

    public FloodCounterComposer(int time) {
        this.time = time;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FloodCounterComposer);
        this.response.appendInt(this.time);
        return this.response;
    }

    public int getTime() {
        return time;
    }
}
