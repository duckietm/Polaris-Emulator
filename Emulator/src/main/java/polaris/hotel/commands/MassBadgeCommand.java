package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.users.AddUserBadgeComposer;

public class MassBadgeCommand extends Command {
    public MassBadgeCommand() {
        super(
                "cmd_massbadge",
                Emulator.getTexts().getValue("commands.keys.cmd_massbadge").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            String badge;

            badge = params[1];

            if (!badge.isEmpty()) {
                if (!BadgesComponent.isValidBadgeCode(badge)) {
                    gameClient
                            .getUser()
                            .whisperLocalizedOrDefault(
                                    "commands.error.cmd_badge.invalid_code",
                                    "A badge code is 1 to 32 letters, digits or underscores.",
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                Map<String, String> keys = new HashMap<>();
                keys.put("display", "BUBBLE");
                keys.put("image", "${image.library.url}album1584/" + badge + ".gif");
                keys.put("message", Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
                ServerMessage message = new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys).compose();

                List<User> recipients = new ArrayList<>();
                for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                        .getUserManager()
                        .getOnlineUsers()
                        .entrySet()) {
                    if (set.getValue().isOnline()) recipients.add(set.getValue());
                }

                BadgesComponent.grantBadge(recipients, badge, (user, b) -> {
                    if (user.getClient() == null) return;

                    user.getClient().sendResponse(new AddUserBadgeComposer(b));
                    user.getClient().sendResponse(message);
                });
            }
            return true;
        }
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_massbadge.no_badge"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
