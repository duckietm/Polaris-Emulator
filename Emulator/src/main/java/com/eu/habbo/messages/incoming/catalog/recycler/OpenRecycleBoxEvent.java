package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.items.RecycleBoxOpening;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.PresentItemOpenedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import com.eu.habbo.threading.runnables.OpenGift;

public class OpenRecycleBoxEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;
        if (!this.mayOpenIn(room)) return;

        HabboItem item = room.getHabboItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));

        if (item == null) return;
        if (item.getUserId() != this.client.getHabbo().getHabboInfo().getId()) return;

        if (item instanceof InteractionGift gift) {
            this.openGift(room, gift);
        } else if (CatalogManager.RECYCLER_BOX.equals(item.getBaseItem().getName())) {
            this.openBox(room, item);
        }
        // Any other furni is not a box: opening it would delete it with nothing in return.
    }

    /** The room's owner, a user with rights in it, or staff who may act as any room's owner. */
    private boolean mayOpenIn(Room room) {
        Habbo habbo = this.client.getHabbo();
        return room.getOwnerId() == habbo.getHabboInfo().getId()
                || habbo.hasPermission(Permission.ACC_ANYROOMOWNER)
                || room.hasRights(habbo);
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

        Emulator.getThreading().run(new OpenGift(gift, this.client.getHabbo(), room), wrapped ? 1000 : 0);
    }

    /**
     * Turns a recycler box into its reward and takes the box out of the room. Whether the box held a prize is decided
     * by its prize row, in the same transaction that deletes it; a failed opening leaves the box where it was.
     */
    private void openBox(Room room, HabboItem box) {
        RecycleBoxOpening opening =
                Emulator.getGameEnvironment().getItemManager().openRecycleBox(this.client.getHabbo(), box);

        switch (opening.result()) {
            case OPENED -> this.giveReward(box, opening.reward());
            case EMPTY ->
                this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                        Emulator.getTexts().getValue("error.recycler.box.empty"),
                        this.client.getHabbo(),
                        this.client.getHabbo(),
                        RoomChatMessageBubbles.BOT)));
            case FAILED -> {
                return;
            }
        }
        this.takeFromRoom(room, box);
    }

    private void giveReward(HabboItem box, HabboItem reward) {
        this.client.getHabbo().getInventory().getItemsComponent().addItem(reward);
        this.client.sendResponse(new AddHabboItemComposer(reward));
        this.client.sendResponse(new InventoryRefreshComposer());

        this.client.sendResponse(new PresentItemOpenedComposer(reward, box.getExtradata(), true));
    }

    /**
     * Takes the box out of the room and lowers its tile, when the tile is still there. The tile used to be refreshed
     * only for a furni whose room id was 0, which a box still in the room never has, so its tile stayed raised.
     */
    private void takeFromRoom(Room room, HabboItem box) {
        room.sendComposer(new RemoveFloorItemComposer(box).compose());
        room.removeHabboItem(box);
        RoomTile tile = room.getLayout().getTile(box.getX(), box.getY());
        if (tile == null) return;

        tile.setStackHeight(room.getStackHeight(box.getX(), box.getY(), false));
        room.updateTile(tile);
        room.sendComposer(
                new UpdateStackHeightComposer(box.getX(), box.getY(), tile.z, tile.relativeHeight()).compose());
    }
}
