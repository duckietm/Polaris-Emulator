package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CatalogUpdatedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogUpdatedComposer);
        this.response.appendBoolean(false);
        return this.response;
    }
}
