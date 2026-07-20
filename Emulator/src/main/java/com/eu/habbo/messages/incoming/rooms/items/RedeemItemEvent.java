package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;
import com.eu.habbo.plugin.events.furniture.FurnitureRedeemedEvent;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedeemItemEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedeemItemEvent.class);

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            HabboItem item = room.getHabboItem(itemId);

            if (item != null && this.client.getHabbo().getHabboInfo().getId() == item.getUserId()) {
                boolean furnitureRedeemEventRegistered = Emulator.getPluginManager().isRegistered(FurnitureRedeemedEvent.class, true);
                FurnitureRedeemedEvent furniRedeemEvent = new FurnitureRedeemedEvent(item, this.client.getHabbo(), 0, FurnitureRedeemedEvent.CREDITS);

                if (item.getBaseItem().getName().startsWith("CF_") || item.getBaseItem().getName().startsWith("CFC_") || item.getBaseItem().getName().startsWith("DF_") || item.getBaseItem().getName().startsWith("PF_")) {
                    if ((item.getBaseItem().getName().startsWith("CF_") || item.getBaseItem().getName().startsWith("CFC_")) && !item.getBaseItem().getName().contains("_diamond_")) {
                        int credits;
                        try {
                            credits = Integer.parseInt(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable furniture: {}. Must be in format of CF_<amount>", item.getBaseItem().getName());
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, this.client.getHabbo(), credits, FurnitureRedeemedEvent.CREDITS);
                    } else if (item.getBaseItem().getName().startsWith("PF_")) {
                        int pixels;

                        try {
                            pixels = Integer.parseInt(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable pixel furniture: {}. Must be in format of PF_<amount>", item.getBaseItem().getName());
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, this.client.getHabbo(), pixels, FurnitureRedeemedEvent.PIXELS);
                    } else if (item.getBaseItem().getName().startsWith("DF_")) {
                        int pointsType;
                        int points;

                        try {
                            pointsType = Integer.parseInt(item.getBaseItem().getName().split("_")[1]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable points furniture: {}. Must be in format of DF_<pointstype>_<amount> where <pointstype> equals integer representation of seasonal currency.", item.getBaseItem().getName());
                            return;
                        }

                        try {
                            points = Integer.parseInt(item.getBaseItem().getName().split("_")[2]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable points furniture: {}. Must be in format of DF_<pointstype>_<amount> where <pointstype> equals integer representation of seasonal currency.", item.getBaseItem().getName());
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, this.client.getHabbo(), points, pointsType);
                    } else if (item.getBaseItem().getName().startsWith("CF_diamond_")) {
                        int points;

                        try {
                            points = Integer.parseInt(item.getBaseItem().getName().split("_")[2]);
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse redeemable diamonds furniture: {}. Must be in format of CF_diamond_<amount>", item.getBaseItem().getName());
                            return;
                        }

                        furniRedeemEvent = new FurnitureRedeemedEvent(item, this.client.getHabbo(), points, FurnitureRedeemedEvent.DIAMONDS);
                    }

                    if (furnitureRedeemEventRegistered) {
                        Emulator.getPluginManager().fireEvent(furniRedeemEvent);

                        if (furniRedeemEvent.isCancelled())
                            return;
                    }

                    if (furniRedeemEvent.amount < 1)
                        return;

                    PreparedCurrencyGrant currencyGrant = prepareCurrencyGrant(
                            furniRedeemEvent.currencyID, furniRedeemEvent.amount);
                    if (currencyGrant == null || currencyGrant.amount() <= 0)
                        return;

                    if (room.getHabboItem(item.getId()) == null) // plugins may cause a lag between which time the item can be removed from the room
                        return;

                    EconomyMutationResult walletMutation =
                            LedgerWalletMutation.coordinated(
                                    this.client.getHabbo(),
                                    () -> {
                                        EconomyMutationResult mutation =
                                                RedeemItemTransaction.commit(
                                                        item.getId(),
                                                        this.client.getHabbo().getHabboInfo().getId(),
                                                        currencyGrant.currencyType(),
                                                        currencyGrant.amount(),
                                                        item.getBaseItem().getName());
                                        if (mutation != null) {
                                            LedgerWalletMutation.applyCommitted(
                                                    this.client.getHabbo(),
                                                    currencyGrant.currencyType(),
                                                    mutation.balanceAfter());
                                        }
                                        return mutation;
                                    });
                    if (walletMutation == null)
                        return;

                    room.removeHabboItem(item);
                    room.sendComposer(new RemoveFloorItemComposer(item).compose());
                    RoomTile t = room.getLayout().getTile(item.getX(), item.getY());
                    if (t == null)
                        return;

                    t.setStackHeight(room.getStackHeight(item.getX(), item.getY(), false));
                    room.updateTile(t);
                    room.sendComposer(new UpdateStackHeightComposer(item.getX(), item.getY(), t.z, t.relativeHeight()).compose());
                    publishCurrencyGrant(currencyGrant);
                }
            }
        }
    }

    private PreparedCurrencyGrant prepareCurrencyGrant(int currencyType, int amount) {
        if (currencyType == FurnitureRedeemedEvent.CREDITS) {
            UserCreditsEvent event = new UserCreditsEvent(this.client.getHabbo(), amount);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return null;
            return new PreparedCurrencyGrant(FurnitureRedeemedEvent.CREDITS, event.credits);
        }

        UserPointsEvent event = new UserPointsEvent(this.client.getHabbo(), amount, currencyType);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return null;
        return new PreparedCurrencyGrant(event.type, event.points);
    }

    private void publishCurrencyGrant(PreparedCurrencyGrant grant) {
        if (grant.currencyType() == FurnitureRedeemedEvent.CREDITS) {
            this.client.sendResponse(new UserCreditsComposer(this.client.getHabbo()));
            return;
        }

        if (grant.currencyType() == FurnitureRedeemedEvent.PIXELS) {
            this.client.sendResponse(new UserCurrencyComposer(this.client.getHabbo()));
        } else {
            this.client.sendResponse(new UserPointsComposer(
                    this.client.getHabbo().getHabboInfo().getCurrencyAmount(grant.currencyType()),
                    grant.amount(),
                    grant.currencyType()));
        }
    }

    private record PreparedCurrencyGrant(int currencyType, int amount) {
    }
}
