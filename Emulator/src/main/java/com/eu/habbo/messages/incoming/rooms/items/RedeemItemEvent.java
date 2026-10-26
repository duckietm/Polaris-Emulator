package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
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

    /** The owner's own redeemable furni in the room: never a wired chest, whatever it is named. */
    private boolean isRedeemable(HabboItem item) {
        // A wired chest is never redeemed for currency, whatever it is named: redeeming deletes it
        // without releasing what it holds (RedeemItemTransaction deletes the row directly).
        if (item == null
                || item instanceof InteractionWiredChest
                || this.client.getHabbo().getHabboInfo().getId() != item.getUserId()) {
            return false;
        }

        String name = item.getBaseItem().getName();
        return name.startsWith("CF_") || name.startsWith("CFC_") || name.startsWith("DF_") || name.startsWith("PF_");
    }

    /**
     * What the furni's name is worth: CF_/CFC_<credits>, PF_<pixels>, DF_<points type>_<points> or
     * CF_diamond_<diamonds>. Null when the number in the name cannot be read; an amount of 0 when the name is none of
     * these.
     */
    private FurnitureRedeemedEvent redeemEvent(HabboItem item) {
        String name = item.getBaseItem().getName();

        if ((name.startsWith("CF_") || name.startsWith("CFC_")) && !name.contains("_diamond_")) {
            int credits;
            try {
                credits = Integer.parseInt(name.split("_")[1]);
            } catch (Exception e) {
                LOGGER.error("Failed to parse redeemable furniture: {}. Must be in format of CF_<amount>", name);
                return null;
            }

            return new FurnitureRedeemedEvent(item, this.client.getHabbo(), credits, FurnitureRedeemedEvent.CREDITS);
        } else if (name.startsWith("PF_")) {
            int pixels;

            try {
                pixels = Integer.parseInt(name.split("_")[1]);
            } catch (Exception e) {
                LOGGER.error("Failed to parse redeemable pixel furniture: {}. Must be in format of PF_<amount>", name);
                return null;
            }

            return new FurnitureRedeemedEvent(item, this.client.getHabbo(), pixels, FurnitureRedeemedEvent.PIXELS);
        } else if (name.startsWith("DF_")) {
            int pointsType;
            int points;

            try {
                pointsType = Integer.parseInt(name.split("_")[1]);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to parse redeemable points furniture: {}. Must be in format of DF_<pointstype>_<amount> where <pointstype> equals integer representation of seasonal currency.",
                        name);
                return null;
            }

            try {
                points = Integer.parseInt(name.split("_")[2]);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to parse redeemable points furniture: {}. Must be in format of DF_<pointstype>_<amount> where <pointstype> equals integer representation of seasonal currency.",
                        name);
                return null;
            }

            return new FurnitureRedeemedEvent(item, this.client.getHabbo(), points, pointsType);
        } else if (name.startsWith("CF_diamond_")) {
            int points;

            try {
                points = Integer.parseInt(name.split("_")[2]);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to parse redeemable diamonds furniture: {}. Must be in format of CF_diamond_<amount>",
                        name);
                return null;
            }

            return new FurnitureRedeemedEvent(item, this.client.getHabbo(), points, FurnitureRedeemedEvent.DIAMONDS);
        }

        return new FurnitureRedeemedEvent(item, this.client.getHabbo(), 0, FurnitureRedeemedEvent.CREDITS);
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (!this.isRedeemable(item)) return;

        boolean furnitureRedeemEventRegistered =
                Emulator.getPluginManager().isRegistered(FurnitureRedeemedEvent.class, true);
        FurnitureRedeemedEvent furniRedeemEvent = this.redeemEvent(item);

        if (furniRedeemEvent == null) return;

        if (furnitureRedeemEventRegistered) {
            Emulator.getPluginManager().fireEvent(furniRedeemEvent);

            if (furniRedeemEvent.isCancelled()) return;
        }

        if (furniRedeemEvent.amount < 1) return;

        PreparedCurrencyGrant currencyGrant =
                prepareCurrencyGrant(furniRedeemEvent.currencyID, furniRedeemEvent.amount);
        if (currencyGrant == null || currencyGrant.amount() <= 0) return;

        if (room.getHabboItem(item.getId())
                == null) // plugins may cause a lag between which time the item can be removed from the room
        return;

        EconomyMutationResult walletMutation = LedgerWalletMutation.coordinated(this.client.getHabbo(), () -> {
            EconomyMutationResult mutation = RedeemItemTransaction.commit(
                    item.getId(),
                    this.client.getHabbo().getHabboInfo().getId(),
                    currencyGrant.currencyType(),
                    currencyGrant.amount(),
                    item.getBaseItem().getName());
            if (mutation != null) {
                LedgerWalletMutation.applyCommitted(
                        this.client.getHabbo(), currencyGrant.currencyType(), mutation.balanceAfter());
            }
            return mutation;
        });
        if (walletMutation == null) return;

        this.takeFromRoom(room, item);

        // The currency is already committed: the owner is told the new balance even when the furni's tile is gone.
        publishCurrencyGrant(currencyGrant);
    }

    /** Takes the redeemed furni out of the room and lowers its tile, when the tile is still there. */
    private void takeFromRoom(Room room, HabboItem item) {
        room.removeHabboItem(item);
        room.sendComposer(new RemoveFloorItemComposer(item).compose());
        RoomTile t = room.getLayout().getTile(item.getX(), item.getY());
        if (t == null) return;

        t.setStackHeight(room.getStackHeight(item.getX(), item.getY(), false));
        room.updateTile(t);
        room.sendComposer(new UpdateStackHeightComposer(item.getX(), item.getY(), t.z, t.relativeHeight()).compose());
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

    private record PreparedCurrencyGrant(int currencyType, int amount) {}
}
