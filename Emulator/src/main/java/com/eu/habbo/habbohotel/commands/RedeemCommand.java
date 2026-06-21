package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntIntProcedure;

import java.util.ArrayList;

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

        TIntIntMap points = new TIntIntHashMap();

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

        TIntObjectHashMap<HabboItem> deleted = new TIntObjectHashMap<>();
        for (HabboItem item : items) {
            gameClient.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
            deleted.put(item.getId(), item);
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
            points.forEachEntry(new TIntIntProcedure() {
                @Override
                public boolean execute(int a, int b) {
                    gameClient.getHabbo().givePoints(a, b);
                    message[0] += " ," + Emulator.getTexts().getValue("seasonal.name." + a) + ": " + b;
                    return true;
                }
            });
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

    static boolean addRedeemPoints(TIntIntMap points, int pointsType, int amount) {
        int current = points.get(pointsType);
        Integer total = addRedeemValue(current, amount);
        if (total == null) {
            return false;
        }

        points.put(pointsType, total);
        return true;
    }
}
