package polaris.hotel.commands;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

public class UserInfoCommand extends Command {
    public UserInfoCommand() {
        super(
                "cmd_userinfo",
                Emulator.getTexts().getValue("commands.keys.cmd_userinfo").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_userinfo.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        User onlineUser = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);
        UserInfo user = (onlineUser != null ? onlineUser.getUserInfo() : null);

        if (user == null) {
            user = UserManager.getOfflineUserInfo(params[1]);
        }

        if (user == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_userinfo.not_found")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        StringBuilder message = new StringBuilder(Emulator.getTexts().getValue("command.cmd_userinfo.userinfo") + ": "
                + " <b>" + user.getUsername() + "</b> (<b>" + user.getId() + "</b>)\r"
                + Emulator.getTexts().getValue("command.cmd_userinfo.user_id")
                + ": " + user.getId() + "\r" + Emulator.getTexts().getValue("command.cmd_userinfo.user_name")
                + ": " + user.getUsername() + "\r" + Emulator.getTexts().getValue("command.cmd_userinfo.motto")
                + ": " + user.getMotto().replace("<", "[").replace(">", "]") + "\r"
                + Emulator.getTexts().getValue("command.cmd_userinfo.rank")
                + ": " + user.getRank().getName() + " (" + user.getRank().getId() + ") \r"
                + Emulator.getTexts().getValue("command.cmd_userinfo.online")
                + ": "
                + (onlineUser == null
                        ? Emulator.getTexts().getValue("generic.no")
                        : Emulator.getTexts().getValue("generic.yes"))
                + "\r"
                + ((user.getRank().hasPermission(Permission.ACC_HIDE_MAIL, true))
                        ? ""
                        : Emulator.getTexts().getValue("command.cmd_userinfo.email") + ": " + user.getMail() + "\r")
                + ((user.getRank().hasPermission(Permission.ACC_HIDE_IP, true))
                        ? ""
                        : Emulator.getTexts().getValue("command.cmd_userinfo.ip_register") + ": " + user.getIpRegister()
                                + "\r")
                + ((user.getRank().hasPermission(Permission.ACC_HIDE_IP, true)) || onlineUser == null
                        ? ""
                        : Emulator.getTexts().getValue("command.cmd_userinfo.ip_current") + ": "
                                + onlineUser.getUserInfo().getIpLogin() + "\r")
                + (onlineUser != null
                        ? Emulator.getTexts().getValue("command.cmd_userinfo.achievement_score") + ": "
                                + onlineUser.getUserStats().achievementScore + "\r"
                        : ""));

        ModToolBan ban = Emulator.getGameEnvironment().getModToolManager().checkForBan(user.getId());

        message.append(Emulator.getTexts().getValue("command.cmd_userinfo.total_bans"))
                .append(": ")
                .append(Emulator.getGameEnvironment().getModToolManager().totalBans(user.getId()))
                .append("\r");
        message.append(Emulator.getTexts().getValue("command.cmd_userinfo.banned"))
                .append(": ")
                .append(Emulator.getTexts().getValue(ban != null ? "generic.yes" : "generic.no"))
                .append("\r\r");
        if (ban != null) {
            message.append("<b>")
                    .append(Emulator.getTexts().getValue("command.cmd_userinfo.ban_info"))
                    .append("</b>\r");
            message.append(ban.listInfo()).append("\r");
        }

        message.append("<b>")
                .append(Emulator.getTexts().getValue("command.cmd_userinfo.currencies"))
                .append("</b>\r");
        message.append(Emulator.getTexts().getValue("command.cmd_userinfo.credits"))
                .append(": ")
                .append(user.getCredits())
                .append("\r");
        user.getCurrencies()
                .int2IntEntrySet()
                .forEach(entry -> message.append(Emulator.getTexts().getValue("seasonal.name." + entry.getIntKey()))
                        .append(": ")
                        .append(entry.getIntValue())
                        .append("\r"));
        message.append("\r")
                .append(
                        onlineUser != null
                                ? "<b>" + Emulator.getTexts().getValue("command.cmd_userinfo.current_activity")
                                        + "</b>\r"
                                : "")
                .append(
                        onlineUser != null
                                ? Emulator.getTexts().getValue("command.cmd_userinfo.room") + ": "
                                        + (onlineUser.getUserInfo().getCurrentRoom() != null
                                                ? onlineUser
                                                                .getUserInfo()
                                                                .getCurrentRoom()
                                                                .getName() + "("
                                                        + onlineUser
                                                                .getUserInfo()
                                                                .getCurrentRoom()
                                                                .getId() + ")\r"
                                                : "-")
                                : "")
                .append(
                        onlineUser != null
                                ? Emulator.getTexts().getValue("command.cmd_userinfo.respect_left") + ": "
                                        + onlineUser.getUserStats().respectPointsToGive + "\r"
                                : "")
                .append(
                        onlineUser != null
                                ? Emulator.getTexts().getValue("command.cmd_userinfo.pet_respect_left") + ": "
                                        + onlineUser.getUserStats().petRespectPointsToGive + "\r"
                                : "")
                .append(
                        onlineUser != null
                                ? Emulator.getTexts().getValue("command.cmd_userinfo.allow_trade") + ": "
                                        + ((onlineUser.getUserStats().allowTrade())
                                                ? Emulator.getTexts().getValue("generic.yes")
                                                : Emulator.getTexts().getValue("generic.no"))
                                        + "\r"
                                : "")
                .append(
                        onlineUser != null
                                ? Emulator.getTexts().getValue("command.cmd_userinfo.allow_follow") + ": "
                                        + ((onlineUser.getUserStats().blockFollowing)
                                                ? Emulator.getTexts().getValue("generic.no")
                                                : Emulator.getTexts().getValue("generic.yes"))
                                        + "\r"
                                : "")
                .append(
                        onlineUser != null
                                ? Emulator.getTexts().getValue("command.cmd_userinfo.allow_friend_request") + ": "
                                        + ((onlineUser.getUserStats().blockFriendRequests)
                                                ? Emulator.getTexts().getValue("generic.no")
                                                : Emulator.getTexts().getValue("generic.yes"))
                                        + "\r"
                                : "");

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<Map.Entry<Integer, String>> nameChanges =
                Emulator.getGameEnvironment().getUserManager().getNameChanges(user.getId(), 3);
        if (!nameChanges.isEmpty()) {
            message.append("\r<b>Latest name changes:<b><br/>");
            for (Map.Entry<Integer, String> entry : nameChanges) {
                message.append(format.format(new Date((long) entry.getKey() * 1000L)))
                        .append(" : ")
                        .append(entry.getValue())
                        .append("<br/>");
            }
        }

        if (onlineUser != null) {
            message.append("\r" + "<b>Other accounts (");

            List<UserInfo> users =
                    Emulator.getGameEnvironment().getUserManager().getCloneAccounts(onlineUser, 10);
            users.sort(new Comparator<UserInfo>() {
                @Override
                public int compare(UserInfo o1, UserInfo o2) {
                    return o1.getId() - o2.getId();
                }
            });

            message.append(users.size()).append("):</b>\r");

            message.append("<b>Username,\tID,\tDate register,\tDate last online</b>\r");

            for (UserInfo info : users) {
                message.append(info.getUsername())
                        .append(",\t")
                        .append(info.getId())
                        .append(",\t")
                        .append(format.format(new Date((long) info.getAccountCreated() * 1000L)))
                        .append(",\t")
                        .append(format.format(new Date((long) info.getLastOnline() * 1000L)))
                        .append("\r");
            }
        }
        gameClient.getUser().alert(message.toString());

        return true;
    }
}
