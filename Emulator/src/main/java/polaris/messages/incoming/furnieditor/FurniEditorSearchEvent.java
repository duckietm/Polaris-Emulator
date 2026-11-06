package polaris.messages.incoming.furnieditor;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.items.editor.FurniEditorRepository;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.furnieditor.FurniEditorResultComposer;
import polaris.messages.outgoing.furnieditor.FurniEditorSearchComposer;

public class FurniEditorSearchEvent extends MessageHandler {

    private static final int PAGE_SIZE = 20;

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        String query = this.packet.readString();
        String type = this.packet.readString();
        int page = this.packet.readInt();
        String sortField = this.packet.readString();
        String sortDir = this.packet.readString();

        // Input validation
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }

        if (page < 1) page = 1;

        int offset = (page - 1) * PAGE_SIZE;
        List<String> furnidataClassnames = query.isEmpty()
                ? List.of()
                : Emulator.getGameEnvironment().getFurnitureTextProvider().findClassnamesByName(query);
        var result = new FurniEditorRepository(Emulator.getDatabase().getDataSource())
                .search(new FurniEditorRepository.SearchRequest(
                        query,
                        type == null ? "" : type,
                        sortField == null ? "" : sortField,
                        sortDir == null ? "" : sortDir,
                        PAGE_SIZE,
                        offset,
                        furnidataClassnames));
        this.client.sendResponse(new FurniEditorSearchComposer(result.items(), result.total(), page));
    }
}
