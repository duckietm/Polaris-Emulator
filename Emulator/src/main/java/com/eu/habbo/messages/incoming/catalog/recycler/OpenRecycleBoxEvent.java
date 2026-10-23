package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
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
        } else {
            // Any other furni is not a box: opening it would delete it with nothing in return.
            return;
        }

        if (item.getRoomId() == 0) {
            this.refreshTile(room, item);
        }
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

    /** Turns a recycler box into its reward and takes the box out of the room. */
    private void openBox(Room room, HabboItem box) {
        if (box.getExtradata().length() == 0) {
            this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                    Emulator.getTexts().getValue("error.recycler.box.empty"),
                    this.client.getHabbo(),
                    this.client.getHabbo(),
                    RoomChatMessageBubbles.BOT)));
        } else {
            HabboItem reward =
                    Emulator.getGameEnvironment().getItemManager().handleOpenRecycleBox(this.client.getHabbo(), box);

            if (reward != null) {
                this.giveReward(box, reward);
            }
        }
        room.sendComposer(new RemoveFloorItemComposer(box).compose());
        room.removeHabboItem(box);
    }

    private void giveReward(HabboItem box, HabboItem reward) {
        this.client.getHabbo().getInventory().getItemsComponent().addItem(reward);
        this.client.sendResponse(new AddHabboItemComposer(reward));
        this.client.sendResponse(new InventoryRefreshComposer());

        this.client.sendResponse(new PresentItemOpenedComposer(reward, box.getExtradata(), true));
    }

    private void refreshTile(Room room, HabboItem item) {
        room.updateTile(room.getLayout().getTile(item.getX(), item.getY()));
        RoomLayout roomLayout = room.getLayout();
        short z = (short) room.getStackHeight(item.getX(), item.getY(), true);
        if (roomLayout != null) {
            RoomTile roomTile = roomLayout.getTile(item.getX(), item.getY());
            if (roomTile != null) {
                z = roomTile.z;
            }
        }
        room.sendComposer(new UpdateStackHeightComposer(
                        item.getX(), item.getY(), z, room.getStackHeight(item.getX(), item.getY(), true))
                .compose());
    }
}
