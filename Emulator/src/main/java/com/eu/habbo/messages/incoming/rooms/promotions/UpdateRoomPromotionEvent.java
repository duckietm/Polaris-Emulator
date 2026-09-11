package com.eu.habbo.messages.incoming.rooms.promotions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomPromotion;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;
import com.eu.habbo.messages.outgoing.navigator.CanCreateEventComposer;
import com.eu.habbo.messages.outgoing.rooms.promotions.RoomPromotionMessageComposer;

public class UpdateRoomPromotionEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {

        int id = this.packet.readInt();
        String promotionName =
                RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_PROMOTION_TITLE_LENGTH);
        String promotionDescription = RoomItemInputGuard.trimToMax(
                this.packet.readString(), RoomItemInputGuard.MAX_PROMOTION_DESCRIPTION_LENGTH);
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(id);

        // A refusal used to be silence. The client has a sentence for each of these.
        if (room == null) {
            this.client.sendResponse(new CanCreateEventComposer(CanCreateEventComposer.ERROR_NOT_IN_GUEST_ROOM));
            return;
        }

        if (room.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()
                && !this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            this.client.sendResponse(new CanCreateEventComposer(CanCreateEventComposer.ERROR_NOT_THE_OWNER));
            return;
        }

        RoomPromotion roomPromotion = room.getPromotion();

        if (roomPromotion != null) {

            roomPromotion.setTitle(promotionName);
            roomPromotion.setDescription(promotionDescription);

            roomPromotion.needsUpdate = true;
            roomPromotion.save();

            room.sendComposer(new RoomPromotionMessageComposer(room, roomPromotion).compose());
        }
    }
}
