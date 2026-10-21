package com.eu.habbo.messages.incoming.rooms.promotions;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.RemoveRoomEventComposer;

/**
 * CancelEventMessageComposer (2725): the owner ends the room event from the event settings window
 * (AIR navigator/inroom/RoomEventViewCtrl, "end_button"). The promotion id is the room id, as in
 * RoomPromotionMessageComposer. Everyone in the room drops the event banner on RoomEventCancel (3479).
 */
public class CancelRoomPromotionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int promotionId = this.packet.readInt();
        Room room = this.currentRoom();

        if (room == null || !mayCancel(room.getId(), promotionId)) return;

        if (!room.isOwner(this.client.getHabbo())) return;

        if (!room.getPromotionManager().endPromotion((int) (System.currentTimeMillis() / 1000L))) return;

        room.sendComposer(new RemoveRoomEventComposer().compose());
    }

    /** The event of the room the user stands in, and only that one. */
    static boolean mayCancel(int roomId, int promotionId) {
        return promotionId > 0 && roomId == promotionId;
    }
}
