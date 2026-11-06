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
 * Withdraw furni of one type from a furni chest (AIR {@code FurniChestSubController}):
 * {@code int chestId, ChestItemType (bool wall, int typeId, string posterId), int amount}. The items
 * themselves leave the chest for the inventory.
 */
public class ChestWithdrawFurniEvent extends MessageHandler {
    private static final int MAX_WITHDRAW_AMOUNT = 1000;

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
        boolean wallItem = this.packet.readBoolean();
        int typeId = this.packet.readInt();
        String legacyPosterId = this.packet.readString();
        int amount = this.packet.readInt();

        UserItem item = room.getUserItem(itemId);
        // A refusal is answered with its reason, not silence: 12 when this is no wired chest, 15 when
        // the chest is locked to this player or they lack the rights to withdraw.
        int refusal = ChestTransactionFailures.withdrawRefusal(item, user, room.hasRights(user));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(user, refusal);
            return;
        }
        if (chest.chestKind() != ChestStorage.KIND_FURNI) return;

        int requested = (amount < 0) ? MAX_WITHDRAW_AMOUNT : Math.min(amount, MAX_WITHDRAW_AMOUNT);
        if (requested <= 0) return;

        ChestWithdrawals.Result result = ChestWithdrawals.furni(
                user,
                chest,
                room,
                storage -> storage.removeFurniByWireType(wallItem, typeId, legacyPosterId, requested),
                ChestTransactionLog.SOURCE_USER);
        if (!result.isEmpty()) ChestWithdrawals.confirm(user);
    }
}
