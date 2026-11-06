package polaris.messages.outgoing.generic;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InterstitialComposer extends MessageComposer {
    private final boolean unknownBoolean;

    public InterstitialComposer(boolean unknownBoolean) {
        this.unknownBoolean = unknownBoolean;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InterstitialComposer);
        this.response.appendBoolean(this.unknownBoolean);
        return this.response;
    }

    public boolean isUnknownBoolean() {
        return unknownBoolean;
    }
}
