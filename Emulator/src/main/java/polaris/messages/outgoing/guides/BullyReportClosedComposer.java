package polaris.messages.outgoing.guides;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BullyReportClosedComposer extends MessageComposer {
    /** GuideTicketResolution codes of the official client: action taken, forwarded, reporter abusive. */
    public static final int TOOK_ACTION = 0;

    public static final int CLOSED = 1;
    public static final int MISUSE = 2;

    public final int code;

    public BullyReportClosedComposer(int code) {
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BullyReportClosedComposer);
        this.response.appendInt(this.code);
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
