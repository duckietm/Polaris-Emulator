package com.eu.habbo.messages.incoming.rooms.items.lovelock;

import com.eu.habbo.habbohotel.items.interactions.InteractionLoveLock;
import com.eu.habbo.habbohotel.rooms.Room;
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

        boolean confirmed = this.packet.readBoolean();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionLoveLock loveLock))
            return;

        Habbo self = this.client.getHabbo();
        if (self == null)
            return;

        if (!confirmed) {
            loveLock.cancel(self);
            return;
        }

        int selfId = self.getHabboInfo().getId();
        int partnerId = 0;

        if (loveLock.userOneId == selfId) {
            loveLock.userOneConfirmed = true;
            partnerId = loveLock.userTwoId;
        } else if (loveLock.userTwoId == selfId) {
            loveLock.userTwoConfirmed = true;
            partnerId = loveLock.userOneId;
        } else {
            return;
        }

        if (partnerId <= 0)
            return;

        Habbo partner = room.getHabbo(partnerId);
        if (partner == null || partner.getClient() == null)
            return;

        if (loveLock.userOneConfirmed && loveLock.userTwoConfirmed) {
            Habbo userOne = room.getHabbo(loveLock.userOneId);
            Habbo userTwo = room.getHabbo(loveLock.userTwoId);

            if (userOne != null && userTwo != null && loveLock.lock(userOne, userTwo, room)) {
                userOne.getClient().sendResponse(new LoveLockFurniFinishedComposer(loveLock));
                userTwo.getClient().sendResponse(new LoveLockFurniFinishedComposer(loveLock));
            }

            loveLock.resetSession();
            return;
        }

        partner.getClient().sendResponse(new LoveLockFurniFriendConfirmedComposer(loveLock));
    }
}
