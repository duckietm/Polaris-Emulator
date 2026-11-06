package polaris.messages.outgoing.generic;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ElementPointerComposer extends MessageComposer {
    private final String key;

    public ElementPointerComposer(String key) {
        this.key = key;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ElementPointerComposer);
        this.response.appendString(this.key);
        return this.response;
    }

    public String getKey() {
        return key;
    }
}
