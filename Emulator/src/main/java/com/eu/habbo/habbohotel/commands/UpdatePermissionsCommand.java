package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;

public class UpdatePermissionsCommand extends Command {
    public UpdatePermissionsCommand() {
        super("cmd_update_permissions", Emulator.getTexts().getValue("commands.keys.cmd_update_permissions").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Emulator.getGameEnvironment().getPermissionsManager().reload();

        // PermissionsManager.reload() rebuilt the rank table — each online
        // Habbo's HabboInfo still references the OLD Rank object, so
        // server-side hasPermission() / wire composers would keep
        // reporting stale data until relogin. Re-bind every connected
        // user to the freshly-loaded Rank by id, then ship the new
        // UserPermissionsComposer (which carries clubLevel,
        // securityLevel, isAmbassador, rank metadata and the resolved
        // permission_definitions map) so Nitro clients' React-side
        // useHasPermission(key) / useUserRank() / useUserPermissions()
        // consumers re-render against the updated tables without an F5.
        HabboManager habboManager = Emulator.getGameEnvironment().getHabboManager();
        PermissionsManager permissions = Emulator.getGameEnvironment().getPermissionsManager();

        int refreshed = 0;

        for (Habbo habbo : habboManager.getOnlineHabbos().values()) {
            if (habbo == null || habbo.getHabboInfo() == null || habbo.getClient() == null) continue;

            int currentRankId = habbo.getHabboInfo().getRank().getId();
            // Defensive fallback: if the admin deleted the rank from the
            // permission_ranks table between sessions, fall back to rank 1
            // (Member) so the user isn't stranded with a null Rank.
            Rank freshRank = permissions.rankExists(currentRankId)
                    ? permissions.getRank(currentRankId)
                    : permissions.getRank(1);

            habbo.getHabboInfo().setRank(freshRank);
            habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
            refreshed++;
        }

        gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_update_permissions") + " (" + refreshed + " online refreshed)",
                RoomChatMessageBubbles.ALERT
        );

        return true;
    }
}
