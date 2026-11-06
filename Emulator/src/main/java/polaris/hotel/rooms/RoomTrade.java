package polaris.hotel.rooms;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.messages.outgoing.trading.TradeAcceptedComposer;
import polaris.messages.outgoing.trading.TradeCloseWindowComposer;
import polaris.messages.outgoing.trading.TradeClosedComposer;
import polaris.messages.outgoing.trading.TradeCompleteComposer;
import polaris.messages.outgoing.trading.TradeStartComposer;
import polaris.messages.outgoing.trading.TradeUpdateComposer;
import polaris.messages.outgoing.trading.TradingWaitingConfirmComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.plugin.events.trading.TradeConfirmEvent;
import polaris.plugin.events.users.UserCreditsEvent;

public class RoomTrade {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTrade.class);
    // Configuration. Loaded from database & updated accordingly.
    public static volatile boolean TRADING_ENABLED = true;
    public static volatile boolean TRADING_REQUIRES_PERK = true;
    public static final int MAX_OFFERED_ITEMS = 100;

    private final List<RoomTradeUser> users;
    private final Room room;
    private boolean completed = false;

    public RoomTrade(User userOne, User userTwo, Room room) {
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
            if (!roomTradeUser.getUser().getRoomUnit().hasStatus(RoomUnitStatus.TRADING)) {
                roomTradeUser.getUser().getRoomUnit().setStatus(RoomUnitStatus.TRADING, "");
                if (!roomTradeUser.getUser().getRoomUnit().isWalking())
                    this.room.sendComposer(
                            new RoomUserStatusComposer(roomTradeUser.getUser().getRoomUnit()).compose());
            }
        }
    }

    protected void openTrade() {
        this.sendMessageToUsers(new TradeStartComposer(this));
    }

    public synchronized void offerItem(User trader, UserItem item) {
        // A trade that was completed or cancelled takes no more offers or changes.
        if (this.completed) return;

        RoomTradeUser user = this.getRoomTradeUserForUser(trader);

        if (user == null
                || item == null
                || user.getItems().contains(item)
                || user.getItems().size() >= MAX_OFFERED_ITEMS) return;

        if (!trader.getInventory().getItemsComponent().takeUserItemsAtomically(List.of(item))) return;

        user.getItems().add(item);

        this.clearAccepted();
        this.updateWindow();
    }

    public synchronized void offerMultipleItems(User trader, Set<UserItem> items) {
        // A trade that was completed or cancelled takes no more offers or changes.
        if (this.completed) return;

        RoomTradeUser user = this.getRoomTradeUserForUser(trader);

        if (user == null || items == null) return;

        for (UserItem item : items) {
            if (user.getItems().size() >= MAX_OFFERED_ITEMS) break;

            if (!user.getItems().contains(item)) {
                if (!trader.getInventory().getItemsComponent().takeUserItemsAtomically(List.of(item))) continue;

                user.getItems().add(item);
            }
        }

        this.clearAccepted();
        this.updateWindow();
    }

    public synchronized void removeItem(User trader, UserItem item) {
        // A trade that was completed or cancelled takes no more offers or changes.
        if (this.completed) return;

        RoomTradeUser user = this.getRoomTradeUserForUser(trader);

        if (user == null || item == null || !user.getItems().contains(item)) return;

        trader.getInventory().getItemsComponent().addItem(item);
        user.getItems().remove(item);

        this.clearAccepted();
        this.updateWindow();
    }

    public synchronized void accept(User trader, boolean value) {
        // A trade that was completed or cancelled takes no more offers or changes.
        if (this.completed) return;

        RoomTradeUser user = this.getRoomTradeUserForUser(trader);

        if (user == null) return;

        user.setAccepted(value);

        this.sendMessageToUsers(new TradeAcceptedComposer(user));
        boolean accepted = true;
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.getAccepted()) accepted = false;
        }
        if (accepted) {
            this.sendMessageToUsers(new TradingWaitingConfirmComposer());
        }
    }

    public synchronized void confirm(User trader) {
        // Re-entry guard: both participants confirm on their own EventLoop
        // threads. Without this (and the method-level lock) two concurrent
        // confirms could each observe "all confirmed" and run tradeItems()
        // twice → item/credit duplication.
        if (this.completed) return;

        RoomTradeUser user = this.getRoomTradeUserForUser(trader);

        // Checked here, under the trade's lock: the handler checked "accepted" before taking it, so an offer changed
        // in between (which clears every acceptance) could still be confirmed.
        if (user == null || !user.getAccepted()) return;

        user.confirm();

        this.sendMessageToUsers(new TradeAcceptedComposer(user));
        boolean accepted = true;
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.getConfirmed()) accepted = false;
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
            for (UserItem item : roomTradeUser.getItems()) {
                if (roomTradeUser.getUser().getInventory().getItemsComponent().getUserItem(item.getId()) != null) {
                    this.sendMessageToUsers(new TradeClosedComposer(
                            roomTradeUser.getUser().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
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

        Set<UserItem> itemsUserOne = new HashSet<>(userOne.getItems());
        Set<UserItem> itemsUserTwo = new HashSet<>(userTwo.getItems());

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
                    userOne.getUser().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
            return false;
        }
        Set<UserItem> creditFurniUserOne = new HashSet<>();
        for (UserItem item : itemsUserOne) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditFurniUserOne.add(item);
            }
        }
        itemsUserOne.removeAll(creditFurniUserOne);

        Set<UserItem> creditFurniUserTwo = new HashSet<>();
        for (UserItem item : itemsUserTwo) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditFurniUserTwo.add(item);
            }
        }
        itemsUserTwo.removeAll(creditFurniUserTwo);

        creditsForUserOne = resolveCredits(userOne.getUser(), creditsForUserOne);
        creditsForUserTwo = resolveCredits(userTwo.getUser(), creditsForUserTwo);
        final int committedCreditsForUserOne = creditsForUserOne;
        final int committedCreditsForUserTwo = creditsForUserTwo;

        try {
            LedgerWalletMutation.coordinated(userOne.getUser(), userTwo.getUser(), () -> {
                RoomTradeTransaction.CommitResult commit = RoomTradeTransaction.execute(
                        userOne.getUser(),
                        userTwo.getUser(),
                        userOne.getItems(),
                        userTwo.getItems(),
                        committedCreditsForUserOne,
                        committedCreditsForUserTwo,
                        Emulator.getConfig().getBoolean("hotel.log.trades"));
                applyCommittedCreditBalance(userOne.getUser(), commit.userOneCreditBalance());
                applyCommittedCreditBalance(userTwo.getUser(), commit.userTwoCreditBalance());
                return commit;
            });
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.sendMessageToUsers(new TradeClosedComposer(
                    userOne.getUser().getRoomUnit().getId(), TradeClosedComposer.ITEMS_NOT_FOUND));
            return false;
        }

        for (UserItem item : itemsUserOne)
            item.setUserId(userTwo.getUser().getUserInfo().getId());
        for (UserItem item : itemsUserTwo)
            item.setUserId(userOne.getUser().getUserInfo().getId());

        userOne.clearItems();
        userTwo.clearItems();

        publishCommittedCredits(userOne.getUser(), committedCreditsForUserOne);
        publishCommittedCredits(userTwo.getUser(), committedCreditsForUserTwo);

        userOne.getUser().getInventory().getItemsComponent().addItems(itemsUserTwo);
        userTwo.getUser().getInventory().getItemsComponent().addItems(itemsUserOne);

        userOne.getUser().getClient().sendResponse(new AddUserItemComposer(itemsUserTwo));
        userTwo.getUser().getClient().sendResponse(new AddUserItemComposer(itemsUserOne));

        userOne.getUser().getClient().sendResponse(new InventoryRefreshComposer());
        userTwo.getUser().getClient().sendResponse(new InventoryRefreshComposer());
        return true;
    }

    private static int resolveCredits(User trader, int credits) {
        if (credits <= 0) return 0;
        UserCreditsEvent event = new UserCreditsEvent(trader, credits);
        return Emulator.getPluginManager().fireEvent(event).isCancelled() ? 0 : Math.max(0, event.credits);
    }

    private static void applyCommittedCreditBalance(User trader, Integer committedBalance) {
        if (committedBalance == null) return;
        LedgerWalletMutation.applyCommitted(trader, polaris.hotel.economy.EconomyLedger.CREDITS, committedBalance);
    }

    private static void publishCommittedCredits(User trader, int credits) {
        if (credits <= 0) return;
        if (trader.getClient() != null) trader.getClient().sendResponse(new UserCreditsComposer(trader));
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

    public synchronized void stopTrade(User trader) {
        // Same lock and guard as confirm(): a cancel that lands while the trade is being completed must not
        // hand the offered items back after they have changed owner.
        if (this.completed) return;

        this.completed = true;

        this.removeStatusses();
        this.clearAccepted();
        this.returnItems();
        for (RoomTradeUser user : this.users) {
            user.clearItems();
        }
        this.updateWindow();
        this.sendMessageToUsers(
                new TradeClosedComposer(trader.getUserInfo().getId(), TradeClosedComposer.USER_CANCEL_TRADE));
        this.room.stopTrade(this);
    }

    private void removeStatusses() {
        for (RoomTradeUser user : this.users) {
            User trader = user.getUser();

            if (trader == null) continue;

            trader.getRoomUnit().removeStatus(RoomUnitStatus.TRADING);
            this.room.sendComposer(new RoomUserStatusComposer(trader.getRoomUnit()).compose());
        }
    }

    public RoomTradeUser getRoomTradeUserForUser(User trader) {
        for (RoomTradeUser roomTradeUser : this.users) {
            if (roomTradeUser.getUser() == trader) return roomTradeUser;
        }
        return null;
    }

    public void sendMessageToUsers(MessageComposer message) {
        for (RoomTradeUser roomTradeUser : this.users) {
            roomTradeUser.getUser().getClient().sendResponse(message);
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

    private static int checkedCreditTotal(Iterable<UserItem> items) {
        int total = 0;
        for (UserItem item : items) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                total = checkedAddCreditValue(total, worth);
            }
        }
        return total;
    }

    private static void checkedRecipientBalance(RoomTradeUser recipient, int incomingCredits) {
        checkedAddCreditValue(recipient.getUser().getUserInfo().getCredits(), incomingCredits);
    }

    public static int getCreditsByItem(UserItem item) {
        // A wired chest is never credit furni, whatever it is named: it trades as itself, with what it
        // holds, instead of being destroyed by RoomTradeTransaction's redeem delete.
        if (item instanceof InteractionWiredChest) return 0;
        if (!Emulator.getConfig().getBoolean("redeem.currency.trade")) return 0;

        if (!item.getBaseItem().getName().startsWith("CF_")
                && !item.getBaseItem().getName().startsWith("CFC_")) return 0;

        try {
            return Integer.parseInt(item.getBaseItem().getName().split("_")[1]);
        } catch (Exception e) {
            return 0;
        }
    }
}
