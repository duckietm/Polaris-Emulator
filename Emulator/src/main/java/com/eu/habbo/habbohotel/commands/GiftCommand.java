package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
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
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_gift.not_a_number"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (itemId <= 0) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_gift.not_a_number"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemId);

            if (baseItem == null) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_gift.not_found").replace("%itemid%", itemId + ""), RoomChatMessageBubbles.ALERT);
                return true;
            }

            HabboInfo habboInfo = HabboManager.getOfflineHabboInfo(username);

            if (habboInfo == null) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_gift.user_not_found").replace("%username%", username), RoomChatMessageBubbles.ALERT);
                return true;
            }

            StringBuilder message = new StringBuilder();

            if (params.length > 3) {
                for (int i = 3; i < params.length; i++) {
                    message.append(params[i]).append(" ");
                }
            }

            final String finalMessage = message.toString();

            HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(0, baseItem, 0, 0, "");

            Item giftItem = Emulator.getGameEnvironment().getItemManager().getItem((Integer) Emulator.getGameEnvironment().getCatalogManager().giftFurnis.values().toArray()[Emulator.getRandom().nextInt(Emulator.getGameEnvironment().getCatalogManager().giftFurnis.size())]);

            String extraData = "1\t" + item.getId();
            extraData += "\t0\t0\t0\t" + finalMessage + "\t0\t0";

            Emulator.getGameEnvironment().getItemManager().createGift(username, giftItem, extraData, 0, 0);

            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_gift").replace("%username%", username).replace("%itemname%", item.getBaseItem().getName()), RoomChatMessageBubbles.ALERT);

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

        return false;
    }
}
