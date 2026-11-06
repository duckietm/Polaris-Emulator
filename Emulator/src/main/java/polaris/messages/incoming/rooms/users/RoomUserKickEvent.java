package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.plugin.events.users.UserKickEvent;

public class RoomUserKickEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        int userId = this.packet.readInt();

        if (!RoomUserInputGuard.isPositiveId(userId)) return;

        User target = room.getUser(userId);

        if (target == null) return;

        if (target.hasPermission(Permission.ACC_UNKICKABLE)) {
            this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                    Emulator.getTexts()
                            .getValue("commands.error.cmd_kick.unkickable")
                            .replace("%username%", target.getUserInfo().getUsername()),
                    this.client.getUser(),
                    this.client.getUser(),
                    RoomChatMessageBubbles.ALERT)));
            return;
        }

        if (room.isOwner(target)) {
            return;
        }

        if (room.getRightsManager().canKick(this.client.getUser())
                || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)
                || this.client.getUser().hasPermission(Permission.ACC_AMBASSADOR)) {
            if (target.hasPermission(Permission.ACC_UNKICKABLE)) return;

            UserKickEvent event = new UserKickEvent(this.client.getUser(), target);
            Emulator.getPluginManager().fireEvent(event);

            if (event.isCancelled()) return;

            room.kickUser(target, true);
            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("SelfModKickSeen"));
        }
    }
}
