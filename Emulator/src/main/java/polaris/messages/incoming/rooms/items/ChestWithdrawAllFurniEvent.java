package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestStorage;
import polaris.hotel.items.interactions.wired.chest.ChestTransactionFailures;
import polaris.hotel.items.interactions.wired.chest.ChestTransactionLog;
import polaris.hotel.items.interactions.wired.chest.ChestWithdrawals;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * Withdraw everything (AIR {@code WiredChestWrapperView.onWithdrawAllConfirmed}): {@code int chestId}.
 * Empties a furni chest into the inventory or a coin chest into the wallet.
 */
public class ChestWithdrawAllFurniEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        UserItem item = room.getUserItem(itemId);
        // A refusal is answered with its reason, not silence: 12 when this is no wired chest, 15 when
        // the chest is locked to this player or they lack the rights to withdraw.
        int refusal = ChestTransactionFailures.withdrawRefusal(item, user, room.hasRights(user));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(user, refusal);
            return;
        }

        ChestWithdrawals.Result result = chest.chestKind() == ChestStorage.KIND_CURRENCY
                ? ChestWithdrawals.coins(user, chest, room, -1, ChestTransactionLog.SOURCE_USER)
                : ChestWithdrawals.furni(
                        user, chest, room, ChestStorage::removeAllFurniItems, ChestTransactionLog.SOURCE_USER);
        if (!result.isEmpty()) ChestWithdrawals.confirm(user);
    }
}
