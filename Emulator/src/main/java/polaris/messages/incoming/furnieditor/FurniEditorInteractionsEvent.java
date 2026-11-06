package polaris.messages.incoming.furnieditor;

import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.furnieditor.FurniEditorInteractionsComposer;
import polaris.messages.outgoing.furnieditor.FurniEditorResultComposer;

public class FurniEditorInteractionsEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        // The types the item manager has a class for, not the distinct values in
        // items_base: the editor offers what will actually work, and a furni whose
        // stored type is absent from this list is one that behaves as default.
        this.client.sendResponse(
                new FurniEditorInteractionsComposer(FurniEditorUpdateEvent.registeredInteractionTypes()));
    }
}
