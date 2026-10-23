package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * What opening a recycler box came to. The box is opened in one transaction: its prize row is claimed, the reward is
 * inserted and the box is deleted together, so a failure leaves the box as it was and one box never pays twice.
 */
public record RecycleBoxOpening(Result result, HabboItem reward) {
    public enum Result {
        /** The reward is in the opener's inventory and the box is gone. */
        OPENED,
        /** The box held no prize: it is gone and nothing came out. */
        EMPTY,
        /** Nothing changed: the box is still there. */
        FAILED
    }

    public static final RecycleBoxOpening EMPTY = new RecycleBoxOpening(Result.EMPTY, null);
    public static final RecycleBoxOpening FAILED = new RecycleBoxOpening(Result.FAILED, null);

    private static final String SELECT_PRIZE =
            "SELECT base_item_reward FROM items_presents WHERE item_id = ? LIMIT 1 FOR UPDATE";
    private static final String DELETE_PRIZE = "DELETE FROM items_presents WHERE item_id = ? LIMIT 1";
    private static final String INSERT_REWARD = "INSERT INTO items (user_id, item_id) VALUES (?, ?)";
    private static final String SELECT_REWARD = "SELECT * FROM items WHERE id = ? LIMIT 1";
    private static final String DELETE_BOX = "DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1";

    /** Opens {@code box} for {@code userId} on {@code connection}, committing only a complete opening. */
    static RecycleBoxOpening open(Connection connection, ItemManager items, int userId, HabboItem box)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            RecycleBoxOpening opening = openIn(connection, items, userId, box);
            if (opening.result() == Result.FAILED) {
                connection.rollback();
            } else {
                connection.commit();
            }
            return opening;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static RecycleBoxOpening openIn(Connection connection, ItemManager items, int userId, HabboItem box)
            throws SQLException {
        int prize = prizeOf(connection, box.getId());
        if (prize <= 0) {
            return deleteBox(connection, userId, box) ? EMPTY : FAILED;
        }

        try (PreparedStatement delete = connection.prepareStatement(DELETE_PRIZE)) {
            delete.setInt(1, box.getId());
            if (delete.executeUpdate() != 1) return FAILED;
        }

        HabboItem reward = insertReward(connection, items, userId, prize);
        if (reward == null) return FAILED;

        return deleteBox(connection, userId, box) ? new RecycleBoxOpening(Result.OPENED, reward) : FAILED;
    }

    /** The base item the box pays out, or 0 when it holds none. */
    private static int prizeOf(Connection connection, int boxId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SELECT_PRIZE)) {
            select.setInt(1, boxId);
            try (ResultSet set = select.executeQuery()) {
                return set.next() ? set.getInt("base_item_reward") : 0;
            }
        }
    }

    private static HabboItem insertReward(Connection connection, ItemManager items, int userId, int baseItemId)
            throws SQLException {
        int rewardId;
        try (PreparedStatement insert = connection.prepareStatement(INSERT_REWARD, Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, userId);
            insert.setInt(2, baseItemId);
            insert.execute();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) return null;
                rewardId = keys.getInt(1);
            }
        }

        try (PreparedStatement select = connection.prepareStatement(SELECT_REWARD)) {
            select.setInt(1, rewardId);
            try (ResultSet set = select.executeQuery()) {
                return set.next() ? items.loadHabboItem(set) : null;
            }
        }
    }

    private static boolean deleteBox(Connection connection, int userId, HabboItem box) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(DELETE_BOX)) {
            delete.setInt(1, box.getId());
            delete.setInt(2, userId);
            return delete.executeUpdate() == 1;
        }
    }
}
