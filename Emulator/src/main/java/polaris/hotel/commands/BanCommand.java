package polaris.hotel.commands;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.modtool.ModToolBanType;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

public class BanCommand extends Command {
    public BanCommand() {
        super("cmd_ban", Emulator.getTexts().getValue("commands.keys.cmd_ban").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.forgot_user"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 3) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.forgot_time"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        int banTime;
        try {
            banTime = Integer.parseInt(params[2]);
        } catch (Exception e) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.invalid_time"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (banTime < 600) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.time_to_short"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params[1].equalsIgnoreCase(gameClient.getUser().getUserInfo().getUsername())) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.ban_self"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        User t = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

        UserInfo target;
        if (t != null) {
            target = t.getUserInfo();
        } else {
            target = UserManager.getOfflineUserInfo(params[1]);
        }

        if (target == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.user_offline"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getUser(), target)) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        StringBuilder reason = new StringBuilder();

        if (params.length > 3) {
            for (int i = 3; i < params.length; i++) {
                reason.append(params[i]).append(" ");
            }
        }

        List<ModToolBan> bans = Emulator.getGameEnvironment()
                .getModToolManager()
                .ban(target.getId(), gameClient.getUser(), reason.toString(), banTime, ModToolBanType.ACCOUNT, -1);
        if (bans == null || bans.isEmpty()) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.user_offline"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        ModToolBan ban = bans.get(0);
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_ban.ban_issued")
                                .replace("%user%", target.getUsername())
                                .replace("%time%", ban.expireDate - Emulator.getIntUnixTimestamp() + "")
                                .replace("%reason%", ban.reason),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }
}
