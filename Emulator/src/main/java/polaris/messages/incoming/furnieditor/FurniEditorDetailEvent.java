package polaris.messages.incoming.furnieditor;

import polaris.Emulator;
import polaris.hotel.items.editor.FurniEditorRepository;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.furnieditor.FurniEditorDetailComposer;
import polaris.messages.outgoing.furnieditor.FurniEditorResultComposer;

public class FurniEditorDetailEvent extends MessageHandler {

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

        sendDetailResponse(this.client, id);
    }

    /**
     * Shared method to build and send a detail response for a given item ID.
     * Used by both FurniEditorDetailEvent and FurniEditorBySpriteEvent.
     */
    public static void sendDetailResponse(polaris.hotel.gameclients.GameClient client, int itemId) throws Exception {
        var detail = new FurniEditorRepository(Emulator.getDatabase().getDataSource()).findDetail(itemId);
        var item = detail.item();
        String furniDataJson = "{}";
        String furniDataDiagnosticJson = "{}";

        if (item == null) {
            client.sendResponse(new FurniEditorResultComposer(false, "Item not found: " + itemId));
            return;
        }

        // Try to read furnidata.json entry
        try {
            Object classname = item.get("item_name");
            FurniDataManager.LookupResult lookup =
                    FurniDataManager.getItemLookup(itemId, classname != null ? classname.toString() : null);
            furniDataJson = lookup.itemJson();
            furniDataDiagnosticJson = lookup.diagnosticJson();
        } catch (Exception e) {
            furniDataJson = "{}";
            furniDataDiagnosticJson = "{}";
        }

        client.sendResponse(new FurniEditorDetailComposer(
                item, detail.usageCount(), detail.catalogItems(), furniDataJson, furniDataDiagnosticJson));
    }
}
