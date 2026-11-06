package polaris.threading.runnables;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionCrackable;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.items.AddFloorItemComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;

public class CrackableExplode implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrackableExplode.class);

    private final Room room;
    private final InteractionCrackable userItem;
    private final User user;
    private final boolean toInventory;
    private final short x;
    private final short y;

    public CrackableExplode(Room room, InteractionCrackable item, User user, boolean toInventory, short x, short y) {
        this.room = room;
        this.userItem = item;
        this.user = user;
        this.toInventory = toInventory;

        this.x = x;
        this.y = y;
    }

    @Override
    public void run() {
        if (this.userItem.getRoomId() == 0) {
            return;
        }

        // A temporary copy (the "place furni" wired) has no database row and no owner:
        // breaking one must not mint the reward furni. Second line of defence - saving
        // a crackable as that box's template is refused too.
        if (this.userItem.isTemporary()) {
            return;
        }

        Item rewardItem = Emulator.getGameEnvironment()
                .getItemManager()
                .getCrackableReward(this.userItem.getBaseItem().getId());
        boolean deleteCrackable = !this.userItem.resetable();

        // The crackable's row goes and the reward's row comes in one transaction: before, the box was
        // deleted first and a failed reward insert lost it, or a failed delete let it be cracked again.
        UserItem newItem;
        try {
            newItem = Emulator.getGameEnvironment()
                    .getItemManager()
                    .crackInTransaction(
                            this.userItem,
                            deleteCrackable,
                            this.userItem.allowAnyone()
                                    ? this.user.getUserInfo().getId()
                                    : this.userItem.getUserId(),
                            rewardItem);
        } catch (SQLException e) {
            LOGGER.error("Crackable {} could not be paid out, it stays as it was", this.userItem.getId(), e);
            return;
        }

        if (deleteCrackable) {
            this.room.removeUserItem(this.userItem);
            this.room.sendComposer(new RemoveFloorItemComposer(this.userItem, true).compose());
            this.userItem.setRoomId(0);
        } else {
            this.userItem.reset(this.room);
        }

        if (newItem != null) {
            // Add to inventory in case if isn't possible place the item or in case is wall item
            if (this.toInventory || newItem.getBaseItem().getType() == FurnitureType.WALL) {
                this.user.getInventory().getItemsComponent().addItem(newItem);
                this.user.getClient().sendResponse(new AddUserItemComposer(newItem));
                this.user.getClient().sendResponse(new InventoryRefreshComposer());
            } else {
                newItem.setX(this.x);
                newItem.setY(this.y);
                newItem.setZ(this.room.getStackHeight(this.x, this.y, false));
                newItem.setRoomId(this.room.getId());
                newItem.needsUpdate(true);
                this.room.addUserItem(newItem);
                this.room.updateItem(newItem);
                this.room.sendComposer(new AddFloorItemComposer(
                                newItem, this.room.getFurniOwnerNames().get(newItem.getUserId()))
                        .compose());
            }
        }

        this.room.updateTile(this.room.getLayout().getTile(this.x, this.y));
    }
}
