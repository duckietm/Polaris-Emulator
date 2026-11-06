package polaris.hotel.commands;

import java.util.HashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

public class GiftCommand extends Command {
    public GiftCommand() {
        super("cmd_gift", Emulator.getTexts().getValue("commands.keys.cmd_gift").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length >= 3) {
            String username = params[1];
            int itemId;

            try {
                itemId = Integer.parseInt(params[2]);
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

            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemId);

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

            UserInfo userInfo = UserManager.getOfflineUserInfo(username);

            if (userInfo == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_gift.user_not_found")
                                        .replace("%username%", username),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            StringBuilder message = new StringBuilder();

            if (params.length > 3) {
                for (int i = 3; i < params.length; i++) {
                    message.append(params[i]).append(" ");
                }
            }

            final String finalMessage = message.toString();

            ItemManager items = Emulator.getGameEnvironment().getItemManager();
            Map<Integer, Integer> giftFurniture = Emulator.getGameEnvironment()
                    .getCatalogManager()
                    .getGiftWrappingSnapshot()
                    .furniture();
            Item giftItem = CommandGifts.pickWrapper(giftFurniture, items, Emulator.getRandom());

            // The wrapper is checked before anything is created, and the content goes again if the gift fails.
            if (!CommandGifts.send(items, baseItem, giftItem, username, finalMessage)) {
                gameClient
                        .getUser()
                        .whisperLocalizedOrDefault(
                                "commands.error.cmd_gift.failed",
                                "The gift could not be sent.",
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_gift")
                                    .replace("%username%", username)
                                    .replace("%itemname%", baseItem.getName()),
                            RoomChatMessageBubbles.ALERT);

            User user = Emulator.getGameEnvironment().getUserManager().getUser(userInfo.getId());

            if (user != null) {
                user.getClient().sendResponse(new InventoryRefreshComposer());

                Map<String, String> keys = new HashMap<>();
                keys.put("display", "BUBBLE");
                keys.put("image", "${image.library.url}notifications/gift.gif");
                keys.put("message", Emulator.getTexts().getValue("generic.gift.received.anonymous"));
                user.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
            }
            return true;
        }

        gameClient.getUser().whisperLocalized("commands.description.cmd_gift", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
