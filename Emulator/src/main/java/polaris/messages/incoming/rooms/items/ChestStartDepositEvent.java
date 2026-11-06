package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestTransactionFailures;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * Start a deposit (AIR {@code WiredChestWrapperView.onDepositClick}): {@code int chestId}.
 *
 * <p>The only way into a chest. The server answers by opening the wired trade window pointed at the
 * chest: the player puts inventory items on the table and confirms. A coin chest takes credit furni,
 * which become its coins; a furni chest takes furni. Allowed to anyone when the chest takes donations,
 * else to the owner and players with rights while the lock does not stand in their way; a refusal
 * answers fail 12 or 15.
 */
public class ChestStartDepositEvent extends MessageHandler {
    /** Long enough to go looking through a full inventory, short enough that a table is not left held. */
    private static final int DEPOSIT_TIMEOUT_SECONDS = 300;

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int chestItemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        UserItem item = room.getUserItem(chestItemId);

        int refusal = ChestTransactionFailures.depositRefusal(item, user, room.hasRights(user));
        if (!(item instanceof InteractionWiredChest chest) || refusal != ChestTransactionFailures.NONE) {
            ChestTransactionFailures.send(user, refusal);
            return;
        }

        room.getWiredRuntime().getTradingManager().openDeposit(user, chest, DEPOSIT_TIMEOUT_SECONDS);
    }
}
