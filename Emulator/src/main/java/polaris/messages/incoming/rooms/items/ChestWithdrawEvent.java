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
 * Withdraw coins from a coin chest (AIR {@code CoinChestSubController}): {@code int chestId, int
 * amount}. The coins reach the wallet as credits. Allowed to the owner and to players with rights
 * while the lock does not stand in their way; a refusal answers fail 12 (no wired chest) or 15
 * (locked or no rights).
 */
public class ChestWithdrawEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int amount = this.packet.readInt();
        if (amount <= 0) return;

        UserItem item = room.getUserItem(itemId);
        int refusal = ChestTransactionFailures.withdrawRefusal(item, user, room.hasRights(user));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(user, refusal);
            return;
        }
        if (chest.chestKind() != ChestStorage.KIND_CURRENCY) return;

        ChestWithdrawals.Result result =
                ChestWithdrawals.coins(user, chest, room, amount, ChestTransactionLog.SOURCE_USER);
        if (!result.isEmpty()) ChestWithdrawals.confirm(user);
    }
}
