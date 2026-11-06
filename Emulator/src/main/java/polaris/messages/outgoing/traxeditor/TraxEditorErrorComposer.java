package polaris.messages.outgoing.traxeditor;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TraxEditorErrorComposer extends MessageComposer {
    private final int errorCode;

    public TraxEditorErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TraxEditorErrorComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }
}
