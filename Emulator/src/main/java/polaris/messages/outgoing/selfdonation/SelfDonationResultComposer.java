package polaris.messages.outgoing.selfdonation;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 13 SelfDonationResult (2920). {@code SelfDonationTool.onSelfDonationResult}
 * maps 0 to {@code selfdonation.result.success}, 1 to
 * {@code selfdonation.result.not_allowed} and anything else to
 * {@code selfdonation.result.failed}.
 */
public class SelfDonationResultComposer extends MessageComposer {
    public static final int SUCCESS = 0;
    public static final int NOT_ALLOWED = 1;
    public static final int FAILED = 2;

    private final int resultCode;

    public SelfDonationResultComposer(int resultCode) {
        this.resultCode = resultCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SelfDonationResultComposer);
        this.response.appendInt(this.resultCode);
        return this.response;
    }
}
