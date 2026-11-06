package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomFilterWordsComposer;

public class RequestRoomWordFilterEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.packet.readInt());

        if (room != null && room.hasRights(this.client.getUser())) {
            this.client.sendResponse(new RoomFilterWordsComposer(room));

            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModRoomFilterSeen"));
        }
    }
}
