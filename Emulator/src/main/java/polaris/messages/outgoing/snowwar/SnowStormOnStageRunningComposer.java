package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormOnStageRunningComposer extends MessageComposer {

    private final int totalSecondsLeft;

    public SnowStormOnStageRunningComposer(int totalSecondsLeft) {
        this.totalSecondsLeft = totalSecondsLeft;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormOnStageRunningComposer);
        this.response.appendInt(this.totalSecondsLeft);
        return this.response;
    }
}
