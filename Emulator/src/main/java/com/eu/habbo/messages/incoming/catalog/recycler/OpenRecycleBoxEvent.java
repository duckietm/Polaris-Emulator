package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.*;
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

        if (room == null)
            return;

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER) || room.hasRights(this.client.getHabbo())) {
            HabboItem item = room.getHabboItem(this.packet.readInt());

            if (item == null) return;
            if (item.getUserId() != this.client.getHabbo().getHabboInfo().getId()) return;

            if (item instanceof InteractionGift) {
                // The actual unwrap (OpenGift) runs async/delayed and only then
                // removes the wrapper, so a second packet would otherwise pass
                // the room/owner checks and double-process the gift. Claim it once.
                if (!((InteractionGift) item).tryStartOpening()) return;

                if (item.getBaseItem().getName().contains("present_wrap")) {
                    ((InteractionGift) item).explode = true;
                    room.updateItem(item);
                }

                Emulator.getThreading().run(new OpenGift(item, this.client.getHabbo(), room), item.getBaseItem().getName().contains("present_wrap") ? 1000 : 0);
            } else {
                if (item.getExtradata().length() == 0) {
                    this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(Emulator.getTexts().getValue("error.recycler.box.empty"), this.client.getHabbo(), this.client.getHabbo(), RoomChatMessageBubbles.BOT)));
                } else {
                    HabboItem reward = Emulator.getGameEnvironment().getItemManager().handleOpenRecycleBox(this.client.getHabbo(), item);

                    if (reward != null) {
                        this.client.getHabbo().getInventory().getItemsComponent().addItem(reward);
                        this.client.sendResponse(new AddHabboItemComposer(reward));
                        this.client.sendResponse(new InventoryRefreshComposer());

                        this.client.sendResponse(new PresentItemOpenedComposer(reward, item.getExtradata(), true));
                    }
                }
                room.sendComposer(new RemoveFloorItemComposer(item).compose());
                room.removeHabboItem(item);

            }

            if (item.getRoomId() == 0) {
                room.updateTile(room.getLayout().getTile(item.getX(), item.getY()));
                RoomLayout roomLayout = room.getLayout();
                short z = (short)room.getStackHeight(item.getX(), item.getY(), true);
                if(roomLayout != null) {
                    RoomTile roomTile = roomLayout.getTile(item.getX(), item.getY());
                    if(roomTile != null) {
                        z = roomTile.z;
                    }
                }
                room.sendComposer(new UpdateStackHeightComposer(item.getX(), item.getY(), z, room.getStackHeight(item.getX(), item.getY(), true)).compose());
            }
        }
    }
}
