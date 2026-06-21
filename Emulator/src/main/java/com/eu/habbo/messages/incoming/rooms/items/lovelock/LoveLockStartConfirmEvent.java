package com.eu.habbo.messages.incoming.rooms.items.lovelock;

import com.eu.habbo.habbohotel.items.interactions.InteractionLoveLock;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;
import com.eu.habbo.messages.outgoing.rooms.items.lovelock.LoveLockFurniFinishedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.lovelock.LoveLockFurniFriendConfirmedComposer;

public class LoveLockStartConfirmEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        if (this.packet.readBoolean()) {
            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null)
                return;

            HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(itemId);

            if (item == null)
                return;

            if (item instanceof InteractionLoveLock loveLock) {
                int userId = 0;

                if (loveLock.userOneId == this.client.getHabbo().getHabboInfo().getId() && loveLock.userTwoId != 0) {
                    userId = loveLock.userTwoId;
                } else if (loveLock.userOneId != 0 && loveLock.userTwoId == this.client.getHabbo().getHabboInfo().getId()) {
                    userId = loveLock.userOneId;
                }

                if (userId > 0) {
                    Habbo habbo = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabbo(userId);

                    if (habbo != null) {
                        habbo.getClient().sendResponse(new LoveLockFurniFriendConfirmedComposer(loveLock));

                        habbo.getClient().sendResponse(new LoveLockFurniFinishedComposer(loveLock));
                        this.client.sendResponse(new LoveLockFurniFinishedComposer(loveLock));

                        loveLock.lock(habbo, this.client.getHabbo(), this.client.getHabbo().getHabboInfo().getCurrentRoom());
                    }
                }
            }
        }
    }
}
