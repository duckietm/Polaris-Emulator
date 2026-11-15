package com.srth.plugin.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RedeemCommand extends Command {
    public RedeemCommand() {
        super("cmd_redeem", Emulator.getTexts().getValue("commands.keys.cmd_redeem").split(";"));
    }

    @Override
    public boolean handle(final GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(gameClient.getHabbo()) != null)
            return false;
        ArrayList<HabboItem> items = new ArrayList<>();

        int credits = 0;
        int pixels = 0;

        Map<Integer, Integer> points = new HashMap<>();

        for (HabboItem item : gameClient.getHabbo().getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (item.getBaseItem().getName().startsWith("CF_") || item.getBaseItem().getName().startsWith("CFC_")
                    || item.getBaseItem().getName().startsWith("DF_") || item.getBaseItem().getName().startsWith("PF_")
                    || item.getBaseItem().getName().startsWith("CASH_")) {
                if (item.getUserId() == gameClient.getHabbo().getHabboInfo().getId()) {

                    items.add(item);

                    if ((item.getBaseItem().getName().startsWith("CF_")
                            || item.getBaseItem().getName().startsWith("CFC_"))
                            && !item.getBaseItem().getName().contains("_diamond_")) {
                        try {
                            credits += Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                        }
                    } else if (item.getBaseItem().getName().startsWith("PF_")) {
                        try {
                            pixels += Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                        }
                    } else if (item.getBaseItem().getName().startsWith("DF_")) {
                        int pointsType;
                        int pointsAmount;

                        pointsType = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        pointsAmount = Integer.valueOf(item.getBaseItem().getName().split("_")[2]);

                        points.put(pointsType, points.getOrDefault(pointsType, 0) + pointsAmount);
                    } else if (item.getBaseItem().getName().startsWith("CF_diamond_")) {
                        int pointsType = 5;
                        int pointsAmount = Integer.valueOf(item.getBaseItem().getName().split("_")[2]);

                        points.put(pointsType, points.getOrDefault(pointsType, 0) + pointsAmount);
                    } else if (item.getBaseItem().getName().startsWith("CASH_")) {
                        int pointsType = 2;
                        int pointsAmount = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        points.put(pointsType, points.getOrDefault(pointsType, 0) + pointsAmount);
                    }
                }
            }
        }

        Map<Integer, HabboItem> deleted = new HashMap<>();
        for (HabboItem item : items) {
            gameClient.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
            deleted.put(item.getId(), item);
        }
        Emulator.getThreading().run(new QueryDeleteHabboItems(deleted.values()));

        gameClient.sendResponse(new InventoryRefreshComposer());
        gameClient.getHabbo().giveCredits(credits);
        gameClient.getHabbo().givePixels(pixels);

        final String[] message = { Emulator.getTexts().getValue("generic.redeemed") };

        message[0] += Emulator.getTexts().getValue("generic.credits");
        message[0] += ": " + credits;

        if (pixels > 0) {
            message[0] += ", " + Emulator.getTexts().getValue("generic.pixels");
            message[0] += ": " + pixels + "";
        }

        if (!points.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : points.entrySet()) {
                int a = entry.getKey();
                int b = entry.getValue();
                gameClient.getHabbo().givePoints(a, b);
                message[0] += ", " + Emulator.getTexts().getValue("seasonal.name." + a) + ": " + b;
            }
        }

        gameClient.getHabbo().whisper(message[0], RoomChatMessageBubbles.ALERT);

        return true;
    }
}