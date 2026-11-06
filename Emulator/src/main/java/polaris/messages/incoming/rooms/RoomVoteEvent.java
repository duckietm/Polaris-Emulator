package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

public class RoomVoteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int points = this.packet.readInt();

        if (points != 1 || this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        Emulator.getGameEnvironment()
                .getRoomManager()
                .voteForRoom(
                        this.client.getUser(),
                        this.client.getUser().getUserInfo().getCurrentRoom());
    }
}
