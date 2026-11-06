package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormStartLobbyCounterComposer extends MessageComposer {

    private final int secondsUntilStart;

    public SnowStormStartLobbyCounterComposer(int secondsUntilStart) {
        this.secondsUntilStart = secondsUntilStart;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormStartLobbyCounterComposer);
        this.response.appendInt(this.secondsUntilStart);
        return this.response;
    }
}
