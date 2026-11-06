package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BullyReportedMessageComposer extends MessageComposer {
    public static final int RECEIVED = 0;
    public static final int IGNORED = 1;
    public static final int NO_CHAT = 2;
    public static final int ALREADY_REPORTED = 3;
    public static final int NO_MISSUSE = 4;

    private final int code;

    public BullyReportedMessageComposer(int code) {
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BullyReportedMessageComposer);
        this.response.appendInt(this.code);
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
