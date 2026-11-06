package polaris.messages.incoming.habbicons;

import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredHabbiconVariables;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.habbicons.UserHabbiconsComposer;
import polaris.messages.outgoing.rooms.users.RoomUseHabbiconComposer;

public class TriggerHabbiconEvent extends MessageHandler {
    private static final int MAX_HABBICON_ID = 1000000;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null || user.getRoomUnit() == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int habbiconId = this.packet.readInt();
        if (habbiconId <= 0 || habbiconId > MAX_HABBICON_ID) return;

        if (!user.hasPermission(Permission.ACC_NOMUTE)
                && (room.isMuted(user) || room.isMuted() && !room.hasRights(user))) {
            return;
        }

        HabbiconService habbicons = user.getHabbiconService();
        if (!user.getUserStats().allowTalk()
                || (room.getChatManager() != null && !room.getChatManager().allowsHabbicon(user))
                || !habbicons.use(user.getUserInfo().getId(), habbiconId)) {
            return;
        }

        room.sendComposer(new RoomUseHabbiconComposer(user.getRoomUnit(), habbiconId).compose());
        WiredHabbiconVariables.used(room, user.getRoomUnit(), habbiconId);
        polaris.hotel.quests.QuestProgressEvents.progress(user, polaris.hotel.quests.QuestGoalType.USE_HABBICON, 1);
        this.client.sendResponse(
                new UserHabbiconsComposer(habbicons.load(user.getUserInfo().getId())));
    }
}
