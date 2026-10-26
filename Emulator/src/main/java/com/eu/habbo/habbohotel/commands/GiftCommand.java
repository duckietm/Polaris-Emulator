package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredWebApiOwnership;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import java.util.HashMap;
import java.util.Map;

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

            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemId);

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

            HabboInfo habboInfo = HabboManager.getOfflineHabboInfo(username);

            if (habboInfo == null) {
                gameClient
                        .getHabbo()
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
                        .getHabbo()
                        .whisperLocalizedOrDefault(
                                "commands.error.cmd_gift.failed",
                                "The gift could not be sent.",
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_gift")
                                    .replace("%username%", username)
                                    .replace("%itemname%", baseItem.getName()),
                            RoomChatMessageBubbles.ALERT);

            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboInfo.getId());

            if (habbo != null) {
                habbo.getClient().sendResponse(new InventoryRefreshComposer());

                Map<String, String> keys = new HashMap<>();
                keys.put("display", "BUBBLE");
                keys.put("image", "${image.library.url}notifications/gift.gif");
                keys.put("message", Emulator.getTexts().getValue("generic.gift.received.anonymous"));
                habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
            }
            return true;
        }

        gameClient.getHabbo().whisperLocalized("commands.description.cmd_gift", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
