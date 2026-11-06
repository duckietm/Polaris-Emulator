package polaris.messages.outgoing.guides;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideSessionErrorComposer extends MessageComposer {
    public static final int SOMETHING_WRONG_REQUEST = 0;
    public static final int NO_HELPERS_AVAILABLE = 1;
    public static final int NO_GUARDIANS_AVAILABLE = 2;

    private final int errorCode;

    public GuideSessionErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideSessionErrorComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
