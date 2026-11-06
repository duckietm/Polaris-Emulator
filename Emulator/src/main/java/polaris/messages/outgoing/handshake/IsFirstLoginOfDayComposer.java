package polaris.messages.outgoing.handshake;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class IsFirstLoginOfDayComposer extends MessageComposer {
    private final boolean isFirstLoginOfDay;

    public IsFirstLoginOfDayComposer(boolean isFirstLoginOfDay) {
        this.isFirstLoginOfDay = isFirstLoginOfDay;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.IsFirstLoginOfDayComposer);
        this.response.appendBoolean(this.isFirstLoginOfDay);
        return this.response;
    }
}
