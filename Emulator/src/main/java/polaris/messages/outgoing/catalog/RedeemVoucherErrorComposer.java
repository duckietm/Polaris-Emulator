package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RedeemVoucherErrorComposer extends MessageComposer {
    public static final int INVALID_CODE = 0;
    public static final int TECHNICAL_ERROR = 1;

    private final int code;

    public RedeemVoucherErrorComposer(int code) {
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RedeemVoucherErrorComposer);
        this.response.appendString(this.code + "");
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
