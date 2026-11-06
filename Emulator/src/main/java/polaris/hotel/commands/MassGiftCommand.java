package polaris.hotel.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

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
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_gift.not_a_number"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (itemId <= 0) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_gift.not_a_number"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            final Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemId);

            if (baseItem == null) {
                gameClient
                        .getUser()
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
                        .getUser()
                        .whisperLocalizedOrDefault(
                                "commands.error.cmd_gift.failed",
                                "The gift could not be sent.",
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            Emulator.getThreading().run(() -> {
                for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                        .getUserManager()
                        .getOnlineUsers()
                        .entrySet()) {
                    User user = set.getValue();

                    // One user's failure no longer ends the loop for everyone after them, in silence.
                    Item giftItem = CommandGifts.pickWrapper(giftFurniture, items, random);

                    if (!CommandGifts.send(
                            items, baseItem, giftItem, user.getUserInfo().getUsername(), finalMessage)) {
                        continue;
                    }

                    if (user.getClient() != null) {
                        user.getClient().sendResponse(new InventoryRefreshComposer());
                        user.getClient().sendResponse(giftNotificiationMessage);
                    }
                }
            });

            return true;
        }

        gameClient.getUser().whisperLocalized("commands.description.cmd_massgift", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
