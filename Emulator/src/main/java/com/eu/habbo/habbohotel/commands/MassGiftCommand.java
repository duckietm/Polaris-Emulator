package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredWebApiOwnership;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MassGiftCommand extends Command {
    public MassGiftCommand() {
        super(
                "cmd_massgift",
                Emulator.getTexts().getValue("commands.keys.cmd_massgift").split(";"));
    }

    @Override
    public boolean handle(final GameClient gameClient, String[] params) throws Exception {
        if (params.length >= 2) {
            int itemId;

            try {
                itemId = Integer.parseInt(params[1]);
            } catch (Exception e) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_gift.not_a_number"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (itemId <= 0) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_gift.not_a_number"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            final Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemId);

            if (baseItem == null) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_gift.not_found")
                                        .replace("%itemid%", itemId + ""),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (WiredWebApiOwnership.isWebApiItem(baseItem)) {
                gameClient.sendResponse(new BubbleAlertComposer(WiredWebApiOwnership.NOT_TRANSFERABLE_KEY));
                return true;
            }

            StringBuilder message = new StringBuilder();

            if (params.length > 2) {
                for (int i = 2; i < params.length; i++) {
                    message.append(params[i]).append(" ");
                }
            }

            final String finalMessage = message.toString();

            Map<String, String> keys = new HashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}notifications/gift.gif");
            keys.put("message", Emulator.getTexts().getValue("generic.gift.received.anonymous"));
            ServerMessage giftNotificiationMessage =
                    new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys).compose();
            Map<Integer, Integer> giftFurniture = Emulator.getGameEnvironment()
                    .getCatalogManager()
                    .getGiftWrappingSnapshot()
                    .furniture();

            ItemManager items = Emulator.getGameEnvironment().getItemManager();
            Random random = Emulator.getRandom();

            if (CommandGifts.pickWrapper(giftFurniture, items, random) == null) {
                gameClient
                        .getHabbo()
                        .whisperLocalizedOrDefault(
                                "commands.error.cmd_gift.failed",
                                "The gift could not be sent.",
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            Emulator.getThreading().run(() -> {
                for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment()
                        .getHabboManager()
                        .getOnlineHabbos()
                        .entrySet()) {
                    Habbo habbo = set.getValue();

                    // One user's failure no longer ends the loop for everyone after them, in silence.
                    Item giftItem = CommandGifts.pickWrapper(giftFurniture, items, random);

                    if (!CommandGifts.send(
                            items, baseItem, giftItem, habbo.getHabboInfo().getUsername(), finalMessage)) {
                        continue;
                    }

                    if (habbo.getClient() != null) {
                        habbo.getClient().sendResponse(new InventoryRefreshComposer());
                        habbo.getClient().sendResponse(giftNotificiationMessage);
                    }
                }
            });

            return true;
        }

        gameClient.getHabbo().whisperLocalized("commands.description.cmd_massgift", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
