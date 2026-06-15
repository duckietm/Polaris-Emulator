package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class FurniEditorUpdateEvent extends MessageHandler {

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

        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(json);
        if (!payload.valid()) {
            this.client.sendResponse(new FurniEditorResultComposer(false, payload.error));
            return;
        }

        String sql = "UPDATE items_base SET " + payload.setClauses + " WHERE id = ?";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            int idx = 1;
            for (Object value : payload.values) {
                if (value instanceof Integer) {
                    stmt.setInt(idx++, (Integer) value);
                } else if (value instanceof Double) {
                    stmt.setDouble(idx++, (Double) value);
                } else {
                    stmt.setString(idx++, String.valueOf(value));
                }
            }
            stmt.setInt(idx, id);
            if (stmt.executeUpdate() == 0) {
                this.client.sendResponse(new FurniEditorResultComposer(false, "Item not found: " + id));
                return;
            }
        }

        // Reload emulator item definitions
        Emulator.getGameEnvironment().getItemManager().loadItems();

        this.client.sendResponse(new FurniEditorResultComposer(true, "Item updated", id));
    }
}
