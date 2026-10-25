package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.incoming.rooms.items.RedeemItemTransaction;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;
import com.eu.habbo.plugin.events.furniture.FurnitureRedeemedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RedeemCommand extends Command {
    public RedeemCommand() {
        super("cmd_redeem", text("commands.keys.cmd_redeem").split(";"));
    }

    /** Every text lookup in one place, so the file reaches the global accessor once. */
    private static String text(String key) {
        return Emulator.getTexts().getValue(key);
    }

    /** One redeemable furni and what it is worth. */
    private record Redeemable(HabboItem item, int currencyType, int amount) {}

    @Override
    public boolean handle(final GameClient gameClient, String[] params) throws Exception {
        Habbo habbo = gameClient.getHabbo();

        if (habbo.getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(habbo) != null) {
            habbo.whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
            return true;
        }

        List<Redeemable> redeemables = collect(habbo);

        // Every furni is deleted and paid for in one transaction, the same one the redeem
        // packet uses. The command used to hand over all the currency first and queue the
        // deletes for later, so a failure in between - or a shutdown - left the user with
        // the credits and the furni both.
        Map<Integer, Integer> redeemed = new LinkedHashMap<>();
        for (Redeemable redeemable : redeemables) {
            EconomyMutationResult mutation = LedgerWalletMutation.coordinated(habbo, () -> {
                EconomyMutationResult result = RedeemItemTransaction.commit(
                        redeemable.item().getId(),
                        habbo.getHabboInfo().getId(),
                        redeemable.currencyType(),
                        redeemable.amount(),
                        redeemable.item().getBaseItem().getName());
                if (result != null) {
                    LedgerWalletMutation.applyCommitted(habbo, redeemable.currencyType(), result.balanceAfter());
                }
                return result;
            });

            if (mutation == null) continue;

            habbo.getInventory().getItemsComponent().removeHabboItem(redeemable.item());
            addRedeemPoints(redeemed, redeemable.currencyType(), redeemable.amount());
        }

        if (redeemed.isEmpty()) {
            habbo.whisper(text("generic.redeemed"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient.sendResponse(new InventoryRefreshComposer());
        this.publishBalances(gameClient, redeemed);
        habbo.whisper(describe(redeemed), RoomChatMessageBubbles.ALERT);

        return true;
    }

    /** The user's own redeemable furni, each with the currency and amount its name carries. */
    private static List<Redeemable> collect(Habbo habbo) {
        List<Redeemable> redeemables = new ArrayList<>();

        for (HabboItem item : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (item.getUserId() != habbo.getHabboInfo().getId()) continue;

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

    private static void add(List<Redeemable> redeemables, HabboItem item, int currencyType, Integer amount) {
        if (amount == null) return;

        redeemables.add(new Redeemable(item, currencyType, amount));
    }

    /** Tells the client the balances the commits changed. */
    private void publishBalances(GameClient gameClient, Map<Integer, Integer> redeemed) {
        Habbo habbo = gameClient.getHabbo();

        for (Map.Entry<Integer, Integer> entry : redeemed.entrySet()) {
            int currencyType = entry.getKey();

            if (currencyType == FurnitureRedeemedEvent.CREDITS) {
                gameClient.sendResponse(new UserCreditsComposer(habbo));
            } else if (currencyType == FurnitureRedeemedEvent.PIXELS) {
                gameClient.sendResponse(new UserCurrencyComposer(habbo));
            } else {
                gameClient.sendResponse(new UserPointsComposer(
                        habbo.getHabboInfo().getCurrencyAmount(currencyType), entry.getValue(), currencyType));
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
