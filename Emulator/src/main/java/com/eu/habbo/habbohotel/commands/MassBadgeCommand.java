package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;

import java.util.HashMap;
import java.util.Map;

public class MassBadgeCommand extends Command {
    public MassBadgeCommand() {
        super("cmd_massbadge", Emulator.getTexts().getValue("commands.keys.cmd_massbadge").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            String badge;

            badge = params[1];

            if (!badge.isEmpty()) {
                Map<String, String> keys = new HashMap<>();
                keys.put("display", "BUBBLE");
                keys.put("image", "${image.library.url}album1584/" + badge + ".gif");
                keys.put("message", Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
                ServerMessage message = new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys).compose();

                for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
                    Habbo habbo = set.getValue();

                    if (habbo.isOnline()) {
                        if (habbo.getInventory() != null && habbo.getInventory().getBadgesComponent() != null && !habbo.getInventory().getBadgesComponent().hasBadge(badge)) {
                            HabboBadge b = BadgesComponent.createBadge(badge, habbo);

                            if (b != null) {
                                habbo.getClient().sendResponse(new AddUserBadgeComposer(b));

                                habbo.getClient().sendResponse(message);
                            }
                        }
                    }
                }
            }
            return true;
        }
        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_massbadge.no_badge"), RoomChatMessageBubbles.ALERT);
        return true;
    }
}
