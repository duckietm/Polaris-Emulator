package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.outgoing.inventory.InventoryBadgesComposer;
import polaris.messages.outgoing.users.UserBadgesComposer;

public class TakeBadgeCommand extends Command {
    public TakeBadgeCommand() {
        super(
                "cmd_take_badge",
                Emulator.getTexts().getValue("commands.keys.cmd_take_badge").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_take_badge.forgot_badge"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else if (params.length == 1) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_take_badge.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length == 3) {
            String username = params[1];
            String badge = params[2];

            User user = Emulator.getGameEnvironment().getUserManager().getUser(username);
            UserInfo target = user != null ? user.getUserInfo() : UserManager.getOfflineUserInfo(username);

            // Online or not, a badge is never taken from a higher rank.
            if (CommandTargetGuard.refuse(gameClient.getUser(), target)) {
                return true;
            }

            if (user != null) {
                UserBadge b = user.getInventory().getBadgesComponent().removeBadge(badge);

                if (b == null) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.error.cmd_take_badge.no_badge")
                                            .replace("%username%", username)
                                            .replace("%badge%", badge),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                user.getClient().sendResponse(new InventoryBadgesComposer(user));
                if (user.getUserInfo().getCurrentRoom() != null) {
                    user.getUserInfo()
                            .getCurrentRoom()
                            .sendComposer(new UserBadgesComposer(
                                            user.getInventory()
                                                    .getBadgesComponent()
                                                    .getWearingBadges(),
                                            user.getUserInfo().getId())
                                    .compose());
                }
            }

            int userId = target == null ? 0 : target.getId();

            if (userId > 0) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.succes.cmd_take_badge"),
                                RoomChatMessageBubbles.ALERT);

                BadgesComponent.deleteBadge(userId, badge);
            } else {
                gameClient
                        .getUser()
                        .whisperLocalized("generic.user.not_found", "%user%", username, RoomChatMessageBubbles.ALERT);
            }
        } else {
            gameClient.getUser().whisperLocalized("commands.description.cmd_take_badge", RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
