package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FurniEditorDeleteEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        int id = this.packet.readInt();

        if (id <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid item ID"));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // Check if item exists
            try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM items_base WHERE id = ?")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        this.client.sendResponse(new FurniEditorResultComposer(false, "Item not found: " + id));
                        return;
                    }
                }
            }

            // Check usage count - items placed in rooms
            int usageCount = 0;
            try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM items WHERE item_id = ?")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        usageCount = rs.getInt(1);
                    }
                }
            }

            if (usageCount > 0) {
                this.client.sendResponse(new FurniEditorResultComposer(false,
                    "Cannot delete: " + usageCount + " instances exist in the game"));
                return;
            }

            // Check catalog_items references
            int catalogCount = 0;
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM catalog_items WHERE " + FurniEditorHelper.catalogItemIdsTokenSql("item_ids"))) {
                stmt.setString(1, FurniEditorHelper.catalogItemIdsTokenPattern(id));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        catalogCount = rs.getInt(1);
                    }
                }
            }

            if (catalogCount > 0) {
                this.client.sendResponse(new FurniEditorResultComposer(false,
                    "Cannot delete: item is referenced by " + catalogCount + " catalog entries"));
                return;
            }

            // Safe to delete
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM items_base WHERE id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
        }

        // Reload emulator item definitions
        Emulator.getGameEnvironment().getItemManager().loadItems();

        this.client.sendResponse(new FurniEditorResultComposer(true, "Item deleted"));
    }
}
