package polaris.hotel.commands;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

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
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.generic.cmd_summonrank.error"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            Room target = gameClient.getUser().getUserInfo().getCurrentRoom();
            if (target == null) return true;

            // Never below the first rank, and never more people than the room holds.
            minRank = Math.max(1, minRank);
            int places = Math.max(0, target.getUsersMax() - target.getUserCount());

            for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                    .getUserManager()
                    .getOnlineUsers()
                    .entrySet()) {
                if (places <= 0) break;

                User user = set.getValue();
                if (!summons(gameClient.getUser(), user, minRank, target)) continue;

                Room current = user.getUserInfo().getCurrentRoom();
                if (current != null) {
                    Emulator.getGameEnvironment().getRoomManager().logExit(user);

                    current.removeUser(user, true);

                    user.getUserInfo().setCurrentRoom(null);
                }

                Emulator.getGameEnvironment().getRoomManager().enterRoom(user, target.getId(), "", true);

                user.getClient().sendResponse(new ForwardToRoomComposer(target.getId()));
                places--;
            }
        }

        return true;
    }

    /**
     * Whether this user is brought: of the rank asked for, one the sender may act on (as :summon checks), not
     * unkickable, not in a trade and not already there. Nobody of a higher rank is moved by a lower one.
     */
    static boolean summons(User sender, User user, int minRank, Room target) {
        if (user == null || user == sender || user.getUserInfo().getRank().getId() < minRank) return false;
        if (!CommandTargetGuard.canTarget(sender, user) || user.hasPermission(Permission.ACC_UNKICKABLE)) return false;

        Room current = user.getUserInfo().getCurrentRoom();
        if (current == target) return false;

        return current == null || current.getActiveTradeForUser(user) == null;
    }
}
