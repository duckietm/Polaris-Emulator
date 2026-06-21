package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedeemCommand extends Command {
    public RedeemCommand() {
        super("cmd_redeem", Emulator.getTexts().getValue("commands.keys.cmd_redeem").split(";"));
    }

    @Override
    public boolean handle(final GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(gameClient.getHabbo()) != null)
            return false;
        List<HabboItem> items = new ArrayList<>();

        int credits = 0;
        int pixels = 0;

        Map<Integer, Integer> points = new HashMap<>();

        for (HabboItem item : gameClient.getHabbo().getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (item.getBaseItem().getName().startsWith("CF_") || item.getBaseItem().getName().startsWith("CFC_") || item.getBaseItem().getName().startsWith("DF_") || item.getBaseItem().getName().startsWith("PF_")) {
                if (item.getUserId() == gameClient.getHabbo().getHabboInfo().getId()) {
                    boolean redeemable = false;
                    if ((item.getBaseItem().getName().startsWith("CF_") || item.getBaseItem().getName().startsWith("CFC_")) && !item.getBaseItem().getName().contains("_diamond_")) {
                        Integer amount = parsePositiveRedeemValue(item.getBaseItem().getName(), 1);
                        if (amount != null) {
                            Integer total = addRedeemValue(credits, amount);
                            if (total != null) {
                                credits = total;
                                redeemable = true;
                            }
                        }

                    } else if (item.getBaseItem().getName().startsWith("PF_")) {
                        Integer amount = parsePositiveRedeemValue(item.getBaseItem().getName(), 1);
                        if (amount != null) {
                            Integer total = addRedeemValue(pixels, amount);
                            if (total != null) {
                                pixels = total;
                                redeemable = true;
                            }
                        }
                    } else if (item.getBaseItem().getName().startsWith("DF_")) {
                        Integer pointsType = parsePositiveRedeemValue(item.getBaseItem().getName(), 1);
                        Integer pointsAmount = parsePositiveRedeemValue(item.getBaseItem().getName(), 2);

                        if (pointsType != null && pointsAmount != null && addRedeemPoints(points, pointsType, pointsAmount)) {
                            redeemable = true;
                        }
                    }
                    else if (item.getBaseItem().getName().startsWith("CF_diamond_")) {
                        Integer pointsAmount = parsePositiveRedeemValue(item.getBaseItem().getName(), 2);

                        if (pointsAmount != null && addRedeemPoints(points, 5, pointsAmount)) {
                            redeemable = true;
                        }
                    }

                    if (redeemable) {
                        items.add(item);
                    }
                }
            }
        }

        List<HabboItem> deleted = new ArrayList<>();
        for (HabboItem item : items) {
            gameClient.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
            deleted.add(item);
        }

        Emulator.getThreading().run(new QueryDeleteHabboItems(deleted));

        gameClient.sendResponse(new InventoryRefreshComposer());
        gameClient.getHabbo().giveCredits(credits);
        gameClient.getHabbo().givePixels(pixels);

        final String[] message = {Emulator.getTexts().getValue("generic.redeemed")};

        message[0] += Emulator.getTexts().getValue("generic.credits");
        message[0] += ": " + credits;

        if (pixels > 0) {
            message[0] += ", " + Emulator.getTexts().getValue("generic.pixels");
            message[0] += ": " + pixels + "";
        }

        if (!points.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : points.entrySet()) {
                gameClient.getHabbo().givePoints(entry.getKey(), entry.getValue());
                message[0] += " ," + Emulator.getTexts().getValue("seasonal.name." + entry.getKey()) + ": " + entry.getValue();
            }
        }

        gameClient.getHabbo().whisper(message[0], RoomChatMessageBubbles.ALERT);

        return true;
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
