package polaris.messages.incoming.catalog.recycler;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.items.RecycleBoxOpening;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.PresentItemOpenedComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.threading.runnables.OpenGift;

public class OpenRecycleBoxEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;
        if (!this.mayOpenIn(room)) return;

        UserItem item = room.getUserItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));

        if (item == null) return;
        if (item.getUserId() != this.client.getUser().getUserInfo().getId()) return;

        if (item instanceof InteractionGift gift) {
            this.openGift(room, gift);
        } else if (CatalogManager.RECYCLER_BOX.equals(item.getBaseItem().getName())) {
            this.openBox(room, item);
        }
        // Any other furni is not a box: opening it would delete it with nothing in return.
    }

    /** The room's owner, a user with rights in it, or staff who may act as any room's owner. */
    private boolean mayOpenIn(Room room) {
        User user = this.client.getUser();
        return room.getOwnerId() == user.getUserInfo().getId()
                || user.hasPermission(Permission.ACC_ANYROOMOWNER)
                || room.hasRights(user);
    }

    /**
     * Hands the gift to the delayed unwrap. The actual unwrap (OpenGift) runs async/delayed and only then removes the
     * wrapper, so a second packet would otherwise pass the room/owner checks and double-process the gift. Claim it once.
     */
    private void openGift(Room room, InteractionGift gift) {
        if (!gift.tryStartOpening()) return;

        boolean wrapped = gift.getBaseItem().getName().contains("present_wrap");
        if (wrapped) {
            gift.explode = true;
            room.updateItem(gift);
        }

        Emulator.getThreading().run(new OpenGift(gift, this.client.getUser(), room), wrapped ? 1000 : 0);
    }

    /**
     * Turns a recycler box into its reward and takes the box out of the room. Whether the box held a prize is decided
     * by its prize row, in the same transaction that deletes it; a failed opening leaves the box where it was.
     */
    private void openBox(Room room, UserItem box) {
        RecycleBoxOpening opening =
                Emulator.getGameEnvironment().getItemManager().openRecycleBox(this.client.getUser(), box);

        switch (opening.result()) {
            case OPENED -> this.giveReward(box, opening.reward());
            case EMPTY ->
                this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                        Emulator.getTexts().getValue("error.recycler.box.empty"),
                        this.client.getUser(),
                        this.client.getUser(),
                        RoomChatMessageBubbles.BOT)));
            case FAILED -> {
                return;
            }
        }
        this.takeFromRoom(room, box);
    }

    private void giveReward(UserItem box, UserItem reward) {
        this.client.getUser().getInventory().getItemsComponent().addItem(reward);
        this.client.sendResponse(new AddUserItemComposer(reward));
        this.client.sendResponse(new InventoryRefreshComposer());

        this.client.sendResponse(new PresentItemOpenedComposer(reward, box.getExtradata(), true));
    }

    /**
     * Takes the box out of the room and lowers its tile, when the tile is still there. The tile used to be refreshed
     * only for a furni whose room id was 0, which a box still in the room never has, so its tile stayed raised.
     */
    private void takeFromRoom(Room room, UserItem box) {
        room.sendComposer(new RemoveFloorItemComposer(box).compose());
        room.removeUserItem(box);
        RoomTile tile = room.getLayout().getTile(box.getX(), box.getY());
        if (tile == null) return;

        tile.setStackHeight(room.getStackHeight(box.getX(), box.getY(), false));
        room.updateTile(tile);
        room.sendComposer(
                new UpdateStackHeightComposer(box.getX(), box.getY(), tile.z, tile.relativeHeight()).compose());
    }
}
