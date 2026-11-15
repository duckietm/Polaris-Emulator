package com.srth.plugin.events.habboHandlers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.plugin.events.furniture.FurnitureRedeemedEvent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandlerRedeemItemEvent implements ICallable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandlerRedeemItemEvent.class);

    @Override
    public void call(MessageHandler event) {

        int itemId = event.packet.readInt();
        Room room = event.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            HabboItem item = room.getHabboItem(itemId);

            if (item != null && event.client.getHabbo().getHabboInfo().getId() == item.getUserId()) {

                boolean furnitureRedeemEventRegistered = Emulator.getPluginManager().isRegistered(FurnitureRedeemedEvent.class, true);
                FurnitureRedeemedEvent furniRedeemEvent = new FurnitureRedeemedEvent(item, event.client.getHabbo(), 0, FurnitureRedeemedEvent.CREDITS);

                if (item.getBaseItem().getName().startsWith("CF_") || item.getBaseItem().getName().startsWith("CFC_") || item.getBaseItem().getName().startsWith("DF_") || item.getBaseItem().getName().startsWith("PF_") || item.getBaseItem().getName().startsWith("CASH_")) {

                    if ((item.getBaseItem().getName().startsWith("CF_") || item.getBaseItem().getName().startsWith("CFC_")) && !item.getBaseItem().getName().contains("_diamond_")) {
                        int credits;
                        try {
                            credits = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable furniture: " + item.getBaseItem().getName() + ". Must be in format of CF_<amount>");
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, event.client.getHabbo(), credits, FurnitureRedeemedEvent.CREDITS);
                    } else if (item.getBaseItem().getName().startsWith("PF_")) {
                        int pixels;

                        try {
                            pixels = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable pixel furniture: " + item.getBaseItem().getName() + ". Must be in format of PF_<amount>");
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, event.client.getHabbo(), pixels, FurnitureRedeemedEvent.PIXELS);
                    } else if (item.getBaseItem().getName().startsWith("DF_")) {
                        int pointsType;
                        int points;

                        try {
                            pointsType = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable points furniture: " + item.getBaseItem().getName() + ". Must be in format of DF_<pointstype>_<amount> where <pointstype> equals integer representation of seasonal currency.");
                            return;
                        }

                        try {
                            points = Integer.valueOf(item.getBaseItem().getName().split("_")[2]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable points furniture: " + item.getBaseItem().getName() + ". Must be in format of DF_<pointstype>_<amount> where <pointstype> equals integer representation of seasonal currency.");
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, event.client.getHabbo(), points, pointsType);
                    } else if (item.getBaseItem().getName().startsWith("CF_diamond_")) {
                        int points;

                        try {
                            points = Integer.valueOf(item.getBaseItem().getName().split("_")[2]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable diamonds furniture: " + item.getBaseItem().getName() + ". Must be in format of CF_diamond_<amount>");
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, event.client.getHabbo(), points, FurnitureRedeemedEvent.DIAMONDS);
                    } else if (item.getBaseItem().getName().startsWith("CASH_")) {
                        int cash;
                        try {
                            cash = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable cash furniture: " + item.getBaseItem().getName() + ". Must be in format of CASH_<amount>");
                            return;
                        }
                        furniRedeemEvent = new FurnitureRedeemedEvent(item, event.client.getHabbo(), cash, 2);
                    }

                    if (furnitureRedeemEventRegistered) {
                        Emulator.getPluginManager().fireEvent(furniRedeemEvent);

                        if (furniRedeemEvent.isCancelled())
                            return;
                    }

                    if (furniRedeemEvent.amount < 1)
                        return;

                    if (room.getHabboItem(item.getId()) == null) // plugins may cause a lag between which time the item can be removed from the room
                        return;

                    room.removeHabboItem(item);
                    room.sendComposer(new RemoveFloorItemComposer(item).compose());
                    RoomTile t = room.getLayout().getTile(item.getX(), item.getY());
                    t.setStackHeight(room.getStackHeight(item.getX(), item.getY(), false));
                    room.updateTile(t);
                    room.sendComposer(new UpdateStackHeightComposer(item.getX(), item.getY(), t.z, t.relativeHeight()).compose());
                    Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));

                    switch (furniRedeemEvent.currencyID) {
                        case FurnitureRedeemedEvent.CREDITS:
                            event.client.getHabbo().giveCredits(furniRedeemEvent.amount);
                            break;

                        case FurnitureRedeemedEvent.DIAMONDS:
                            event.client.getHabbo().givePoints(furniRedeemEvent.amount);
                            break;

                        case FurnitureRedeemedEvent.PIXELS:
                            event.client.getHabbo().givePixels(furniRedeemEvent.amount);
                            break;

                        default:
                            event.client.getHabbo().givePoints(furniRedeemEvent.currencyID, furniRedeemEvent.amount);
                            break;
                    }
                }
            }
        }
    }
}