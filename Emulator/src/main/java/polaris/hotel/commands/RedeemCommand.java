package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.rooms.items.RedeemItemTransaction;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;
import polaris.messages.outgoing.users.UserPointsComposer;
import polaris.plugin.events.furniture.FurnitureRedeemedEvent;

public class RedeemCommand extends Command {
    public RedeemCommand() {
        super("cmd_redeem", text("commands.keys.cmd_redeem").split(";"));
    }

    /** Every text lookup in one place, so the file reaches the global accessor once. */
    private static String text(String key) {
        return Emulator.getTexts().getValue(key);
    }

    /** One redeemable furni and what it is worth. */
    private record Redeemable(UserItem item, int currencyType, int amount) {}

    @Override
    public boolean handle(final GameClient gameClient, String[] params) throws Exception {
        User user = gameClient.getUser();

        if (user.getUserInfo().getCurrentRoom().getActiveTradeForUser(user) != null) {
            user.whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
            return true;
        }

        List<Redeemable> redeemables = collect(user);

        // Every furni is deleted and paid for in one transaction, the same one the redeem
        // packet uses. The command used to hand over all the currency first and queue the
        // deletes for later, so a failure in between - or a shutdown - left the user with
        // the credits and the furni both.
        Map<Integer, Integer> redeemed = new LinkedHashMap<>();
        for (Redeemable redeemable : redeemables) {
            EconomyMutationResult mutation = LedgerWalletMutation.coordinated(user, () -> {
                EconomyMutationResult result = RedeemItemTransaction.commit(
                        redeemable.item().getId(),
                        user.getUserInfo().getId(),
                        redeemable.currencyType(),
                        redeemable.amount(),
                        redeemable.item().getBaseItem().getName());
                if (result != null) {
                    LedgerWalletMutation.applyCommitted(user, redeemable.currencyType(), result.balanceAfter());
                }
                return result;
            });

            if (mutation == null) continue;

            user.getInventory().getItemsComponent().removeUserItem(redeemable.item());
            addRedeemPoints(redeemed, redeemable.currencyType(), redeemable.amount());
        }

        if (redeemed.isEmpty()) {
            user.whisper(text("generic.redeemed"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient.sendResponse(new InventoryRefreshComposer());
        this.publishBalances(gameClient, redeemed);
        user.whisper(describe(redeemed), RoomChatMessageBubbles.ALERT);

        return true;
    }

    /** The user's own redeemable furni, each with the currency and amount its name carries. */
    private static List<Redeemable> collect(User user) {
        List<Redeemable> redeemables = new ArrayList<>();

        for (UserItem item : user.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            // A wired chest is never cashed in, whatever it is named: it was deleted without releasing what it held,
            // as the room redeem already refuses.
            if (item.getUserId() != user.getUserInfo().getId()
                    || item.hasRentPeriod()
                    || item instanceof InteractionWiredChest) continue;

            String name = item.getBaseItem().getName();

            if ((name.startsWith("CF_") || name.startsWith("CFC_")) && !name.contains("_diamond_")) {
                add(redeemables, item, FurnitureRedeemedEvent.CREDITS, parsePositiveRedeemValue(name, 1));
            } else if (name.startsWith("PF_")) {
                add(redeemables, item, FurnitureRedeemedEvent.PIXELS, parsePositiveRedeemValue(name, 1));
            } else if (name.startsWith("DF_")) {
                Integer pointsType = parsePositiveRedeemValue(name, 1);
                if (pointsType != null) {
                    add(redeemables, item, pointsType, parsePositiveRedeemValue(name, 2));
                }
            } else if (name.startsWith("CF_diamond_")) {
                add(redeemables, item, FurnitureRedeemedEvent.DIAMONDS, parsePositiveRedeemValue(name, 2));
            }
        }

        return redeemables;
    }

    private static void add(List<Redeemable> redeemables, UserItem item, int currencyType, Integer amount) {
        if (amount == null) return;

        redeemables.add(new Redeemable(item, currencyType, amount));
    }

    /** Tells the client the balances the commits changed. */
    private void publishBalances(GameClient gameClient, Map<Integer, Integer> redeemed) {
        User user = gameClient.getUser();

        for (Map.Entry<Integer, Integer> entry : redeemed.entrySet()) {
            int currencyType = entry.getKey();

            if (currencyType == FurnitureRedeemedEvent.CREDITS) {
                gameClient.sendResponse(new UserCreditsComposer(user));
            } else if (currencyType == FurnitureRedeemedEvent.PIXELS) {
                gameClient.sendResponse(new UserCurrencyComposer(user));
            } else {
                gameClient.sendResponse(new UserPointsComposer(
                        user.getUserInfo().getCurrencyAmount(currencyType), entry.getValue(), currencyType));
            }
        }
    }

    /** "Redeemed ... credits: 10, duckets: 5", from what actually committed. */
    private static String describe(Map<Integer, Integer> redeemed) {
        StringBuilder message = new StringBuilder(text("generic.redeemed"));

        Integer credits = redeemed.get(FurnitureRedeemedEvent.CREDITS);
        message.append(text("generic.credits")).append(": ").append(credits == null ? 0 : credits);

        Integer pixels = redeemed.get(FurnitureRedeemedEvent.PIXELS);
        if (pixels != null && pixels > 0) {
            message.append(", ").append(text("generic.pixels")).append(": ").append(pixels);
        }

        for (Map.Entry<Integer, Integer> entry : redeemed.entrySet()) {
            int currencyType = entry.getKey();
            if (currencyType == FurnitureRedeemedEvent.CREDITS || currencyType == FurnitureRedeemedEvent.PIXELS) {
                continue;
            }

            message.append(" ,")
                    .append(text("seasonal.name." + currencyType))
                    .append(": ")
                    .append(entry.getValue());
        }

        return message.toString();
    }

    static Integer parsePositiveRedeemValue(String itemName, int index) {
        if (itemName == null) {
            return null;
        }

        String[] parts = itemName.split("_");
        if (index < 0 || index >= parts.length) {
            return null;
        }

        try {
            int value = Integer.parseInt(parts[index]);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer addRedeemValue(int current, int amount) {
        try {
            return Math.addExact(current, amount);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    static boolean addRedeemPoints(Map<Integer, Integer> points, int pointsType, int amount) {
        int current = points.getOrDefault(pointsType, 0);
        Integer total = addRedeemValue(current, amount);
        if (total == null) {
            return false;
        }

        points.put(pointsType, total);
        return true;
    }
}
