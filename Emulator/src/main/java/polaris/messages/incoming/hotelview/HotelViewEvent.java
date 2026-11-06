package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomVisitorQueueSupport;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewComposer;

public class HotelViewEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.getUser().getUserInfo().setLoadingRoom(0);

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .leaveRoom(
                            this.client.getUser(),
                            this.client.getUser().getUserInfo().getCurrentRoom());
        }

        if (this.client.getUser().getUserInfo().getRoomQueueId() != 0) {
            Room room = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .getRoom(this.client.getUser().getUserInfo().getRoomQueueId());

            if (room != null) {
                room.removeFromQueue(this.client.getUser());
                // AIR 13: leaving the hotel view also cancels the full-room queue.
                RoomVisitorQueueSupport.remove(room, this.client.getUser());
                this.client.getUser().getUserInfo().setRoomQueueId(0);
            } else {
                this.client.getUser().getUserInfo().setRoomQueueId(0);
            }
            this.client.sendResponse(new HotelViewComposer());
        }

        if (this.client.getUser().getRoomUnit() != null) {
            this.client.getUser().getRoomUnit().clearWalking();
            this.client.getUser().getRoomUnit().setInRoom(false);
        }
    }
}
