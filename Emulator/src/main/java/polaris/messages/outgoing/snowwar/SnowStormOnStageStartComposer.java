package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormOnStageStartComposer extends MessageComposer {

    private final int preparingSeconds;

    public SnowStormOnStageStartComposer(int preparingSeconds) {
        this.preparingSeconds = preparingSeconds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormOnStageStartComposer);
        this.response.appendInt(this.preparingSeconds);
        return this.response;
    }
}
