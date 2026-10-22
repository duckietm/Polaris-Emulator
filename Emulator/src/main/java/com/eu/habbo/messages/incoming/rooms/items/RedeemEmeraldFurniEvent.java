package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.collectibles.CollectiblesRuntime;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.collectibles.CollectorProgress;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleEmeraldBalanceComposer;
import com.eu.habbo.messages.outgoing.collectibles.NftEmeraldConvertResultComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;

/**
 * AIR 15 emerald furni redeem (121, {@code objectId}), from the credit furni widget of a
 * {@code furniture_nft_credit} furni ({@code nft_emerald_<amount>}). Like {@code CF_} credit furni the
 * furni goes and its value is added, here in emeralds. A failure answers
 * {@code NftEmeraldConvertResult (stuffId, result)}: 1 not redeemable, 2 not yours.
 */
public class RedeemEmeraldFurniEvent extends MessageHandler {
    public static final int NOT_REDEEMABLE = 1;
    public static final int NOT_OWNER = 2;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        CollectiblesSettings settings = CollectiblesRuntime.currentSettings();
        if (!settings.enabled() || !settings.emeraldCreditEnabled() || CollectiblesRuntime.installed() == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        HabboItem item = room == null ? null : room.getHabboItem(itemId);
        if (item == null) return;

        int amount = CollectorProgress.emeraldCreditValue(item.getBaseItem().getName());
        if (amount <= 0) {
            this.client.sendResponse(new NftEmeraldConvertResultComposer(itemId, NOT_REDEEMABLE));
            return;
        }
        if (item.getUserId() != habbo.getHabboInfo().getId()) {
            this.client.sendResponse(new NftEmeraldConvertResultComposer(itemId, NOT_OWNER));
            return;
        }

        int type = settings.emeraldType();
        EconomyMutationResult mutation = LedgerWalletMutation.coordinated(habbo, () -> {
            EconomyMutationResult committed = RedeemItemTransaction.commit(
                    item.getId(),
                    habbo.getHabboInfo().getId(),
                    type,
                    amount,
                    item.getBaseItem().getName());
            if (committed != null) LedgerWalletMutation.applyCommitted(habbo, type, committed.balanceAfter());
            return committed;
        });
        if (mutation == null) {
            this.client.sendResponse(new NftEmeraldConvertResultComposer(itemId, NOT_REDEEMABLE));
            return;
        }

        room.removeHabboItem(item);
        room.sendComposer(new RemoveFloorItemComposer(item).compose());
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        if (tile != null) {
            tile.setStackHeight(room.getStackHeight(item.getX(), item.getY(), false));
            room.updateTile(tile);
            room.sendComposer(
                    new UpdateStackHeightComposer(item.getX(), item.getY(), tile.z, tile.relativeHeight()).compose());
        }

        int balance = habbo.getHabboInfo().getCurrencyAmount(type);
        this.client.sendResponse(new UserPointsComposer(balance, amount, type));
        this.client.sendResponse(new CollectibleEmeraldBalanceComposer(balance));
    }
}
