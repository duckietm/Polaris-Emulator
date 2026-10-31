package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.util.Map;

public class SummonRankCommand extends Command {
    public SummonRankCommand() {
        super(
                "cmd_summonrank",
                Emulator.getTexts().getValue("commands.keys.cmd_summonrank").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int minRank;

        if (params.length >= 2) {
            try {
                minRank = Integer.parseInt(params[1]);
            } catch (Exception e) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.generic.cmd_summonrank.error"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            Room target = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
            if (target == null) return true;

            // Never below the first rank, and never more people than the room holds.
            minRank = Math.max(1, minRank);
            int places = Math.max(0, target.getUsersMax() - target.getUserCount());

            for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment()
                    .getHabboManager()
                    .getOnlineHabbos()
                    .entrySet()) {
                if (places <= 0) break;

                Habbo habbo = set.getValue();
                if (!summons(gameClient.getHabbo(), habbo, minRank, target)) continue;

                Room current = habbo.getHabboInfo().getCurrentRoom();
                if (current != null) {
                    Emulator.getGameEnvironment().getRoomManager().logExit(habbo);

                    current.removeHabbo(habbo, true);

                    habbo.getHabboInfo().setCurrentRoom(null);
                }

                Emulator.getGameEnvironment().getRoomManager().enterRoom(habbo, target.getId(), "", true);

                habbo.getClient().sendResponse(new ForwardToRoomComposer(target.getId()));
                places--;
            }
        }

        return true;
    }

    /**
     * Whether this user is brought: of the rank asked for, one the sender may act on (as :summon checks), not
     * unkickable, not in a trade and not already there. Nobody of a higher rank is moved by a lower one.
     */
    static boolean summons(Habbo sender, Habbo habbo, int minRank, Room target) {
        if (habbo == null || habbo == sender || habbo.getHabboInfo().getRank().getId() < minRank) return false;
        if (!CommandTargetGuard.canTarget(sender, habbo) || habbo.hasPermission(Permission.ACC_UNKICKABLE))
            return false;

        Room current = habbo.getHabboInfo().getCurrentRoom();
        if (current == target) return false;

        return current == null || current.getActiveTradeForHabbo(habbo) == null;
    }
}
