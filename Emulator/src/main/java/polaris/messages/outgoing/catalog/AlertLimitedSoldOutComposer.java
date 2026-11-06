package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AlertLimitedSoldOutComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AlertLimitedSoldOutComposer);
        return this.response;
    }
}
