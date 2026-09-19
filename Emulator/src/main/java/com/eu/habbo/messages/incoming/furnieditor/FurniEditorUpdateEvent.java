package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FurniEditorUpdateEvent extends MessageHandler {

    // The one place this package reaches the item manager: the registered
    // interaction types are the guard for an update and the list the editor offers.
    private static ItemManager itemManager() {
        return Emulator.getGameEnvironment().getItemManager();
    }

    /** Interaction types with a registered class, sorted, as the item manager reports them. */
    static List<String> registeredInteractionTypes() {
        return itemManager().getInteractionList();
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        int id = this.packet.readInt();
        String jsonFieldsStr = this.packet.readString();

        if (id <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid item ID"));
            return;
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(jsonFieldsStr).getAsJsonObject();
        } catch (Exception e) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid JSON data"));
            return;
        }

        Set<String> registeredInteractions = new HashSet<>();
        for (String name : registeredInteractionTypes()) {
            registeredInteractions.add(name.toLowerCase(Locale.ROOT));
        }

        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(json, registeredInteractions::contains);
        if (!payload.valid()) {
            this.client.sendResponse(new FurniEditorResultComposer(false, payload.error));
            return;
        }

        if (!new FurniEditorRepository(Emulator.getDatabase().getDataSource())
                .updateItem(id, payload.setClauses, payload.values)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Item not found: " + id));
            return;
        }

        // Reload emulator item definitions
        itemManager().loadItems();

        this.client.sendResponse(new FurniEditorResultComposer(true, "Item updated", id));
    }
}
