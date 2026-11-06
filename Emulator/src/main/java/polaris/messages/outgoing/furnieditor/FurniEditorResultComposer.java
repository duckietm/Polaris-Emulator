package polaris.messages.outgoing.furnieditor;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FurniEditorResultComposer extends MessageComposer {
    private final boolean success;
    private final String message;
    private final int id;

    public FurniEditorResultComposer(boolean success, String message) {
        this(success, message, -1);
    }

    public FurniEditorResultComposer(boolean success, String message, int id) {
        this.success = success;
        this.message = message;
        this.id = id;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniEditorResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.message);
        this.response.appendInt(this.id);
        return this.response;
    }
}
