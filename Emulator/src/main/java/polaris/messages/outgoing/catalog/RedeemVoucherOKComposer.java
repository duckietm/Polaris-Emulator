package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RedeemVoucherOKComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RedeemVoucherOKComposer);
        this.response.appendString("");
        this.response.appendString("");
        return this.response;
    }
}
