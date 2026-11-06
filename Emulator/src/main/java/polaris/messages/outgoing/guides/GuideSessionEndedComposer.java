package polaris.messages.outgoing.guides;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideSessionEndedComposer extends MessageComposer {
    public static final int SOMETHING_WRONG = 0;
    public static final int HELP_CASE_CLOSED = 1;

    private final int errorCode;

    public GuideSessionEndedComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideSessionEndedComposer);
        this.response.appendInt(this.errorCode); // ?
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
