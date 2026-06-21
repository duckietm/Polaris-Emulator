package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.trading.*;
import com.eu.habbo.plugin.events.trading.TradeConfirmEvent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RoomTrade {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTrade.class);
    //Configuration. Loaded from database & updated accordingly.
    public static boolean TRADING_ENABLED = true;
    public static boolean TRADING_REQUIRES_PERK = true;
    public static final int MAX_OFFERED_ITEMS = 100;

    private final List<RoomTradeUser> users;
    private final Room room;
    private boolean completed = false;

    public RoomTrade(Habbo userOne, Habbo userTwo, Room room) {
        this.users = new ArrayList<>();

        this.users.add(new RoomTradeUser(userOne));
        this.users.add(new RoomTradeUser(userTwo));
        this.room = room;
    }

    public void start() {
        this.initializeTradeStatus();
        this.openTrade();
    }

    protected void initializeTradeStatus() {
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.getHabbo().getRoomUnit().hasStatus(RoomUnitStatus.TRADING)) {
                roomTradeUser.getHabbo().getRoomUnit().setStatus(RoomUnitStatus.TRADING, "");
                if (!roomTradeUser.getHabbo().getRoomUnit().isWalking())
                    this.room.sendComposer(new RoomUserStatusComposer(roomTradeUser.getHabbo().getRoomUnit()).compose());
            }
        }
    }

    protected void openTrade() {
        this.sendMessageToUsers(new TradeStartComposer(this));
    }

    public synchronized void offerItem(Habbo habbo, HabboItem item) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        if (user == null || item == null || user.getItems().contains(item) || user.getItems().size() >= MAX_OFFERED_ITEMS)
            return;

        habbo.getInventory().getItemsComponent().removeHabboItem(item);
        user.getItems().add(item);

        this.clearAccepted();
        this.updateWindow();
    }

    public synchronized void offerMultipleItems(Habbo habbo, THashSet<HabboItem> items) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        if (user == null || items == null)
            return;

        for (HabboItem item : items) {
            if (user.getItems().size() >= MAX_OFFERED_ITEMS)
                break;

            if (!user.getItems().contains(item)) {
                habbo.getInventory().getItemsComponent().removeHabboItem(item);
                user.getItems().add(item);
            }
        }

        this.clearAccepted();
        this.updateWindow();
    }

    public synchronized void removeItem(Habbo habbo, HabboItem item) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        if (user == null || item == null || !user.getItems().contains(item))
            return;

        habbo.getInventory().getItemsComponent().addItem(item);
        user.getItems().remove(item);

        this.clearAccepted();
        this.updateWindow();
    }

    public synchronized void accept(Habbo habbo, boolean value) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        if (user == null)
            return;

        user.setAccepted(value);

        this.sendMessageToUsers(new TradeAcceptedComposer(user));
        boolean accepted = true;
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.getAccepted())
                accepted = false;
        }
        if (accepted) {
            this.sendMessageToUsers(new TradingWaitingConfirmComposer());
        }
    }

    public synchronized void confirm(Habbo habbo) {
        // Re-entry guard: both participants confirm on their own EventLoop
        // threads. Without this (and the method-level lock) two concurrent
        // confirms could each observe "all confirmed" and run tradeItems()
        // twice → item/credit duplication.
        if (this.completed) return;

        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        if (user == null)
            return;

        user.confirm();

        this.sendMessageToUsers(new TradeAcceptedComposer(user));
        boolean accepted = true;
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.getConfirmed())
                accepted = false;
        }
        if (accepted) {
            this.completed = true;

            if (this.tradeItems()) {
                this.closeWindow();
                this.sendMessageToUsers(new TradeCompleteComposer());
            } else {
                this.returnItems();
                for (RoomTradeUser roomTradeUser : this.users) {
                    roomTradeUser.clearItems();
                }
                this.closeWindow();
            }

            this.room.stopTrade(this);
        }
    }

    boolean tradeItems() {
        for (RoomTradeUser roomTradeUser : this.users) {
            for (HabboItem item : roomTradeUser.getItems()) {
                if (roomTradeUser.getHabbo().getInventory().getItemsComponent().getHabboItem(item.getId()) != null) {
                    this.sendMessageToUsers(new TradeClosedComposer(roomTradeUser.getHabbo().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
                    return false;
                }
            }
        }

        RoomTradeUser userOne = this.users.get(0);
        RoomTradeUser userTwo = this.users.get(1);

        boolean tradeConfirmEventRegistered = Emulator.getPluginManager().isRegistered(TradeConfirmEvent.class, true);
        TradeConfirmEvent tradeConfirmEvent = new TradeConfirmEvent(userOne, userTwo);
        if (tradeConfirmEventRegistered) {
            Emulator.getPluginManager().fireEvent(tradeConfirmEvent);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {

            int tradeId = 0;

            boolean logTrades = Emulator.getConfig().getBoolean("hotel.log.trades");
            if (logTrades) {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO room_trade_log (user_one_id, user_two_id, user_one_ip, user_two_ip, timestamp, user_one_item_count, user_two_item_count) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, userOne.getHabbo().getHabboInfo().getId());
                    statement.setInt(2, userTwo.getHabbo().getHabboInfo().getId());
                    statement.setString(3, userOne.getHabbo().getHabboInfo().getIpLogin());
                    statement.setString(4, userTwo.getHabbo().getHabboInfo().getIpLogin());
                    statement.setInt(5, Emulator.getIntUnixTimestamp());
                    statement.setInt(6, userOne.getItems().size());
                    statement.setInt(7, userTwo.getItems().size());
                    statement.executeUpdate();
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            tradeId = generatedKeys.getInt(1);
                        }
                    }
                }
            }

            int userOneId = userOne.getHabbo().getHabboInfo().getId();
            int userTwoId = userTwo.getHabbo().getHabboInfo().getId();

            try (PreparedStatement statement = connection.prepareStatement("UPDATE items SET user_id = ? WHERE id = ? AND user_id = ? LIMIT 1")) {
                try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO room_trade_log_items (id, item_id, user_id) VALUES (?, ?, ?)")) {
                    for (HabboItem item : userOne.getItems()) {
                        statement.setInt(1, userTwoId);
                        statement.setInt(2, item.getId());
                        statement.setInt(3, userOneId);
                        statement.addBatch();

                        if (logTrades) {
                            stmt.setInt(1, tradeId);
                            stmt.setInt(2, item.getId());
                            stmt.setInt(3, userOneId);
                            stmt.addBatch();
                        }
                    }

                    for (HabboItem item : userTwo.getItems()) {
                        statement.setInt(1, userOneId);
                        statement.setInt(2, item.getId());
                        statement.setInt(3, userTwoId);
                        statement.addBatch();

                        if (logTrades) {
                            stmt.setInt(1, tradeId);
                            stmt.setInt(2, item.getId());
                            stmt.setInt(3, userTwoId);
                            stmt.addBatch();
                        }
                    }

                    if (logTrades) {
                        stmt.executeBatch();
                    }
                }

                int expectedUpdates = userOne.getItems().size() + userTwo.getItems().size();
                int[] updateCounts = statement.executeBatch();
                if (!RoomTrade.allOwnershipUpdatesSucceeded(updateCounts, expectedUpdates)) {
                    this.sendMessageToUsers(new TradeClosedComposer(userOne.getHabbo().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
                    return false;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.sendMessageToUsers(new TradeClosedComposer(userOne.getHabbo().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
            return false;
        }

        for (HabboItem item : userOne.getItems()) {
            item.setUserId(userTwo.getHabbo().getHabboInfo().getId());
        }

        for (HabboItem item : userTwo.getItems()) {
            item.setUserId(userOne.getHabbo().getHabboInfo().getId());
        }

        THashSet<HabboItem> itemsUserOne = new THashSet<>(userOne.getItems());
        THashSet<HabboItem> itemsUserTwo = new THashSet<>(userTwo.getItems());

        userOne.clearItems();
        userTwo.clearItems();

        int creditsForUserTwo = 0;
        THashSet<HabboItem> creditFurniUserOne = new THashSet<>();
        for (HabboItem item : itemsUserOne) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditsForUserTwo += worth;
                creditFurniUserOne.add(item);
                new QueryDeleteHabboItem(item).run();
            }
        }
        itemsUserOne.removeAll(creditFurniUserOne);

        int creditsForUserOne = 0;
        THashSet<HabboItem> creditFurniUserTwo = new THashSet<>();
        for (HabboItem item : itemsUserTwo) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditsForUserOne += worth;
                creditFurniUserTwo.add(item);
                new QueryDeleteHabboItem(item).run();
            }
        }
        itemsUserTwo.removeAll(creditFurniUserTwo);

        userOne.getHabbo().giveCredits(creditsForUserOne);
        userTwo.getHabbo().giveCredits(creditsForUserTwo);

        userOne.getHabbo().getInventory().getItemsComponent().addItems(itemsUserTwo);
        userTwo.getHabbo().getInventory().getItemsComponent().addItems(itemsUserOne);

        userOne.getHabbo().getClient().sendResponse(new AddHabboItemComposer(itemsUserTwo));
        userTwo.getHabbo().getClient().sendResponse(new AddHabboItemComposer(itemsUserOne));

        userOne.getHabbo().getClient().sendResponse(new InventoryRefreshComposer());
        userTwo.getHabbo().getClient().sendResponse(new InventoryRefreshComposer());
        return true;
    }

    protected void clearAccepted() {
        for (RoomTradeUser user : this.users) {
            user.setAccepted(false);
            // Any change to the offered items invalidates a prior confirmation;
            // without this a stale confirmed=true lets a user strip their side
            // and still complete the trade once the partner re-confirms.
            user.setConfirmed(false);
        }
    }

    protected void updateWindow() {
        this.sendMessageToUsers(new TradeUpdateComposer(this));
    }

    private void returnItems() {
        for (RoomTradeUser user : this.users) {
            user.putItemsIntoInventory();
        }
    }

    private void closeWindow() {
        this.removeStatusses();
        this.sendMessageToUsers(new TradeCloseWindowComposer());
    }

    public void stopTrade(Habbo habbo) {
        this.removeStatusses();
        this.clearAccepted();
        this.returnItems();
        for (RoomTradeUser user : this.users) {
            user.clearItems();
        }
        this.updateWindow();
        this.sendMessageToUsers(new TradeClosedComposer(habbo.getHabboInfo().getId(), TradeClosedComposer.USER_CANCEL_TRADE));
        this.room.stopTrade(this);
    }

    private void removeStatusses() {
        for (RoomTradeUser user : this.users) {
            Habbo habbo = user.getHabbo();

            if (habbo == null)
                continue;

            habbo.getRoomUnit().removeStatus(RoomUnitStatus.TRADING);
            this.room.sendComposer(new RoomUserStatusComposer(habbo.getRoomUnit()).compose());
        }
    }

    public RoomTradeUser getRoomTradeUserForHabbo(Habbo habbo) {
        for (RoomTradeUser roomTradeUser : this.users) {
            if (roomTradeUser.getHabbo() == habbo)
                return roomTradeUser;
        }
        return null;
    }

    public void sendMessageToUsers(MessageComposer message) {
        for (RoomTradeUser roomTradeUser : this.users) {
            roomTradeUser.getHabbo().getClient().sendResponse(message);
        }
    }

    public List<RoomTradeUser> getRoomTradeUsers() {
        return this.users;
    }

    static boolean allOwnershipUpdatesSucceeded(int[] updateCounts, int expectedUpdates) {
        if (updateCounts == null || updateCounts.length != expectedUpdates) {
            return false;
        }

        for (int updateCount : updateCounts) {
            if (updateCount == Statement.EXECUTE_FAILED || updateCount == 0) {
                return false;
            }
        }

        return true;
    }

    public static int getCreditsByItem(HabboItem item) {
        if (!Emulator.getConfig().getBoolean("redeem.currency.trade")) return 0;

        if (!item.getBaseItem().getName().startsWith("CF_") && !item.getBaseItem().getName().startsWith("CFC_")) return 0;

        try {
            return Integer.parseInt(item.getBaseItem().getName().split("_")[1]);
        } catch (Exception e) {
            return 0;
        }
    }
}
