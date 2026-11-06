package polaris.messages.outgoing.catalog.catalogadmin.studio;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.Outgoing;

public final class CatalogStudioUndoComposer extends AbstractCatalogStudioOperationComposer {
    public CatalogStudioUndoComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            List<CatalogStudioChangedEntity> changedEntities) {
        super(operationId, success, code, message, revision, changedEntities);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioUndoComposer);
        return appendPayload();
    }
}
