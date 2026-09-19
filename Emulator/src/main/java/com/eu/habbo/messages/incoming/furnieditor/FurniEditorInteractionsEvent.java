package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorInteractionsComposer;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;

public class FurniEditorInteractionsEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
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
