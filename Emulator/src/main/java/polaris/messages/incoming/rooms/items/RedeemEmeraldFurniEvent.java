package polaris.messages.incoming.rooms.items;

import polaris.hotel.collectibles.CollectiblesRuntime;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.collectibles.CollectorProgress;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectibleEmeraldBalanceComposer;
import polaris.messages.outgoing.collectibles.NftEmeraldConvertResultComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.users.UserPointsComposer;

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
        User user = this.client.getUser();
        if (user == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        CollectiblesSettings settings = CollectiblesRuntime.currentSettings();
        if (!settings.enabled() || !settings.emeraldCreditEnabled() || CollectiblesRuntime.installed() == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        UserItem item = room == null ? null : room.getUserItem(itemId);
        if (item == null) return;

        int amount = CollectorProgress.emeraldCreditValue(item.getBaseItem().getName());
        if (amount <= 0) {
            this.client.sendResponse(new NftEmeraldConvertResultComposer(itemId, NOT_REDEEMABLE));
            return;
        }
        if (item.getUserId() != user.getUserInfo().getId()) {
            this.client.sendResponse(new NftEmeraldConvertResultComposer(itemId, NOT_OWNER));
            return;
        }

        int type = settings.emeraldType();
        EconomyMutationResult mutation = LedgerWalletMutation.coordinated(user, () -> {
            EconomyMutationResult committed = RedeemItemTransaction.commit(
                    item.getId(),
                    user.getUserInfo().getId(),
                    type,
                    amount,
                    item.getBaseItem().getName());
            if (committed != null) LedgerWalletMutation.applyCommitted(user, type, committed.balanceAfter());
            return committed;
        });
        if (mutation == null) {
            this.client.sendResponse(new NftEmeraldConvertResultComposer(itemId, NOT_REDEEMABLE));
            return;
        }

        room.removeUserItem(item);
        room.sendComposer(new RemoveFloorItemComposer(item).compose());
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        if (tile != null) {
            tile.setStackHeight(room.getStackHeight(item.getX(), item.getY(), false));
            room.updateTile(tile);
            room.sendComposer(
                    new UpdateStackHeightComposer(item.getX(), item.getY(), tile.z, tile.relativeHeight()).compose());
        }

        int balance = user.getUserInfo().getCurrencyAmount(type);
        this.client.sendResponse(new UserPointsComposer(balance, amount, type));
        this.client.sendResponse(new CollectibleEmeraldBalanceComposer(balance));
    }
}
