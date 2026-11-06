package polaris.messages.incoming.rooms.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserTypingComposer;

public class RoomUserStopTypingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) {
            return;
        }

        if (this.client.getUser().getRoomUnit() == null) {
            return;
        }

        this.client
                .getUser()
                .getUserInfo()
                .getCurrentRoom()
                .sendComposer(new RoomUserTypingComposer(this.client.getUser().getRoomUnit(), false).compose());
    }
}
