package polaris.hotel.commands;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.ModToolBanType;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

public class IPBanCommand extends Command {
    public static final int TEN_YEARS = 315569260;

    public IPBanCommand() {
        super(
                "cmd_ip_ban",
                Emulator.getTexts().getValue("commands.keys.cmd_ip_ban").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        UserInfo user;
        StringBuilder reason = new StringBuilder();
        if (params.length >= 2) {
            User h = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

            if (h != null) {
                user = h.getUserInfo();
            } else {
                user = UserManager.getOfflineUserInfo(params[1]);
            }
        } else {
            return true;
        }

        if (params.length > 2) {
            for (int i = 2; i < params.length; i++) {
                reason.append(params[i]);
                reason.append(" ");
            }
        }

        int count = 0;
        if (user != null) {
            if (user == gameClient.getUser().getUserInfo()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_ip_ban.ban_self"),
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

            // One ban: it already carries over to the other accounts online on the same address. Banning
            // each of them again repeated that for every one, n squared rows, events and disconnects.
            List<?> bans = Emulator.getGameEnvironment()
                    .getModToolManager()
                    .ban(user.getId(), gameClient.getUser(), reason.toString(), TEN_YEARS, ModToolBanType.IP, -1);
            count += bans != null ? bans.size() : 0;
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
                                .getValue("commands.succes.cmd_ip_ban")
                                .replace("%count%", count + ""),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }
}
