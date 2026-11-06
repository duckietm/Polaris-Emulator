package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CatalogModeComposer extends MessageComposer {
    private final int mode;

    public CatalogModeComposer(int mode) {
        this.mode = mode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogModeComposer);
        this.response.appendInt(this.mode);
        return this.response;
    }

    public int getMode() {
        return mode;
    }
}
