package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewComposer;
import polaris.messages.outgoing.rooms.HideDoorbellComposer;
import polaris.messages.outgoing.rooms.RoomAccessDeniedComposer;

public class HandleDoorbellEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() != null
                && this.client.getUser().getUserInfo().getCurrentRoom().hasRights(this.client.getUser())) {
            String username = this.packet.readString();
            boolean accepted = this.packet.readBoolean();

            User user = Emulator.getGameServer().getGameClientManager().getUser(username);

            if (user != null
                    && user.getUserInfo().getRoomQueueId()
                            == this.client
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getId()) {
                this.client.getUser().getUserInfo().getCurrentRoom().removeFromQueue(user);

                if (accepted) {
                    user.getClient()
                            .sendResponse(new HideDoorbellComposer(
                                    this.client
                                            .getUser()
                                            .getUserInfo()
                                            .getCurrentRoom()
                                            .getId(),
                                    ""));
                    Emulator.getGameEnvironment()
                            .getRoomManager()
                            .enterRoom(
                                    user,
                                    this.client
                                            .getUser()
                                            .getUserInfo()
                                            .getCurrentRoom()
                                            .getId(),
                                    "",
                                    true);
                } else {
                    user.getClient()
                            .sendResponse(new RoomAccessDeniedComposer(
                                    this.client
                                            .getUser()
                                            .getUserInfo()
                                            .getCurrentRoom()
                                            .getId(),
                                    ""));
                    user.getClient().sendResponse(new HotelViewComposer());
                }
                user.getUserInfo().setRoomQueueId(0);
            }
        }
    }
}
