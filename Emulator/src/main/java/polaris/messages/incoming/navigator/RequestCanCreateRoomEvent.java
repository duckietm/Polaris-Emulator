package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.hotel.permissions.RankLimits;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.CanCreateRoomComposer;

public class RequestCanCreateRoomEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int count = Emulator.getGameEnvironment()
                .getRoomManager()
                .getRoomsForUser(this.client.getUser())
                .size();
        int max = RankLimits.rooms(this.client.getUser());
        this.client.sendResponse(new CanCreateRoomComposer(count, max));
    }
}
