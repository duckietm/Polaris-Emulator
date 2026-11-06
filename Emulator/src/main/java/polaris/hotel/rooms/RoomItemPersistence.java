package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import polaris.hotel.items.interactions.InteractionGuildGate;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.wired.chest.ChestRelease;
import polaris.hotel.users.UserItem;

final class RoomItemPersistence {

    private static final String DELETE_ITEM_SQL = "DELETE FROM items WHERE id = ?";
    private static final String UPDATE_ITEM_SQL = "UPDATE items SET user_id = ?, room_id = ?, wall_pos = ?, "
            + "x = ?, y = ?, z = ?, rot = ?, extra_data = ?, "
            + "limited_data = ?, wired_data = CASE WHEN ? = 1 THEN ? ELSE wired_data END WHERE id = ?";

    private final RoomDependencies.ConnectionProvider database;

    RoomItemPersistence(RoomDependencies.ConnectionProvider database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    void save(List<UserItem> items) throws SQLException {
        if (items.isEmpty()) {
            return;
        }

        // Temporary furni (wired "place furni") have no rows.
        List<UserItem> deletedItems = items.stream()
                .filter(item -> !item.isTemporary() && item.needsDelete())
                .toList();
        List<UserItem> updatedItems = items.stream()
                .filter(item -> !item.isTemporary() && !item.needsDelete() && item.needsUpdate())
                .toList();

        try (Connection connection = this.database.openConnection()) {
            this.deleteItems(connection, deletedItems);
            this.updateItems(connection, updatedItems);
        }
    }

    private void deleteItems(Connection connection, List<UserItem> items) throws SQLException {
        if (items.isEmpty()) {
            return;
        }

        // Wired chests give what they hold back to their owner before their rows go.
        ChestRelease.releaseAll(items);

        try (PreparedStatement statement = connection.prepareStatement(DELETE_ITEM_SQL)) {
            for (UserItem item : items) {
                statement.setInt(1, item.getId());
                statement.addBatch();
            }

            statement.executeBatch();
        }

        for (UserItem item : items) {
            item.needsUpdate(false);
            item.needsDelete(false);
        }
    }

    private void updateItems(Connection connection, List<UserItem> items) throws SQLException {
        if (items.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_ITEM_SQL)) {
            for (UserItem item : items) {
                statement.setInt(1, item.getDatabaseUserId());
                statement.setInt(2, item.getRoomId());
                statement.setString(3, item.getWallPosition());
                statement.setInt(4, item.getX());
                statement.setInt(5, item.getY());
                statement.setDouble(6, persistedHeight(item.getZ()));
                statement.setInt(7, item.getRotation());
                statement.setString(8, item instanceof InteractionGuildGate ? "" : item.getDatabaseExtraData());
                statement.setString(9, item.getLimitedStack() + ":" + item.getLimitedSells());
                statement.setInt(10, item instanceof InteractionWired ? 1 : 0);
                statement.setString(11, wiredData(item));
                statement.setInt(12, item.getId());
                statement.addBatch();
            }

            statement.executeBatch();
        }

        for (UserItem item : items) {
            item.needsUpdate(false);
        }
    }

    private static double persistedHeight(double height) {
        double bounded = Math.max(-9999, Math.min(9999, height));
        return Math.round(bounded * Math.pow(10, 6)) / Math.pow(10, 6);
    }

    private static String wiredData(UserItem item) {
        if (!(item instanceof InteractionWired wired) || item.getRoomId() == 0) {
            return "";
        }

        String data = wired.getWiredData();
        return data == null ? "" : data;
    }
}
