package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.PermissionAuditLog;
import com.eu.habbo.habbohotel.permissions.PermissionExplanation;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import java.util.ArrayList;
import java.util.List;

/**
 * :perm check <user> <key> explains why a user has a permission key or not (rank value, plugins,
 * the result). :perm log [user] shows the newest permission audit rows, all or one user's.
 */
public class PermissionCommand extends Command {
    private static final int LOG_ROWS = 15;

    public PermissionCommand() {
        super("cmd_perm", new String[] {"perm"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        String sub = params.length > 1 ? params[1].toLowerCase() : "";

        if ("check".equals(sub) && params.length >= 4) {
            this.check(gameClient, params[2], params[3]);
        } else if ("log".equals(sub)) {
            this.log(gameClient, params.length >= 3 ? params[2] : null);
        } else {
            gameClient.getHabbo().whisper(":perm check <user> <key> | :perm log [user]", RoomChatMessageBubbles.ALERT);
        }

        return true;
    }

    private void check(GameClient gameClient, String username, String key) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        PermissionsManager permissions = environment.getPermissionsManager();
        Habbo online = environment.getHabboManager().getHabbo(username);
        HabboInfo info = online != null ? online.getHabboInfo() : HabboManager.getOfflineHabboInfo(username);

        if (info == null) {
            gameClient.getHabbo().whisper("No user called " + username + ".", RoomChatMessageBubbles.ALERT);
            return;
        }

        Rank rank = info.getRank();
        PermissionExplanation.PluginCheck plugin;

        if (rank != null && rank.hasPermission(key, false)) {
            plugin = PermissionExplanation.PluginCheck.NOT_NEEDED;
        } else if (online == null) {
            plugin = PermissionExplanation.PluginCheck.NOT_ASKED_OFFLINE;
        } else {
            plugin = permissions.hasPermission(online, key)
                    ? PermissionExplanation.PluginCheck.GRANTED
                    : PermissionExplanation.PluginCheck.REFUSED;
        }

        List<String> lines = PermissionExplanation.explain(
                info.getUsername(), key, rank, permissions.isDefinedByAnyRank(key), plugin);

        gameClient.getHabbo().alert(String.join("\r", lines));
    }

    private void log(GameClient gameClient, String username) {
        int userId = 0;

        if (username != null) {
            HabboInfo info = HabboManager.getOfflineHabboInfo(username);

            if (info == null) {
                gameClient.getHabbo().whisper("No user called " + username + ".", RoomChatMessageBubbles.ALERT);
                return;
            }

            userId = info.getId();
        }

        List<String> lines = new ArrayList<>();
        lines.add(username == null ? "Permission audit (newest first)" : "Permission audit for " + username);

        for (PermissionAuditLog.Entry entry : PermissionAuditLog.recent(userId, LOG_ROWS)) {
            lines.add(PermissionExplanation.line(entry));
        }

        if (lines.size() == 1) {
            lines.add("Nothing recorded yet.");
        }

        gameClient.getHabbo().alert(String.join("\r", lines));
    }
}
