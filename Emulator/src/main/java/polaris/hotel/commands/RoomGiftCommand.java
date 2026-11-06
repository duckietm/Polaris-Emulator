package polaris.hotel.commands;

import java.util.Map;
import java.util.Random;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.wired.WiredRewardAlertComposer;

public class RoomGiftCommand extends Command {
    public RoomGiftCommand() {
        super(
                "cmd_roomgift",
                Emulator.getTexts().getValue("commands.keys.cmd_roomgift").split(";"));
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

            for (User user : gameClient.getUser().getUserInfo().getCurrentRoom().getUsers()) {
                Item giftItem = CommandGifts.pickWrapper(giftFurniture, items, random);

                if (!CommandGifts.send(
                        items, baseItem, giftItem, user.getUserInfo().getUsername(), finalMessage)) {
                    continue;
                }

                user.getClient().sendResponse(new InventoryRefreshComposer());

                user.getClient()
                        .sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_RECEIVED_ITEM));
            }

            return true;
        }

        gameClient.getUser().whisperLocalized("commands.description.cmd_roomgift", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
