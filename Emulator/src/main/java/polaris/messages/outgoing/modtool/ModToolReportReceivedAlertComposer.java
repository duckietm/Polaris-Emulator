package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolReportReceivedAlertComposer extends MessageComposer {
    public static final int REPORT_RECEIVED = 0;
    public static final int REPORT_WINDOW = 1;
    public static final int REPORT_ABUSIVE = 2;

    private final int errorCode;
    private final String message;

    public ModToolReportReceivedAlertComposer(int errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolReportReceivedAlertComposer);
        this.response.appendInt(this.errorCode);
        this.response.appendString(this.message);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
