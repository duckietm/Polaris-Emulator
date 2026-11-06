package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.ModToolBanType;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

public class SuperbanCommand extends Command {
    public SuperbanCommand() {
        super(
                "cmd_super_ban",
                Emulator.getTexts().getValue("commands.keys.cmd_super_ban").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        UserInfo user = null;
        StringBuilder reason = new StringBuilder();
        if (params.length >= 2) {
            User h = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

            if (h != null) {
                user = h.getUserInfo();
            } else {
                user = UserManager.getOfflineUserInfo(params[1]);
            }
        }

        if (params.length > 2) {
            for (int i = 2; i < params.length; i++) {
                reason.append(params[i]);
                reason.append(" ");
            }
        }

        int count;
        if (user != null) {
            if (user == gameClient.getUser().getUserInfo()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_super_ban.ban_self"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!CommandTargetGuard.canTarget(gameClient.getUser(), user)) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            count = Emulator.getGameEnvironment()
                    .getModToolManager()
                    .ban(
                            user.getId(),
                            gameClient.getUser(),
                            reason.toString(),
                            IPBanCommand.TEN_YEARS,
                            ModToolBanType.SUPER,
                            -1)
                    .size();
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.user_offline"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_super_ban")
                                .replace("%count%", count + ""),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }
}
