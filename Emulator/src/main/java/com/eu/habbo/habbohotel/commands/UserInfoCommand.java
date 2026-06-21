package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.ModToolBan;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;

import java.text.SimpleDateFormat;
import java.util.*;

public class UserInfoCommand extends Command {
    public UserInfoCommand() {
        super("cmd_userinfo", Emulator.getTexts().getValue("commands.keys.cmd_userinfo").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_userinfo.forgot_username"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo onlineHabbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);
        HabboInfo habbo = (onlineHabbo != null ? onlineHabbo.getHabboInfo() : null);

        if (habbo == null) {
            habbo = HabboManager.getOfflineHabboInfo(params[1]);
        }

        if (habbo == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_userinfo.not_found").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
            return true;
        }

        StringBuilder message = new StringBuilder(Emulator.getTexts().getValue("command.cmd_userinfo.userinfo") + ": " + " <b>" + habbo.getUsername() + "</b> (<b>" + habbo.getId() + "</b>)\r" +
                Emulator.getTexts().getValue("command.cmd_userinfo.user_id") + ": " + habbo.getId() + "\r" +
                Emulator.getTexts().getValue("command.cmd_userinfo.user_name") + ": " + habbo.getUsername() + "\r" +
                Emulator.getTexts().getValue("command.cmd_userinfo.motto") + ": " + habbo.getMotto().replace("<", "[").replace(">", "]") + "\r" +
                Emulator.getTexts().getValue("command.cmd_userinfo.rank") + ": " + habbo.getRank().getName() + " (" + habbo.getRank().getId() + ") \r" +
                Emulator.getTexts().getValue("command.cmd_userinfo.online") + ": " + (onlineHabbo == null ? Emulator.getTexts().getValue("generic.no") : Emulator.getTexts().getValue("generic.yes")) + "\r" +
                ((habbo.getRank().hasPermission(Permission.ACC_HIDE_MAIL, true)) ? "" : Emulator.getTexts().getValue("command.cmd_userinfo.email") + ": " + habbo.getMail() + "\r") +
                ((habbo.getRank().hasPermission(Permission.ACC_HIDE_IP, true)) ? "" : Emulator.getTexts().getValue("command.cmd_userinfo.ip_register") + ": " + habbo.getIpRegister() + "\r") +
                ((habbo.getRank().hasPermission(Permission.ACC_HIDE_IP, true)) || onlineHabbo == null ? "" : Emulator.getTexts().getValue("command.cmd_userinfo.ip_current") + ": " + onlineHabbo.getHabboInfo().getIpLogin() + "\r") +
                (onlineHabbo != null ? Emulator.getTexts().getValue("command.cmd_userinfo.achievement_score") + ": " + onlineHabbo.getHabboStats().achievementScore + "\r" : ""));

        ModToolBan ban = Emulator.getGameEnvironment().getModToolManager().checkForBan(habbo.getId());

        message.append(Emulator.getTexts().getValue("command.cmd_userinfo.total_bans")).append(": ").append(Emulator.getGameEnvironment().getModToolManager().totalBans(habbo.getId())).append("\r");
        message.append(Emulator.getTexts().getValue("command.cmd_userinfo.banned")).append(": ").append(Emulator.getTexts().getValue(ban != null ? "generic.yes" : "generic.no")).append("\r\r");
        if (ban != null) {
            message.append("<b>").append(Emulator.getTexts().getValue("command.cmd_userinfo.ban_info")).append("</b>\r");
            message.append(ban.listInfo()).append("\r");
        }

        message.append("<b>").append(Emulator.getTexts().getValue("command.cmd_userinfo.currencies")).append("</b>\r");
        message.append(Emulator.getTexts().getValue("command.cmd_userinfo.credits")).append(": ").append(habbo.getCredits()).append("\r");
        habbo.getCurrencies().int2IntEntrySet().forEach(entry ->
                message.append(Emulator.getTexts().getValue("seasonal.name." + entry.getIntKey())).append(": ").append(entry.getIntValue()).append("\r"));
        message.append("\r").append(onlineHabbo != null ? "<b>" + Emulator.getTexts().getValue("command.cmd_userinfo.current_activity") + "</b>\r" : "").append(onlineHabbo != null ? Emulator.getTexts().getValue("command.cmd_userinfo.room") + ": " + (onlineHabbo.getHabboInfo().getCurrentRoom() != null ? onlineHabbo.getHabboInfo().getCurrentRoom().getName() + "(" + onlineHabbo.getHabboInfo().getCurrentRoom().getId() + ")\r" : "-") : "").append(onlineHabbo != null ? Emulator.getTexts().getValue("command.cmd_userinfo.respect_left") + ": " + onlineHabbo.getHabboStats().respectPointsToGive + "\r" : "").append(onlineHabbo != null ? Emulator.getTexts().getValue("command.cmd_userinfo.pet_respect_left") + ": " + onlineHabbo.getHabboStats().petRespectPointsToGive + "\r" : "").append(onlineHabbo != null ? Emulator.getTexts().getValue("command.cmd_userinfo.allow_trade") + ": " + ((onlineHabbo.getHabboStats().allowTrade()) ? Emulator.getTexts().getValue("generic.yes") : Emulator.getTexts().getValue("generic.no")) + "\r" : "").append(onlineHabbo != null ? Emulator.getTexts().getValue("command.cmd_userinfo.allow_follow") + ": " + ((onlineHabbo.getHabboStats().blockFollowing) ? Emulator.getTexts().getValue("generic.no") : Emulator.getTexts().getValue("generic.yes")) + "\r" : "").append(onlineHabbo != null ? Emulator.getTexts().getValue("command.cmd_userinfo.allow_friend_request") + ": " + ((onlineHabbo.getHabboStats().blockFriendRequests) ? Emulator.getTexts().getValue("generic.no") : Emulator.getTexts().getValue("generic.yes")) + "\r" : "");

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<Map.Entry<Integer, String>> nameChanges = Emulator.getGameEnvironment().getHabboManager().getNameChanges(habbo.getId(), 3);
        if (!nameChanges.isEmpty()) {
            message.append("\r<b>Latest name changes:<b><br/>");
            for (Map.Entry<Integer, String> entry : nameChanges) {
                message.append(format.format(new Date((long) entry.getKey() * 1000L))).append(" : ").append(entry.getValue()).append("<br/>");
            }
        }

        if (onlineHabbo != null) {
            message.append("\r" + "<b>Other accounts (");

            List<HabboInfo> users = Emulator.getGameEnvironment().getHabboManager().getCloneAccounts(onlineHabbo, 10);
            users.sort(new Comparator<HabboInfo>() {
                @Override
                public int compare(HabboInfo o1, HabboInfo o2) {
                    return o1.getId() - o2.getId();
                }
            });

            message.append(users.size()).append("):</b>\r");


            message.append("<b>Username,\tID,\tDate register,\tDate last online</b>\r");

            for (HabboInfo info : users) {
                message.append(info.getUsername()).append(",\t").append(info.getId()).append(",\t").append(format.format(new Date((long) info.getAccountCreated() * 1000L))).append(",\t").append(format.format(new Date((long) info.getLastOnline() * 1000L))).append("\r");
            }
        }
        gameClient.getHabbo().alert(message.toString());

        return true;
    }
}
