package polaris.messages.outgoing.generic;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

public class testcomposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(3019);
        this.response.appendInt(3);

        this.response.appendInt(1);
        this.response.appendInt(2);
        this.response.appendString("Key");

        this.response.appendInt(1);
        this.response.appendInt(2);
        this.response.appendString("Key");

        this.response.appendInt(1);
        this.response.appendInt(2);
        this.response.appendString("Key");
        this.response.appendBoolean(true);
        this.response.appendInt(1);
        return this.response;
    }
}
