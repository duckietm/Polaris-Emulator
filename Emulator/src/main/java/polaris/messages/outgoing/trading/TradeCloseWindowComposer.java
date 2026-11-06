package polaris.messages.outgoing.trading;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TradeCloseWindowComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TradeCloseWindowComposer);
        return this.response;
    }
}
