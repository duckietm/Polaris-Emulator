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

public class RoomBadgeCommand extends Command {
    public RoomBadgeCommand() {
        super(
                "cmd_roombadge",
                Emulator.getTexts().getValue("commands.keys.cmd_roombadge").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient == null) return true;

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
                for (User user : gameClient.getUser().getRoomUnit().getRoom().getUsers()) {
                    if (user.isOnline()) recipients.add(user);
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
                        Emulator.getTexts().getValue("commands.error.cmd_roombadge.no_badge"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
