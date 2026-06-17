package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorSearchComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FurniEditorSearchEvent extends MessageHandler {

    private static final int PAGE_SIZE = 20;

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
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

        // Build WHERE clause
        StringBuilder whereClause = new StringBuilder("WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!query.isEmpty()) {
            // Try numeric match first (id or sprite_id)
            boolean isNumeric = false;
            try {
                int numericQuery = Integer.parseInt(query);
                isNumeric = true;
                String likeQuery = "%" + com.eu.habbo.util.SqlLikeEscaper.escape(query) + "%";
                whereClause.append(" AND (id = ? OR sprite_id = ? OR item_name LIKE ? OR public_name LIKE ?)");
                params.add(numericQuery);
                params.add(numericQuery);
                params.add(likeQuery);
                params.add(likeQuery);
            } catch (NumberFormatException e) {
                String likeQuery = "%" + com.eu.habbo.util.SqlLikeEscaper.escape(query) + "%";
                whereClause.append(" AND (item_name LIKE ? OR public_name LIKE ?)");
                params.add(likeQuery);
                params.add(likeQuery);
            }
        }

        if (type != null && !type.isEmpty()) {
            whereClause.append(" AND type = ?");
            params.add(type);
        }

        // Extend search with furnidata display-name matches (server-authoritative names in JSON).
        // Appends: OR (LOWER(item_name) IN (?,?,...) [AND type=?])
        // Both branches carry their own type filter, so type scoping is preserved.
        // Params: [existing LIKE params] [existing type?] [furniCns...] [type again?]
        if (!query.isEmpty()) {
            java.util.List<String> furniCns = Emulator.getGameEnvironment()
                    .getFurnitureTextProvider()
                    .findClassnamesByName(query);
            if (!furniCns.isEmpty()) {
                // Build: OR (LOWER(item_name) IN (?,?,...) [AND type = ?])
                StringBuilder orBranch = new StringBuilder(" OR (LOWER(item_name) IN (");
                for (int i = 0; i < furniCns.size(); i++) {
                    if (i > 0) orBranch.append(", ");
                    orBranch.append('?');
                }
                orBranch.append(')');
                if (type != null && !type.isEmpty()) {
                    orBranch.append(" AND type = ?");
                }
                orBranch.append(')');
                whereClause.append(orBranch);
                params.addAll(furniCns);
                if (type != null && !type.isEmpty()) {
                    params.add(type);
                }
            }
        }

        // Resolve a SAFE ORDER BY from the whitelisted sort field/direction
        // (column names are never taken from raw user input — injection-proof).
        String orderColumn;
        switch (sortField == null ? "" : sortField) {
            case "spriteId":        orderColumn = "sprite_id"; break;
            case "itemName":        orderColumn = "item_name"; break;
            case "publicName":      orderColumn = "public_name"; break;
            case "type":            orderColumn = "type"; break;
            case "interactionType": orderColumn = "interaction_type"; break;
            case "id":
            default:                orderColumn = "id"; break;
        }
        String orderDir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        // Count total
        int total = 0;
        String countSql = "SELECT COUNT(*) FROM items_base " + whereClause;
        String dataSql = "SELECT * FROM items_base " + whereClause
                + " ORDER BY " + orderColumn + " " + orderDir + ", id ASC LIMIT ? OFFSET ?";

        List<Map<String, Object>> items = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // Get total count
            try (PreparedStatement stmt = connection.prepareStatement(countSql)) {
                int idx = 1;
                for (Object param : params) {
                    if (param instanceof Integer) {
                        stmt.setInt(idx++, (Integer) param);
                    } else {
                        stmt.setString(idx++, (String) param);
                    }
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }
            }

            // Get items page
            try (PreparedStatement stmt = connection.prepareStatement(dataSql)) {
                int idx = 1;
                for (Object param : params) {
                    if (param instanceof Integer) {
                        stmt.setInt(idx++, (Integer) param);
                    } else {
                        stmt.setString(idx++, (String) param);
                    }
                }
                stmt.setInt(idx++, PAGE_SIZE);
                stmt.setInt(idx, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(FurniEditorHelper.readBaseItem(rs));
                    }
                }
            }
        }

        this.client.sendResponse(new FurniEditorSearchComposer(items, total, page));
    }
}
