package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolIssueHandledComposer extends MessageComposer {
    public static final int HANDLED = 0;
    public static final int USELESS = 1;
    public static final int ABUSIVE = 2;

    private final int code;
    private final String message;

    public ModToolIssueHandledComposer(int code) {
        this.code = code;
        this.message = "";
    }

    public ModToolIssueHandledComposer(String message) {
        this.code = 0;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolIssueHandledComposer);
        this.response.appendInt(this.code);
        this.response.appendString(this.message);
        return this.response;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
