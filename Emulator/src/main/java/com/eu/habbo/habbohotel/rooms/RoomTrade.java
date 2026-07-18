package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.trading.*;
import com.eu.habbo.plugin.events.trading.TradeConfirmEvent;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public synchronized void offerMultipleItems(Habbo habbo, Set<HabboItem> items) {
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

        Set<HabboItem> itemsUserOne = new HashSet<>(userOne.getItems());
        Set<HabboItem> itemsUserTwo = new HashSet<>(userTwo.getItems());

        int creditsForUserTwo;
        int creditsForUserOne;
        try {
            creditsForUserTwo = checkedCreditTotal(itemsUserOne);
            creditsForUserOne = checkedCreditTotal(itemsUserTwo);
            checkedRecipientBalance(userTwo, creditsForUserTwo);
            checkedRecipientBalance(userOne, creditsForUserOne);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Rejected trade with unrepresentable credit-furni value", e);
            this.sendMessageToUsers(new TradeClosedComposer(
                    userOne.getHabbo().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
            return false;
        }
        Set<HabboItem> creditFurniUserOne = new HashSet<>();
        for (HabboItem item : itemsUserOne) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditFurniUserOne.add(item);
            }
        }
        itemsUserOne.removeAll(creditFurniUserOne);

        Set<HabboItem> creditFurniUserTwo = new HashSet<>();
        for (HabboItem item : itemsUserTwo) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditFurniUserTwo.add(item);
            }
        }
        itemsUserTwo.removeAll(creditFurniUserTwo);

        creditsForUserOne = resolveCredits(userOne.getHabbo(), creditsForUserOne);
        creditsForUserTwo = resolveCredits(userTwo.getHabbo(), creditsForUserTwo);

        try {
            if (!RoomTradeTransaction.execute(userOne.getHabbo(), userTwo.getHabbo(),
                    userOne.getItems(), userTwo.getItems(), creditsForUserOne, creditsForUserTwo,
                    Emulator.getConfig().getBoolean("hotel.log.trades"))) {
                return false;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.sendMessageToUsers(new TradeClosedComposer(userOne.getHabbo().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
            return false;
        }

        for (HabboItem item : itemsUserOne) item.setUserId(userTwo.getHabbo().getHabboInfo().getId());
        for (HabboItem item : itemsUserTwo) item.setUserId(userOne.getHabbo().getHabboInfo().getId());

        userOne.clearItems();
        userTwo.clearItems();

        applyCommittedCredits(userOne.getHabbo(), creditsForUserOne);
        applyCommittedCredits(userTwo.getHabbo(), creditsForUserTwo);

        userOne.getHabbo().getInventory().getItemsComponent().addItems(itemsUserTwo);
        userTwo.getHabbo().getInventory().getItemsComponent().addItems(itemsUserOne);

        userOne.getHabbo().getClient().sendResponse(new AddHabboItemComposer(itemsUserTwo));
        userTwo.getHabbo().getClient().sendResponse(new AddHabboItemComposer(itemsUserOne));

        userOne.getHabbo().getClient().sendResponse(new InventoryRefreshComposer());
        userTwo.getHabbo().getClient().sendResponse(new InventoryRefreshComposer());
        return true;
    }

    private static int resolveCredits(Habbo habbo, int credits) {
        if (credits <= 0) return 0;
        UserCreditsEvent event = new UserCreditsEvent(habbo, credits);
        return Emulator.getPluginManager().fireEvent(event).isCancelled() ? 0 : Math.max(0, event.credits);
    }

    private static void applyCommittedCredits(Habbo habbo, int credits) {
        if (credits <= 0) return;
        habbo.getHabboInfo().addCredits(credits);
        if (habbo.getClient() != null) habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
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

    static int checkedAddCreditValue(int total, int worth) {
        if (total < 0 || worth < 0) {
            throw new IllegalArgumentException("trade credit value must not be negative");
        }

        long updated = (long) total + worth;
        if (updated > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("trade credit value exceeds wallet range");
        }
        return (int) updated;
    }

    private static int checkedCreditTotal(Iterable<HabboItem> items) {
        int total = 0;
        for (HabboItem item : items) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                total = checkedAddCreditValue(total, worth);
            }
        }
        return total;
    }

    private static void checkedRecipientBalance(RoomTradeUser recipient, int incomingCredits) {
        checkedAddCreditValue(recipient.getHabbo().getHabboInfo().getCredits(), incomingCredits);
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
