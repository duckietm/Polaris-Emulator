package polaris.messages.incoming.rooms.promotions;

import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPromotion;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.navigator.CanCreateEventComposer;
import polaris.messages.outgoing.rooms.promotions.RoomPromotionMessageComposer;

public class UpdateRoomPromotionEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {

        int id = this.packet.readInt();
        String promotionName =
                RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_PROMOTION_TITLE_LENGTH);
        String promotionDescription = RoomItemInputGuard.trimToMax(
                this.packet.readString(), RoomItemInputGuard.MAX_PROMOTION_DESCRIPTION_LENGTH);
        Room room = environment().getRoomManager().getRoom(id);

        // A refusal used to be silence. The client has a sentence for each of these.
        if (room == null) {
            this.client.sendResponse(new CanCreateEventComposer(CanCreateEventComposer.ERROR_NOT_IN_GUEST_ROOM));
            return;
        }

        if (room.getOwnerId() != this.client.getUser().getUserInfo().getId()
                && !this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            this.client.sendResponse(new CanCreateEventComposer(CanCreateEventComposer.ERROR_NOT_THE_OWNER));
            return;
        }

        RoomPromotion roomPromotion = room.getPromotion();

        if (roomPromotion != null) {

            // Shown hotel-wide, so filtered like the purchase.
            roomPromotion.setTitle(environment().getWordFilter().filter(promotionName, this.client.getUser()));
            roomPromotion.setDescription(
                    environment().getWordFilter().filter(promotionDescription, this.client.getUser()));

            roomPromotion.needsUpdate = true;
            roomPromotion.save();

            room.sendComposer(new RoomPromotionMessageComposer(room, roomPromotion).compose());
        }
    }

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }
}
