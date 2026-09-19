package com.eu.habbo.habbohotel.items.editor;

import com.eu.habbo.util.SqlLikeEscaper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.sql.DataSource;

public final class FurniEditorRepository {

    private final DataSource dataSource;

    public FurniEditorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public OptionalInt findItemIdBySprite(int spriteId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM items_base WHERE sprite_id = ? LIMIT 1")) {
            statement.setInt(1, spriteId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? OptionalInt.of(set.getInt("id")) : OptionalInt.empty();
            }
        }
    }

    public Optional<String> findClassname(int itemId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT item_name FROM items_base WHERE id = ?")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? Optional.ofNullable(set.getString("item_name")) : Optional.empty();
            }
        }
    }

    public Detail findDetail(int itemId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            Map<String, Object> item = this.findFullItem(connection, itemId);
            if (item == null) {
                return new Detail(null, 0, List.of());
            }
            int usageCount = this.count(connection, "SELECT COUNT(*) FROM items WHERE item_id = ?", itemId);
            List<Map<String, Object>> catalogItems = new ArrayList<>();
            String sql = "SELECT ci.id AS ci_id, ci.catalog_name, ci.cost_credits, ci.cost_points, "
                    + "ci.points_type, ci.page_id AS ci_page_id, "
                    + "COALESCE(cp.caption, '') AS page_caption "
                    + "FROM catalog_items ci LEFT JOIN catalog_pages cp ON ci.page_id = cp.id "
                    + "WHERE " + catalogTokenSql("ci.item_ids");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, catalogTokenPattern(itemId));
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        catalogItems.add(readCatalogReference(set));
                    }
                }
            }
            return new Detail(item, usageCount, List.copyOf(catalogItems));
        }
    }

    public DeleteResult deleteItem(int itemId) throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            if (!exists(connection, itemId)) {
                return new DeleteResult(DeleteStatus.NOT_FOUND, 0);
            }
            int usage = this.count(connection, "SELECT COUNT(*) FROM items WHERE item_id = ?", itemId);
            if (usage > 0) {
                return new DeleteResult(DeleteStatus.IN_USE, usage);
            }
            int catalogReferences = this.count(
                    connection,
                    "SELECT COUNT(*) FROM catalog_items WHERE " + catalogTokenSql("item_ids"),
                    catalogTokenPattern(itemId));
            if (catalogReferences > 0) {
                return new DeleteResult(DeleteStatus.CATALOG_REFERENCED, catalogReferences);
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM items_base WHERE id = ?")) {
                statement.setInt(1, itemId);
                statement.executeUpdate();
            }
            return new DeleteResult(DeleteStatus.DELETED, 0);
        }
    }

    public SearchPage search(SearchRequest request) throws SQLException {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (!request.query().isEmpty()) {
            String like = "%" + SqlLikeEscaper.escape(request.query()) + "%";
            try {
                int number = Integer.parseInt(request.query());
                where.append(" AND (id = ? OR sprite_id = ? OR item_name LIKE ? OR public_name LIKE ?)");
                parameters.add(number);
                parameters.add(number);
                parameters.add(like);
                parameters.add(like);
            } catch (NumberFormatException exception) {
                where.append(" AND (item_name LIKE ? OR public_name LIKE ?)");
                parameters.add(like);
                parameters.add(like);
            }
        }
        if (!request.type().isEmpty()) {
            where.append(" AND type = ?");
            parameters.add(request.type());
        }
        if (!request.query().isEmpty() && !request.furnidataClassnames().isEmpty()) {
            where.append(" OR (LOWER(item_name) IN (");
            for (int index = 0; index < request.furnidataClassnames().size(); index++) {
                if (index > 0) {
                    where.append(", ");
                }
                where.append('?');
            }
            where.append(')');
            if (!request.type().isEmpty()) {
                where.append(" AND type = ?");
            }
            where.append(')');
            parameters.addAll(request.furnidataClassnames());
            if (!request.type().isEmpty()) {
                parameters.add(request.type());
            }
        }

        String orderColumn =
                switch (request.sortField()) {
                    case "spriteId" -> "sprite_id";
                    case "itemName" -> "item_name";
                    case "publicName" -> "public_name";
                    case "type" -> "type";
                    case "interactionType" -> "interaction_type";
                    default -> "id";
                };
        String orderDirection = "desc".equalsIgnoreCase(request.sortDirection()) ? "DESC" : "ASC";
        String countSql = "SELECT COUNT(*) FROM items_base " + where;
        String dataSql = "SELECT * FROM items_base "
                + where
                + " ORDER BY "
                + orderColumn
                + " "
                + orderDirection
                + ", id ASC LIMIT ? OFFSET ?";

        try (Connection connection = this.dataSource.getConnection()) {
            int total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bind(statement, parameters);
                try (ResultSet set = statement.executeQuery()) {
                    total = set.next() ? set.getInt(1) : 0;
                }
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(dataSql)) {
                int next = bind(statement, parameters);
                statement.setInt(next++, request.pageSize());
                statement.setInt(next, request.offset());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        items.add(readBaseItem(set));
                    }
                }
            }
            return new SearchPage(List.copyOf(items), total);
        }
    }

    public boolean updateItem(int itemId, String setClauses, List<Object> values) throws SQLException {
        String sql = "UPDATE items_base SET " + setClauses + " WHERE id = ?";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = bind(statement, values);
            statement.setInt(next, itemId);
            return statement.executeUpdate() > 0;
        }
    }

    public void updatePublicName(int itemId, String publicName) throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE items_base SET public_name = ? WHERE id = ?")) {
            statement.setString(1, publicName);
            statement.setInt(2, itemId);
            statement.executeUpdate();
        }
    }

    public void recordAudit(AuditEntry entry) throws SQLException {
        String sql = "INSERT INTO furnidata_edit_log "
                + "(user_id, classname, action, old_name, new_name, old_description, new_description, timestamp) "
                + "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, entry.userId());
            statement.setString(2, entry.classname());
            statement.setString(3, entry.action());
            statement.setString(4, value(entry.oldName()));
            statement.setString(5, value(entry.newName()));
            statement.setString(6, value(entry.oldDescription()));
            statement.setString(7, value(entry.newDescription()));
            statement.setInt(8, entry.timestamp());
            statement.executeUpdate();
        }
    }

    private Map<String, Object> findFullItem(Connection connection, int itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM items_base WHERE id = ?")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? readFullItem(set) : null;
            }
        }
    }

    private static boolean exists(Connection connection, int itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM items_base WHERE id = ?")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private int count(Connection connection, String sql, Object value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof Integer integer) {
                statement.setInt(1, integer);
            } else {
                statement.setString(1, String.valueOf(value));
            }
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    private static int bind(PreparedStatement statement, List<?> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            if (parameter instanceof Integer integer) {
                statement.setInt(index++, integer);
            } else if (parameter instanceof Double decimal) {
                statement.setDouble(index++, decimal);
            } else {
                statement.setString(index++, String.valueOf(parameter));
            }
        }
        return index;
    }

    private static Map<String, Object> readBaseItem(ResultSet set) throws SQLException {
        Map<String, Object> item = new HashMap<>();
        item.put("id", set.getInt("id"));
        item.put("sprite_id", set.getInt("sprite_id"));
        item.put("item_name", set.getString("item_name"));
        item.put("public_name", set.getString("public_name"));
        item.put("type", set.getString("type"));
        item.put("width", set.getInt("width"));
        item.put("length", set.getInt("length"));
        item.put("stack_height", set.getDouble("stack_height"));
        item.put("allow_stack", set.getString("allow_stack"));
        item.put("allow_walk", set.getString("allow_walk"));
        item.put("allow_sit", set.getString("allow_sit"));
        item.put("allow_lay", set.getString("allow_lay"));
        item.put("interaction_type", set.getString("interaction_type"));
        item.put("interaction_modes_count", set.getInt("interaction_modes_count"));
        return Collections.unmodifiableMap(item);
    }

    private static Map<String, Object> readFullItem(ResultSet set) throws SQLException {
        Map<String, Object> item = new HashMap<>(readBaseItem(set));
        item.put("allow_gift", set.getString("allow_gift"));
        item.put("allow_trade", set.getString("allow_trade"));
        item.put("allow_recycle", set.getString("allow_recycle"));
        item.put("allow_marketplace_sell", set.getString("allow_marketplace_sell"));
        item.put("allow_inventory_stack", set.getString("allow_inventory_stack"));
        item.put("vending_ids", set.getString("vending_ids"));
        item.put("customparams", set.getString("customparams"));
        item.put("effect_id_male", set.getInt("effect_id_male"));
        item.put("effect_id_female", set.getInt("effect_id_female"));
        item.put("clothing_on_walk", set.getString("clothing_on_walk"));
        item.put("multiheight", set.getString("multiheight"));
        try {
            item.put("description", set.getString("description"));
        } catch (SQLException exception) {
            item.put("description", "");
        }
        return Collections.unmodifiableMap(item);
    }

    private static Map<String, Object> readCatalogReference(ResultSet set) throws SQLException {
        Map<String, Object> reference = new HashMap<>();
        reference.put("id", set.getInt("ci_id"));
        reference.put("catalog_name", set.getString("catalog_name"));
        reference.put("cost_credits", set.getInt("cost_credits"));
        reference.put("cost_points", set.getInt("cost_points"));
        reference.put("points_type", set.getInt("points_type"));
        reference.put("page_id", set.getInt("ci_page_id"));
        reference.put("page_caption", set.getString("page_caption"));
        return Collections.unmodifiableMap(reference);
    }

    private static String catalogTokenSql(String column) {
        return "CONCAT(',', REPLACE(" + column + ", ' ', ''), ',') LIKE ?";
    }

    private static String catalogTokenPattern(int itemId) {
        return "%," + itemId + ",%";
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public record Detail(Map<String, Object> item, int usageCount, List<Map<String, Object>> catalogItems) {}

    public record DeleteResult(DeleteStatus status, int referenceCount) {}

    public enum DeleteStatus {
        DELETED,
        NOT_FOUND,
        IN_USE,
        CATALOG_REFERENCED
    }

    public record SearchRequest(
            String query,
            String type,
            String sortField,
            String sortDirection,
            int pageSize,
            int offset,
            List<String> furnidataClassnames) {}

    public record SearchPage(List<Map<String, Object>> items, int total) {}

    public record AuditEntry(
            int userId,
            String classname,
            String action,
            String oldName,
            String newName,
            String oldDescription,
            String newDescription,
            int timestamp) {}
}
