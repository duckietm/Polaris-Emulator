package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorDetailComposer;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FurniEditorDetailEvent extends MessageHandler {

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

        sendDetailResponse(this.client, id);
    }

    /**
     * Shared method to build and send a detail response for a given item ID.
     * Used by both FurniEditorDetailEvent and FurniEditorBySpriteEvent.
     */
    public static void sendDetailResponse(com.eu.habbo.habbohotel.gameclients.GameClient client, int itemId) throws Exception {
        Map<String, Object> item = null;
        int usageCount = 0;
        List<Map<String, Object>> catalogItems = new ArrayList<>();
        String furniDataJson = "{}";
        String furniDataDiagnosticJson = "{}";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // Load full item data
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM items_base WHERE id = ?")) {
                stmt.setInt(1, itemId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        item = FurniEditorHelper.readFullItem(rs);
                    }
                }
            }

            if (item == null) {
                client.sendResponse(new FurniEditorResultComposer(false, "Item not found: " + itemId));
                return;
            }

            // Count placed instances
            try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM items WHERE item_id = ?")) {
                stmt.setInt(1, itemId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        usageCount = rs.getInt(1);
                    }
                }
            }

            // Load catalog references (join catalog_items with catalog_pages)
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT ci.id AS ci_id, ci.catalog_name, ci.cost_credits, ci.cost_points, ci.points_type, " +
                    "ci.page_id AS ci_page_id, COALESCE(cp.caption, '') AS page_caption " +
                    "FROM catalog_items ci " +
                    "LEFT JOIN catalog_pages cp ON ci.page_id = cp.id " +
                    "WHERE ci.item_ids LIKE ?")) {
                stmt.setString(1, "%" + itemId + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        catalogItems.add(FurniEditorHelper.readCatalogRef(rs));
                    }
                }
            }
        }

        // Try to read furnidata.json entry
        try {
            Object classname = item.get("item_name");
            FurniDataManager.LookupResult lookup = FurniDataManager.getItemLookup(itemId, classname != null ? classname.toString() : null);
            furniDataJson = lookup.itemJson();
            furniDataDiagnosticJson = lookup.diagnosticJson();
        } catch (Exception e) {
            furniDataJson = "{}";
            furniDataDiagnosticJson = "{}";
        }

        client.sendResponse(new FurniEditorDetailComposer(item, usageCount, catalogItems, furniDataJson, furniDataDiagnosticJson));
    }
}
