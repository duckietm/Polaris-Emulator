package polaris.hotel.commands;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.PermissionAuditLog;
import polaris.hotel.permissions.PermissionChange;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.users.UserPermissionsComposer;

public class UpdatePermissionsCommand extends Command {
    public UpdatePermissionsCommand() {
        super(
                "cmd_update_permissions",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_permissions")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        List<PermissionChange> changes =
                Emulator.getGameEnvironment().getPermissionsManager().reloadWithChanges();
        PermissionAuditLog.permissionChanges(
                gameClient.getUser().getUserInfo().getId(),
                gameClient.getUser().getUserInfo().getUsername(),
                changes,
                "update_permissions");

        // PermissionsManager.reload() rebuilt the rank table — each online
        // Habbo's UserInfo still references the OLD Rank object, so
        // server-side hasPermission() / wire composers would keep
        // reporting stale data until relogin. Re-bind every connected
        // user to the freshly-loaded Rank by id, then ship the new
        // UserPermissionsComposer (which carries clubLevel,
        // securityLevel, isAmbassador, rank metadata and the resolved
        // permission_definitions map) so Nitro clients' React-side
        // useHasPermission(key) / useUserRank() / useUserPermissions()
        // consumers re-render against the updated tables without an F5.
        UserManager userManager = Emulator.getGameEnvironment().getUserManager();
        PermissionsManager permissions = Emulator.getGameEnvironment().getPermissionsManager();

        int refreshed = 0;

        for (User user : userManager.getOnlineUsers().values()) {
            if (user == null || user.getUserInfo() == null || user.getClient() == null) continue;

            int currentRankId = user.getUserInfo().getRank().getId();
            // Defensive fallback: if the admin deleted the rank from the
            // permission_ranks table between sessions, fall back to rank 1
            // (Member) so the user isn't stranded with a null Rank.
            Rank freshRank =
                    permissions.rankExists(currentRankId) ? permissions.getRank(currentRankId) : permissions.getRank(1);

            user.getUserInfo().setRank(freshRank);
            user.getClient().sendResponse(new UserPermissionsComposer(user));
            refreshed++;
        }

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_permissions") + " (" + refreshed
                                + " online refreshed, " + changes.size() + " permission changes)",
                        RoomChatMessageBubbles.ALERT);

        return true;
    }
}
