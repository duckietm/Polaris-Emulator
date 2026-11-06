package polaris.messages.incoming.furnieditor;

import polaris.Emulator;
import polaris.hotel.items.editor.FurniEditorRepository;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.furnieditor.FurniEditorResultComposer;

public class FurniEditorDeleteEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        int id = this.packet.readInt();

        if (id <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid item ID"));
            return;
        }

        var result = new FurniEditorRepository(Emulator.getDatabase().getDataSource()).deleteItem(id);
        switch (result.status()) {
            case NOT_FOUND -> {
                this.client.sendResponse(new FurniEditorResultComposer(false, "Item not found: " + id));
                return;
            }
            case IN_USE -> {
                this.client.sendResponse(new FurniEditorResultComposer(
                        false, "Cannot delete: " + result.referenceCount() + " instances exist in the game"));
                return;
            }
            case CATALOG_REFERENCED -> {
                this.client.sendResponse(new FurniEditorResultComposer(
                        false, "Cannot delete: item is referenced by " + result.referenceCount() + " catalog entries"));
                return;
            }
            case DELETED -> {
                // Continue with the established cache reload and response.
            }
        }

        // Reload emulator item definitions
        Emulator.getGameEnvironment().getItemManager().loadItems();

        this.client.sendResponse(new FurniEditorResultComposer(true, "Item deleted"));
    }
}
