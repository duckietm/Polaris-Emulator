package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BuildersClubFurniCountComposer extends MessageComposer {
    private final int furniCount;

    public BuildersClubFurniCountComposer(int furniCount) {
        this.furniCount = furniCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogModeComposer);
        this.response.appendInt(this.furniCount);
        return this.response;
    }
}
