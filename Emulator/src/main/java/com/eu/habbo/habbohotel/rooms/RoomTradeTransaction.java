package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

final class RoomTradeTransaction {
    private static final String TRANSFER_ITEM = "UPDATE items SET user_id = ? WHERE id = ? AND user_id = ? LIMIT 1";
    private static final String REDEEM_ITEM = "DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1";

    private RoomTradeTransaction() {
    }

    static CommitResult execute(
            Habbo userOne,
            Habbo userTwo,
            Collection<HabboItem> userOneItems,
            Collection<HabboItem> userTwoItems,
            int creditsForUserOne,
            int creditsForUserTwo,
            boolean logTrades) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                String operationId = EconomyOperationId.create("room-trade");
                int tradeId = logTrades
                        ? insertTradeLog(connection, userOne, userTwo, userOneItems.size(), userTwoItems.size())
                        : 0;

                persistItems(connection, tradeId, userOne.getHabboInfo().getId(), userTwo.getHabboInfo().getId(),
                        userOneItems, logTrades);
                persistItems(connection, tradeId, userTwo.getHabboInfo().getId(), userOne.getHabboInfo().getId(),
                        userTwoItems, logTrades);
                Integer userOneBalance = creditUser(connection,
                        operationId, userOne.getHabboInfo().getId(), creditsForUserOne,
                        userTwo.getHabboInfo().getId(), tradeId);
                Integer userTwoBalance = creditUser(connection,
                        operationId, userTwo.getHabboInfo().getId(), creditsForUserTwo,
                        userOne.getHabboInfo().getId(), tradeId);

                connection.commit();
                return new CommitResult(userOneBalance, userTwoBalance);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static int insertTradeLog(Connection connection, Habbo userOne, Habbo userTwo,
                                      int userOneItemCount, int userTwoItemCount) throws SQLException {
        String sql = "INSERT INTO room_trade_log (user_one_id, user_two_id, user_one_ip, user_two_ip, timestamp, user_one_item_count, user_two_item_count) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, userOne.getHabboInfo().getId());
            statement.setInt(2, userTwo.getHabboInfo().getId());
            statement.setString(3, userOne.getHabboInfo().getIpLogin());
            statement.setString(4, userTwo.getHabboInfo().getIpLogin());
            statement.setInt(5, Emulator.getIntUnixTimestamp());
            statement.setInt(6, userOneItemCount);
            statement.setInt(7, userTwoItemCount);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Unable to create trade log");
    }

    private static void persistItems(Connection connection, int tradeId, int sourceUserId, int targetUserId,
                                     Collection<HabboItem> items, boolean logTrades) throws SQLException {
        for (HabboItem item : items) {
            if (logTrades) insertTradeItemLog(connection, tradeId, item.getId(), sourceUserId);

            int redeemCredits = RoomTrade.getCreditsByItem(item);
            String sql = redeemCredits > 0 ? REDEEM_ITEM : TRANSFER_ITEM;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (redeemCredits > 0) {
                    statement.setInt(1, item.getId());
                    statement.setInt(2, sourceUserId);
                } else {
                    statement.setInt(1, targetUserId);
                    statement.setInt(2, item.getId());
                    statement.setInt(3, sourceUserId);
                }
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Trade item ownership changed before commit: " + item.getId());
                }
            }

        }
    }

    private static void insertTradeItemLog(Connection connection, int tradeId, int itemId, int sourceUserId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO room_trade_log_items (id, item_id, user_id) VALUES (?, ?, ?)")) {
            statement.setInt(1, tradeId);
            statement.setInt(2, itemId);
            statement.setInt(3, sourceUserId);
            statement.executeUpdate();
        }
    }

    private static Integer creditUser(
            Connection connection,
            String operationId,
            int userId,
            int credits,
            int actorId,
            int tradeId) throws SQLException {
        if (credits <= 0) return null;
        EconomyMutationResult mutation = EconomyLedger.apply(connection, new EconomyOperation(
                operationId + ":recipient:" + userId,
                userId,
                actorId,
                "trade_credit_conversion",
                "room.trade.credit_furni",
                EconomyLedger.CREDITS,
                credits,
                null,
                "tradeId=" + tradeId));
        return mutation.balanceAfter();
    }

    record CommitResult(Integer userOneCreditBalance, Integer userTwoCreditBalance) {
    }
}
